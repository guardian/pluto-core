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

      val mockedBackupFile = mock[File]
      val mockedSourceFile = mock[File]
      val mockedConverter = mock[services.PremiereVersionConverter]
      mockedConverter.backupFile(any) returns Future(mockedBackupFile)
      mockedConverter.checkExistingVersion(any, any) returns Future( () )

      mockedConverter.tweakProjectVersion(any,any,any,any) returns Future( () )
      val mockedDAO = mock[FileEntryDAO]
      mockedDAO.getJavaFile(any) returns Future(mockedSourceFile)
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
        println(jsondata.toString())
        (jsondata \ "status").as[String] mustEqual "ok"
        (jsondata \ "entry"\ "filepath").as[String] mustEqual "/path/to/file.ext"
        (jsondata \ "entry" \ "premiereVersion").as[Int] mustEqual 36

        status(response) mustEqual 200

        there was one(mockedTranslationDAO).findDisplayedVersion(DisplayedVersion(1,2,3))
        there was one(mockedConverter).backupFile(fakeFileEntry)
        there was one(mockedConverter).checkExistingVersion(fakeFileEntry, translation)
        there was one(mockedConverter).tweakProjectVersion(mockedSourceFile, mockedBackupFile, 34, translation)

        there was one(mockedDAO).getJavaFile(fakeFileEntry)
        there was one(mockedDAO).entryFor(1234)
        there was one(mockedDAO).saveSimple(org.mockito.ArgumentMatchers.argThat((newFileEntry:FileEntry)=>
          newFileEntry.maybePremiereVersion.contains(36) &&
            newFileEntry.id==fakeFileEntry.id &&
            newFileEntry.filepath==fakeFileEntry.filepath)
        )
      }
    }
  }
}
