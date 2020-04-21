package dynamodb.cluster

import dynamodb.node.ClusterConfig
import dynamodb.node.mainObj.NodeConfig

package object cluster7 {
  val nodes = List(
    NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001),
    NodeConfig(BigInt("14"), "node2", "localhost", 8002, "localhost", 9002),
    NodeConfig(BigInt("28"), "node3", "localhost", 8003, "localhost", 9003),
    NodeConfig(BigInt("42"), "node4", "localhost", 8004, "localhost", 9004),
    NodeConfig(BigInt("56"), "node5", "localhost", 8005, "localhost", 9005),
    NodeConfig(BigInt("70"), "node6", "localhost", 8006, "localhost", 9006),
    NodeConfig(BigInt("84"), "node7", "localhost", 8007, "localhost", 9007)
  )

  val clusterConfig = ClusterConfig(numReplicas = 6, numWriteMinimum = 6, numReadMinimum = 6)
}
