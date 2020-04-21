package dynamodb.client

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.cluster.VectorClock
import akka.stream.Materializer
import akka.util.Timeout
import dynamodb.client.UserClient.{Get, Put}
import dynamodb.node.{ValueRepository, mainObj}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

object UserMain {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[UserClient.Command] =
      ActorSystem(UserClient(dynamodb.cluster.cluster7.nodes), "hello")
    implicit val materializer: Materializer = Materializer(system)
    implicit val executionContext: ExecutionContextExecutor = system.executionContext
    implicit val timeout: Timeout = 5.seconds
    implicit val scheduler: Scheduler = system.scheduler

    val putResult = system.ask(Put(ValueRepository.Value("test_key22", "test_value", new VectorClock()), _: ActorRef[UserClient.Response]))
    putResult.map(f => {
      println(f)
      val getResult = system.ask(Get("test_key22", _: ActorRef[UserClient.Response]))
      getResult.map(f => {
        println(f)
      })
    })


  }
}
