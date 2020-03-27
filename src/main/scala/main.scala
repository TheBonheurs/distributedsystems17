import akka.actor.typed.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import slick.jdbc.H2Profile.api._

case class NodeConfig(index: Int, name: String, externalHost: String, externalPort: Int, internalHost: String, internalPort: Int)

object main {
  def main(args: Array[String]): Unit = {

    // name, node (external), port (external), host (internal), port (internal)
    val nodes = List(
      NodeConfig(0, "node1", "localhost", 8001, "localhost", 9001),
      NodeConfig(1, "node2", "localhost", 8002, "localhost", 9002),
      NodeConfig(2, "node3", "localhost", 8003, "localhost", 9003),
      NodeConfig(3, "node4", "localhost", 8004, "localhost", 9004),
    )

    val db = Database.forConfig("h2mem1")
    try {
      val lines = new ArrayBuffer[Any]()

      def println(s: Any) = lines += s

      //#tables

      class Values(tag: Tag) extends Table[(String, String)](tag, "VALUES") {
        def key = column[String]("KEY")
        def value = column[String]("VALUE")
        def * = (key, value)
      }
      val values = TableQuery[Values]

      // Connect to the database and execute the following block within a session
      //#setup
      val db = Database.forConfig("h2mem1")
      try {

        //#create
        val setup = DBIO.seq(
          // Create the tables, including primary and foreign keys
          (values.schema).create,

          values ++= Seq(
            ("key1", "value1"),
            ("key2", "value2")
          )
        )

        val setupFuture = db.run(setup)
        //#create
        val resultFuture = setupFuture.flatMap { _ =>

          //#readall
          // Read all values and print them to the console
          println("Values:")
          db.run(values.result).map(_.foreach {
            case (id, value) =>
              println("  " + id + "\t" + value)
          })
        }
        //#setup
        Await.result(resultFuture, Duration.Inf)
        lines.foreach(Predef.println _)
      } finally db.close
    }

    for (node <- nodes) {
      ActorSystem(Node(node, nodes), node.name)
    }
  }
}
