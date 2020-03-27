import java.net.http.HttpClient

import akka.actor
import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}

class InternalClientSpec extends TestKit(ActorSystem(classic.))
  with MockFactory
  with ScalaFutures
  with BeforeAndAfterAll {

  trait MockClientHandler extends HttpClient {
    val mock = mockFunction[HttpRequest, Future[HttpResponse]]

    override def sendRequest(httpRequest: HttpRequest)(implicit actorSystem: ActorSystem): Future[HttpResponse] =
      mock(httpRequest)
  }

  val stripString =
    """ """.stripMargin

  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): actor.ActorSystem = testKit.system.toClassic


    // mock Http
    val internalClient = new InternalClient() with MockClientHandler {
      override implicit def actorSystem: ActorSystem = system
      override implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global
    }

    internalClient.mock
      .expects(HttpRequest(uri = "http://dummy.restapiexample.com/api/v1/employees"))
      .returning(Future.successful(HttpResponse(entity = HttpEntity(ByteString(stripString)))))

    val expectResult = {}
}
