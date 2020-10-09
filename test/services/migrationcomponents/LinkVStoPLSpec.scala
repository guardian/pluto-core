package services.migrationcomponents

import java.io.{File, FileInputStream}
import java.sql.Timestamp
import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import models.{EntryStatus, ProductionOffice, ProjectEntry}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.test.WithApplication
import slick.jdbc.PostgresProfile
import utils.BuildMyApp

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class LinkVStoPLSpec extends Specification with Mockito with BuildMyApp {
  "LinkVStoPL.getVSProjectStatus" should {
    "return a matching project status" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_status") returns Some("New")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProjectStatus(mockEntry)
      result must beSome(EntryStatus.New)
    }

    "return In Production for In production" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_status") returns Some("In production")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProjectStatus(mockEntry)
      result must beSome( EntryStatus.InProduction)
    }

    "return In Production for In Production" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_status") returns Some("In Production")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProjectStatus(mockEntry)
      result must beSome( EntryStatus.InProduction)
    }

    "return None if the status is not valid" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_status") returns Some("gsdgsdfgsdfgsdgfdfsg")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProjectStatus(mockEntry)
      result must beNone
    }

    "not fail if no status is set" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_status") returns None

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProjectStatus(mockEntry)
      result must beNone
    }
  }

  "LinkVStoPL.getVSProductionOffice" should {
    "return the production office if set" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_production_office") returns Some("Aus")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProductionOffice(mockEntry)
      result must beSome(ProductionOffice.Aus)
    }

    "return None if invalid" in {
      val mockEntry = mock[VSProjectEntity]
      mockEntry.getSingle("gnm_project_production_office") returns Some("safasdfasfasfsaf")

      val toTest = new LinkVStoPL(1,mock[VSUserCache])(mock[DatabaseConfigProvider])
      val result = toTest.getVSProductionOffice(mockEntry)
      result must beNone
    }
  }

  def vsProjectEntityFor(filePath:String) = {
    val f = new File(filePath)
    val inputStream = new FileInputStream(f)
    val jsonData = Json.parse(inputStream)
    inputStream.close()

    VSProjectEntity(jsonData)
  }

  "LinkVStoPL" should {
    "create a new record if there is nothing pre-existing with that ID" in new WithApplication(buildApp) {
      implicit val actorSystem:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfig:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val ec:ExecutionContext = actorSystem.dispatcher

      val mockVSUserCache = mock[VSUserCache]
      mockVSUserCache.lookup(169) returns Some("someuser")

      val sinkFac = Sink.seq[ProjectEntry]

      val sourceData = vsProjectEntityFor("test/testdata/vs-project-single.json")
      val graph = GraphDSL.create(sinkFac) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator(()=>Seq(sourceData, sourceData).iterator))
        val toTest = builder.add(new LinkVStoPL(1, mockVSUserCache))

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run().map(result=>{
        Thread.sleep(1000)
        result
      }), 10 seconds)

      result.length mustEqual 1
      result.head.projectTitle mustEqual "New Normal episode 3 - Work"
      result.head.productionOffice mustEqual ProductionOffice.UK
      result.head.status mustEqual EntryStatus.New
      result.head.projectTypeId mustEqual 1
      result.head.vidispineProjectId must beSome("KP-55166")
      result.head.created mustEqual Timestamp.valueOf(LocalDateTime.of(2020,8,11,12,9,54, 546000000))
      result.head.updated mustEqual Timestamp.valueOf(LocalDateTime.of(2020,8,11,13,10,7,496000000))
      result.head.user mustEqual "someuser"
      result.head.deletable must beSome(false)
      result.head.sensitive must beSome(false)
      result.head.deep_archive must beSome(true)

      there was atLeastOne(mockVSUserCache).lookup(169)
    }

    "update an existing record if there is one pre-existing with that VS ID" in new WithApplication(buildApp) {
      implicit val actorSystem:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer = app.injector.instanceOf(classOf[Materializer])
      implicit val dbConfig:DatabaseConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfig.get[PostgresProfile].db

      implicit val ec:ExecutionContext = actorSystem.dispatcher

      val mockVSUserCache = mock[VSUserCache]
      mockVSUserCache.lookup(169) returns Some("someuser")

      val sinkFac = Sink.seq[ProjectEntry]

      val preExistingProject = Await.result(ProjectEntry.lookupByVidispineId("VX-3456"), 10 seconds)
      preExistingProject must beSuccessfulTry
      preExistingProject.get.length mustEqual 1

      val sourceData = vsProjectEntityFor("test/testdata/vs-project-update.json")
      val graph = GraphDSL.create(sinkFac) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(Source.fromIterator(()=>Seq(sourceData, sourceData).iterator))
        val toTest = builder.add(new LinkVStoPL(2, mockVSUserCache))

        src ~> toTest ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run().map(result=>{
        Thread.sleep(1000)
        result
      }), 10 seconds)

      result.length mustEqual 1
      result.head.projectTitle mustEqual "New Normal episode 3 - Work"
      result.head.productionOffice mustEqual ProductionOffice.UK
      result.head.status mustEqual EntryStatus.New
      result.head.projectTypeId mustEqual 1
      result.head.vidispineProjectId must beSome("VX-3456")
      result.head.created mustEqual Timestamp.valueOf(LocalDateTime.of(2016,12,11,12,21,11, 21000000))
      result.head.updated mustEqual Timestamp.valueOf(LocalDateTime.of(2020,8,11,13,10,7,496000000))
      result.head.user mustEqual "you"
      result.head.deletable must beNone
      result.head.sensitive must beNone
      result.head.deep_archive must beNone

      there was no(mockVSUserCache).lookup(any)

    }
  }
}
