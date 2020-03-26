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

  def apply(name: String, externalHost: String, externalPort: Int, internalHost: String, internalPort: Int): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info("Starting node {}", name)

    val buildValueRepository = ctx.spawn(ValueRepository(), "ValueRepository")

    val internalServer = ctx.spawn(InternalServer(buildValueRepository, internalHost, internalPort), "InternalServer")
    val externalServer = ctx.spawn(ExternalServer(buildValueRepository, externalHost, externalPort), "ExternalServer")

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
