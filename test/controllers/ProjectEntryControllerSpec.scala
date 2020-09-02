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

  "ProjectEntryController.updateTitle" should {
    "update the title field of an existing record" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val dbRecordBefore = Await.result(ProjectEntry.entryForId(1),5.seconds).get
      val testUpdateDocument =
        s"""{
          |  "updated": "${dbRecordBefore.updated.get.toInstant}",
          |  "title": "some new title",
          |  "vsid": null
          |}""".stripMargin

      dbRecordBefore.projectTitle mustEqual "InitialTestProject"

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/1/title",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      status(response) must equalTo(OK)
      (jsondata \ "status").as[String] must equalTo("ok")
      (jsondata \ "detail").as[String] must equalTo("1 record(s) updated")

      val dbRecordAfter = Await.result(ProjectEntry.entryForId(1),5.seconds).get
      dbRecordAfter.projectTitle mustEqual "some new title"
    }
    "return 404 for a record that does not exist" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testUpdateDocument =
        """{
          |  "updated": "2016-12-11T12:21:11.021Z",
          |  "title": "some new title",
          |  "vsid": null
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/9999/title",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      status(response) must equalTo(NOT_FOUND)
    }
  }

  "ProjectEntryController.updateTitleByVsid" should {
    "update the title field of an existing record" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val dbRecordBefore = Await.result(ProjectEntry.entryForId(2),5.seconds).get
      val testUpdateDocument =
        s"""{
          |  "updated": "${dbRecordBefore.updated.get.toInstant}",
          |  "title": "some other new title",
          |  "vsid": null
          |}""".stripMargin

      dbRecordBefore.projectTitle mustEqual "AnotherTestProject"

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/by-vsid/VX-1234/title",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      status(response) must equalTo(OK)
      (jsondata \ "status").as[String] must equalTo("ok")
      (jsondata \ "detail").as[String] must equalTo("1 record(s) updated")

      val dbRecordAfter = Await.result(ProjectEntry.entryForId(2),5.seconds).get
      dbRecordAfter.projectTitle mustEqual "some other new title"
    }
    "update the title field of multiple matching records" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val dbRecordBefore = Await.result(ProjectEntry.entryForId(3),5.seconds).get
      val testUpdateDocument =
        s"""{
          |  "updated": "${dbRecordBefore.updated.get.toInstant}",
          |  "title": "ThatTestProject",
          |  "vsid": null
          |}""".stripMargin

      dbRecordBefore.projectTitle mustEqual "ThatTestProject"

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/by-vsid/VX-2345/title",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata.toString())
      status(response) must equalTo(OK)
      (jsondata \ "status").as[String] must equalTo("ok")
      (jsondata \ "detail").as[String] must equalTo("2 record(s) updated")

      val dbRecordAfter = Await.result(ProjectEntry.entryForId(3),5.seconds).get
      dbRecordAfter.projectTitle mustEqual "ThatTestProject"
      val dbRecordAfterNext = Await.result(ProjectEntry.entryForId(4),5.seconds).get
      dbRecordAfterNext.projectTitle mustEqual "ThatTestProject"
    }

    "return 404 for a record that does not exist" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testUpdateDocument =
        """{
          |  "title": "some new title",
          |  "updated": "2016-12-11T12:21:11.021Z",
          |  "vsid": null
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/by-vsid/VX-99999/title",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata.toString)
      status(response) must equalTo(NOT_FOUND)
    }
  }

  "ProjectEntryController.updateVsid" should {
    "update the vidipsineProjectId field of an existing record" in new WithApplication(buildApp) {
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val dbRecordBefore = Await.result(ProjectEntry.entryForId(1),5.seconds).get
      val testUpdateDocument =
        s"""{
          |  "updated": "${dbRecordBefore.updated.get.toInstant}",
          |  "title": "",
          |  "vsid": "VX-5678"
          |}""".stripMargin

      dbRecordBefore.vidispineProjectId must beNone

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/1/vsid",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      status(response) must equalTo(OK)
      (jsondata \ "status").as[String] must equalTo("ok")
      (jsondata \ "detail").as[String] must equalTo("1 record(s) updated")

      val dbRecordAfter = Await.result(ProjectEntry.entryForId(1),5.seconds).get
      dbRecordAfter.vidispineProjectId must beSome("VX-5678")
    }

    "return 404 for a record that does not exist" in new WithApplication(buildApp){
      implicit val system:ActorSystem = app.actorSystem
      implicit val materializer:Materializer = Materializer(system)
      //needed for database access
      private val injector = app.injector
      private val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      private implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testUpdateDocument =
        """{
          |  "title": "",
          |  "updated": "2016-12-11T12:21:11.021Z",
          |  "vsid": "VX-5678"
          |}""".stripMargin

      val response = route(app, FakeRequest(
        method="PUT",
        uri="/api/project/9999/vsid",
        headers=FakeHeaders(Seq(("Content-Type","application/json"))),
        body=testUpdateDocument).withSession("uid"->"testuser")).get

      status(response) must equalTo(NOT_FOUND)
    }
  }

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
      resultList.length mustEqual 2
      //the actual id number can be different depending on the order of the tests
      resultList.head.projectTitle mustEqual "ThatTestProject"
      resultList(1).projectTitle mustEqual "ThatTestProject"
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
