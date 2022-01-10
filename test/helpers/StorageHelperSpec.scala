package helpers

import java.io._
import java.sql.Timestamp
import java.time.{Instant, LocalDateTime}
import akka.stream.Materializer
import drivers.{MatrixStoreDriver, PathMetadata, PathStorage, StorageDriver}
import models.{FileEntry, StorageEntry}
import org.apache.commons.io.input.NullInputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process._
import scala.util.{Failure, Success, Try}
import play.api.test.WithApplication
import org.apache.commons.io.FilenameUtils
import org.slf4j.LoggerFactory

import scala.language.reflectiveCalls //needed for testDoByteCopy


class StorageHelperSpec extends Specification with Mockito with utils.BuildMyApp {
  private val logger = LoggerFactory.getLogger(getClass)

  "StorageHelper.copyStream" should {
    "reliably copy one stream to another, returning the number of bytes copied" in {
      val testFileNameSrc = "/tmp/storageHelperSpecTest-src-1" // shouldn't have spaces!
      val testFileNameDest = "/tmp/storageHelperSpecTest-dst-1"

      /* create a test file */
      Seq("/bin/dd","if=/dev/urandom",s"of=$testFileNameSrc","bs=1k","count=600").!

      val checksumSource = Seq("shasum","-a","1",testFileNameSrc) #| "cut -c 1-40" !!

      val sourceFile = new File(testFileNameSrc)
      val sourceStream = new FileInputStream(sourceFile)

      val destFile = new File(testFileNameDest)
      if(destFile.exists()) destFile.delete()

      val destStream = new FileOutputStream(destFile)

      val result =  StorageHelper.copyStream(sourceStream,destStream)

      result mustEqual sourceFile.length
      val checksumDest = s"shasum -a 1 $testFileNameDest" #| "cut -c 1-40" !!

      checksumSource mustEqual checksumDest

      sourceStream.close()
      destStream.close()
      sourceFile.delete()
      destFile.delete()
    }
  }

  "StorageHelper.copyFile" should {
    "look up two file entries, get streams from their device drivers and initiate copy" in new WithApplication(buildApp) {
      private implicit val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db

      val testFileNameSrc = "/tmp/storageHelperSpecTest-src-2" // shouldn't have spaces!
      val testFileNameDest = "/tmp/storageHelperSpecTest-dst-2" // shouldn't have spaces!
      try {
        // create a test file
        logger.debug( Seq("/bin/dd", "if=/dev/urandom", s"of=$testFileNameSrc", "bs=1k", "count=600").toString())
        Seq("/bin/dd", "if=/dev/urandom", s"of=$testFileNameSrc", "bs=1k", "count=600").!
        Seq("/bin/ls", "-lh", testFileNameSrc).!

        val ts = Timestamp.valueOf(LocalDateTime.now())

        val testSourceEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameSrc), 1, "testuser", 1, ts, ts, ts, hasContent = true, hasLink = false, None)
        val testDestEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameDest), 1, "testuser", 1, ts, ts, ts, hasContent = false, hasLink = false, None)

        val realStorageHelper = new StorageHelper

        val savedResults = Await.result(
          Future.sequence(Seq(testSourceEntry.save, testDestEntry.save)).map(results => Try(results.map(_.get)))
          , 10.seconds)

        savedResults must beSuccessfulTry

        val savedSource = savedResults.get.head
        val savedDest = savedResults.get(1)
        val result = Await.result(realStorageHelper.copyFile(savedSource, savedDest), 10.seconds)
        result mustEqual(savedDest.copy(hasContent = true))
      } finally { // ensure that test files get deleted. if you don't use try/finally, then if either of these fails the whole test does
        new File(testFileNameSrc).delete()
        new File(testFileNameDest).delete()
      }
    }

    "fail if the destination file is not the same size as the source" in new WithApplication(buildApp){
      private implicit val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db

      val mockedStorageDriver = mock[PathStorage]
      mockedStorageDriver.getReadStream(any[String],any)
      mockedStorageDriver.getReadStream(any[String],any) answers((_,_)=>Success(new NullInputStream(60*1024L)))
      mockedStorageDriver.getMetadata(any[String],any) answers((_,_)=>Some(PathMetadata(1234L, Instant.now().toEpochMilli)))

      val mockedStorage = mock[StorageEntry]
      mockedStorage.getStorageDriver answers((_,_)=>{println("in mockedStorage"); Some(mockedStorageDriver)})
      mockedStorage.rootpath answers((_,_)=>Some("/tmp"))

      val testFileNameSrc = "/tmp/storageHelperSpecTest-src-4" // shouldn't have spaces!
      val testFileNameDest = "/tmp/storageHelperSpecTest-dst-4" // shouldn't have spaces!
      try {
        // create a test file
        Seq("/bin/dd", "if=/dev/urandom", s"of=$testFileNameSrc", "bs=1k", "count=600").!
        val ts = Timestamp.valueOf(LocalDateTime.now())

        val testSourceEntry = new FileEntry(Some(1234), FilenameUtils.getBaseName(testFileNameSrc), 1, "testuser", 1, ts, ts, ts, hasContent = true, hasLink = false, None){
          override def storage(implicit db: JdbcBackend#DatabaseDef):Future[Option[StorageEntry]] = {
            println("testSourceEntry.storage")
            Future(Some(mockedStorage))
          }
        }

        val testDestEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameDest), 1, "testuser", 1, ts, ts, ts, hasContent = false, hasLink = false,None)

        val realStorageHelper = new StorageHelper

        val savedResults = Await.result(
          Future.sequence(Seq(testDestEntry.save)).map(results => Try(results.map(_.get)))
          , 10.seconds)

        savedResults must beSuccessfulTry

        val savedSource = testSourceEntry
        val savedDest = savedResults.get.head
        println(savedSource)
        println(savedDest)
        val result = Try { Await.result(realStorageHelper.copyFile(savedSource, savedDest), 10.seconds) }

        result must beFailedTry
      } finally { // ensure that test files get deleted. if you don't use try/finally, then if either of these fails the whole test does
        new File(testFileNameSrc).delete()
        new File(testFileNameDest).delete()
      }
    }

    "return an error if source does not have a valid storage driver" in new WithApplication(buildApp){
      private implicit val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db

      // create a test file
      val testFileNameSrc = "/tmp/storageHelperSpecTest-src-3" // shouldn't have spaces!
      val testFileNameDest = "/tmp/storageHelperSpecTest-dst-3" // shouldn't have spaces!
      try {
        Seq("/bin/dd", "if=/dev/urandom", s"of=$testFileNameSrc", "bs=1k", "count=600").!
        val ts = Timestamp.valueOf(LocalDateTime.now())

        val testSourceEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameSrc), 2, "testuser", 1, ts, ts, ts, hasContent = true, hasLink = false, None)
        val testDestEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameSrc), 1, "testuser", 1, ts, ts, ts, hasContent = false, hasLink = false, None)

        val realStorageHelper = new StorageHelper

        val savedResults = Await.result(
          Future.sequence(Seq(testSourceEntry.save, testDestEntry.save)).map(results => Try(results.map(_.get)))
          , 10.seconds)

        savedResults must beSuccessfulTry

        val savedSource = savedResults.get.head
        val savedDest = savedResults.get(1)
        val result = Try { Await.result(realStorageHelper.copyFile(savedSource, savedDest), 10.seconds) }
        result must beFailedTry //Left(List("Either source or destination was missing a storage or a storage driver"))
        result.failed.get.getMessage mustEqual "Storage with ID 2 does not have a valid storage type"
      } finally {
        new File(testFileNameSrc).delete()
        new File(testFileNameDest).delete()
      }
    }

    "return an error if dest does not have a valid storage driver" in new WithApplication(buildApp){
      private implicit val injector = app.injector
      protected val dbConfigProvider = injector.instanceOf(classOf[DatabaseConfigProvider])
      protected implicit val db = dbConfigProvider.get[JdbcProfile].db
      // create a test file
      val testFileNameSrc = "/tmp/storageHelperSpecTest-src-5" // shouldn't have spaces!
      val testFileNameDest = "/tmp/storageHelperSpecTest-dst-5" // shouldn't have spaces!
      try {
        Seq("/bin/dd", "if=/dev/urandom", s"of=$testFileNameSrc", "bs=1k", "count=600").!
        val ts = Timestamp.valueOf(LocalDateTime.now())

        val testSourceEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameSrc), 1, "testuser", 1, ts, ts, ts, hasContent = true, hasLink = false, None)
        val testDestEntry = FileEntry(None, FilenameUtils.getBaseName(testFileNameDest), 2, "testuser", 1, ts, ts, ts, hasContent = false, hasLink = false, None)

        val realStorageHelper = new StorageHelper

        val savedResults = Await.result(
          Future.sequence(Seq(testSourceEntry.save, testDestEntry.save)).map(results => Try(results.map(_.get)))
          , 10.seconds)

        savedResults must beSuccessfulTry

        val savedSource = savedResults.get.head
        val savedDest = savedResults.get(1)
        val result = Try { Await.result(realStorageHelper.copyFile(savedSource, savedDest), 10.seconds) }
        result must beFailedTry //List("Either source or destination was missing a storage or a storage driver"))
      } finally {
        new File(testFileNameSrc).delete()
        new File(testFileNameDest).delete()
      }
    }

  }
}
