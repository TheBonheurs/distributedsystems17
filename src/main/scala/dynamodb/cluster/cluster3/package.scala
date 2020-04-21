package dynamodb.cluster

import dynamodb.node.ClusterConfig
import dynamodb.node.mainObj.NodeConfig

package object cluster3 {
  val nodes = List(
    NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001),
    NodeConfig(BigInt("33"), "node2", "localhost", 8002, "localhost", 9002),
    NodeConfig(BigInt("66"), "node3", "localhost", 8003, "localhost", 9003)
  )
  val clusterConfig = ClusterConfig(numReplicas = 2, numWriteMinimum = 1, numReadMinimum = 1)
}
