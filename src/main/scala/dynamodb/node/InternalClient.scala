package dynamodb.node

import akka.actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.cluster.VectorClock
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.GetTopN
import dynamodb.node.ValueRepository.GetValueByKey

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object InternalClient {
  def apply(host: String, port: Int, numNodes: Int, numReadMinimum: Int, numWriteMinimum: Int, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], dht: ActorRef[DistributedHashTable.Command]): Behavior[Command] = Behaviors.receive { (context, command) =>
    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(classicActorSystem)
    implicit val timeout: Timeout = 5.seconds

    command match {
      case Put(value, replyTo) =>
        context.pipeToSelf(write(value, nodeName, numNodes, numWriteMinimum)) {
          case Success(true) => PutSuccess(replyTo)
          case Success(false) => InternalClientFailure(replyTo, "Not enough writes")
          case Failure(exception) => InternalClientFailure(replyTo, exception.getMessage)
        }
        Behaviors.same
      case PutSuccess(replyTo) =>
        replyTo ! OK
        Behaviors.same
      case InternalClientFailure(replyTo, reason) =>
        replyTo ! KO(reason)
        Behaviors.same
      case RetrieveSuccess(value, replyTo) =>
        replyTo ! ValueRes(value)
        Behaviors.same
      case Get(key, replyTo) =>
        context.pipeToSelf(read(key, numNodes)) {
          case Success(value) => RetrieveSuccess(value, replyTo)
          case Failure(exception) => InternalClientFailure(replyTo, exception.getMessage)
        }
        Behaviors.same
      case Init(host, port, n, r, w) => InternalClient(host, port, n, r, w, nodeName)
    }
  }

  /**
   * Method executed with get() operation
   *
   * @param key the key to get
   * @return the value of that key
   */
  def read(key: String, N: Int)(implicit actorSystem: actor.ActorSystem, dht: ActorRef[DistributedHashTable.Command], scheduler: Scheduler, timeout: Timeout, mat: Materializer): Future[ValueRepository.Value] = for {
    otherNodesOption <- dht.ask(GetTopN(DistributedHashTable.getHash(key), N, _: ActorRef[Option[LazyList[RingNode]]]))
    otherNodes = otherNodesOption.getOrElse(throw new Exception("Error getting top N nodes"))
    responses <- Future.sequence(otherNodes.map(n => getOtherNodes(key, Uri.from("http", "", n.host, n.port, "/internal/"))))
    successfulResponses = responses.filter(response => response.status == StatusCodes.OK)
    versions <- Future.sequence(successfulResponses.map(unmarshal))
  } yield checkVersion(versions.toList)

  def unmarshal(response: HttpResponse)(implicit mat: Materializer): Future[ValueRepository.Value] = {
    import JsonSupport._

    Unmarshal(response).to[ValueRepository.Value]
  }

  /**
   * Write value to other nodes
   *
   * @param v value to write
   * @return True if written to W - 1 other nodes successfully, false otherwise
   */
  def write(v: ValueRepository.Value, nodeName: String, N: Int, W: Int)(implicit actorSystem: actor.ActorSystem, dht: ActorRef[DistributedHashTable.Command], valueRepository: ActorRef[ValueRepository.Command], timeout: Timeout, scheduler: Scheduler): Future[Boolean] = for {
    valueOption <- valueRepository.ask(GetValueByKey(v.key, _: ActorRef[Option[ValueRepository.Value]]))
    value = valueOption match {
      case Some(value) => ValueRepository.Value(v.key, v.value, value.version :+ nodeName)
      case None => ValueRepository.Value(v.key, v.value, new VectorClock(TreeMap(nodeName -> 0)))
    }
    otherNodesOption <- dht.ask(GetTopN(DistributedHashTable.getHash(value.key), N, _: ActorRef[Option[LazyList[RingNode]]]))
    otherNodes = otherNodesOption.getOrElse(throw new Exception("Error getting top N nodes"))
    responses <- Future.sequence(otherNodes.map(node => putOtherNodes(value, Uri.from("http", "", node.host, node.port, "/internal"))))
  } yield responses.count(r => r.status == StatusCodes.OK) >= W - 1

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes[T](key: String, address: Uri)(implicit actorSystem: actor.ActorSystem): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(uri = address + key))
  }

  /**
   *
   * @param value   value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(value: ValueRepository.Value, address: Uri)(implicit actorSystem: actor.ActorSystem): Future[HttpResponse] = {
    import JsonSupport._
    import spray.json._

    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address,
      entity = HttpEntity(`application/json`, value.toJson.compactPrint)
    ))
  }

  /**
   * Filters the value with the latest version
   *
   * @param values values from which to filter
   * @return value with the largest vectorclock
   */
  def checkVersion(values: List[ValueRepository.Value]): ValueRepository.Value = {
    var result = values.head
    for (a <- values; b <- values) {
      if (a.version > b.version && result.version < a.version) {
        result = a
      }
    }
    result
  }

  // Trait defining responses
  sealed trait Response

  sealed trait Command

  final case class ValueRes(value: ValueRepository.Value) extends Response

  case class KO(reason: String) extends Response

  final case class Put(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class PutSuccess(replyTo: ActorRef[Response]) extends Command

  final case class InternalClientFailure(replyTo: ActorRef[Response], reason: String) extends Command

  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command

  final case class RetrieveSuccess(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class Init(host: String, port: Int, n: Int, r: Int, w: Int) extends Command

  case object OK extends Response
}
