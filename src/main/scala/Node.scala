import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer

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

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>
    val buildValueRepository = ctx.spawn(ValueRepository(), "ValueRepository")

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

implicit val valueFormat = jsonFormat1(Value)

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
    case Success(_) => Started(
    case Failure(ex) => StartFailed(ex)
  }

  def read (key : String): Unit = {
    val responses = Seq.empty[Future[HttpResponse]]
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N) ) {
      responses + sendToOtherNodes(key, hosts.get(x.address))
    }
    if (responses.size < R) {
      throw TimeoutException("")
    }
    val result = getResponses(key).onComplete {
      case Success(a) => a
      case Failure(e) => context.log.error(s"Failed to get values from the ValueRepository, exceptionm = $e")
    }
    checkVersion(result)



  }

  def write (): Unit = {

  }

  def sendToOtherNodes(key: String, address : Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + "/" + key))
  }

  def getResponses(key: String): Future[Option[ValueRepository.Value]] = {
    valueRepository.ask(ValueRepository.GetValueByKey(key, _))
    repo.apply(Map.empty); // TODO: replace with actual logic that checks the responses
    repo.Values
  }

  def checkVersion(vectors: ValueRepository.Values.type): Unit = {

  }

  override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case GetValues() =>

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

