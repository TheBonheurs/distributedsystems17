import java.security.MessageDigest

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.collection.mutable

object DistributedHashTable {

  sealed trait Response

  final case object OK extends Response


  sealed trait Command

  final case class AddNode(ringNode: RingNode, replyTo: ActorRef[Response]) extends Command

  final case class ResetRing(replyTo: ActorRef[Response]) extends Command

  final case class GetRing(replyTo: ActorRef[List[RingNode]]) extends Command

  final case class GetTopN(hash: BigInt, n: Int, replyTo: ActorRef[List[RingNode]]) extends Command

  implicit val order: Ordering[RingNode] = Ordering.by(node => node.position)

  def apply(ring: List[RingNode] = List.empty): Behavior[Command] = Behaviors.receiveMessage {
    case AddNode(ringNode, replyTo) =>
      replyTo ! OK
      DistributedHashTable((ringNode :: ring).sorted)
    case ResetRing(replyTo) =>
      replyTo ! OK
      DistributedHashTable(List.empty)
    case GetRing(replyTo) =>
      replyTo ! ring
      Behaviors.same

    case GetTopN(hash, n, replyTo) =>
      val toEnd = ring.filter(r => r.position > hash).sortBy((node: RingNode) => node.position)
      val taken = toEnd.take(n)
      // If needed, wrap around the ring
      if (taken.length < n) {
        replyTo ! taken ::: ring.sortBy((node: RingNode) => node.position).take(n - taken.length)
      } else {
        replyTo ! taken
      }
      Behaviors.same
  }

  private val ring: mutable.ListBuffer[RingNode] = scala.collection.mutable.ListBuffer.empty[RingNode]

  // Add a node to the ring
  def addNode(ringnode: RingNode): Boolean = {
    ring.addOne(ringnode)
    true
  }

  // Add multiple nodes at once
  def addNodes(seq: Seq[RingNode]): Boolean = {
    ring.addAll(seq)
    true
  }

  // Clear the ring
  def resetRing: Boolean = {
    ring.clear()
    true
  }

  // Getter for ring
  def getRing: List[RingNode] = {
    ring.sortBy((node: RingNode) => node.position).toList
  }

  // Get the MD5 hash of a key as a BigInt
  def getHash(key: String): BigInt = {
    val hash = MessageDigest.getInstance("MD5").digest(key.getBytes)
    // We need to convert it to a hex string in order to make it unsigned
    val hexString = DistributedHashTable.convertBytesToHex(hash)
    // Radix 16 because we use hex
    BigInt(hexString, 16)
  }

  // Helper method that converts bytes to a hex string
  def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }
}
