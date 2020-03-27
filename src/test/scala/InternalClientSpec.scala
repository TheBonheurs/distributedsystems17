import java.net.http.HttpClient

import akka.actor
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestKit
import akka.util.ByteString
import akka.actor.typed.scaladsl.adapter._
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.{ExecutionContext, Future}

class InternalClientSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalatestRouteTest {

  import JsonSupport._

  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): actor.ActorSystem = testKit.system.toClassic

  val valueRepository: ActorRef[ValueRepository.Command] = testKit.spawn(ValueRepository(""))
  val dht: ActorRef[DistributedHashTable.Command] = testKit.spawn(DistributedHashTable())
  val internalClient: ActorRef[InternalClient.Command] = testKit.spawn(InternalClient(valueRepository, dht, "", 0))
  lazy val routes: Route = new ExternalRoutes(valueRepository, internalClient).theValueRoutes


  "Get an item" in {
    val mockedBehavior = {

    }

    val probe = testKit.createTestProbe()

//    val mockedPublisher = testKit.spawn()
//
//    Get("a") ~> r
  }




}
