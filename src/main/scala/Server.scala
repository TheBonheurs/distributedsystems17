import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Server extends App {
  val host = "0.0.0.0"
  val port = 9001

  implicit val system: ActorSystem = ActorSystem("helloworld")
  implicit val executor: ExecutionContext = system.dispatcher

  val store: mutable.Map[String, String] = mutable.Map()

  def route = path(Remaining) { key =>
    concat(
      get {
        store.get(key) match {
          case Some(value) => complete(value)
          case None => complete((StatusCodes.NotFound, "Could not find value"))
        }
      },
      put {
        formField(Symbol("value")) { value =>
          if (store.contains(key)) {
            complete((StatusCodes.BadRequest, "Value already in store"))
          } else {
            store.put(key, value);
            complete((StatusCodes.Created), "")
          }
        }
      }
    )
  }

  val bindingFuture = Http().bindAndHandle(route, host, port)
  bindingFuture.onComplete {
    case Success(serverBinding) => println(s"listening to ${serverBinding.localAddress}")
    case Failure(error) => println(s"error: ${error.getMessage}")
  }
}

