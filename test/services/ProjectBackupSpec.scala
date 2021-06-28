package services

import akka.stream.Materializer
import drivers.StorageDriver
import helpers.StorageHelper
import models.{FileEntry, StorageEntry}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.test.WithApplication

import java.nio.file.{Path, Paths}
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class ProjectBackupSpec extends Specification with utils.BuildMyApp with Mockito {
  "ProjectBackup.relativeFilePath" should {
    "relativize the given file path" in  {
      val toTest = new ProjectBackup(mock[Configuration],mock[DatabaseConfigProvider], mock[StorageHelper])(mock[Materializer]) {
        def callRelativize(a:Path, b:Path) = relativeFilePath(a,b)
      }

      val result = toTest.callRelativize(Paths.get("/srv/projects/some-project.dat"), Paths.get("/srv/projects"))
      result must beSuccessfulTry(Paths.get("some-project.dat"))
    }

    "fail if the path is not relativizable" in  {
      val toTest = new ProjectBackup(mock[Configuration],mock[DatabaseConfigProvider], mock[StorageHelper])(mock[Materializer]) {
        def callRelativize(a:Path, b:Path) = relativeFilePath(a,b)
      }

      val result = toTest.callRelativize(Paths.get("/srv/projects/some-project.dat"), Paths.get("mnt/elsewhere"))
      result must beAFailedTry
    }
  }

  "ProjectBackup.ascertainTarget" should {
    "error if the source file entry is None" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val result = Try { Await.result(toTest.ascertainTarget(None,Some(mock[FileEntry]), mock[StorageEntry]), 2.seconds) }
      result must beFailedTry
    }

    "return the incoming destination entry if it exists and the target storage does not support versioning" in new WithApplication(buildApp) {
      val fakeTargetStorage = StorageEntry(
        Some(999), None, None, None, "local",None, None, None,None, None, false, None, None
      )

      val sourceFile = mock[FileEntry]
      val destFile = mock[FileEntry]

      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val result = Await.result(toTest.ascertainTarget(Some(sourceFile), Some(destFile), fakeTargetStorage), 2.seconds)
      result mustEqual destFile
    }

    "build a new destination entry from the destination entry with version incremented" in new WithApplication(buildApp) {
      val fakeTargetStorage = StorageEntry(
        Some(999), None, None, None, "local",None, None, None,None, None, true, None, None
      )

      val nowTime = Instant.now()
      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        hasContent=true,
        hasLink = true,
        backupOf = None
      )

      val prevBackup = FileEntry(
        Some(456),
        "/path/to/sourcefile-backup1",
        999,
        "fred",
        4,
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        hasContent=true,
        hasLink = true,
        backupOf = Some(123)
      )

      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val result = Await.result(toTest.ascertainTarget(Some(sourceFile), Some(prevBackup), fakeTargetStorage), 2.seconds)

      result mustNotEqual prevBackup

      result.id must beNone
      result.filepath mustEqual "/path/to/sourcefile-backup1"
      result.storageId mustEqual 999
      result.version mustEqual 5
      result.ctime mustEqual prevBackup.ctime
      result.mtime mustEqual prevBackup.mtime
      result.atime mustEqual prevBackup.atime
      result.hasContent must beFalse
      result.hasLink must beTrue
      result.backupOf must beSome(123)
    }

    "build a new destination entry from the destination entry with version incremented" in new WithApplication(buildApp) {
      val fakeTargetStorage = StorageEntry(
        Some(999), None, None, None, "local",None, None, None,None, None, true, None, None
      )

      val nowTime = Instant.now()
      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        Timestamp.from(nowTime),
        hasContent=true,
        hasLink = true,
        backupOf = None
      )


      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val result = Await.result(toTest.ascertainTarget(Some(sourceFile), None, fakeTargetStorage), 2.seconds)

      result.id must beNone
      result.filepath mustEqual "/path/to/sourcefile"
      result.storageId mustEqual 999
      result.version mustEqual 1
      result.ctime mustEqual sourceFile.ctime
      result.mtime mustEqual sourceFile.mtime
      result.atime mustEqual sourceFile.atime
      result.hasContent must beFalse
      result.hasLink must beTrue
      result.backupOf must beSome(123)
    }
  }

  "ProjectBackup.checkNeedsBackup" should {
    "error if the source file is None" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val firstResult = Try { Await.result(toTest.checkNeedsBackup(None,Some(mock[FileEntry]), mock[StorageDriver], mock[StorageDriver]), 2.seconds) }
      firstResult must beFailedTry

      val nextResult = Try { Await.result(toTest.checkNeedsBackup(None,None, mock[StorageDriver], mock[StorageDriver]), 2.seconds) }
      nextResult must beFailedTry
    }

    "return true if the source file exists and the destination doesn't" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val result = Try { Await.result(toTest.checkNeedsBackup(Some(mock[FileEntry]), None, mock[StorageDriver], mock[StorageDriver]), 2.seconds) }
      result must beSuccessfulTry(true)
    }

    "return false if metadata shows no difference between files" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val nowTime = Instant.now().toEpochMilli.toString
      val sourceStorageDriver = mock[StorageDriver]
      sourceStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->nowTime)
      val destStorageDriver = mock[StorageDriver]
      destStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->nowTime)

      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = None
      )

      val prevBackup = FileEntry(
        Some(456),
        "/path/to/sourcefile-backup1",
        999,
        "fred",
        4,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = Some(123)
      )

      val result = Await.result(toTest.checkNeedsBackup(Some(sourceFile), Some(prevBackup), sourceStorageDriver, destStorageDriver), 2.seconds)
      result must beFalse
      there was one(sourceStorageDriver).getMetadata("/path/to/sourcefile", 1)
      there was one(destStorageDriver).getMetadata("/path/to/sourcefile-backup1", 4)

    }

    "return true if metadata shows a size difference" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val nowTime = Instant.now().toEpochMilli.toString
      val sourceStorageDriver = mock[StorageDriver]
      sourceStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"23456", Symbol("lastModified")->nowTime)
      val destStorageDriver = mock[StorageDriver]
      destStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->nowTime)

      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = None
      )

      val prevBackup = FileEntry(
        Some(456),
        "/path/to/sourcefile-backup1",
        999,
        "fred",
        4,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = Some(123)
      )

      val result = Await.result(toTest.checkNeedsBackup(Some(sourceFile), Some(prevBackup), sourceStorageDriver, destStorageDriver), 2.seconds)
      result must beTrue
      there was one(sourceStorageDriver).getMetadata("/path/to/sourcefile", 1)
      there was one(destStorageDriver).getMetadata("/path/to/sourcefile-backup1", 4)
    }

    "return false if metadata shows backup modified after source AND size the same" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val nowTime = Instant.now().toEpochMilli
      val sourceStorageDriver = mock[StorageDriver]
      sourceStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->(nowTime-150000).toString)
      val destStorageDriver = mock[StorageDriver]
      destStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->nowTime.toString)

      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = None
      )

      val prevBackup = FileEntry(
        Some(456),
        "/path/to/sourcefile-backup1",
        999,
        "fred",
        4,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = Some(123)
      )

      val result = Await.result(toTest.checkNeedsBackup(Some(sourceFile), Some(prevBackup), sourceStorageDriver, destStorageDriver), 2.seconds)
      result must beFalse
      there was one(sourceStorageDriver).getMetadata("/path/to/sourcefile", 1)
      there was one(destStorageDriver).getMetadata("/path/to/sourcefile-backup1", 4)
    }

    "return true if metadata shows backup modified before source AND size the same" in new WithApplication(buildApp) {
      val toTest = app.injector.instanceOf(classOf[ProjectBackup])

      val nowTime = Instant.now().toEpochMilli
      val sourceStorageDriver = mock[StorageDriver]
      sourceStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->nowTime.toString)
      val destStorageDriver = mock[StorageDriver]
      destStorageDriver.getMetadata(any, any) returns Map(Symbol("size")->"12345", Symbol("lastModified")->(nowTime-150000).toString)

      val sourceFile = FileEntry(
        Some(123),
        "/path/to/sourcefile",
        2,
        "fred",
        1,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = Nonef
      )

      val prevBackup = FileEntry(
        Some(456),
        "/path/to/sourcefile-backup1",
        999,
        "fred",
        4,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        hasContent=true,
        hasLink = true,
        backupOf = Some(123)
      )

      val result = Await.result(toTest.checkNeedsBackup(Some(sourceFile), Some(prevBackup), sourceStorageDriver, destStorageDriver), 2.seconds)
      result must beTrue
      there was one(sourceStorageDriver).getMetadata("/path/to/sourcefile", 1)
      there was one(destStorageDriver).getMetadata("/path/to/sourcefile-backup1", 4)
    }
  }
}
