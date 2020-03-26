
import java.util.concurrent.TimeoutException

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.cluster.VectorClock
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import spray.json._
import DefaultJsonProtocol._

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{Await, ExecutionContextExecutor, Future, TimeoutException}
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

implicit object VectorFormat extends RootJsonFormat[VectorClock]{

  def write(vc: VectorClock) = JsObject(
    List(
      Some("versions" -> vc.versions ),
    ).flatten: _*
  )

  def read(json: JsValue) = {
    val jsObject = json.asJsObject

    jsObject.getFields("versions") match {
      case Seq(versions) => VectorClock(
        versions.convertTo[mutable.TreeMap[Node, Long]
      )
    }
  }

}

implicit val valueFormat = jsonFormat3(Value)

class InternalClient(context: ActorContext[InternalClient.Command], valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int)
  extends AbstractBehavior[InternalClient.Command](context) {

  import InternalClient._

  implicit val actorSystem: ActorSystem[Nothing] = context.system
  implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
  implicit val materializer: Materializer = Materializer(classicActorSystem)

  val routes = new ExternalRoutes(valueRepository)

  var started = false

  val N = 3;
  val R = N-1;
  val W = N;

  val hosts = Map()


  context.pipeToSelf() {
    case Success(_) => Started()
    case Failure(ex) => StartFailed(ex)
  }

  def read (key : String): Value = {
    val futures = List.empty[Future[HttpResponse]]

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N) ) {
      futures + getOtherNodes(key, hosts.get(x.address))
    }
    val responses = Future.sequence(futures.map(_.transform(Success(_))))

    // get only the successful ones
    val successes = responses.map(_.collect{case Success(x)=>x})

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

  def write (v: Value): Boolean = {
    val futures = List.empty[Future[HttpResponse]]

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(v.key), N) ) {
      futures + putOtherNodes(v.key, hosts.get(x.address))
    }

    var counter = 0
    futures.foreach(_.map{
      case response: HttpResponse @ HttpResponse(StatusCodes.OK, _, _, _) =>
        counter += 1
    })
    if (counter < W-1) {
      return false
    }
    true
  }

  /**
   * Send get request to server
   * @param key key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes(key: String, address : Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + "/" + key))
  }

  /**
   *
   * @param v value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(v: Value, address : Uri): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address + "/write",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, v.toJson)
      )
    )
  }

  /**
   * Filters the value with the latest version
   * @param values values from which to filter
   * @return value with the largest vectorclock
   */
  def checkVersion(values: List[Value]): Value = {
    var result = values.head
    for( a <- values; b <- values) {
      if (a.version.>(b.version) && result.version.<(a.version)) {
        result = a
      }
    }
    result
  }

  /*override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case GetValues() =>

    }
  }*/

}

