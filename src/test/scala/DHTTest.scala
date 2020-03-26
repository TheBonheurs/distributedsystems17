import java.security.MessageDigest

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DHTTest extends AnyFlatSpec with Matchers with BeforeAndAfter {
  // Run before each test
  before {
    DHT.resetRing
  }

  "abc" should "result in correct hash " in {
    val res = DHT.getHash("abc")
    val hash = MessageDigest.getInstance("MD5").digest("abc".getBytes)
    val hexString = DHT.convertBytesToHex(hash)
    res should be (BigInt(hexString, 16))
  }

  "add node" should "add a node to the ring" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000)
    val node2 = RingNode(BigInt(10), "localhost", 8001)

    DHT.addNode(node2)
    DHT.addNode(node1)

    val ring = DHT.getRing
    ring.head should be (node1)
    ring(1) should be (node2)
  }

  "preference list" should "return top N nodes" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000)
    val node2 = RingNode(BigInt(100), "localhost", 8001)
    val node3 = RingNode(BigInt(200), "localhost", 8002)
    val node4 = RingNode(BigInt(300), "localhost", 8003)
    val node5 = RingNode(BigInt(400), "localhost", 8004)

    DHT.addNode(node2)
    DHT.addNode(node4)
    DHT.addNode(node5)
    DHT.addNode(node3)
    DHT.addNode(node1)

    val list = DHT.getTopNPreferenceNodes(150, 3)
    list should be (List(node3, node4, node5))
  }

  "preference list" should "return top N nodes circularly" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000)
    val node2 = RingNode(BigInt(100), "localhost", 8001)
    val node3 = RingNode(BigInt(200), "localhost", 8002)
    val node4 = RingNode(BigInt(300), "localhost", 8003)
    val node5 = RingNode(BigInt(400), "localhost", 8004)

    DHT.addNode(node2)
    DHT.addNode(node4)
    DHT.addNode(node5)
    DHT.addNode(node3)
    DHT.addNode(node1)

    val list = DHT.getTopNPreferenceNodes(250, 4)
    list should be (List(node4, node5, node1, node2))
  }
}
