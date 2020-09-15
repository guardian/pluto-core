package services

import java.io.ByteArrayOutputStream
import java.util.UUID

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import models.{PlutoWorkingGroup, PlutoWorkingGroupSerializer}
import org.specs2.mutable.Specification
import play.api.libs.json.Json.JsValueWrapper

class RabbitMQPropagatorSpec extends Specification with PlutoWorkingGroupSerializer{
  "ChangeEvent.json" should {
    "json encode the given data model" in {
      val testdata = PlutoWorkingGroup(None,false,"Workworkwork","me")
      val content:Seq[JsValueWrapper] = Seq(testdata)
      val change = RabbitMqPropagator.ChangeEvent(content, Some("workinggroup"), CreateOperation(), UUID.randomUUID())

      change.json mustEqual "[{\"hide\":false,\"name\":\"Workworkwork\",\"commissioner\":\"me\"}]"

    }
  }

  "ChangeEvent" should {
    "be encodable via jackson-databind" in {
      val testdata = PlutoWorkingGroup(None,false,"Workworkwork","me")
      val content:Seq[JsValueWrapper] = Seq(testdata)
      val change = RabbitMqPropagator.ChangeEvent(content, Some("workinggroup"), CreateOperation(), UUID.randomUUID())

      val mapper = new ObjectMapper()
      val generated = mapper.writeValueAsString(change)
      println("Got " + generated)
      generated.length must beGreaterThan(1)
    }
  }
}
