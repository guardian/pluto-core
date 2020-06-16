package models

import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import slick.jdbc.JdbcProfile
import testHelpers.TestDatabase
import play.api.test.WithApplication
import scala.concurrent.Await
import scala.concurrent.duration._

class PlutoCommissionSpec extends Specification with utils.BuildMyApp with TimestampSerialization {
  "PlutoCommission.mostRecentByWorkingGroup" should {
    "return the most recently modified commission in the provided group" in new WithApplication(buildApp){
      private val injector = app.injector

      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db

      val result = Await.result(PlutoCommission.mostRecentByWorkingGroup(1),10.seconds)

      result must beSuccessfulTry
      result.get must beSome
      result.get.get.collectionId mustEqual 4567
      result.get.get.title mustEqual "My test commission 4"
    }
  }
}
