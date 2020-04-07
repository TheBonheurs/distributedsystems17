package dynamodb.node

import akka.actor.typed.ActorSystem
import akka.cluster.VectorClock
import dynamodb.node.Node.Stop
import dynamodb.node.ValueRepository.Value
import dynamodb.node.mainObj.NodeConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.{Http, HttpResponse}
import spray.json.JsonWriter

import scala.collection.immutable.TreeMap

class BenchmarkSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  import JsonSupport._
  import spray.json._

  private val node1 = "node1"
  private val node2 = "node2"
  private val node3 = "node3"
  private val node4 = "node4"
  private val node5 = "node5"
  private val node6 = "node6"
  private val node7 = "node7"

  private val host1Config = NodeConfig(BigInt("25"), node1, "localhost", 8001, "localhost", 9001)
  private val host2Config = NodeConfig(BigInt("50"), node2, "localhost", 8002, "localhost", 9002)
  private val host3Config = NodeConfig(BigInt("60"), node3, "localhost", 8003, "localhost", 9003)
  private val host4Config = NodeConfig(BigInt("70"), node4, "localhost", 8004, "localhost", 9004)
  private val host5Config = NodeConfig(BigInt("80"), node5, "localhost", 8005, "localhost", 9005)
  private val host6Config = NodeConfig(BigInt("90"), node6, "localhost", 8006, "localhost", 9006)
  private val host7Config = NodeConfig(BigInt("100"), node7, "localhost", 8007, "localhost", 9007)

  private val host1 = s"http://${host1Config.externalHost}:${host1Config.externalPort}"
  private val host2 = s"http://${host2Config.externalHost}:${host2Config.externalPort}"
  private val host3 = s"http://${host3Config.externalHost}:${host3Config.externalPort}"
  private val host4 = s"http://${host4Config.externalHost}:${host4Config.externalPort}"
  private val host5 = s"http://${host5Config.externalHost}:${host5Config.externalPort}"
  private val host6 = s"http://${host6Config.externalHost}:${host6Config.externalPort}"
  private val host7 = s"http://${host7Config.externalHost}:${host7Config.externalPort}"

  private val hostToUrl = Map(
    node1 -> host1,
    node2 -> host2,
    node3 -> host3,
    node4 -> host4,
    node5 -> host5,
    node5 -> host5,
    node5 -> host5,
  )

  var cluster: List[ActorSystem[Node.Message]] = List()

  override def beforeAll {
    val nodes = List(host1Config, host2Config, host3Config, host4Config, host5Config, host6Config, host7Config)

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
    else if (hash < 60) node3
    else if (hash < 70) node4
    else if (hash < 80) node5
    else if (hash < 90) node6
    else node7
  }

  private def get(host: String, path: String) =
    Http(s"$host/values/$path").asString

  private def post[T](host: String, json: T)(implicit writer: JsonWriter[T]): HttpResponse[String] =
    Http(s"$host/values")
      .postData(json.toJson.compactPrint)
      .header("content-type", "application/json")
      .asString


  private def getBench(key: String): Long = {
    val coordinator = getCoordinatorUrlForKey(key)
    val coordinatorUrl = hostToUrl(coordinator)
    val start = System.nanoTime()
    get(coordinatorUrl, key)
      .body.parseJson.convertTo[Value] should be(Value(key, "myValue", new VectorClock(TreeMap(coordinator -> 0))))
    val end = System.nanoTime()
    val time = end - start
    time
  }

  def putBench(key: String, version: Long): Long = {
    val coordinator = getCoordinatorUrlForKey(key)
    val coordinatorUrl = hostToUrl(coordinator)
    val value = get(coordinatorUrl, key)
      .body.parseJson.convertTo[Value]
    value should be(Value(key, "myValue"+version, new VectorClock(TreeMap(coordinator -> version))))
    val start = System.nanoTime()
    post(coordinatorUrl, Value(key, "myValue" + (version+1), value.version))
      .body should be("Value added")
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
      post(coordinatorUrl, Value("myKey", "myValue"))
        .body should be("Value added")
      val res = for (_ <- 0 until 10000) yield getBench("myKey")
      val latencies = res.toList
      val avg = mean(latencies)
      val std = stddev(latencies, avg)
      println("Get Mean: %.4f ms".format(avg/1000000))
      println("Get Std dev: %.4f ms".format(std/1000000))
      println(latencies)
    }

    "survive put benchmark" in  {
      val coordinator = getCoordinatorUrlForKey("putKey")
      val coordinatorUrl = hostToUrl(coordinator)
      post(coordinatorUrl, Value("putKey", "myValue0"))
        .body should be("Value added")
      val res = for (i <- 0 until 10000) yield putBench("putKey", i)
      val latencies: List[Long] = res.toList
      val avg = mean(latencies)
      val std = stddev(latencies, avg)
      println("Put Mean: %.4f ms".format(avg/1000000))
      println("Put Std dev: %.4f ms".format(std/1000000))
      println(latencies)
    }
  }
}