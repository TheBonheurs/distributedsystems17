package dynamodb.node

import akka.actor.typed.ActorSystem
import dynamodb.node.Node.Stop
import dynamodb.node.mainObj.NodeConfig
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.Http

class ClusterSpec extends AnyWordSpec with Matchers with BeforeAndAfter {
  var cluster: List[ActorSystem[Node.Message]] = List()

  private val host1Config = NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001)
  private val host2Config = NodeConfig(BigInt("20"), "node1", "localhost", 8002, "localhost", 9002)
  private val host3Config = NodeConfig(BigInt("50"), "node1", "localhost", 8003, "localhost", 9003)
  private val host4Config = NodeConfig(BigInt("90"), "node1", "localhost", 8004, "localhost", 9004)

  private val host1 = s"http://${host1Config.externalHost}:${host1Config.externalPort}"
  private val host2 = s"http://${host2Config.externalHost}:${host2Config.externalPort}"
  private val host3 = s"http://${host3Config.externalHost}:${host3Config.externalPort}"
  private val host4 = s"http://${host4Config.externalHost}:${host4Config.externalPort}"

  before {
    val nodes = List(host1Config, host2Config, host3Config, host4Config)

    cluster = nodes.map(n => ActorSystem(Node(n, nodes), n.name))

    // ActorSytem needs some time to boot, nothing implemented yet to check this.
    Thread.sleep(1000)
  }

  after {
    cluster.foreach(n => n ! Stop)
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
    "start" in {
      post(host1, "/values",  """{"key": "myKey", "value": "myValue", "version": {}}""")
        .body should be("Value added")

      // This host should know about it
      get(host1, "/values/myKey")
        .body should be("""{"key":"myKey","value":"myValue","version":{"node1":0}}""")

      // It should be replicated here
      get(host2, "/values/myKey")
        .body should be("""{"key":"myKey","value":"myValue","version":{"node1":0}}""")
    }
  }
}
