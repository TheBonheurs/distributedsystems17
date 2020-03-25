import ValueRepository.{OK, Value}
import akka.actor
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.VectorClock
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalatestRouteTest with JsonSupport {

  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): actor.ActorSystem = testKit.system.toClassic

  val valueRepository: ActorRef[ValueRepository.Command] = testKit.spawn(ValueRepository())
  lazy val routes: Route = new ExternalRoutes(valueRepository).theValueRoutes

  "The service" should {
    "return a 404 when item does not exist" in {
      Get("/entity") ~> Route.seal(routes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "create an item" in {
      Post("/values", Value("myKey", "myVal", new VectorClock())) ~> routes ~> check {
        responseAs[String] shouldEqual "Value added"
      }
    }

    "retrieve created item" in {
      val mockedBehavior = Behaviors.receiveMessage[ValueRepository.Command] {
        case ValueRepository.GetValueByKey("myKey", replyTo) =>
          replyTo ! Option(Value("myKey", "myValue", new VectorClock()))
          Behaviors.same
      }
      val probe = testKit.createTestProbe[ValueRepository.Command]()
      val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

      val routes = new ExternalRoutes(mockedPublisher).theVlueRoutes

      Get("/values/myKey") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "delete an item" in {
      val mockedBehavior = Behaviors.receiveMessage[ValueRepository.Command] {
        case ValueRepository.RemoveValue("myKey", replyTo) =>
          replyTo ! OK
          Behaviors.same
      }
      val probe = testKit.createTestProbe[ValueRepository.Command]()
      val mockedPublisher = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))

      val routes = new ExternalRoutes(mockedPublisher).theValueRoutes

      Delete("/values/myKey") ~> routes ~> check {
        status shouldEqual StatusCodes.OK

        probe.expectMessageType[ValueRepository.RemoveValue]
      }
    }
  }

}
