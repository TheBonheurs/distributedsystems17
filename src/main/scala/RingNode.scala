import java.io.{File, PrintWriter}
import java.net.InetAddress

import scala.collection.immutable
import spray.json._
import DefaultJsonProtocol._

import scala.io.Source

case class RingNode(position: BigInt, address: InetAddress, map: immutable.HashMap[String, String])

private val ReplicateSize = 3

object NodeJsonProtocol extends DefaultJsonProtocol {

    implicit object NodeJsonFormat extends RootJsonFormat[RingNode] {
        def write(node: RingNode): JsObject = {
            JsObject(
                "position" -> JsNumber(node.position),
                "address" -> JsString(node.address.toString),
                "keys" -> JsArray(node.map.keySet.toJson),
                "values" -> JsArray(node.map.keySet.map(e => node.map.get(e)).toJson)
            )
        }

        def read(value: JsValue) = {
            value.asJsObject.getFields("position", "address", "keys", "values") match {
                case Seq(JsNumber(position), JsString(address), JsArray(keys), JsArray(values)) =>
                    new RingNode(position.toBigInt, InetAddress.getByName(address), new immutable.HashMap[String, String] = (keys zip values) groupBy (_._1) map { case (k, v) => (k.convertTo[String], v.map(_._2)) }
                    )
                case _ => throw new DeserializationException("RingNode expected")
            }
        }
    }

    import NodeJsonProtocol._

    def store(node: RingNode, key: String, value: String) = {

        val writer = new PrintWriter(new File("Node_" + node.position + ".json"))

        //writing text to the file
        writer.write(node.toJson.prettyPrint)

        //closing the writer
        writer.close()

        DHT.addNode(node.copy(map = node.map + (key -> value)))

        // One could do something here with replication but I suggest putting the cooridnator logic in a more central place like the server

    }

    def read(node: RingNode, key: String): RingNode = {
        val source = Source.fromFile("Node_" + node.position + ".json")
        val lines = try source.mkString finally source.close()
        lines.parseJson.convertTo[RingNode]
    }
}

