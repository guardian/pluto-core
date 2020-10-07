package services.migrationcomponents

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import utils.AkkaTestkitSpecs2Support
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class VSProjectSourceSpec extends Specification with Mockito {
  def responseFor(filename:String):Future[HttpResponse] = Future {
    val s = scala.io.Source.fromFile(filename, "UTF-8")
    val bytesContent = s.getLines().foldLeft[ByteString](ByteString())((acc,elem)=>acc.concat(ByteString(elem)))
    s.close()

    val entity = HttpEntity(bytesContent)
    HttpResponse(StatusCodes.OK, entity=entity)
  }

  "VSProjectSource" should {
    "yield objects for each result in the given search response" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = Materializer(system)

      val mockHttpRequest = mock[HttpRequest=>Future[HttpResponse]]
      //ok, so the raw data is taken from an _item_ search; the data shape should not change though
      mockHttpRequest.apply(any) returns responseFor("test/testdata/vs-search-p1.json") thenReturns responseFor("test/testdata/vs-search-p2.json") thenReturns responseFor("test/testdata/vs-search-end.json")
      val sinkFact = Sink.seq[VSProjectEntity]
      val graph = GraphDSL.create(sinkFact) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = builder.add(new VSProjectSource("https://fake-base-uri","user","passwd"){
          override def makeHttpRequest(req: HttpRequest): Future[HttpResponse] = mockHttpRequest(req)
        })
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.length mustEqual 4
      there were three(mockHttpRequest).apply(any)
      result.head.getMetaOptional("itemId").flatMap(_.headOption) must beSome("VX-75")
      result(1).getMetaOptional("itemId").flatMap(_.headOption) must beSome("VX-68")
      result(2).getMetaOptional("itemId").flatMap(_.headOption) must beSome("VX-67")
      result(3).getMetaOptional("itemId").flatMap(_.headOption) must beSome("VX-65")
    }

    "raise an error if VS returns an error" in new AkkaTestkitSpecs2Support {
      implicit val mat:Materializer = Materializer(system)

      val mockHttpRequest = mock[HttpRequest=>Future[HttpResponse]]
      //ok, so the raw data is taken from an _item_ search; the data shape should not change though
      mockHttpRequest.apply(any) returns Future(HttpResponse(StatusCodes.BadRequest))
      val sinkFact = Sink.seq[VSProjectEntity]
      val graph = GraphDSL.create(sinkFact) {implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._
        val src = builder.add(new VSProjectSource("https://fake-base-uri","user","passwd"){
          override def makeHttpRequest(req: HttpRequest): Future[HttpResponse] = mockHttpRequest(req)
        })
        src ~> sink
        ClosedShape
      }

      def getResult() = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      getResult() should throwA[RuntimeException]
      there was one(mockHttpRequest).apply(any)
    }
  }
}
