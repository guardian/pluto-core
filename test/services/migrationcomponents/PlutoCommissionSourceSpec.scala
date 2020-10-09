package services.migrationcomponents

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import models.PlutoCommission
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import utils.BuildMyApp
import scala.concurrent.duration._
import scala.concurrent.Await

class PlutoCommissionSourceSpec extends Specification with BuildMyApp {
  "PlutoCommissionSource" should {
    "yield all of the pluto commissions in the database" in new WithApplication(buildApp) {
      protected val dbConfigProvider = app.injector.instanceOf(classOf[DatabaseConfigProvider])
      implicit val actorSystem:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer = app.injector.instanceOf(classOf[Materializer])

      val finalSink = Sink.fold[Seq[PlutoCommission],PlutoCommission](Seq())((acc, elem)=>acc :+ elem)
      val graph = GraphDSL.create(finalSink) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = builder.add(new PlutoCommissionSource(dbConfigProvider, 2))
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 5 seconds)
      result.length must beGreaterThanOrEqualTo(4)
      result.head.title mustEqual("My test commission")
      result(1).title mustEqual "My test commission 2"
      result(2).title mustEqual "My test commission 3"
      result(3).title mustEqual "My test commission 4"
    }
  }
}
