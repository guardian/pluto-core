package streamcomponents

import java.sql.Timestamp
import java.time.ZonedDateTime

import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import models.{EntryStatus, ProductionOffice, ProjectEntry, ProjectEntryRow}
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import utils.BuildMyApp

import scala.concurrent.Await
import scala.concurrent.duration._

class ProjectSearchSourceSpec extends Specification with BuildMyApp {
  "ProjectSearchSource" should {
    "yield matching results from the database" in new WithApplication(buildApp) {
      val dbConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])

      val sinkFactory = Sink.fold[Seq[ProjectEntry],ProjectEntry](Seq())((acc,entry)=>acc++Seq(entry))
      val graph = GraphDSL.create(sinkFactory) { implicit builder => sink =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = new ProjectSearchSource(dbConfigProvider)(TableQuery[ProjectEntryRow])
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run, 30 seconds)

      result.length must beGreaterThanOrEqualTo(4)
      //other tests may have done a dummy update, so we can't assert the whole record as "updated" time may have changed
      result.head.id must beSome(1)
      result.head.projectTypeId mustEqual 1
      result.head.vidispineProjectId must beNone
      result.head.projectTitle mustEqual "InitialTestProject"
      result.head.created mustEqual Timestamp.valueOf("2016-12-11 12:21:11.021")
      result.head.user mustEqual "me"
      result.head.status mustEqual EntryStatus.InProduction
      result.head.productionOffice mustEqual ProductionOffice.UK

    }
  }
}
