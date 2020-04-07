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
import dynamodb.node.ring.Ring
import dynamodb.node.ValueRepository.GetValueByKey

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object InternalClient {
  def apply(host: String, port: Int, numNodes: Int, numReadMinimum: Int, numWriteMinimum: Int, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], dht: ActorRef[DistributedHashTable.Command]): Behavior[Command] = Behaviors.receive { (context, command) =>
    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(classicActorSystem)
    implicit val timeout: Timeout = 5.seconds

    command match {
      case Put(value, replyTo) =>
        try for {
          internalValue <- retrieveRawValueFromRepository(value, nodeName)
        } yield if ((!internalValue.version.versions.get(nodeName).contains(0)).&&(internalValue.version.!=(value.version))) {
          replyTo ! KO("Version mismatch: server = {}, request = {}".format(internalValue.version, value.version))
        } else {
          try for {
            internalValue <- retrieveValueFromRepository(value, nodeName)
            otherNodes <- getTopNByKey(internalValue.key, numNodes)
            responses <- Future.sequence(otherNodes.map(node => putOtherNodes(internalValue, Uri.from("http", "", node.host, node.port, "/internal"))))
          } yield if (responses.count(r => r.status == StatusCodes.OK) >= numWriteMinimum - 1) {
            replyTo ! OK
          } else {
            replyTo ! KO("Not enough writes")
          } catch {
            case e: Exception => replyTo ! KO(e.getMessage)
          }
        }
        Behaviors.same
      case Get(key, replyTo) =>
        try for {
          otherNodes <- getTopNByKey(key, numNodes)
          responses <- Future.sequence(otherNodes.map(n => getOtherNodes(key, Uri.from("http", "", n.host, n.port, "/internal/"))))
          numFailedResponses = responses.count(response => response.isEmpty)
          versions = responses.flatten
        } yield if (numFailedResponses > numReadMinimum) {
          replyTo ! KO("Not enough reads")
        } else {
          replyTo ! ValueRes(checkVersion(versions.toList))
        } catch {
          case e: Exception => replyTo ! KO(e.getMessage)
        }
        Behaviors.same
    }
  }


  def retrieveRawValueFromRepository(previousValue: ValueRepository.Value, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], timeout: Timeout, scheduler: Scheduler): Future[ValueRepository.Value] =
    for {
      internalValueOption <- valueRepository.ask(GetValueByKey(previousValue.key, _: ActorRef[Option[ValueRepository.Value]]))
    } yield internalValueOption match {
      case Some(value) => ValueRepository.Value(value.key, value.value, value.version)
      case None => ValueRepository.Value(previousValue.key, previousValue.value, new VectorClock(TreeMap(nodeName -> 0)))
    }

  def retrieveValueFromRepository(previousValue: ValueRepository.Value, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], timeout: Timeout, scheduler: Scheduler): Future[ValueRepository.Value] =
    for {
      internalValueOption <- valueRepository.ask(GetValueByKey(previousValue.key, _: ActorRef[Option[ValueRepository.Value]]))
    } yield internalValueOption match {
      case Some(value) => ValueRepository.Value(value.key, value.value, value.version :+ nodeName)
      case None => ValueRepository.Value(previousValue.key, previousValue.value, new VectorClock(TreeMap(nodeName -> 0)))
    }

  def getTopNByKey(key: String, numNodes: Int)(implicit dht: ActorRef[DistributedHashTable.Command], timeout: Timeout, scheduler: Scheduler): Future[Ring] =
    for {
      otherNodesOption <- dht.ask(GetTopN(DistributedHashTable.getHash(key), numNodes, _: ActorRef[Option[Ring]]))
    } yield otherNodesOption.getOrElse(throw new Exception("Error getting top N nodes"))

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes[T](key: String, address: Uri)(implicit actorSystem: actor.ActorSystem, mat: Materializer): Future[Option[ValueRepository.Value]] = {
    import JsonSupport._

    try for {
      response <- Http().singleRequest(HttpRequest(uri = address + key))
      value <- if (response.status == StatusCodes.OK) Unmarshal(response).to[ValueRepository.Value] else throw new Exception("Empty value")
    } yield Some(value) catch {
      case _: Exception => Future {
        None
      }
    }
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

  // Trait defining responses
  sealed trait Response

  sealed trait Command

  final case class ValueRes(value: ValueRepository.Value) extends Response

  final case class KO(reason: String) extends Response

  final case class Put(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command

  case object OK extends Response

}
