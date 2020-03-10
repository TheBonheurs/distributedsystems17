import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.duration._
import scala.concurrent.Future

class DynamoRoutes(buildValueRepository: ActorRef[ValueRepository.Command])(implicit system: ActorSystem[_]) extends JsonSupport {

  import akka.actor.typed.scaladsl.AskPattern._

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds

  lazy val theValueRoutes: Route =
    pathPrefix("values") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[ValueRepository.Value]) { job =>
                val operationPerformed: Future[ValueRepository.Response] =
                  buildValueRepository.ask(ValueRepository.AddValue(job, _))
                onSuccess(operationPerformed) {
                  case ValueRepository.OK         => complete("Value added")
                  case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
                }
              }
            },
            delete {
              val operationPerformed: Future[ValueRepository.Response] =
                buildValueRepository.ask(ValueRepository.ClearValues(_))
              onSuccess(operationPerformed) {
                case ValueRepository.OK         => complete("Values cleared")
                case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          )
        },
        (get & path(LongNumber)) { id =>
          val maybeValue: Future[Option[ValueRepository.Value]] =
            buildValueRepository.ask(ValueRepository.GetValueById(id, _))
          rejectEmptyResponse {
            complete(maybeValue)
          }
        }
      )
    }
}