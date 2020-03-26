import java.net.InetAddress
import akka.actor.typed.{ActorRef, Behavior}

import ValueRepository.{Value, Values}

class Client {

  val N = 3;
  val R = N-1;

  val hosts = Map()

  val repo = ValueRepository

  def read (key : String): Unit = {
    for (x <- DHT.getTopNPreferenceNodes(DHT.getHash(key), N) ) {
      sendToOtherNodes(hosts.get(x.address))
    }
    val result = getResponses()
    if (result. < R) {
      wait(5)
      // TODO: make sure that this isn't blocking infinitely
      result = getResponses()
    }
    checkVersion(result)



  }

  def write (): Unit = {

  }

  def sendToOtherNodes(address : InetAddress): Unit = {
    // TODO: add http request
  }

  def getResponses(): ValueRepository.Values.type = {
    repo.apply(Map.empty); // TODO: replace with actual logic that checks the responses
    repo.Values
  }

  def checkVersion(vectors: ValueRepository.Values.type): Unit = {
    vectors.
  }

}
