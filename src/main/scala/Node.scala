import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http.ServerBinding

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
    val buildValueRepository = ctx.spawn(ValueRepository(name), "ValueRepository")

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
