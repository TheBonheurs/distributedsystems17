import java.security.MessageDigest

import scala.collection.mutable
object DHT {
  val ring: mutable.ListBuffer[RingNode] = scala.collection.mutable.ListBuffer.empty[RingNode]

  def addNode(ringnode: RingNode): ring.type = {
    ring.addOne(ringnode)
  }

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
    ring.filter(r => r.position > hash).take(n).sortBy((node: RingNode) => node.position).toList
  }

  def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }
}
