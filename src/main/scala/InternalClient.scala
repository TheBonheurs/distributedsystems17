import java.util.concurrent.TimeoutException

import InternalClient.{Get, Put}
import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object InternalClient {
  def apply(valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int): Behavior[Command] =
    Behaviors.setup(context => new InternalClient(context, valueRepository, host, port))

  sealed trait Command
  final case class Put(value: ValueRepository.Value) extends Command
  final case class Get(key: String) extends  Command

}


class InternalClient(context: ActorContext[InternalClient.Command], valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int)
  extends AbstractBehavior[InternalClient.Command](context) {

  import spray.json._
  import JsonSupport._

  implicit val actorSystem: ActorSystem[Nothing] = context.system
  implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
  implicit val materializer: Materializer = Materializer(classicActorSystem)

  val routes = new ExternalRoutes(valueRepository)
  // TODO make sure to initialize self with proper host + port
  var self: Uri = Uri.from(scheme = "http", host ="localhost", port = 8001, path = "/internal")

  val N: Int = 3
  val R: Int = N - 1
  val W: Int = N

  /**
   * Helper method for converting futures to future try
   * See: https://stackoverflow.com/questions/20874186/scala-listfuture-to-futurelist-disregarding-failed-futures/20874404#20874404
   *
   * @param f Future to convert
   * @tparam T Type of the future
   * @return Future of type Try of type T
   */
  def futureToFutureTry[T](f: Future[T]): Future[Try[T]] =
    f.map(Success(_)).recover(x => Failure(x))

  /**
   * Method executed with get() operation
   *
   * @param key the key to get
   * @return the value of that key
   */
  def read(key: String): Future[ValueRepository.Value] = {
    // add to it's own server
    val futures: List[Future[HttpResponse]] = List(getOtherNodes(key, self))

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N)) {
      futures :+ getOtherNodes(key, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/internal"))
    }
    // Convert to Future[Try[T]] to catch exceptions in the Future.sequence line
    val listOfFutureTrys = futures.map(futureToFutureTry)
    // Convert to Future[List[Try[HttpResponse]]]
    val responsesFuture = Future.sequence(listOfFutureTrys)

    // Get only the successful responses
    val successesFuture = responsesFuture.map(_.filter(_.isSuccess))
    val failuresFuture = responsesFuture.map(_.filter(_.isFailure))

    // Remove the Try from Future[List[Try[HttpResponse]]]
    val successResponsesFuture = successesFuture.map(f => f.map(t => t.get))
    val valueFuture = successResponsesFuture.map(l => {
      if (l.size < R - 1) {
       throw new TimeoutException("Did not get R - 1 successfull reads from other nodes")
      } else {
        val values = List.empty[ValueRepository.Value]
        // For all responses
        for (y <- l) {
          values :+ Unmarshal(y).to[ValueRepository.Value]
        }
        checkVersion(values)
      }
    })
    valueFuture
  }

  /**
   * Write value to other nodes
   * @param v value to write
   * @return True if written to W - 1 other nodes successfully, false otherwise
   */
  def write(v: ValueRepository.Value): Future[Boolean] = {
    // add to it's own server
    val futures: List[Future[HttpResponse]] = List(putOtherNodes(v, self))

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(v.key), N)) {
      futures :+ putOtherNodes(v, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/internal"))
    }
    // Convert to Future[Try[T]] to catch exceptions in the Future.sequence line
    val listOfFutureTrys = futures.map(futureToFutureTry)
    // Convert to Future[List[Try[HttpResponse]]]
    val responsesFuture = Future.sequence(listOfFutureTrys)

    // Get only the successful responses
    val successesFuture = responsesFuture.map(_.filter(_.isSuccess))
    val failuresFuture = responsesFuture.map(_.filter(_.isFailure))

    // Remove the Try from Future[List[Try[HttpResponse]]]
    val successResponsesFuture = successesFuture.map(f => f.map(t => t.get))
    val booleanFuture = successResponsesFuture.map(l => {
      if (l.size < W - 1) {
        false
      } else {
        true
      }
    })
    booleanFuture
  }

  /**
   * Send get request to server
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
      entity = HttpEntity(ContentTypes.`application/json`, v.toJson.prettyPrint)
    )
    )
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
      if (a.version.>(b.version) && result.version.<(a.version)) {
        result = a
      }
    }
    result
  }

  override def onMessage(msg: InternalClient.Command): Behavior[InternalClient.Command] = {
    msg match {
      case Put(value) => write(value)
        Behaviors.same
      case Get(key) => read(key)
        Behaviors.same
    }
  }
}
