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
import scala.util.Success

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

  var started = false

  val N = 3;
  val R = N - 1;
  val W = N;


  /**
   * Method executed with get() operation
   * @param key the key to get
   * @return the value of that key
   */
  def read(key: String): ValueRepository.Value = {
    val futures: List[Future[HttpResponse]] = List.empty[Future[HttpResponse]]

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N)) {
      futures :+ getOtherNodes(key, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/"))
    }
    val responses = Future.sequence(futures.map(_.transform(Success(_))))

    // get only the successful ones
    val successes = responses.map(_.collect { case Success(x) => x })

    if (successes.isCompleted) {
      if (successes.value.size < R) {
        throw TimeoutException //add msg
      } else {
        val values = List.empty[ValueRepository.Value]

        for (y <- successes.value.map(_.toOption)) {
          for (z <- y.get) {
            values :+ Unmarshal(z).to[ValueRepository.Value]
          }
        }
        return checkVersion(values)
      }
    } else return null // moet echt iets beters voor verzinnen straks

  }

  /**
   *
   * @param v
   * @return
   */
  def write(v: ValueRepository.Value): Boolean = {
    val futures: List[Future[HttpResponse]] = List.empty

    // Use DHT to get top N nodes
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(v.key), N)) {
      futures:+(putOtherNodes(v, Uri.from(scheme = "http", host = x.host, port = x.port, path = "/")))
    }

    var counter = 0
    futures.foreach(_.map {
      case response: HttpResponse@HttpResponse(StatusCodes.OK, _, _, _) =>
        counter += 1
    })
    if (counter < W - 1) {
      return false
    }
    true
  }

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes(key: String, address: Uri): Future[HttpResponse] = {
    Http().singleRequest(
      HttpRequest(uri = address + "/" + key))
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
      uri = address + "/write",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, v.toJson.prettyPrint)
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
