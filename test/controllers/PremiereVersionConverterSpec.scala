package controllers

import models.{DisplayedVersion, FileEntry, FileEntryDAO, PremiereVersionTranslation, PremiereVersionTranslationDAO}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.cache.SyncCacheApi
import play.api.cache.ehcache.EhCacheModule
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers.route
import play.api.test.{FakeHeaders, FakeRequest, WithApplication}
import testHelpers.TestDatabase
import utils.BuildMyApp
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.duration._
import java.io.File
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class PremiereVersionConverterSpec extends Specification with Mockito with BuildMyApp {
  def buildAppWithMockedConverter(mock:services.PremiereVersionConverter, mockDAO:FileEntryDAO, mockTranslationDAO:PremiereVersionTranslationDAO) = new GuiceApplicationBuilder().disable(classOf[EhCacheModule])
    .overrides(bind[DatabaseConfigProvider].to[TestDatabase.testDbProvider])
    .overrides(bind[SyncCacheApi].toInstance(mockedSyncCacheApi))
    .overrides(bind[services.PremiereVersionConverter].toInstance(mock))
    .overrides(bind[FileEntryDAO].toInstance(mockDAO))
    .overrides(bind[PremiereVersionTranslationDAO].toInstance(mockTranslationDAO))
    .configure("akka.persistence.journal.plugin"->"akka.persistence.journal.inmem")
    .configure("akka.persistence.journal.auto-start-journals"->Seq())
    .configure("akka.persistence.snapshot-store.plugin"->"akka.persistence.snapshot-store.local")
    .configure("akka.persistence.snapshot-store.auto-start-snapshot-stores"->Seq())
    .configure("akka.cluster.seed-nodes"->Seq("akka://application@127.0.0.1:2551"))
    .build

  "PremiereVersionConverter.changeVersion" should {
    "parse the required version and run through the process" in {
      val now = Timestamp.from(Instant.now())
      val fakeFileEntry = FileEntry(Some(1234), "/path/to/file.ext", 2, "fred", 1, now, now, now, true, true, None, Some(34))

      val mockedBackupFile = mock[Path]
      val mockedSourceFile = mock[Path]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future(mockedBackupFile)
      mockedConverter.checkExistingVersion(any, any) returns Future( () )

      mockedConverter.tweakProjectVersionStreaming(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaPath(any) returns Future(mockedSourceFile)
      mockedDAO.entryFor(any) returns Future(Some(fakeFileEntry))
      mockedDAO.saveSimple(any) answers((args:Array[AnyRef])=>Future(args.head.asInstanceOf[FileEntry]))

      val translation = PremiereVersionTranslation(36,"Some test", DisplayedVersion(1,2,3))
      val mockedTranslationDAO = mock[PremiereVersionTranslationDAO]
      mockedTranslationDAO.findDisplayedVersion(any) returns Future(Seq(translation))

      new WithApplication(buildAppWithMockedConverter(mockedConverter, mockedDAO, mockedTranslationDAO)) {
        val response = route(app, FakeRequest(
          method="POST",
          uri="/api/file/1234/changePremiereVersion?requiredDisplayVersion=1.2.3",
          headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
          body=""
        ).withSession("uid"->"testuser")
        ).get


        val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
        (jsondata \ "status").as[String] mustEqual "ok"
        (jsondata \ "entry"\ "filepath").as[String] mustEqual "/path/to/file.ext"
        (jsondata \ "entry" \ "premiereVersion").as[Int] mustEqual 36

        status(response) mustEqual 200

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was one(mockedConverter).backupFile(fakeFileEntry)
        there was one(mockedConverter).checkExistingVersion(fakeFileEntry, translation)
        there was one(mockedConverter).tweakProjectVersionStreaming(mockedSourceFile, mockedBackupFile, 34, translation)

        there was one(mockedDAO).getJavaPath(fakeFileEntry)
        there was one(mockedDAO).entryFor(1234)
        there was one(mockedDAO).saveSimple(org.mockito.ArgumentMatchers.argThat((newFileEntry:FileEntry)=>
          newFileEntry.maybePremiereVersion.contains(36) &&
            newFileEntry.id==fakeFileEntry.id &&
            newFileEntry.filepath==fakeFileEntry.filepath)
        )
      }
    }

    "return a 400 error if the version is not found" in {
      val now = Timestamp.from(Instant.now())
      val fakeFileEntry = FileEntry(Some(1234), "/path/to/file.ext", 2, "fred", 1, now, now, now, true, true, None, Some(34))

      val mockedBackupFile = mock[Path]
      val mockedSourceFile = mock[File]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future(mockedBackupFile)
      mockedConverter.checkExistingVersion(any, any) returns Future( () )

      mockedConverter.tweakProjectVersionStreaming(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaFile(any) returns Future(mockedSourceFile)
      mockedDAO.entryFor(any) returns Future(Some(fakeFileEntry))
      mockedDAO.saveSimple(any) answers((args:Array[AnyRef])=>Future(args.head.asInstanceOf[FileEntry]))

      val mockedTranslationDAO = mock[PremiereVersionTranslationDAO]
      mockedTranslationDAO.findDisplayedVersion(any) returns Future(Seq())

      new WithApplication(buildAppWithMockedConverter(mockedConverter, mockedDAO, mockedTranslationDAO)) {
        val response = route(app, FakeRequest(
          method="POST",
          uri="/api/file/1234/changePremiereVersion?requiredDisplayVersion=1.2.3",
          headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
          body=""
        ).withSession("uid"->"testuser")
        ).get


        val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
        (jsondata \ "status").as[String] mustEqual "error"
        (jsondata \ "detail").as[String] mustEqual "Version number is not recognised"

        status(response) mustEqual 400

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was no(mockedConverter).backupFile(any)
        there was no(mockedConverter).checkExistingVersion(any, any)
        there was no(mockedConverter).tweakProjectVersionStreaming(any, any, any, any)

        there was no(mockedDAO).getJavaFile(any)
        there was no(mockedDAO).entryFor(any)
        there was no(mockedDAO).saveSimple(any)
      }
    }

    "return a 400 error if the target file entry does not exist" in {
      val now = Timestamp.from(Instant.now())
      val fakeFileEntry = FileEntry(Some(1234), "/path/to/file.ext", 2, "fred", 1, now, now, now, true, true, None, Some(34))

      val mockedBackupFile = mock[Path]
      val mockedSourceFile = mock[File]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future(mockedBackupFile)
      mockedConverter.checkExistingVersion(any, any) returns Future( () )

      mockedConverter.tweakProjectVersionStreaming(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaFile(any) returns Future(mockedSourceFile)
      mockedDAO.entryFor(any) returns Future(None)
      mockedDAO.saveSimple(any) answers((args:Array[AnyRef])=>Future(args.head.asInstanceOf[FileEntry]))

      val translation = PremiereVersionTranslation(36,"Some test", DisplayedVersion(1,2,3))
      val mockedTranslationDAO = mock[PremiereVersionTranslationDAO]
      mockedTranslationDAO.findDisplayedVersion(any) returns Future(Seq(translation))

      new WithApplication(buildAppWithMockedConverter(mockedConverter, mockedDAO, mockedTranslationDAO)) {
        val response = route(app, FakeRequest(
          method="POST",
          uri="/api/file/1234/changePremiereVersion?requiredDisplayVersion=1.2.3",
          headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
          body=""
        ).withSession("uid"->"testuser")
        ).get


        val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
        (jsondata \ "status").as[String] mustEqual "error"
        (jsondata \ "detail").as[String] mustEqual "project id 1234 does not exist"

        status(response) mustEqual 400

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was no(mockedConverter).backupFile(any)
        there was no(mockedConverter).checkExistingVersion(any, any)
        there was no(mockedConverter).tweakProjectVersionStreaming(any, any, any, any)

        there was no(mockedDAO).getJavaFile(any)
        there was one(mockedDAO).entryFor(1234)
        there was no(mockedDAO).saveSimple(any)
      }
    }

    "return a 400 if the file does not need updating" in {
      val now = Timestamp.from(Instant.now())
      val fakeFileEntry = FileEntry(Some(1234), "/path/to/file.ext", 2, "fred", 1, now, now, now, true, true, None, Some(34))

      val mockedBackupFile = mock[Path]
      val mockedSourceFile = mock[Path]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future(mockedBackupFile)
      mockedConverter.checkExistingVersion(any, any) returns Future.failed(new RuntimeException("The target file is already at the requested version"))

      mockedConverter.tweakProjectVersionStreaming(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaPath(any) returns Future(mockedSourceFile)
      mockedDAO.entryFor(any) returns Future(Some(fakeFileEntry))
      mockedDAO.saveSimple(any) answers((args:Array[AnyRef])=>Future(args.head.asInstanceOf[FileEntry]))

      val translation = PremiereVersionTranslation(36,"Some test", DisplayedVersion(1,2,3))
      val mockedTranslationDAO = mock[PremiereVersionTranslationDAO]
      mockedTranslationDAO.findDisplayedVersion(any) returns Future(Seq(translation))

      new WithApplication(buildAppWithMockedConverter(mockedConverter, mockedDAO, mockedTranslationDAO)) {
        val response = route(app, FakeRequest(
          method="POST",
          uri="/api/file/1234/changePremiereVersion?requiredDisplayVersion=1.2.3",
          headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
          body=""
        ).withSession("uid"->"testuser")
        ).get

        val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
        (jsondata \ "status").as[String] mustEqual "error"
        (jsondata \ "detail").as[String] mustEqual "The target file is already at the requested version"

        status(response) mustEqual 400

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was no(mockedConverter).backupFile(any)
        there was one(mockedConverter).checkExistingVersion(fakeFileEntry, translation)
        there was no(mockedConverter).tweakProjectVersionStreaming(any, any, any, any)

        there was one(mockedDAO).getJavaPath(fakeFileEntry)
        there was one(mockedDAO).entryFor(1234)
        there was no(mockedDAO).saveSimple(any)
      }
    }

    "return 400 if the backup fails" in {
      val now = Timestamp.from(Instant.now())
      val fakeFileEntry = FileEntry(Some(1234), "/path/to/file.ext", 2, "fred", 1, now, now, now, true, true, None, Some(34))

      val mockedBackupFile = mock[Path]
      val mockedSourceFile = mock[Path]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future.failed(new RuntimeException("something blew up"))
      mockedConverter.checkExistingVersion(any, any) returns Future( () )

      mockedConverter.tweakProjectVersionStreaming(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaPath(any) returns Future(mockedSourceFile)
      mockedDAO.entryFor(any) returns Future(Some(fakeFileEntry))
      mockedDAO.saveSimple(any) answers((args:Array[AnyRef])=>Future(args.head.asInstanceOf[FileEntry]))

      val translation = PremiereVersionTranslation(36,"Some test", DisplayedVersion(1,2,3))
      val mockedTranslationDAO = mock[PremiereVersionTranslationDAO]
      mockedTranslationDAO.findDisplayedVersion(any) returns Future(Seq(translation))

      new WithApplication(buildAppWithMockedConverter(mockedConverter, mockedDAO, mockedTranslationDAO)) {
        val response = route(app, FakeRequest(
          method="POST",
          uri="/api/file/1234/changePremiereVersion?requiredDisplayVersion=1.2.3",
          headers=FakeHeaders(Seq(("Content-Type", "application/json"))),
          body=""
        ).withSession("uid"->"testuser")
        ).get


        val jsondata = Await.result(bodyAsJsonFuture(response), 5.seconds).as[JsValue]
        (jsondata \ "status").as[String] mustEqual "error"
        (jsondata \ "detail").as[String] mustEqual "something blew up"

        status(response) mustEqual 400

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was one(mockedConverter).backupFile(fakeFileEntry)
        there was one(mockedConverter).checkExistingVersion(fakeFileEntry, translation)
        there was no(mockedConverter).tweakProjectVersionStreaming(any,any,any,any)

        there was no(mockedDAO).getJavaPath(any)
        there was no(mockedDAO).entryFor(any)
        there was no(mockedDAO).saveSimple(any)
      }
    }
  }
}
