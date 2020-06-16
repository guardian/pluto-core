package controllers

import java.sql.Timestamp
import java.time.Instant
import models.{EntryStatus, PlutoCommission, PlutoCommissionRow, ProductionOffice}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.AfterEach
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.test.WithApplication
import slick.jdbc.{JdbcProfile, PostgresProfile}
import utils.BuildMyApp

import scala.util.{Failure, Success}
import play.api.test.Helpers._
import play.api.test._
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps._

class PlutoCommissionControllerSpec extends Specification with Mockito with AfterEach with BuildMyApp{
  sequential
  /**
   * ensures that any database records created also get deleted again, regardless of whether test succeeds or fails
   */
  def after = new WithApplication(buildApp) {
    private val injector = app.injector
    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

    val dbFut = db.run(TableQuery[PlutoCommissionRow].filter(_.siteId==="PX").delete)
    val deletedRows = Await.result(dbFut, 5 seconds)
    println(s"cleanup deleted $deletedRows rows")
  }

  "PlutoCommissionController.updateStatus" should {
    "update the given record" in new WithApplication(buildApp) {
      private val injector = app.injector
      val testDocument = """{"status":"In Production"}"""

      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

      val initialTestRecord = PlutoCommission(None,2,"PX",Timestamp.from(Instant.now),Timestamp.from(Instant.now),
        "test commission", EntryStatus.New, None, 1, None,Timestamp.from(Instant.now), "TestUser", None, ProductionOffice.UK, None)

      val saveResult  = Await.result(initialTestRecord.save(db), 1 second)
      saveResult must beSuccessfulTry
      val savedRecord = saveResult.get

      println(s"saved record id is ${savedRecord.id}")
      val rq = FakeRequest(
        PUT,
        s"/api/pluto/commission/${savedRecord.id.get}/status",
        FakeHeaders(Seq("Content-Type"->"application/json")),
        testDocument
      ).withSession("uid"->"testuser")
      val response = Await.result(route(app, rq).get, 5 seconds)
      val responseContent = Await.result(response.body.consumeData.map(_.decodeString("UTF-8")), 5 seconds)
      println(responseContent)
      response.header.status mustEqual 200

      val updatedRecords = Await.result(db.run(TableQuery[PlutoCommissionRow].filter(_.id===savedRecord.id.get).result), 1 second)
      updatedRecords.isEmpty must beFalse
      updatedRecords.head.status mustEqual EntryStatus.InProduction
    }

    "return a bad data error if the provided status string is invalid" in new WithApplication(buildApp) {
      private val injector = app.injector
      val testDocument = """{"status":"gdfgdsgdgdsgd"}"""

      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

      val initialTestRecord = PlutoCommission(None,1,"PX",Timestamp.from(Instant.now),Timestamp.from(Instant.now), "test commission", EntryStatus.New,
        None, 1, None,Timestamp.from(Instant.now), "TestUser", None, ProductionOffice.UK, None)

      val saveResult  = Await.result(initialTestRecord.save(db), 1 second)
      saveResult must beSuccessfulTry
      val savedRecord = saveResult.get

      println(s"saved record id is ${savedRecord.id}")
      val rq = FakeRequest(
        PUT,
        s"/api/pluto/commission/${savedRecord.id.get}/status",
        FakeHeaders(Seq("Content-Type"->"application/json")),
        testDocument
      ).withSession("uid"->"testuser")
      val response = Await.result(route(app, rq).get, 5 seconds)
      val responseContent = Await.result(response.body.consumeData.map(_.decodeString("UTF-8")), 5 seconds)
      println(responseContent)
      response.header.status mustEqual 400

      val parsedJson = Json.parse(responseContent)
      (parsedJson \ "detail" \ "obj.status" \ 0 \ "msg" \ 0).get.toString() mustEqual "\"error.expected.validenumvalue\""
    }
  }
}
