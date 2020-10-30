package services.migrationcomponents

import akka.actor.ActorSystem
import org.specs2.mutable.Specification
import play.api.libs.json.{JsValue, Json}
import play.api.test.WithApplication
import utils.BuildMyApp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.concurrent.duration._

class VSGlobalMetadataSpec extends Specification with BuildMyApp {
  "VSGlobalMetadataSpec" should {
    "load in global metadata from a data dump" in new WithApplication(buildApp) {
      val injector = app.injector
      implicit val actorSystem = injector.instanceOf(classOf[ActorSystem])

      val toTest = new VSGlobalMetadata() {
        override def extractGlobalMetadata(vsBaseUri: String, vsUser: String, vsPasswd: String): Future[JsValue] = {
          val s = Source.fromFile("globalmeta.json")
          val unparsedContent = s.mkString("")
          s.close()

          Future(Json.parse(unparsedContent))
        }
      }

      val result = Await.result(toTest.loadGroups(Seq("WorkingGroup","Commissioner"),"","",""), 2 seconds)
      println(result.head)
      println(result(1))
      result.length mustEqual 2
    }
  }
}
