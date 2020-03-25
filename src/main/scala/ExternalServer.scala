import akka.actor.{ActorSystem, typed}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ExternalServer {
  def apply(valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int): Behavior[Command] =
    Behaviors.setup(context => new ExternalServer(context, valueRepository, host, port))

  sealed trait Command

  final case class ExternalServerStarted(binding: ServerBinding) extends Command

  final case class ExternalServerStopped() extends Command

  final case class ExternalServerStartFailed(cause: Throwable) extends Command

  final case class StopExternalServer() extends  Command

}

class ExternalServer(context: ActorContext[ExternalServer.Command], valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int)
  extends AbstractBehavior[ExternalServer.Command](context) {

  import ExternalServer._

  implicit val actorSystem: typed.ActorSystem[Nothing] = context.system;
  implicit val classicActorSystem: ActorSystem = context.system.toClassic;
  implicit val materializer: Materializer = Materializer(classicActorSystem)

  val routes = new DynamoRoutes(valueRepository)

  var started = false;
  var binding: ServerBinding = _;

  val serverBinding: Future[Http.ServerBinding] =
    Http.apply().bindAndHandle(routes.theValueRoutes, host, port)

  context.pipeToSelf(serverBinding) {
    case Success(binding) => ExternalServerStarted(binding)
    case Failure(ex) => ExternalServerStartFailed(ex)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case ExternalServerStarted(binding) =>
        started = true
        this.binding = binding;
        context.log.info(
          "Internal server online at http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)

        this

      case StopExternalServer() =>
        binding.unbind()
        context.log.info(
          "Stopping server http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)
        Behaviors.same
      case ExternalServerStartFailed(cause) =>
        throw new RuntimeException("External Server failed to start", cause)
    }
  }
}
