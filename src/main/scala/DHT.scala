import java.security.MessageDigest

import scala.collection.mutable
object DHT {
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
    val hexString = DHT.convertBytesToHex(hash)
    // Radix 16 because we use hex
    BigInt(hexString, 16)
  }

  // Get Top N nodes from preference list depending on hash location
  def getTopNPreferenceNodes(hash: BigInt, n: Int): List[RingNode] = {
    val toEnd = ring.filter(r => r.position > hash).sortBy((node: RingNode) => node.position)
    val taken = toEnd.take(n).toList
    // If needed, wrap around the ring
    if (taken.length < n) {
      val rest = ring.sortBy((node: RingNode) => node.position).take(n - taken.length).toList
      taken ::: rest
    } else {
      taken
    }
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
