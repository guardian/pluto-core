import akka.actor._
import akka.testkit._
import controllers.ProjectEntryController
import models.EntryStatus
import org.specs2.mock.Mockito
import org.specs2.mutable._
import org.specs2.specification.AfterAll
import services.CommissionStatusPropagator

import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

class CommissionStatusPropagatorSpec extends TestKit(ActorSystem("testsystem"))
  with ImplicitSender with SpecificationLike with Mockito with AfterAll {

  def afterAll = system.terminate()

  "CommissionStatusPropagator" should {

    "update commission projects and respond with Success" in {
      val mockController = mock[ProjectEntryController]
      mockController.updateCommissionProjects(EntryStatus.Completed, 1) returns Future.successful(Seq(Try(1)))

      val commissionStatusPropagator = system.actorOf(Props(new CommissionStatusPropagator(mockController)))
      val probe = TestProbe()

      commissionStatusPropagator.tell(CommissionStatusPropagator.CommissionStatusUpdate(1, EntryStatus.Completed, UUID.randomUUID()), probe.ref)

      probe.expectMsgPF() {
        case akka.actor.Status.Success(_) => ok
        case _ => ko
      }
    }

    "log error and respond with Failure if an error occurs during the update" in {
      val mockController = mock[ProjectEntryController]
      mockController.updateCommissionProjects(EntryStatus.Completed, 1) returns Future.failed(new Exception("error during update"))

      val commissionStatusPropagator = system.actorOf(Props(new CommissionStatusPropagator(mockController)))
      val probe = TestProbe()

      commissionStatusPropagator.tell(CommissionStatusPropagator.CommissionStatusUpdate(1, EntryStatus.Completed, UUID.randomUUID()), probe.ref)

      probe.expectMsgPF() {
        case akka.actor.Status.Failure(_) => ok
        case _ => ko
      }
    }
  }
}




//package services
//
//import java.sql.Timestamp
//import java.time.Instant
//
//import akka.actor.{ActorSystem, Props}
//import models.{EntryStatus, PlutoCommission, PlutoCommissionRow, ProductionOffice, ProjectEntry, ProjectEntryRow}
//import org.specs2.mutable.Specification
//import org.specs2.specification.BeforeAfterEach
//import play.api.db.slick.DatabaseConfigProvider
//import play.api.test.WithApplication
//import slick.jdbc.{JdbcProfile, PostgresProfile}
//import slick.lifted.TableQuery
//import utils.AkkaTestkitSpecs2Support
//import slick.jdbc.PostgresProfile
//import slick.jdbc.PostgresProfile.api._
//import akka.pattern.ask
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.duration._
//import scala.concurrent.{Await, Future}
//
//class CommissionStatusPropagatorTests extends Specification with BeforeAfterEach with utils.BuildMyApp {
//  sequential
//
//  implicit val actorTimeout:akka.util.Timeout = 30 seconds
//
//  override def after: Unit = new WithApplication(buildApp) {
//    private val injector = app.injector
//    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//    val deleteFut = getParentCommissionId.flatMap({
//      case Some(commissionRec)=>
//        db.run(DBIO.seq(
//          TableQuery[ProjectEntryRow].filter(_.commission===commissionRec.id.get).delete,
//          TableQuery[PlutoCommissionRow].filter(_.id===commissionRec.id.get).delete
//        ))
//      case None=>
//        println("Nothing to remove")
//        Future( () )
//    })
//
//    Await.ready(deleteFut, 10 seconds)
//    println("afterEach completed")
//  }
//
//  //whole application is required to initialise slick
//  override def before = new WithApplication(buildApp) {
//
//    private val injector = app.injector
//    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//    val commFut = db.run(
//      TableQuery[PlutoCommissionRow] returning TableQuery[PlutoCommissionRow].map(_.id) += PlutoCommission(
//        Some(1499),
//        None,None,
//        Timestamp.from(Instant.now()),
//        Timestamp.from(Instant.now()),
//        "CommissionStatusPropagatorTests",
//        EntryStatus.New,
//        None,
//        1,
//        None,
//        Timestamp.from(Instant.now),
//        "TestUser",
//        None,
//        ProductionOffice.UK,
//        None,
//        None
//      )
//    )
//
//    val insertFut = commFut.flatMap(insertedRowId=>{
//      println(s"inserting projects with parent $insertedRowId")
//      val timestamp = Timestamp.from(Instant.now())
//      db.run(
//        (TableQuery[ProjectEntryRow] ++= Seq(
//          ProjectEntry(Some(1111),1,None,"TestNewProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.New, ProductionOffice.Aus, None),
//          ProjectEntry(Some(1112),1,None,"TestInProdProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.InProduction, ProductionOffice.Aus, None),
//          ProjectEntry(Some(1113),1,None,"TestHeldProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Held, ProductionOffice.Aus, None),
//          ProjectEntry(Some(1114),1,None,"TestCompletedProject",timestamp, timestamp,"testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Completed, ProductionOffice.Aus, None),
//          ProjectEntry(Some(1115),1,None,"TestKilledProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Killed,ProductionOffice.Aus, None),
//        ))
//      )
//    })
//
//    val result = Await.result(insertFut, 10 seconds)
//    println(result)
//    val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//    println(newDatabaseState)
//  }
//
//  private def getParentCommissionId(implicit db:JdbcProfile#Backend#Database) =
//    db.run(TableQuery[PlutoCommissionRow].filter(_.title==="CommissionStatusPropagatorTests").result.headOption)
//
//  private def getTestRecords(implicit db:JdbcProfile#Backend#Database) = getParentCommissionId.flatMap(commissionId=>
//    db.run(TableQuery[ProjectEntryRow].filter(_.commission===commissionId.get.id).result)
//  )
//
//  "CommissionStatusPropagator!CommissionStatusUpdate" should {
//    "Not change contained project statuses if the commission status is NEW" in new WithApplication(buildApp) {
//      private val injector = app.injector
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//      private val actorSystem = injector.instanceOf(classOf[ActorSystem])
//      val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))
//
//      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
//      parentCommission must beSome
//      val result = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.New), 30 seconds)
//
//      result mustEqual 0
//
//      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//      println(newDatabaseState)
//      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
//    }
//
//    "Not change contained project statuses if the commission status is InProduction" in new WithApplication(buildApp) {
//      private val injector = app.injector
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//      private val actorSystem = injector.instanceOf(classOf[ActorSystem])
//      val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))
//
//      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
//      parentCommission must beSome
//      val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.InProduction), 30 seconds)
//
//      updatedRows mustEqual 0
//
//      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
//    }
//
//    "Change New and InProduction projects to Held if the status is Held" in new WithApplication(buildApp) {
//      private val injector = app.injector
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//      private val actorSystem = injector.instanceOf(classOf[ActorSystem])
//      val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))
//
//      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
//      parentCommission must beSome
//      val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Held), 30 seconds)
//
//      updatedRows mustEqual 2
//
//      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//      println(newDatabaseState)
//      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 3
//      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
//    }
//
//    "Change New, InProduction and Held projects to Completed if the status is Completed" in new WithApplication(buildApp) {
//      private val injector = app.injector
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//      private val actorSystem = injector.instanceOf(classOf[ActorSystem])
//      val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))
//
//      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
//      parentCommission must beSome
//      val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Completed), 30 seconds)
//
//      updatedRows mustEqual 3
//
//      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//      println(newDatabaseState)
//      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 4
//      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
//    }
//
//    "Change New, InProduction and Held projects to Killed if the status is Killed" in new WithApplication(buildApp) {
//      private val injector = app.injector
//      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
//
//      private val actorSystem = injector.instanceOf(classOf[ActorSystem])
//      val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))
//
//      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
//      parentCommission must beSome
//      val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Killed), 30 seconds)
//
//      updatedRows mustEqual 3
//
//      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
//      println(newDatabaseState)
//      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 0
//      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
//      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 4
//    }
//  }
//}
