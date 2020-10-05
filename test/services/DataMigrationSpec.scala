package services

import java.io.{File, FileInputStream}

import models.{EntryStatus, PlutoCommission, ProductionOffice}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import java.sql.Timestamp
import java.time.{Instant, LocalDateTime}

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import services.migrationcomponents.VSUserCache
import slick.jdbc.PostgresProfile
import utils.BuildMyApp

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.Source

class DataMigrationSpec extends Specification with Mockito with BuildMyApp {
  "DataMigration.valuesForField" should {
    "Lift a single value and return it" in {
      val jsonSrc = new File("test/testdata/vscommission.json")
      val jsonStream = new FileInputStream(jsonSrc)
      val jsonContent = Json.parse(jsonStream)
      jsonStream.close()

      val result = DataMigration.valuesForField(jsonContent, "gnm_commission_status")
      result must beSome(Seq("New"))
    }

    "Return None if the value does not exist" in {
      val jsonSrc = new File("test/testdata/vscommission.json")
      val jsonStream = new FileInputStream(jsonSrc)
      val jsonContent = Json.parse(jsonStream)
      jsonStream.close()

      val result = DataMigration.valuesForField(jsonContent, "gfsdgfdsgsdgs")
      result must beNone
    }

    "Return multiple values as a sequence" in {
      val jsonSrc = new File("test/testdata/vscommission.json")
      val jsonStream = new FileInputStream(jsonSrc)
      val jsonContent = Json.parse(jsonStream)
      jsonStream.close()

      val result = DataMigration.valuesForField(jsonContent, "gnm_commission_owner")
      result must beSome(Seq("2","6"))
    }
  }

  "DataMigration.performCommissionFieldUpdate" should {
    "update the given values on an existing record" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer   = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfigProvider:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val db = dbConfigProvider.get[PostgresProfile].db

      val testComm = PlutoCommission(None,None,None,Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), "test to update",
      EntryStatus.New,None,2, None, Timestamp.from(Instant.now()), "",None,ProductionOffice.UK,None)

      val savedEntry = Await.result(testComm.save, 5 seconds)
      savedEntry must beSuccessfulTry

      val mockUserCache = mock[VSUserCache]
      mockUserCache.lookup(any) returns Some("test_user")

      val toTest = new DataMigration("https://base-path","user","password","VX", mockUserCache)

      Await.ready(toTest.performCommissionFieldUpdate(
        savedEntry.get.id.get,
        Timestamp.valueOf(LocalDateTime.of(2020,5,6,0,0)),
        "new_owner",
        Some("some new notes"),
        ProductionOffice.Aus,
        Some("original title here")
      ), 5 seconds)

      val updatedComm = Await.result(PlutoCommission.forId(savedEntry.get.id.get), 5 seconds)

      updatedComm must beSome
      updatedComm.get.title mustEqual "test to update"
      updatedComm.get.status mustEqual EntryStatus.New
      updatedComm.get.owner mustEqual "new_owner"
      updatedComm.get.notes must beSome("some new notes")
      updatedComm.get.productionOffice mustEqual ProductionOffice.Aus
      updatedComm.get.originalTitle must beSome("original title here")
    }
  }
}
