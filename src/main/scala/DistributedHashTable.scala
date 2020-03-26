import java.security.MessageDigest

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

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
