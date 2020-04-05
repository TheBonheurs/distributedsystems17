package dynamodb.node

import akka.actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
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
  def apply(valueRepository: ActorRef[ValueRepository.Command], dht: ActorRef[DistributedHashTable.Command], host: String, port: Int, n: Int, r: Int, w: Int, nodeName: String): Behavior[Command] =
    Behaviors.setup(context => new InternalClient(context, valueRepository, dht, host, port, n, r, w, nodeName))

  // Trait defining responses
  sealed trait Response

  final case class ValueRes(value: ValueRepository.Value) extends Response

  case object OK extends Response

  case class KO(reason: String) extends Response

  sealed trait Command

  final case class Put(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class Putted(replyTo: ActorRef[Response]) extends Command

  final case class InternalClientFailure(replyTo: ActorRef[Response], reason: String) extends Command

  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command

  final case class Retrieved(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class Init(host: String, port: Int, n: Int, r: Int, w: Int) extends Command

}


class InternalClient(context: ActorContext[InternalClient.Command], valueRepository: ActorRef[ValueRepository.Command], dht: ActorRef[DistributedHashTable.Command], host: String, port: Int, n: Int, r: Int, w: Int, nodeName: String)
  extends AbstractBehavior[InternalClient.Command](context) {

  import InternalClient._
  import JsonSupport._
  import spray.json._

  implicit val actorSystem: ActorSystem[Nothing] = context.system
  implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
  implicit val materializer: Materializer = Materializer(classicActorSystem)
  implicit val timeout: Timeout = 5.seconds

  var meHost: Uri = Uri.from(scheme = "http", host = host, port = port, path = "/internal")

  var N: Int = n
  var R: Int = r
  var W: Int = w

  /**
   * Method executed with get() operation
   *
   * @param key the key to get
   * @return the value of that key
   */
  def read(key: String): Future[ValueRepository.Value] = for {
    otherNodesOption <- dht.ask(GetTopN(DistributedHashTable.getHash(key), N, _: ActorRef[Option[LazyList[RingNode]]]))
    otherNodes = otherNodesOption match {
      case Some(otherNodes) => otherNodes
      case _ => throw new Exception("Error getting top N nodes")
    }
    responses <- Future.sequence(otherNodes.map(n => getOtherNodes(key, Uri.from("http", "", n.host, n.port, "/internal/"))))
    successfulResponses = responses.filter(response => response.status == StatusCodes.OK)
    versions <- Future.sequence(successfulResponses.map(Unmarshal(_).to[ValueRepository.Value]))
  } yield checkVersion(versions.toList)

  /**
   * Write value to other nodes
   *
   * @param v value to write
   * @return True if written to W - 1 other nodes successfully, false otherwise
   */
  def write(v: ValueRepository.Value): Future[Boolean] = for {
    valueOption <- valueRepository.ask(GetValueByKey(v.key, _: ActorRef[Option[ValueRepository.Value]]))
    value = valueOption match {
      case Some(value) => ValueRepository.Value(v.key, v.value, value.version :+ nodeName)
      case None => ValueRepository.Value(v.key, v.value, new VectorClock(TreeMap(nodeName -> 0)))
    }
    topN <- dht.ask(GetTopN(DistributedHashTable.getHash(value.key), N, _: ActorRef[Option[LazyList[RingNode]]]))
    responses <- topN match {
      case Some(topN) => Future.sequence(topN.map(node => putOtherNodes(value, Uri.from("http", "", node.host, node.port, "/internal"))))
      case _ => throw new Exception("Error getting top N nodes")
    }
  } yield responses.count(r => r.status == StatusCodes.OK) >= W - 1

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes(key: String, address: Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + key))
  }

  /**
   *
   * @param v       value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(v: ValueRepository.Value, address: Uri): Future[HttpResponse] = {
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address,
      entity = HttpEntity(`application/json`, v.toJson.compactPrint)
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

  /**
   * Helper method to adjust parameters of the client
   *
   * @param h hostname
   * @param p port
   * @param n N value
   * @param r R value
   * @param w W value
   */
  def initParams(h: String, p: Int, n: Int, r: Int, w: Int): Unit = {
    this.meHost = Uri.from(scheme = "http", host = h, port = p, path = "/internal")
    this.N = n
    this.R = r
    this.W = w
  }

  override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case Put(value, replyTo) =>
        context.pipeToSelf(write(value)) {
          case Success(true) => Putted(replyTo)
          case Success(false) => InternalClientFailure(replyTo, "Not enough writes")
          case Failure(exception) => InternalClientFailure(replyTo, exception.getMessage)
        }
        Behaviors.same
      case Putted(replyTo) =>
        replyTo ! OK
        Behaviors.same
      case InternalClientFailure(replyTo, reason) =>
        replyTo ! KO(reason)
        Behaviors.same
      case Retrieved(value, replyTo) =>
        replyTo ! ValueRes(value)
        Behaviors.same
      case Get(key, replyTo) =>
        context.pipeToSelf(read(key)) {
          case Success(value) => Retrieved(value, replyTo)
          case Failure(exception) => InternalClientFailure(replyTo, exception.getMessage)
        }
        Behaviors.same
      case Init(h, p, n, r, w) => initParams(h, p, n, r, w)
        this
    }
  }
}
