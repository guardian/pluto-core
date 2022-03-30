package models

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}

import java.sql.Timestamp
import java.time.LocalDateTime
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication
import slick.jdbc.JdbcProfile

import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class FileEntrySpec extends Specification with utils.BuildMyApp {
  tag("controllers")
  sequential

  "updateFileHasContent" should {
    "update an existing database record if an id exists" in new WithApplication(buildApp){
      val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db
      private implicit val fileEntryDAO:FileEntryDAO = injector.instanceOf[FileEntryDAO]

      val testFileEntryBeforeFuture = fileEntryDAO.entryFor(1).map(_.get)
      val fileEntryBefore = Await.result(testFileEntryBeforeFuture, 10.seconds)
      fileEntryBefore.hasContent mustEqual false

      val resultFuture = testFileEntryBeforeFuture.flatMap(_.updateFileHasContent)
      val finalResult = Await.result(resultFuture, 10.seconds)

      finalResult must beSuccessfulTry(1) //expect 1 row updated
      val testFileEntryAfterFuture = fileEntryDAO.entryFor(1).map(_.get)

      val fileEntryAfter = Await.result(testFileEntryAfterFuture, 10.seconds)
      fileEntryAfter.hasContent mustEqual true
    }

    "return a failure if there is no id set in the record" in new WithApplication(buildApp) {
      val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db
      private implicit val fileEntryDAO:FileEntryDAO = injector.instanceOf[FileEntryDAO]

      val ts = Timestamp.valueOf(LocalDateTime.now())
      val testFileEntryBefore = FileEntry(None,"notexistingtestfile",1,"test-user",1,ts,ts,ts,false,false, None, None)

      val resultFuture = testFileEntryBefore.updateFileHasContent
      val finalResult = Await.result(resultFuture, 10.seconds)

      finalResult must beFailedTry
      finalResult.failed.get.toString mustEqual "java.lang.RuntimeException: Can't update a file record that has not been saved"
    }
  }

  "save" should {
    "insert a non-existing file into the database" in new WithApplication(buildApp) {
      val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db
      private implicit val fileEntryDAO:FileEntryDAO = injector.instanceOf[FileEntryDAO]

      val ts = Timestamp.valueOf(LocalDateTime.now())
      val testFileEntry = FileEntry(None,"/path/to/nonexisting",1,"test-user",1,ts,ts,ts,false,false, None, None)

      val result = Await.result(testFileEntry.save, 10.seconds)
      result must beSuccessfulTry
      result.get.id must beSome //ensure that the ID has been set

      val testEntryRead = Await.result(fileEntryDAO.entryFor(result.get.id.get),10.seconds)
      testEntryRead must beSome
      testEntryRead.get.filepath mustEqual "/path/to/nonexisting"
      testEntryRead.get.storageId mustEqual 1
      testEntryRead.get.user mustEqual "test-user"
      testEntryRead.get.mtime mustEqual ts
    }

    "update a pre-existing file in the database" in new WithApplication(buildApp) {
      val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db
      private implicit val fileEntryDAO:FileEntryDAO = injector.instanceOf[FileEntryDAO]

      val testFileEntry = Await.result(fileEntryDAO.entryFor(4),10.seconds)
      val ts = Timestamp.valueOf(LocalDateTime.now())
      testFileEntry must beSome
      testFileEntry.get.id must beSome(4)
      testFileEntry.get.filepath mustEqual "testprojectfile"
      testFileEntry.get.user mustEqual "you"
      testFileEntry.get.mtime mustEqual Timestamp.valueOf("2016-12-11 12:21:11.021")
      val fileEntryUpdated = testFileEntry.get.copy(user="test-user", mtime = ts)

      val updateResult = Await.result(fileEntryUpdated.save,10.seconds)
      updateResult must beSuccessfulTry

      val testEntryRead = Await.result(fileEntryDAO.entryFor(4),10.seconds)

      testEntryRead must beSome
      testEntryRead.get.id must beSome(4)
      testEntryRead.get.filepath mustEqual "testprojectfile"
      testEntryRead.get.mtime mustEqual ts
      testEntryRead.get.user mustEqual "test-user"
    }
  }

  "FileEntry.entryFor" should {
    "return None if the record does not exist" in new WithApplication(buildApp) {
      val injector = app.injector
      val fileEntryDAO = app.injector.instanceOf[FileEntryDAO]

      val testFileEntryBeforeFuture = fileEntryDAO.entryFor(9999)
      val result = Await.result(testFileEntryBeforeFuture, 10.seconds)
      result must beNone

    }
  }

  "FileEntry.findByFilename" should {
    "return a list of matching files" in new WithApplication(buildApp) {
      val fileEntryDAO = app.injector.instanceOf[FileEntryDAO]
      val result = Await.result(fileEntryDAO.findByFilename(Paths.get("/path/to/a/file.project"), None), 2.seconds)
      result.length mustEqual 1
      result.head.id must beSome(2)
    }
  }

  "FileEntry.scanAllFiles" should {
    "return a source suitable for iterating all files" in new WithApplication(buildApp) {
      implicit val actorSystem = app.injector.instanceOf(classOf[ActorSystem])
      implicit val mat = app.injector.instanceOf(classOf[Materializer])
      val fileEntryDAO = app.injector.instanceOf[FileEntryDAO]

      val future = fileEntryDAO.scanAllFiles(None)
        .toMat(Sink.seq[FileEntry])(Keep.right)
        .run()

      val result = Await.result(future, 10.seconds)
      result.length must beGreaterThanOrEqualTo(4)  //depending on the ordering of test cases, some test entries may have been added or removed
      result.head mustNotEqual result(1)
      result(1) mustNotEqual result(2)
      result(2) mustNotEqual result(3)
      result(1) mustNotEqual result(3)

    }
  }
}
