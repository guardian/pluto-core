package services

import java.util.UUID

import models.{PlutoWorkingGroup, PlutoWorkingGroupSerializer}
import org.specs2.mutable.Specification
import play.api.libs.json.Json.JsValueWrapper

class RabbitMQPropagatorSpec extends Specification with PlutoWorkingGroupSerializer{
  "ChangeEvent.json" should {
    "json encode the given data model" in {
      val testdata = PlutoWorkingGroup(None,false,"Workworkwork","me")
      val content:Seq[JsValueWrapper] = Seq(testdata)
      val change = RabbitMqPropagator.ChangeEvent(content, Some("workinggroup"), CreateOperation, UUID.randomUUID())

      change.json mustEqual "[{\"hide\":false,\"name\":\"Workworkwork\",\"commissioner\":\"me\"}]"

    }
  }
}
