import akka.actor.typed.ActorSystem

object main {
  def main(args: Array[String]): Unit = {
    ActorSystem(Node("localhost", 8080, "node1"), "BuildValuesServer")
    ActorSystem(Node("localhost", 8085, "node2"), "BuildValuesServer")
    ActorSystem(Node("localhost", 8090, "node3"), "BuildValuesServer")
  }
}
