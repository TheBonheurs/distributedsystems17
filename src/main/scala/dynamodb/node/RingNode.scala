package dynamodb.node

case class RingNode(position: BigInt, host: String, port: Int, externalHost: String, externalPort: Int)
