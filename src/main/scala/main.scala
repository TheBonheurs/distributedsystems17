import akka.actor.typed.ActorSystem

object main {
  def main(args: Array[String]): Unit = {
    ActorSystem(Node("localhost", 8080), "BuildValuesServer")
    ActorSystem(Node("localhost", 8085), "BuildValuesServer")
    ActorSystem(Node("localhost", 8090), "BuildValuesServer")
  }
}
