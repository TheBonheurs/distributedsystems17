import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors

object ValueRepository {

  // Definition of the a build job and its possible status values
  sealed trait Status
  object Successful extends Status
  object Failed extends Status

  final case class Value(id: Long, projectName: String, status: Status, duration: Long)
  final case class Values(values: Seq[Value])

  // Trait defining successful and failure responses
  sealed trait Response
  case object OK extends Response
  final case class KO(reason: String) extends Response

  // Trait and its implementations representing all possible messages that can be sent to this Behavior
  sealed trait Command
  final case class AddValue(value: Value, replyTo: ActorRef[Response]) extends Command
  final case class GetValueById(id: Long, replyTo: ActorRef[Option[Value]]) extends Command
  final case class GetValueByStatus(status: Status, replyTo: ActorRef[Seq[Value]]) extends Command
  final case class ClearValues(replyTo: ActorRef[Response]) extends Command

  // This behavior handles all possible incoming messages and keeps the state in the function parameter
  def apply(values: Map[Long, Value] = Map.empty): Behavior[Command] = Behaviors.receiveMessage {
    case AddValue(value, replyTo) if values.contains(value.id) =>
      replyTo ! KO("Value already exists")
      Behaviors.same
    case AddValue(value, replyTo) =>
      replyTo ! OK
      ValueRepository(values.+(value.id -> value))
    case GetValueById(id, replyTo) =>
      replyTo ! values.get(id)
      Behaviors.same
    case ClearValues(replyTo) =>
      replyTo ! OK
      ValueRepository(Map.empty)
  }

}