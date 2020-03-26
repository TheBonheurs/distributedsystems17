import akka.actor.typed.ActorSystem

object main {
  def main(args: Array[String]): Unit = {

    // name, node (external), port (external), host (internal), port (internal)
    val nodes = List(
      ("node1", "localhost", 8001, "localhost", 9001),
      ("node2", "localhost", 8002, "localhost", 9002),
      ("node3", "localhost", 8003, "localhost", 9003),
      ("node4", "localhost", 8004, "localhost", 9004),
    )

    for (node <- nodes) {
      ActorSystem(Node(node._1, node._2, node._3, node._4, node._5), node._1)
    }
  }
}
