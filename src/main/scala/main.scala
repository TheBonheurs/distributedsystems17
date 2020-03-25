import akka.actor.typed.ActorSystem

object main {
  def main(args: Array[String]): Unit = {
    val hosts = Map(
      1 -> "localhost:8080",
      2 -> "localhost:8081",
      3 -> "localhost:8082",
      4 -> "localhost:8083",
    )

    hosts.foreach(i => {

    })

    ActorSystem(Node("localhost", 8080), "BuildValuesServer")
    ActorSystem(Node("localhost", 8085), "BuildValuesServer")
    ActorSystem(Node("localhost", 8090), "BuildValuesServer")
  }
}
