import Node.Stop
import akka.actor.typed.ActorSystem
import main.NodeConfig
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.Http

class ClusterSpec extends AnyWordSpec with Matchers with BeforeAndAfter {
  var cluster: List[ActorSystem[Node.Message]] = List()

  before {
    val nodes = List(
      NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001),
      NodeConfig(BigInt("20"), "node2", "localhost", 8002, "localhost", 9002),
      NodeConfig(BigInt("50"), "node3", "localhost", 8003, "localhost", 9003),
      NodeConfig(BigInt("90"), "node4", "localhost", 8004, "localhost", 9004),
    )

    cluster = nodes.map(n => ActorSystem(Node(n, nodes), n.name))
  }

  after {
    cluster.foreach(n => n ! Stop)
  }

  "The cluster" should {
    "start" in {
      Thread.sleep(1000)
      Http("http://localhost:8001/values")
        .timeout(1000000, 100000)
        .postData("""{"key": "myKey", "value": "myValue", "version": {}}""")
        .header("content-type", "application/json")
        .asString
        .body should be ("Value added")

      // This host should know about it
      Http("http://localhost:8001/values/myKey")
        .asString
        .body should be ("myValue")

      // It should be replicated here
      Http("http://localhost:8002/values/myKey")
        .asString
        .body should be ("myValue")
    }
  }
}
