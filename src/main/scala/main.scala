import akka.actor.typed.ActorSystem


object main {
  case class NodeConfig(index: Int, name: String, externalHost: String, externalPort: Int, internalHost: String, internalPort: Int)

  def main(args: Array[String]): Unit = {

    // name, node (external), port (external), host (internal), port (internal)
    val nodes = List(
      NodeConfig(0, "node1", "localhost", 8001, "localhost", 9001),
      NodeConfig(1, "node2", "localhost", 8002, "localhost", 9002),
      NodeConfig(2, "node3", "localhost", 8003, "localhost", 9003),
      NodeConfig(3, "node4", "localhost", 8004, "localhost", 9004),
    )

    for (node <- nodes) {
      ActorSystem(Node(node, nodes), node.name)
    }

    val testCLient = InternalClient
    ActorSystem(Node, "d")

    testCLient.Init("localhost", 9001, 3, 2, 3)
    testCLient.Put(ValueRepository.Value("a", "b"))

    testCLient.Get("a")
  }
}
