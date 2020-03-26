
import java.util.concurrent.TimeoutException

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.VectorClock
import akka.cluster.VectorClock.Node
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


object Node {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  private final case class StartFailedInternal(cause: Throwable) extends Message

  private final case class StartedInternal(binding: ServerBinding) extends Message
  private final case class StopInternal(binding: ServerBinding) extends Message
  private final case class StopExternal(binding: ServerBinding) extends Message

  case object Stop extends Message

  def apply(config: NodeConfig, allNodes: List[NodeConfig]): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info("Starting node {}", config.name)

    for (node <- allNodes) {
      DHT.addNode(RingNode(node.index, node.internalHost, node.internalPort))
    }

    val buildValueRepository = ctx.spawn(ValueRepository(config.name), "ValueRepository")

    val internalServer = ctx.spawn(InternalServer(buildValueRepository, config.internalHost, config.internalPort), "InternalServer")
    val externalServer = ctx.spawn(ExternalServer(buildValueRepository, config.externalHost, config.externalPort), "ExternalServer")

    def starting(): Behaviors.Receive[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          internalServer ! InternalServer.Stop()
          externalServer ! ExternalServer.Stop()
          Behaviors.same
      }

    starting()
  }

}

object InternalClient {
  def apply(valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int): Behavior[Command] =
    Behaviors.setup(context => new InternalClient(context, valueRepository, host, port))

  sealed trait Command
  final case class Started() extends Command
  final case class StartFailed(cause: Throwable) extends Command
  final case class Stop() extends  Command
  final case class GetValues() extends Command
}

final case class Value(key: String, value: String, version: VectorClock)

implicit val valueFormat = jsonFormat3(Value)

implicit object VectorFormat extends RootJsonFormat[VectorClock]{

  def write(vc: VectorClock): JsValue = {
    JsObject("versions" -> JsObject(
        "Node" -> JsArray(vc.versions.map(_._1.toString.toJson).toVector),
        "Version" -> JsArray(vc.versions.map(_._2.toJson).toVector)
      )
    )
  }

  def read(json: JsValue) = {
    json.asJsObject.getFields("versions") match {
      case JsObject(fields) => fields match {
        case Seq(JsArray(nodes), JsArray(versions)) => VectorClock(immutable.TreeMap[Node, Long]() ++
          nodes.map(_.convertTo[String]).toList.zip(versions.map(_.convertTo[Long]).toList).toMap)
      }
    }
  }

}

class InternalClient(context: ActorContext[InternalClient.Command], valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int)
  extends AbstractBehavior[InternalClient.Command](context) {

  import InternalClient._

  implicit val actorSystem: ActorSystem[Nothing] = context.system
  implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
  implicit val materializer: Materializer = Materializer(classicActorSystem)

  val routes = new ExternalRoutes(valueRepository)

  var started = false

  val N = 3;
  val R = N - 1;
  val W = N;


  /**
   * Method executed with get() operation
   * @param key the key to get
   * @return the value of that key
   */
  def read(key: String): Value = {
    val futures: List[Future[HttpResponse]] = List.empty[Future[HttpResponse]]

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N)) {
      futures + getOtherNodes(key, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/"))
    }
    val responses = Future.sequence(futures.map(_.transform(Success(_))))

    // get only the successful ones
    val successes = responses.map(_.collect { case Success(x) => x })

    if (successes.isCompleted) {
      if (successes.value.size < R) {
        throw TimeoutException //add msg
      } else {
        val values = List.empty[Value]

        for (y <- successes.value) {
          values + Unmarshal(y).to[Value]
        }
        checkVersion(values)
      }
    }

  }

  /**
   *
   * @param v
   * @return
   */
  def write(v: Value): Boolean = {
    val futures: List[Future[HttpResponse]] = List.empty

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(v.key), N)) {
      futures.+(putOtherNodes(v, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/")))
    }

    var counter = 0
    futures.foreach(_.map {
      case response: HttpResponse@HttpResponse(StatusCodes.OK, _, _, _) =>
        counter += 1
    })
    if (counter < W - 1) {
      return false
    }
    true
  }

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes(key: String, address: Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + "/" + key))
  }

  /**
   *
   * @param v       value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(v: Value, address: Uri): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address + "/write",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, v.toJson.prettyPrint)
      )
    )
  }

  /**
   * Filters the value with the latest version
   *
   * @param values values from which to filter
   * @return value with the largest vectorclock
   */
  def checkVersion(values: List[Value]): Value = {
    var result = values.head
    for (a <- values; b <- values) {
      if (a.version.>(b.version) && result.version.<(a.version)) {
        result = a
      }
    }
    result
  }
}

  /*override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case GetValues() =>

    }

    }
  }

  /*responseFuture.map {
    case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
      val setCookies = response.headers[`Set-Cookie`]
      println(s"Cookies set by a server: $setCookies")
      response.discardEntityBytes()
    case _ => sys.error("something wrong")

  }*/

}

