import java.util.concurrent.TimeoutException

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.cluster.VectorClock
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.unmarshalling.{ Unmarshal, Unmarshaller }
import akka.stream.Materializer
import spray.json._
import DefaultJsonProtocol._

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object Node {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  private final case class StartFailedInternal(cause: Throwable) extends Message

  private final case class StartedInternal(binding: ServerBinding) extends Message
  private final case class StopInternal(binding: ServerBinding) extends Message
  private final case class StopExternal(binding: ServerBinding) extends Message

  case object Stop extends Message

  def apply(host: String, port: Int, name: String): Behavior[Message] = Behaviors.setup { ctx =>
    val buildValueRepository = ctx.spawn(ValueRepository(Map.empty, name), "ValueRepository")

    val internalServer = ctx.spawn(InternalServer(buildValueRepository, host, port), "InternalServer")
    val externalServer = ctx.spawn(ExternalServer(buildValueRepository, host, port + 1), "ExternalServer")

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
  //val responseFuture: Future[HttpResponse] = sendToOtherNodes()
  val hosts = Map()


  context.pipeToSelf() {
    case Success(_) => Started()
    case Failure(ex) => StartFailed(ex)
  }

  def read (key : String): Value = {
    val futures = List.empty[Future[HttpResponse]]

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N) ) {
      futures + sendToOtherNodes(key, hosts.get(x.address))
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

  def write (v: Value): Unit = {

  }

  def put (v: Value): Unit = {

  }

  /**
   * Send get request to server
   * @param key key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def sendToOtherNodes(key: String, address : Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + "/" + key))
  }

  /**
   * Filters the value with the latest version
   * @param values values from which to filter
   * @return value with the largest vectorclock
   */
  def checkVersion(values: List[Value]): Value = {
    match values.maxBy(Value => Value.version) {
      // case l: List[Value] =>
      case Value(a,b,c) => Value(a,b,c)
      case List.empty => throw RuntimeException // add msg
    }


  }

  /*override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case GetValues() =>

    }
  }*/


}
