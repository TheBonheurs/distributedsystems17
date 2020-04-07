package dynamodb.node

import akka.actor.typed.ActorSystem
import dynamodb.node.Node.Stop
import dynamodb.node.mainObj.NodeConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.Http

class BenchmarkSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private val node1 = "node1"
  private val node2 = "node2"
  private val node3 = "node3"
  private val node4 = "node4"

  private val host1Config = NodeConfig(BigInt("25"), node1, "localhost", 8001, "localhost", 9001)
  private val host2Config = NodeConfig(BigInt("50"), node2, "localhost", 8002, "localhost", 9002)
  private val host3Config = NodeConfig(BigInt("75"), node3, "localhost", 8003, "localhost", 9003)
  private val host4Config = NodeConfig(BigInt("100"), node4, "localhost", 8004, "localhost", 9004)

  private val host1 = s"http://${host1Config.externalHost}:${host1Config.externalPort}"
  private val host2 = s"http://${host2Config.externalHost}:${host2Config.externalPort}"
  private val host3 = s"http://${host3Config.externalHost}:${host3Config.externalPort}"
  private val host4 = s"http://${host4Config.externalHost}:${host4Config.externalPort}"

  private val hostToUrl = Map(
    node1 -> host1,
    node2 -> host2,
    node3 -> host3,
    node4 -> host4,
  )

  var cluster: List[ActorSystem[Node.Message]] = List()

  override def beforeAll {
    val nodes = List(host1Config, host2Config, host3Config, host4Config)

    cluster = nodes.map(n => ActorSystem(Node(n, nodes), n.name))

    // ActorSytem needs some time to boot, nothing implemented yet to check this.
    Thread.sleep(1000)
  }

  override def afterAll {
    cluster.foreach(n => n ! Stop)
  }

  private def getCoordinatorUrlForKey(key: String): String = {
    val hash = DistributedHashTable.getHash(key)
    if (hash < 25) node1
    else if (hash < 50) node2
    else if (hash < 75) node3
    else node4
  }

  private def get(host: String, path: String) =
    Http(s"$host$path").asString

  private def delete(host: String, path: String) =
    Http(s"$host$path").method("DELETE").asString

  private def post(host: String, path: String, json: String) =
    Http(s"$host$path")
      .postData(json)
      .header("content-type", "application/json")
      .asString


  private def getBench(key: String): Long = {
    val coordinator = getCoordinatorUrlForKey(key)
    val coordinatorUrl = hostToUrl(coordinator)
    val start = System.nanoTime()
    get(coordinatorUrl, "/values/myKey")
      .body should be(s"""{"key":"myKey","value":"myValue","version":{"$coordinator":0}}""")
    val end = System.nanoTime()
    val time = end - start
    time
  }

  // See https://gist.github.com/softprops/3936429 for mean and std dev code
  def mean(xs: List[Long]): Double = xs match {
    case Nil => 0.0
    case ys => ys.sum / ys.size.toDouble
  }

  def stddev(xs: List[Long], avg: Double): Double = xs match {
    case Nil => 0.0
    case ys => math.sqrt((0.0 /: ys) {
      (a,e) => a + math.pow(e - avg, 2.0)
    } / xs.size)
  }

  "The cluster" should {
    /**
     * The cluster is kept online during all the tests, so make sure that you use unique keys for each test.
     */
    "survive get benchmark" in {
      val coordinator = getCoordinatorUrlForKey("myKey")
      val coordinatorUrl = hostToUrl(coordinator)
      post(coordinatorUrl, "/values", """{"key": "myKey", "value": "myValue"}""")
        .body should be("Value added")
      var latencies: List[Long] = List.empty
      for (i <- 0 until 1000) {
        val res = getBench("myKey")
//        println(res + " ns")
        latencies = res :: latencies
      }
      val avg = mean(latencies)
      val std = stddev(latencies, avg)
      println("Mean: %.4f ms".format(avg/1000000))
      println("Std: %.4f ms".format(std/1000000))
      println(latencies)
    }
  }
}
