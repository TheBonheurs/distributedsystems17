import ExternalServer.StopExternalServer
import InternalServer.StopInternalServer
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer

import scala.concurrent.{ExecutionContextExecutor, Future}
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
    implicit val system: ActorSystem[Nothing] = ctx.system
    // http doesn't know about akka typed so provide untyped system
    implicit val untypedSystem: akka.actor.ActorSystem = ctx.system.toClassic
    // implicit materializer only required in Akka 2.5
    // in 2.6 having an implicit classic or typed ActorSystem in scope is enough
    implicit val materializer: Materializer = Materializer(ctx.system.toClassic)
    implicit val executionContext: ExecutionContextExecutor = ctx.system.executionContext

    val buildValueRepository = ctx.spawn(ValueRepository(), "ValueRepository")
    val internalServer = ctx.spawn(InternalServer(buildValueRepository, host, port), "InternalServer")
    val externalServer = ctx.spawn(ExternalServer(buildValueRepository, host, port + 1), "ExternalServer")

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          internalServer ! StopInternalServer()
          externalServer ! StopExternalServer()
          Behaviors.same
      }

    starting(wasStopped = false)
  }

}
