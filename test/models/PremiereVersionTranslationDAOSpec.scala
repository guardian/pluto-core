package models

import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.WithApplication
import testHelpers.TestDatabase
import utils.BuildMyApp
import scala.concurrent.duration._
import scala.concurrent.Await

class PremiereVersionTranslationDAOSpec extends Specification with BuildMyApp {
  "PremiereVersionTranslationDAO.findInternalVersion" should {
    "return a record for a known internal version" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[PremiereVersionTranslationDAO])
      val result = Await.result(toTest.findInternalVersion(35), 2.seconds)
      result must beSome(PremiereVersionTranslation(35, "Adobe Premiere Pro CC 2019", DisplayedVersion(13,0,0)))
    }

    "return None for an unknown internal version" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[PremiereVersionTranslationDAO])
      val result = Await.result(toTest.findInternalVersion(999), 2.seconds)
      result must beNone
    }
  }

  "PremiereVersionTranslationDAO.findDisplayedVersion" should {
    "return a record for a known display version" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[PremiereVersionTranslationDAO])
      val result = Await.result(toTest.findDisplayedVersion(DisplayedVersion(13,0,0)), 2.seconds)
      result.headOption must beSome(PremiereVersionTranslation(35, "Adobe Premiere Pro CC 2019", DisplayedVersion(13,0,0)))
      result.length mustEqual 1
    }

    "return an empty list for an unknown version" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[PremiereVersionTranslationDAO])
      val result = Await.result(toTest.findDisplayedVersion(DisplayedVersion(99,9,999)), 2.seconds)
      result.isEmpty must beTrue
    }
  }
}
