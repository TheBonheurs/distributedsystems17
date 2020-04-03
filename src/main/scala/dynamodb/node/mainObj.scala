package dynamodb.node

import akka.actor.typed.ActorSystem

object mainObj {
  case class NodeConfig(position: BigInt, name: String, externalHost: String, externalPort: Int, internalHost: String, internalPort: Int)

  val nodes = List(
    NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001),
    NodeConfig(BigInt("85070591730234615865843651857942052864"), "node2", "localhost", 8002, "localhost", 9002),
    NodeConfig(BigInt("170141183460469231731687303715884105728"), "node3", "localhost", 8003, "localhost", 9003),
    NodeConfig(BigInt("255211775190703847597530955573826158592"), "node4", "localhost", 8004, "localhost", 9004),
  )

  def main(args: Array[String]): Unit = {

    // name, node (external), port (external), host (internal), port (internal)
    // max range of MD5 is 2^128=340282366920938463463374607431768211456


    for (node <- nodes) {
      ActorSystem(Node(node, nodes), node.name)
    }
  }
}
