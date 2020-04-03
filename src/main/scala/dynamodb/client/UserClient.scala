package dynamodb.client

import akka.actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.{AddNode, GetTopN, Response}
import dynamodb.node._
import dynamodb.node.mainObj.NodeConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

object UserClient {

  sealed trait Command

  def apply(allNodes: List[NodeConfig], nodes: List[mainObj.NodeConfig]): Behavior[Command] =
    Behaviors.setup { ctx =>
      val dht: ActorRef[DistributedHashTable.Command] = ctx.spawn(DistributedHashTable(), "DistributedHashTable")
      for (node <- nodes) {
        dht ! AddNode(RingNode(node.position, node.internalHost, node.internalPort), ctx.system.ignoreRef[Response])
      }
      new UserClient(ctx, dht)
      // url: ("http://localhost:8001/values")
      // postData("""{"key": "myKey", "value": "myValue", "version": {}}""")
    }

  class UserClient(context: ActorContext[UserClient.Command], dht: ActorRef[DistributedHashTable.Command])
    extends AbstractBehavior[UserClient.Command](context) {

    import UserClient._
    import dynamodb.node.JsonSupport._
    import spray.json._

    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(classicActorSystem)
    implicit val executionContext: ExecutionContextExecutor = context.system.executionContext
    implicit val timeout: Timeout = 5.seconds
    //    responseFuture
    //      .onComplete {
    //        case Success(res) => println(res)
    //        case Failure(_)   => sys.error("something wrong")
    //      }

    /**
     * Send get request to server
     *
     * @param key key of the value to get
     * @return Http Response
     */
    def get(key: String): Future[ValueRepository.Value] = {
      val getNodeFuture = dht.ask(GetTopN(DistributedHashTable.getHash(key), 1, _: ActorRef[Option[LazyList[RingNode]]]))
      val nodeFuture = getNodeFuture.map {
        case Some(top1) => top1.head
        case None => throw new Exception("Error getting Top N list")
      }
      val httpFuture = nodeFuture.flatMap(node => {
        val address = Uri.from("http", "", node.host, node.port, "/values/")
        val httpFuture = Http().singleRequest(
          HttpRequest(uri = address + key))
        httpFuture
      })
      val valueFuture = httpFuture.flatMap(res => res.status match {
        case StatusCodes.OK => Unmarshal(res).to[ValueRepository.Value]
        case _ => throw new Exception("Error with HTTP request for getting value")
      })
      valueFuture
    }

    /**
     *
     * @param v value to write
     * @return
     */
    def put(v: ValueRepository.Value, nodes: Seq[mainObj.NodeConfig]): Future[Boolean] = {
      val getNodeFuture = dht.ask(GetTopN(DistributedHashTable.getHash(v.key), 1, _: ActorRef[Option[LazyList[RingNode]]]))
      val nodeFuture = getNodeFuture.map {
        case Some(top1) => top1.head
        case None => throw new Exception("Error getting Top N list")
      }
      val httpFuture = nodeFuture.flatMap(node => {
        val address = Uri.from("http", "", node.host, node.port, "/values/")
        val httpFuture = Http().singleRequest(HttpRequest(
          method = HttpMethods.POST,
          uri = address,
          entity = HttpEntity(`application/json`, v.toJson(JsonSupport.valueFormat).compactPrint)
        ))
        httpFuture
      })
      val resFuture = httpFuture.map(res => res.status match {
        case StatusCodes.OK => true
        case _ => false
      })
      resFuture
    }

    override def onMessage(msg: UserClient.Command): Behavior[UserClient.Command] = {
      msg match {
        case s => Behaviors.same
      }
    }
  }

}
