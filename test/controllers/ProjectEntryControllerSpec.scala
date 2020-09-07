package controllers

import java.sql.Timestamp
import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.stream.Materializer
import models._
import org.specs2.matcher.ThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import play.api.test._
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ProjectEntryControllerSpec extends Specification with utils.BuildMyApp with ThrownExpectations with Mockito with ProjectEntrySerializer with PlutoConflictReplySerializer {
  sequential

//  "ProjectEntryController.create" should {
//    "validate request data and call out to ProjectCreationActor" in new WithApplication(buildAppWithMockedProjectHelper){
//      implicit val system:ActorSystem = app.actorSystem
//      implicit val materializer:Materializer = Materializer(system)
//      //needed for database access
//      private val injector = app.injector
//      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
//      private implicit val db = dbConfigProvider.get[JdbcProfile].db
//
//      val testCreateDocument =
//        """
//          |{
//          |  "filename": "sometestprojectfile",
//          |  "destinationStorageId": 1,
//          |  "title": "MyTestProjectEntry",
//          |  "projectTemplateId": 1,
//          |  "user": "test-user"
//          |}
//        """.stripMargin
//
//      val fakeProjectEntry = ProjectEntry(Some(999),1,None,"MyTestProjectEntry",Timestamp.valueOf(LocalDateTime.now()),"test-user",None,None)
//      mockedProjectHelper.create(any[ProjectRequestFull],org.mockito.Matchers.eq(None))(org.mockito.Matchers.eq(db),org.mockito.Matchers.any[play.api.Configuration]) answers((arglist,mock)=>Future(Success(fakeProjectEntry)))
//      val response = route(app, FakeRequest(
//        method="PUT",
//        uri="/api/project",
//        headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
//        body=testCreateDocument).withSession("uid"->"testuser")).get
//
//      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
//      println(jsondata.toString)
//      (jsondata \ "status").as[String] must equalTo("ok")
//      (jsondata \ "projectId").as[Int] must equalTo(999)
//      (jsondata \ "detail").as[String] must equalTo("created project")
//      status(response) must equalTo(OK)
//    }
//  }

  "ProjectEntryController.listFiltered" should {
    "show projectentry items filtered by title" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "title": "ThatTestProject",
          |  "match": "W_ENDSWITH"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata.toString())
      status(response) must equalTo(OK)

      val resultList = (jsondata \ "result").as[List[ProjectEntry]]
      resultList.length mustEqual 1
      //the actual id number can be different depending on the order of the tests
      resultList.head.projectTitle mustEqual "ThatTestProject"
    }

    "show projectentry items filtered by vidispine id" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "vidispineId": "VX-2345",
          |  "match": "W_ENDSWITH"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      status(response) must equalTo(OK)

      val resultList = (jsondata \ "result").as[List[ProjectEntry]]
      resultList.length mustEqual 2
      resultList.head.vidispineProjectId must beSome("VX-2345")
      resultList(1).vidispineProjectId must beSome("VX-2345")
    }

    "return an empty list for a filename that is not associated with any projects" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "match": "W_ENDSWITH",
          |  "filename": "/path/to/another/file.project"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata.toString)
      status(response) must equalTo(OK)

      val resultList = (jsondata \ "result").as[List[ProjectEntry]]
      resultList.length mustEqual 0
    }

    "show projectentry items filtered by file association" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "match": "W_ENDSWITH",
          |  "filename": "/path/to/thattestproject"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata.toString)
      status(response) must equalTo(OK)

      val resultList = (jsondata \ "result").as[List[ProjectEntry]]
      resultList.length mustEqual 1
      resultList.head.id must beSome(3)
      resultList.head.projectTitle mustEqual "ThatTestProject"
    }

    "return an empty list if nothing matches" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "match": "W_ENDSWITH",
          |  "title": "Fdgfsdgfgdsfgdsfgsd"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      status(response) must equalTo(OK)

      val resultList = (jsondata \ "result").as[List[ProjectEntry]]
      resultList.length mustEqual 0
    }

    "reject invalid json doc with 400" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testSearchDocument =
        """{
          |  "title": "ThatTestProject",
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/list",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testSearchDocument).withSession("uid"->"testuser")).get

      status(response) must equalTo(BAD_REQUEST)
    }
  }
}
