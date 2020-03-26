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

  def apply(config: NodeConfig, allNodes: List[NodeConfig]): Behavior[Message] = Behaviors.setup { ctx =>
    ctx.log.info("Starting node {}", config.name)

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
