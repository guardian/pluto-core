package controllers

import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test._
import utils.BuildMyApp

import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import io.circe.parser

class PlutoWorkingGroupControllerSpec extends Specification with BuildMyApp {
  "PlutoWorkingGroupController.list" should {
    "list all working groups" in new WithApplication(buildApp) {
      val rq = FakeRequest(
        "GET",
        "/api/pluto/workinggroup"
      ).withSession("uid"->"testuser")

      val contentFuture = route(app, rq).get.flatMap(_.body.consumeData.map(_.decodeString(StandardCharsets.UTF_8)))
      val content = Await.result(contentFuture, 10.seconds)

      val maybeParsed = parser.parse(content)
          .toOption
          .flatMap(parsed=>(parsed \\ "result").headOption)
          .flatMap(_.asArray)

      maybeParsed must beSome
      val unmarshalled = maybeParsed.get
      unmarshalled.length mustEqual 2

      (unmarshalled.head \\ "hide").head.as[Boolean] must beRight(true)
      (unmarshalled.head \\ "name").head.as[String] must beRight("Multimedia Anti-Social")
      (unmarshalled(1) \\ "hide").head.as[Boolean] must beRight(false)
      (unmarshalled(1) \\ "name").head.as[String] must beRight("Multimedia Social")
    }

    "only show non-hidden if requested" in new WithApplication(buildApp) {
      val rq = FakeRequest(
        "GET",
        "/api/pluto/workinggroup?showHidden=false"
      ).withSession("uid"->"testuser")

      val contentFuture = route(app, rq).get.flatMap(_.body.consumeData.map(_.decodeString(StandardCharsets.UTF_8)))
      val content = Await.result(contentFuture, 10.seconds)

      val maybeParsed = parser.parse(content)
        .toOption
        .flatMap(parsed=>(parsed \\ "result").headOption)
        .flatMap(_.asArray)

      maybeParsed must beSome
      val unmarshalled = maybeParsed.get
      unmarshalled.length mustEqual 1

      (unmarshalled.head \\ "hide").head.as[Boolean] must beRight(false)
      (unmarshalled.head \\ "name").head.as[String] must beRight("Multimedia Social")
    }
  }
}
