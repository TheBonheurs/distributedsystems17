package dynamodb.node

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import dynamodb.node.Node.Stop
import dynamodb.node.mainObj.NodeConfig
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.Http

class ClusterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
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
    Thread.sleep(2400)
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

  "The cluster" should {
    /**
     * The cluster is kept online during all the tests, so make sure that you use unique keys for each test.
     */

    "start" in {
      val coordinator = getCoordinatorUrlForKey("myKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, "/values", """{"key": "myKey", "value": "myValue"}""")
        .body should be("Value added")

      // This host should know about it
      get(coordinatorUrl, "/values/myKey")
        .body should be(s"""{"key":"myKey","value":"myValue","version":{"${coordinator}":0}}""")

      // It should be replicated here
      get(host1, "/values/myKey")
        .body should be(s"""{"key":"myKey","value":"myValue","version":{"${coordinator}":0}}""")
    }

    "update a value" in {
      val coordinator = getCoordinatorUrlForKey("updateKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, "/values", """{"key": "updateKey", "value": "myValue"}""")
        .body shouldBe "Value added"
      post(coordinatorUrl, "/values", s"""{"key": "updateKey", "value": "myOverrideValue", "version": {"${coordinator}": 0}}""")
        .body shouldBe "Value added"

      get(coordinatorUrl, "/values/updateKey")
        .body should be(s"""{"key":"updateKey","value":"myOverrideValue","version":{"${coordinator}":1}}""")
    }

    "reject overriding a value with wrong version" in {
      val coordinator = getCoordinatorUrlForKey("rejectedKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, "/values", """{"key": "rejectedKey", "value": "myValue"}""")
          .body shouldBe "Value added"
      post(coordinatorUrl, "/values", s"""{"key": "rejectedKey", "value": "myUpdatedValue", "version": {"${coordinator}": 0}}""")
        .body shouldBe "Value added"
      post(coordinatorUrl, "/values", s"""{"key": "rejectedKey", "value": "myOtherUpdatedValue", "version": {"${coordinator}": 1}}""")
        .body shouldBe "Value added"
      post(coordinatorUrl, "/values", s"""{"key": "rejectedKey", "value": "myRejectedValue", "version": {"${coordinator}": 1}}""")
        .code shouldBe 400
    }

    "fail read after top N nodes timeout " in {
      val coordinator = getCoordinatorUrlForKey("timeKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, "/values", """{"key": "timeKey", "value": "1"}""")
        .body shouldBe "Value added"

      // THis should succeed
      get(coordinatorUrl, "/values/timeKey")
        .body should be(s"""{"key":"timeKey","value":"1","version":{"${coordinator}":0}}""")

      cluster(3) ! Node.Sleep(10000)
      // This should fail
      get(coordinatorUrl, "/values/timeKey")
        .code shouldBe 200 // dit moet falen
    }
  }
}
