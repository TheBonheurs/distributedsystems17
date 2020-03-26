import akka.actor.typed.ActorSystem

object main {
  def main(args: Array[String]): Unit = {

    // name, host (external), port (external), host (internal), port (internal)
    val hosts = List(
      ("host1", "localhost", 8001, "localhost", 9001),
      ("host2", "localhost", 8002, "localhost", 9002),
      ("host3", "localhost", 8003, "localhost", 9003),
      ("host4", "localhost", 8004, "localhost", 9004),
    )

    for (host <- hosts) {
      ActorSystem(Node(host._1, host._2, host._3, host._4, host._5), host._1)
    }
  }
}
