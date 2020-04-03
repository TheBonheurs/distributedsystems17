package dynamodb.node

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import dynamodb.node.InternalClient.{Get, KO, Put, ValueRes}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ExternalRoutes(buildValueRepository: ActorRef[ValueRepository.Command], internalClient: ActorRef[InternalClient.Command])(implicit system: ActorSystem[_]) {
  import JsonSupport._
  import akka.actor.typed.scaladsl.AskPattern._

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds
  implicit val ec: ExecutionContext = system.executionContext

  lazy val theValueRoutes: Route =
    pathPrefix("values") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[ValueRepository.Value]) { job =>
                val putResult = internalClient.ask(Put(job, _: ActorRef[InternalClient.Response]))
                onSuccess(putResult) {
                  case InternalClient.ValueRes(_) => complete(StatusCodes.OK -> "Value added")
                  case InternalClient.OK => complete("Value added")
                  case InternalClient.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
                }
              }
            },
            delete {
              val operationPerformed: Future[ValueRepository.Response] =
                buildValueRepository.ask(ValueRepository.ClearValues(_))
              onSuccess(operationPerformed) {
                case ValueRepository.OK => complete("Values cleared")
                case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          )
        },
        (delete & path(Remaining)) { id =>
          val operationPerformed: Future[ValueRepository.Response] = buildValueRepository.ask(ValueRepository.RemoveValue(id, _))
          onSuccess(operationPerformed) {
            case ValueRepository.OK => complete("Value deleted")
            case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
          }
        },
        (get & path(Remaining)) { id =>
          val getResult = internalClient.ask(Get(id, _: ActorRef[InternalClient.Response]))
          onSuccess(getResult) {

            case ValueRes(value) => complete(value.value)
            case KO(reason) => complete(StatusCodes.InternalServerError -> reason)
          }
        }
      )
    }
}