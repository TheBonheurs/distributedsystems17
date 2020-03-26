import ValueRepository._
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.cluster.VectorClock
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.TreeMap

class ValueRepositorySpec extends AnyFlatSpec with Matchers with BeforeAndAfter {
  val testKit: ActorTestKit = ActorTestKit()

  it should "add a value" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myValue"), probe.ref)
    probe.expectMessage(OK)
  }

  it should "read an empty value" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val readProbe = testKit.createTestProbe[Option[Value]]()

    valueRepository ! GetValueByKey("myKey", readProbe.ref)

    readProbe.expectMessage(None)
  }

  it should "read an added value" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myValue"), probe.ref)
    probe.expectMessage(OK)

    val readProbe = testKit.createTestProbe[Option[Value]]()

    valueRepository ! GetValueByKey("myKey", readProbe.ref)

    readProbe.expectMessage(Option(Value("myKey", "myValue")))
  }

  it should "remove a value" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myValue"), probe.ref)
    probe.expectMessage(OK)

    valueRepository ! RemoveValue("myKey", probe.ref)
    probe.expectMessage(OK)
  }

  it should "return an error when removing a value if it does not extist" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! RemoveValue("myKey", probe.ref)
    probe.expectMessage(KO("Not Found"))
  }

  it should "update a value if the version is newer" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("Node"-> 0))), probe.ref)
    probe.expectMessage(OK)

    valueRepository ! AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("Node" -> 1))), probe.ref)
    probe.expectMessage(OK)
  }

  it should "not update a value if the version is equal" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("Node" -> 0))), probe.ref)
    probe.expectMessage(OK)

    valueRepository ! AddValue(Value("myKey", "myAnotherValue", new VectorClock(TreeMap("Node" -> 0))), probe.ref)
    probe.expectMessage(KO("Version too old"))
  }

  it should "not update a value if the version is older" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("Node" -> 1))), probe.ref)
    probe.expectMessage(OK)

    valueRepository ! AddValue(Value("myKey", "myAnotherValue", new VectorClock(TreeMap("Node" -> 0))), probe.ref)
    probe.expectMessage(KO("Version too old"))
  }

  it should "clear the repository" in {
    val valueRepository = testKit.spawn(ValueRepository())
    val probe = testKit.createTestProbe[Response]()

    valueRepository ! AddValue(Value("myKey", "myValue"), probe.ref)
    probe.expectMessage(OK)

    valueRepository ! ClearValues(probe.ref)
    probe.expectMessage(OK)

    val readProbe = testKit.createTestProbe[Option[Value]]()

    valueRepository ! GetValueByKey("myKey", readProbe.ref)
    readProbe.expectMessage(None)
  }
}
