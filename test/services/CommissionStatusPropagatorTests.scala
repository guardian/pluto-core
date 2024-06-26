package services

import java.sql.Timestamp
import java.time.Instant
import akka.actor.{ActorSystem, Props}
import models.{EntryStatus, PlutoCommission, PlutoCommissionRow, ProductionOffice, ProjectEntry, ProjectEntryRow}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import akka.pattern.ask
import akka.testkit.TestProbe
import play.api.libs.json.{JsValue, Json}
import services.RabbitMqPropagator.ChangeEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class CommissionStatusPropagatorTests extends Specification with BeforeAfterEach with utils.BuildMyApp {
  sequential

  implicit val actorTimeout:akka.util.Timeout = 30 seconds

  override def after: Unit = new WithApplication(buildApp) {
    private val injector = app.injector
    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

    val deleteFut = getParentCommissionId.flatMap({
      case Some(commissionRec)=>
        db.run(DBIO.seq(
          TableQuery[ProjectEntryRow].filter(_.commission===commissionRec.id.get).delete,
          TableQuery[PlutoCommissionRow].filter(_.id===commissionRec.id.get).delete
        ))
      case None=>
        println("Nothing to remove")
        Future( () )
    })

    Await.ready(deleteFut, 5 seconds)
    println("afterEach completed")
  }

  //whole application is required to initialise slick
  override def before: Unit = new WithApplication(buildApp) {

    private val injector = app.injector
    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

    val commFut = db.run(
      TableQuery[PlutoCommissionRow] returning TableQuery[PlutoCommissionRow].map(_.id) += PlutoCommission(
        Some(1499),
        None,None,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        "CommissionStatusPropagatorTests",
        EntryStatus.New,
        None,
        1,
        None,
        Timestamp.from(Instant.now),
        "TestUser",
        None,
        ProductionOffice.UK,
        None,
        None,
        None
      )
    )

    val insertFut = commFut.flatMap(insertedRowId=>{
      println(s"inserting projects with parent $insertedRowId")
      val timestamp = Timestamp.from(Instant.now())
      db.run(
        (TableQuery[ProjectEntryRow] ++= Seq(
          ProjectEntry(Some(1111),1,None,"TestNewProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.New, ProductionOffice.Aus, None, None),
          ProjectEntry(Some(1112),1,None,"TestInProdProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.InProduction, ProductionOffice.Aus, None, None),
          ProjectEntry(Some(1113),1,None,"TestHeldProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Held, ProductionOffice.Aus, None, None),
          ProjectEntry(Some(1114),1,None,"TestCompletedProject",timestamp, timestamp,"testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Completed, ProductionOffice.Aus, None, None),
          ProjectEntry(Some(1115),1,None,"TestKilledProject",timestamp, timestamp, "testuser", None,Some(insertedRowId),None,None,None,EntryStatus.Killed,ProductionOffice.Aus, None, None),
        ))
      )
    })

    val result = Await.result(insertFut, 5 seconds)
    println(s"Result: ${result}")
    val newDatabaseState = Await.result(getTestRecords, 5 seconds)
    println(s"newDatabaseState: ${newDatabaseState}")
  }

  private def getParentCommissionId(implicit db:JdbcProfile#Backend#Database) =
    db.run(TableQuery[PlutoCommissionRow].filter(_.title==="CommissionStatusPropagatorTests").result.headOption)

  private def getTestRecords(implicit db:JdbcProfile#Backend#Database) = getParentCommissionId.flatMap(commissionId=>
    db.run(TableQuery[ProjectEntryRow].filter(_.commission===commissionId.get.id).result)
  )

  private def validateRabbitMQMessage(projectJson: JsValue): Unit = {
    val id = (projectJson \ "id").asOpt[Int]
    val projectTypeId = (projectJson \ "projectTypeId").asOpt[Int]
    val title = (projectJson \ "title").asOpt[String]
    val created = (projectJson \ "created").asOpt[String]
    val updated = (projectJson \ "updated").asOpt[String]
    val user = (projectJson \ "user").asOpt[String]
    val commissionId = (projectJson \ "commissionId").asOpt[Int]
    val status = (projectJson \ "status").asOpt[String]
    val productionOffice = (projectJson \ "productionOffice").asOpt[String]

    println(s"Values: id=$id, projectTypeId=$projectTypeId, title=$title, created=$created, updated=$updated, user=$user, commissionId=$commissionId, status=$status, productionOffice=$productionOffice")

    val isoDateFormat = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{1,3}(Z|[+-]\\d{2}:\\d{2})".r

    Seq(id, projectTypeId, title, created, updated, user, commissionId, status, productionOffice).forall(_.isDefined) mustEqual true
    isoDateFormat.findFirstIn(created.getOrElse("")) mustEqual created
    isoDateFormat.findFirstIn(updated.getOrElse("")) mustEqual updated
  }

  "CommissionStatusPropagator!CommissionStatusUpdate" should {

      "Not change contained project statuses if the commission status is NEW" in new WithApplication(buildApp) {
      private val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

      implicit val system: ActorSystem = ActorSystem("test-rabbitmq-propagator")
      private val actorSystem = injector.instanceOf(classOf[ActorSystem])

      val mockedRmqPropagatorProbe = TestProbe()
      val mockedRmqPropagator = mockedRmqPropagatorProbe.ref

      val toTest = actorSystem.actorOf(Props(new CommissionStatusPropagator(dbConfigProvider, mockedRmqPropagator)))

      val parentCommission = Await.result(getParentCommissionId, 5 seconds)
      parentCommission must beSome
      val result = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.New), 30 seconds)

      result mustEqual 0

      val sentMessage = mockedRmqPropagatorProbe.receiveOne(5.seconds)  // Wait for a message to be sent to the mockRmqPropagator
      println(s"Message received by mockedRmqPropagator: $sentMessage")


      val newDatabaseState = Await.result(getTestRecords, 2 seconds)
      println(newDatabaseState)
      newDatabaseState.count(_.status==EntryStatus.New) mustEqual 1
      newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 1
      newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 1
      newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
      newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
    }

      "Not change contained project statuses if the commission status is InProduction" in new WithApplication(buildApp) {
        private val injector = app.injector
        protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
        protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

        private val actorSystem = injector.instanceOf(classOf[ActorSystem])
        val toTest = actorSystem.actorOf(Props(injector.instanceOf(classOf[CommissionStatusPropagator])))

        val parentCommission = Await.result(getParentCommissionId, 5 seconds)
        parentCommission must beSome
        val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.InProduction), 30 seconds)

        updatedRows mustEqual 0

        val newDatabaseState = Await.result(getTestRecords, 4 seconds)
        newDatabaseState.count(_.status==EntryStatus.New) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
      }


      "Change New and InProduction projects to Held if the status is Held" in new WithApplication(buildApp) {
        private val injector = app.injector
        protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
        protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

        implicit val system: ActorSystem = ActorSystem("test-rabbitmq-propagator")
        private val actorSystem = injector.instanceOf(classOf[ActorSystem])

        val mockedRmqPropagatorProbe = TestProbe()
        val mockedRmqPropagator = mockedRmqPropagatorProbe.ref

        val toTest = actorSystem.actorOf(Props(new CommissionStatusPropagator(dbConfigProvider, mockedRmqPropagator)))

        val parentCommission = Await.result(getParentCommissionId, 10 seconds)
        parentCommission must beSome
        val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Held), 30 seconds)

        updatedRows mustEqual 2

        val sentMessage = mockedRmqPropagatorProbe.receiveOne(10.seconds).asInstanceOf[ChangeEvent]
        val projectJsonArray: JsValue = Json.parse(sentMessage.json)
        val projectJson = projectJsonArray(0)
        validateRabbitMQMessage(projectJson)

        val newDatabaseState = Await.result(getTestRecords, 4 seconds)
        println(newDatabaseState)
        newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 3
        newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
      }
    }

      "Change New, InProduction and Held projects to Completed if the status is Completed" in new WithApplication(buildApp) {
        private val injector = app.injector
        protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
        protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

        implicit val system: ActorSystem = ActorSystem("test-rabbitmq-propagator")
        private val actorSystem = injector.instanceOf(classOf[ActorSystem])

        val mockedRmqPropagatorProbe = TestProbe()
        val mockedRmqPropagator = mockedRmqPropagatorProbe.ref

        val toTest = actorSystem.actorOf(Props(new CommissionStatusPropagator(dbConfigProvider, mockedRmqPropagator)))

        val parentCommission = Await.result(getParentCommissionId, 10 seconds)
        parentCommission must beSome
        val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Completed), 30 seconds)

        updatedRows mustEqual 3

        val sentMessage = mockedRmqPropagatorProbe.receiveOne(5.seconds).asInstanceOf[ChangeEvent]
        val projectJsonArray: JsValue = Json.parse(sentMessage.json)
        val projectJson = projectJsonArray(0)
        validateRabbitMQMessage(projectJson)

        val newDatabaseState = Await.result(getTestRecords, 4 seconds)
        println(newDatabaseState)
        newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 4
        newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 1
    }

      "Change New, InProduction and Held projects to Killed if the status is Killed" in new WithApplication(buildApp) {
        private val injector = app.injector
        protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
        protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db

        implicit val system: ActorSystem = ActorSystem("test-rabbitmq-propagator")
        private val actorSystem = injector.instanceOf(classOf[ActorSystem])

        val mockedRmqPropagatorProbe = TestProbe()
        val mockedRmqPropagator = mockedRmqPropagatorProbe.ref

        val toTest = actorSystem.actorOf(Props(new CommissionStatusPropagator(dbConfigProvider, mockedRmqPropagator)))

        val parentCommission = Await.result(getParentCommissionId, 10 seconds)
        parentCommission must beSome
        val updatedRows = Await.result(toTest ? CommissionStatusPropagator.CommissionStatusUpdate(parentCommission.get.id.get,EntryStatus.Killed), 30 seconds)

        updatedRows mustEqual 3

        val sentMessage = mockedRmqPropagatorProbe.receiveOne(10.seconds).asInstanceOf[ChangeEvent]
        val projectJsonArray: JsValue = Json.parse(sentMessage.json)
        val projectJson = projectJsonArray(0)
        validateRabbitMQMessage(projectJson)

        val newDatabaseState = Await.result(getTestRecords, 4 seconds)
        println(newDatabaseState)
        newDatabaseState.count(_.status==EntryStatus.New) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.InProduction) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.Held) mustEqual 0
        newDatabaseState.count(_.status==EntryStatus.Completed) mustEqual 1
        newDatabaseState.count(_.status==EntryStatus.Killed) mustEqual 4
      }
}