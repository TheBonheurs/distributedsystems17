package dynamodb.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import dynamodb.node.{ValueRepository, mainObj}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{ Failure, Success }

object UserClient {
  val nodes: Seq[mainObj.NodeConfig] = mainObj.nodes

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://akka.io"))

    responseFuture
      .onComplete {
        case Success(res) => println(res)
        case Failure(_)   => sys.error("something wrong")
      }
  }

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes(actorSystem: ActorSystem, key: String, address: Uri): Future[HttpResponse] = {
    Http()(actorSystem).singleRequest(
      HttpRequest(uri = address + key))
  }

  /**
   *
   * @param v       value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(actorSystem: ActorSystem, v: ValueRepository.Value, address: Uri): Future[HttpResponse] = {
    Http()(actorSystem).singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address,
//      entity = HttpEntity(`application/json`, v.toJson.compactPrint)
    ))
  }
  // url: ("http://localhost:8001/values")
  // postData("""{"key": "myKey", "value": "myValue", "version": {}}""")
}
