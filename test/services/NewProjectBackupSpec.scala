package services

import akka.stream.Materializer
import drivers.StorageDriver
import helpers.StorageHelper
import models.{EntryStatus, FileEntry, FileEntryDAO, ProductionOffice, ProjectEntry, StorageEntry, StorageStatus}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector

import scala.concurrent.duration._
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class NewProjectBackupSpec extends Specification with Mockito {
  "NewProjectBackup.conditionalBackup" should {
    "attempt to back the given file up" in {
      implicit val mat = mock[Materializer]
      implicit val fileEntryDAO = mock[FileEntryDAO]
      implicit val injector = mock[Injector]
      val config = Configuration.empty

      val mockPerformBackup = mock[(FileEntry, Option[FileEntry], StorageEntry)=>Future[(FileEntry, FileEntry)]]
      mockPerformBackup.apply(any,any,any) answers((args:Any)=>{
        val sourceFile = args.asInstanceOf[Array[AnyRef]].head.asInstanceOf[FileEntry]
        //return value is a tuple of (copied file, source file)
        Future((mock[FileEntry], sourceFile))
      })

      val mockMakeProjectLink = mock[(FileEntry, FileEntry)=>Future[Option[Int]]]
      mockMakeProjectLink.apply(any,any) returns Future(Some(1234))

      val toTest = new NewProjectBackup(config, mock[DatabaseConfigProvider], mock[StorageHelper]) {
        override def performBackup(sourceEntry: FileEntry, maybePrevDestEntry: Option[FileEntry], destStorage: StorageEntry): Future[(FileEntry, FileEntry)] = mockPerformBackup(sourceEntry, maybePrevDestEntry, destStorage)

        override def makeProjectLink(sourceEntry: FileEntry, destEntry: FileEntry): Future[Option[Int]] = mockMakeProjectLink(sourceEntry, destEntry)
      }

      val nowTime = Timestamp.from(Instant.now)
      val backupRecord = (
        ProjectEntry(Some(1234),1,None,"Test project", nowTime, nowTime, "Test", None, None, None, None, None, EntryStatus.InProduction, ProductionOffice.US),
        Seq(
          FileEntry(Some(2345), "/path/to/projectfile", 1, "testuser", 1, nowTime, nowTime, nowTime, true, true, None, Some(39))
        )
      )
      val storageDriversMap = Map[Int, StorageDriver](
        1 -> mock[StorageDriver],
        2 -> mock[StorageDriver]
      )
      val storageMap = Map[Int, StorageEntry](
        1 -> StorageEntry(Some(1),Some("source"),Some("/data"),None, "local", None, None, None, None, None, false, Some(StorageStatus.ONLINE), Some(2)),
        2 -> StorageEntry(Some(2), Some("dest"), None, None, "remote", None, None, None, None, None, true, Some(StorageStatus.ONLINE), None)
      )
      val result = Await.result(toTest.conditionalBackup(backupRecord, storageDriversMap, storageMap), 10.seconds)

      result must beRight(true)
      there was one(mockPerformBackup).apply(backupRecord._2.head, None, storageMap(2))
      there was one(mockMakeProjectLink).apply(org.mockito.ArgumentMatchers.eq(backupRecord._2.head), any)
    }

    "abort if source storage==dest storage" in {
      implicit val mat = mock[Materializer]
      implicit val fileEntryDAO = mock[FileEntryDAO]
      implicit val injector = mock[Injector]
      val config = Configuration.empty

      val mockPerformBackup = mock[(FileEntry, Option[FileEntry], StorageEntry)=>Future[(FileEntry, FileEntry)]]
      mockPerformBackup.apply(any,any,any) answers((args:Any)=>{
        val sourceFile = args.asInstanceOf[Array[AnyRef]].head.asInstanceOf[FileEntry]
        //return value is a tuple of (copied file, source file)
        Future((mock[FileEntry], sourceFile))
      })

      val mockMakeProjectLink = mock[(FileEntry, FileEntry)=>Future[Option[Int]]]
      mockMakeProjectLink.apply(any,any) returns Future(Some(1234))

      val toTest = new NewProjectBackup(config, mock[DatabaseConfigProvider], mock[StorageHelper]) {
        override def performBackup(sourceEntry: FileEntry, maybePrevDestEntry: Option[FileEntry], destStorage: StorageEntry): Future[(FileEntry, FileEntry)] = mockPerformBackup(sourceEntry, maybePrevDestEntry, destStorage)

        override def makeProjectLink(sourceEntry: FileEntry, destEntry: FileEntry): Future[Option[Int]] = mockMakeProjectLink(sourceEntry, destEntry)
      }

      val nowTime = Timestamp.from(Instant.now)
      val backupRecord = (
        ProjectEntry(Some(1234),1,None,"Test project", nowTime, nowTime, "Test", None, None, None, None, None, EntryStatus.InProduction, ProductionOffice.US),
        Seq(
          FileEntry(Some(2345), "/path/to/projectfile", 1, "testuser", 1, nowTime, nowTime, nowTime, true, true, None, Some(39))
        )
      )
      val storageDriversMap = Map[Int, StorageDriver](
        1 -> mock[StorageDriver],
        2 -> mock[StorageDriver]
      )
      val storageMap = Map[Int, StorageEntry](
        1 -> StorageEntry(Some(1),Some("source"),Some("/data"),None, "local", None, None, None, None, None, false, Some(StorageStatus.ONLINE), Some(1)),
        2 -> StorageEntry(Some(2), Some("dest"), None, None, "remote", None, None, None, None, None, true, Some(StorageStatus.ONLINE), None)
      )
      val result = Try { Await.result(toTest.conditionalBackup(backupRecord, storageDriversMap, storageMap), 10.seconds) }

      result must beAFailedTry
      result.failed.get.getMessage mustEqual "Cannot back up Test project (Some(1234)) because storage 1 is configured to back up to itself. This is not supported and can lead to data loss, please fix."
      there was no(mockPerformBackup).apply(any,any,any)
      there was no(mockMakeProjectLink).apply(any,any)
    }
  }
}
