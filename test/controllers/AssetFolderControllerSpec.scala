package controllers

import models.{ProjectEntry, ProjectMetadata, ProjectMetadataRow, ProjectMetadataSerializer}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterEach
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.api.test.{FakeRequest, WithApplication}
import slick.jdbc.{JdbcProfile, PostgresProfile}
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

class AssetFolderControllerSpec extends Specification with utils.BuildMyApp with BeforeAfterEach with ProjectMetadataSerializer {
  sequential

  override def before = new WithApplication(buildApp) {
    private val injector = app.injector
    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
    val insertFut = db.run(
        TableQuery[ProjectMetadataRow] ++= Seq(
          ProjectMetadata(id=None,projectRef=1,key="somekey",value=Some("somevalue")),
          ProjectMetadata(None,3,key=ProjectMetadata.ASSET_FOLDER_KEY, value=Some("/path/to/otherassets")),
          ProjectMetadata(None, projectRef=1,key=ProjectMetadata.ASSET_FOLDER_KEY, value=Some("/path/to/assets"))
        )
    )

    Await.ready(insertFut, 10 seconds)
  }

  override def after = new WithApplication(buildApp) {
    private val injector = app.injector
    protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
    protected implicit val db:JdbcProfile#Backend#Database = dbConfigProvider.get[PostgresProfile].db
    val deleteFut = db.run(
      DBIO.seq(
        TableQuery[ProjectMetadataRow].filter(_.projectRef===12).delete,
        TableQuery[ProjectMetadataRow].filter(_.projectRef===18).delete
      )
    )

    Await.ready(deleteFut, 10 seconds)
  }

  "AssetFolderController.assetFolderForPath" should {
    "return the project and asset folder for a matching path" in new WithApplication(buildApp) {
      val response = route(app, FakeRequest(
       "GET",
       "/api/assetfolder/lookup?path=/path/to/assets/and/some/file.mxf",
      ).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]

      (jsondata \ "status").as[String] must equalTo("ok")
      (jsondata \ "project").as[String] must equalTo("1")
      (jsondata \ "path").as[String] mustEqual "/path/to/assets"
      status(response) must equalTo(OK)
    }

    "return a 404 not found if nothing was there" in new WithApplication(buildApp) {
      val response = route(app, FakeRequest(
        "GET",
        "/api/assetfolder/lookup?path=/path/to/somethingnotthere",
      ).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]

      (jsondata \ "status").as[String] must equalTo("notfound")
      status(response) must equalTo(NOT_FOUND)
    }
  }

  "AssetFolderController.assetFolderForProject" should {
    "return asset folder data for an existing project" in new WithApplication(buildApp) {
      val response = route(app, FakeRequest(
        "GET",
        "/api/project/1/assetfolder",
      ).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      println(jsondata)
      (jsondata \ "status").as[String] must equalTo("ok")
      val returnedRecord = (jsondata \ "result").as[ProjectMetadata]
      returnedRecord.projectRef mustEqual 1
      returnedRecord.value must beSome("/path/to/assets")

      status(response) must equalTo(OK)
    }

    "return 404 if the project has no asset folder" in new WithApplication(buildApp) {
      val response = route(app, FakeRequest(
        "GET",
        "/api/project/2/assetfolder",
      ).withSession("uid"->"testuser")).get

      val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
      (jsondata \ "status").as[String] must equalTo("not_found")
      status(response) must equalTo(NOT_FOUND)
    }
  }
}
