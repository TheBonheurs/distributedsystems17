import akka.cluster.VectorClock
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.collection.immutable.TreeMap

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)

  import spray.json._
  import DefaultJsonProtocol._
  import ValueRepository._

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue with Serializable = x match {
      case n: Long => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
    }
    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.longValue
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
    }
  }

  implicit def treeFormat[A: JsonFormat : Ordering, B: JsonFormat]: RootJsonFormat[TreeMap[A, B]] = new RootJsonFormat[TreeMap[A, B]] {
    override def write(obj: TreeMap[A, B]): JsValue = obj.iterator.map(a => Map(a._1 -> a._2)).toList.toJson
    override def read(json: JsValue): TreeMap[A, B] = TreeMap.from(json.convertTo[List[Map[Any, Any]]].map(a => (a.keys.head.asInstanceOf[A], a.values.head.asInstanceOf[B])))
  }

  implicit object ClockFormat extends RootJsonFormat[VectorClock] {
    override def write(obj: VectorClock): JsValue = obj.versions.toJson;
    override def read(json: JsValue): VectorClock = new VectorClock(json.convertTo[TreeMap[String, Long]])
  }

  implicit val valueFormat: RootJsonFormat[Value] = jsonFormat3(Value)
  implicit val valuesFormat: RootJsonFormat[Values] = jsonFormat1(Values)
}