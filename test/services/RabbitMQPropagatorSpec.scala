package services

import java.util.UUID

import models.{PlutoWorkingGroup, PlutoWorkingGroupSerializer}
import org.specs2.mutable.Specification

class RabbitMQPropagatorSpec extends Specification with PlutoWorkingGroupSerializer{
  "ChangeEvent.json" should {
    "json encode the given data model" in {
      val testdata = PlutoWorkingGroup(None,false,"Workworkwork","me")

      val change = RabbitMqPropagator.ChangeEvent(Seq(testdata), Some("workinggroup"), CreateOperation, UUID.randomUUID())

      change.json mustEqual "[{\"hide\":false,\"name\":\"Workworkwork\",\"commissioner\":\"me\"}]"

    }
  }
}
