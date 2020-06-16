package models

import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import slick.jdbc.PostgresProfile
import utils.BuildMyApp
import play.api.test.WithApplication

import scala.concurrent.Await
import scala.concurrent.duration._

class PlutoWorkingGroupSpec extends Specification with BuildMyApp with PlutoWorkingGroupSerializer {

  "PlutoWorkingGroup" should {
    "automatically deserialize working group entries" in {
      val jsonData = """{"name":"test working group","commissioner":"Fred","hide":false}"""
      val content = Json.parse(jsonData).as[PlutoWorkingGroup]

      content.name mustEqual "test working group"
      content.commissioner_name mustEqual "Fred"
      content.id must beNone
      content.hide must beFalse
    }

    "save deserialized working group entries to the database" in new WithApplication(buildApp) {
      private val injector = app.injector

      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[PostgresProfile].db

      val jsonData = """{"name":"test working group","commissioner":"Fred","hide":false}"""
      val content = Json.parse(jsonData).as[PlutoWorkingGroup]

      content.name mustEqual "test working group"
      content.commissioner_name mustEqual "Fred"
      content.id must beNone
      content.hide must beFalse

      val result = Await.result(content.save, 10.seconds)
      result must beSuccessfulTry
      result.get.id must beSome
    }

//    "not re-save a model that already exists" in new WithApplication(buildApp) {
//      private val injector = app.injector
//
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db = dbConfigProvider.get[PostgresProfile].db
//      val jsonData = """{"name":"test working group","commissioner":"Fred","hide":false}"""
//      val content = Json.parse(jsonData).as[PlutoWorkingGroup]
//
//      content.name mustEqual "test working group"
//      content.commissioner_name mustEqual "Fred"
//      content.id must beNone
//      content.hide must beNone
//
//      val result = Await.result(content.ensureRecorded, 10.seconds)
//      result.id must beSome
//    }
  }


}
