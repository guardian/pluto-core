package services.migrationcomponents

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import org.specs2.mutable.Specification
import play.api.test.WithApplication
import utils.BuildMyApp
import scala.concurrent.duration._

import scala.concurrent.Await

class MultipleCounterSpec extends Specification with BuildMyApp {
  "MultipleCounter" should {
    "count every input" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat:Materializer = app.injector.instanceOf(classOf[Materializer])

      val ctrFac = new MultipleCounter[String](5)
      val graph = GraphDSL.create(ctrFac) { implicit builder=> ctr=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val sources = (1 to 5).map(n=>builder.add(Source.single[String](s"$n")))
        (0 to 4).foreach(n=>sources(n) ~> ctr.inlets(n))
        val sink = builder.add(Sink.ignore)
        ctr ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 3 seconds)
      result mustEqual Seq(1,1,1,1,1)
    }
  }
}
