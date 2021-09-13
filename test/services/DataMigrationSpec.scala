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
import scala.concurrent.{Await, ExecutionContext, Future}

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
      result must beSome(Seq("2","6","not-valid-entry"))
    }
  }

  "DataMigration.performCommissionFieldUpdate" should {
    "update the given values on an existing record" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer   = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfigProvider:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val db = dbConfigProvider.get[PostgresProfile].db
      implicit val injector = app.injector
      val testComm = PlutoCommission(None,None,None,Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), "test to update",
      EntryStatus.New,None,2, None, Timestamp.from(Instant.now()), "",None,ProductionOffice.UK,None, None)

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

      //double-check that it has only affected the targetted commission
      val notUpdatedComm = Await.result(PlutoCommission.forId(1), 5 seconds)
      notUpdatedComm must beSome
      notUpdatedComm.get.owner mustNotEqual "new_owner"
      notUpdatedComm.get.notes must not beSome("some new notes")
      notUpdatedComm.get.originalTitle must not beSome("original title here")
    }
  }

  "DataMigration.migrateCommissionsData" should {
    "call updateCommission for every entry in the database" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer   = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfigProvider:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val db = dbConfigProvider.get[PostgresProfile].db
      implicit val ec:ExecutionContext = system.dispatcher
      implicit val injector = app.injector

      val mockUpdateCommission = mock[(PlutoCommission)=>Future[Option[PlutoCommission]]]
      mockUpdateCommission.apply(any) returns Future(Some(mock[PlutoCommission]))
      val toTest = new DataMigration("source-base","user","password","VX",mock[VSUserCache]) {
        override def updateCommission(itemToUpdate: PlutoCommission): Future[Option[PlutoCommission]] = mockUpdateCommission(itemToUpdate)
      }

      val finalCount = Await.result(toTest.migrateCommissionsData, 10 seconds)

      val firstComm = Await.result(PlutoCommission.forId(1), 5 seconds)
      val fourthComm = Await.result(PlutoCommission.forId(1), 5 seconds)
      println(s"test db has $finalCount commissions")
      //we don't know the exact number of comms in the database as other tests may have changed the state. But there should be at least 4
      there were atLeast(4)(mockUpdateCommission).apply(any)
      //spot-check a couple of the known commissions
      there was one(mockUpdateCommission).apply(firstComm.get)
      there was one(mockUpdateCommission).apply(fourthComm.get)
    }
  }

  "DataMigration.updateCommission" should {
    "call requestOriginalRecord to retrieve data from source and then call performCommissionFieldUpdate with values to update" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer   = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfigProvider:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val db = dbConfigProvider.get[PostgresProfile].db
      implicit val ec:ExecutionContext = system.dispatcher

      val jsonSrc = new File("test/testdata/vscommission.json")
      val jsonStream = new FileInputStream(jsonSrc)
      val jsonContent = Json.parse(jsonStream)
      jsonStream.close()

      val mockRequestOriginal = mock[String=>Future[JsValue]]
      mockRequestOriginal.apply(any) returns Future(jsonContent)

      val mockPerformCommissionFieldUpdate = mock[(Int, Timestamp, String, Option[String], ProductionOffice.Value, Option[String])=>Future[Unit]]
      mockPerformCommissionFieldUpdate.apply(any,any,any,any,any,any) returns Future( () )

      val mockUserCache = mock[VSUserCache]
      mockUserCache.lookup(any) returns Some("test-user")
      implicit val injector = app.injector

      val toTest = new DataMigration("source-base","user","passwrod","VX",mockUserCache) {
        override def requestOriginalRecord(vsId: String): Future[JsValue] = mockRequestOriginal(vsId)

        override def performCommissionFieldUpdate(recordId: Int,
                                                  updatedScheduledCompletion: Timestamp,
                                                  updatedOwner: String,
                                                  updatedNotes: Option[String],
                                                  updatedProductionOffice: ProductionOffice.Value,
                                                  updatedOriginalTitle: Option[String]): Future[Unit] =
          mockPerformCommissionFieldUpdate(recordId, updatedScheduledCompletion, updatedOwner, updatedNotes, updatedProductionOffice, updatedOriginalTitle)
      }

      val sourceCommission = mock[PlutoCommission]
      sourceCommission.collectionId returns Some(1234)
      sourceCommission.id returns Some(333)
      val result = Await.result(toTest.updateCommission(sourceCommission), 5 seconds)
      there was one(mockRequestOriginal).apply("VX-1234")

      there was one(mockUserCache).lookup(2)
      there was one(mockUserCache).lookup(6)
      there was one(mockPerformCommissionFieldUpdate).apply(
        333,
        Timestamp.valueOf(LocalDateTime.of(2020,3,4,0,0,0)),
        "test-user|test-user",
        None,
        ProductionOffice.UK,
        None
      )
    }
  }
}
