package helpers

import java.io.{EOFException, InputStream, OutputStream}
import akka.stream.Materializer
import drivers.StorageDriver
import helpers.StorageHelper.{defaultBufferSize, getClass}

import javax.inject.Inject
import models.{FileEntry, StorageEntry}
import play.api.Logger
import org.slf4j.{LoggerFactory, MDC}
import play.api.inject.Injector

import java.nio.ByteBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

object StorageHelper {
  private val logger = LoggerFactory.getLogger(getClass)

  val defaultBufferSize:Int = 10*1024*1024  //10Mbyte copy buffer

  /**
    * utility function to directly copy from one stream to another
    * @param input InputStream to read from
    * @param output OutputStream to write to
    * @param bufferSize size of the temporary buffer to use.
    * @return the number of bytes written as a Long. Closes the streams when it is done. Raises exceptions on failure (assumed it's within a try/catch block)
    */
  def copyStream(input:InputStream, output:OutputStream, bufferSize:Int=defaultBufferSize) = {
    val buf=ByteBuffer.allocate(bufferSize)
    var bytesRead: Int = 0
    var totalRead: Long = 0

    try {
      do {
        bytesRead = input.read(buf.array())
        if(bytesRead == -1) throw new EOFException
        totalRead += bytesRead
        buf.flip()
        output.write(buf.array(),0,bytesRead)
        buf.clear()
      } while (bytesRead > 0)
      output.close()
      input.close()
      totalRead
    } catch {
      case _:EOFException=>
        logger.debug(s"Stream copy reached EOF")
        totalRead
    }
  }
}

class StorageHelper @Inject() (implicit mat:Materializer, injector:Injector) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * proxy for the static copyStream method so that it can be mocked in testing
    */
  protected def callCopyStream(input:InputStream, output:OutputStream, bufferSize:Int=defaultBufferSize) = StorageHelper.copyStream(input, output, bufferSize)

  def deleteFile(targetFile: FileEntry)(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Either[String, FileEntry]] = {
    val futures = Future.sequence(Seq(targetFile.storage, targetFile.getFullPath))

    futures.map(results=>{
      val storageResult = results.head.asInstanceOf[Option[StorageEntry]]
      MDC.put("storageResult", storageResult.toString)
      val fullPath = results(1).asInstanceOf[String]
      MDC.put("fullPath", fullPath)
      storageResult match {
        case Some(storageEntry) =>
          storageEntry.getStorageDriver match {
            case Some(storageDriver) =>
              MDC.put("storageDriver", storageDriver.toString)
              storageDriver.deleteFileAtPath(fullPath, targetFile.version) match {
                case true=>
                  val updatedFileEntry = targetFile.copy(hasContent = false)
                  updatedFileEntry.save
                  Right(updatedFileEntry)
                case false=>
                  Left("storage driver failed to delete file")
              }

            case None =>
              logger.error(s"Can't delete file at $fullPath because storage $storageEntry has no storage driver")
              Left("No storage driver configured, enable debugging for helpers.StorageHelper for more info")
          }
        case None =>
          logger.error(s"Can't delete file at $fullPath because file record has no storage")
          Left("No storage reference on record, enable debugging for helpers.StorageHelper for more info")
      }
    })
  }

  /**
    * Copies from the file represented by sourceFile to the (non-existing) file represented by destFile.
    * Both should have been saved to the database before calling this method.  The files do not need to be on the same
    * storage type
    * @param sourceFile - [[models.FileEntry]] instance representing file to copy from
    * @param destFile - [[models.FileEntry]] instance representing file to copy to
    * @param db - database instance, usually passed implicitly.
    * @return Future[FileEntry] - a future containing e new, updated [[models.FileEntry]] representing @destFile.
    *         this future fails if there was an error
    */
  def copyFile(sourceFile: FileEntry, destFile:FileEntry)
              (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[FileEntry] = {
    def getStorageDriverForFile(file:FileEntry) = {
      file
        .storage
        .map(_.flatMap(_.getStorageDriver))
        .map({
          case Some(storageDriver)=>storageDriver
          case None=> throw new RuntimeException(s"Storage with ID ${file.storageId} does not have a valid storage type")
        })
    }

    def withReadStream[A](sourceFile:FileEntry)(cb:(Map[Symbol, String], InputStream)=>Try[A]) = {
      val readStreamFut = for {
        driver <- getStorageDriverForFile(sourceFile)
        fullPath <- sourceFile.getFullPath
        readStream <- Future.fromTry(driver.getReadStream(fullPath, sourceFile.version))
        meta <- Future.fromTry(Try { driver.getMetadata(fullPath, sourceFile.version)})
      } yield (driver, readStream, meta)

      readStreamFut.map({
        case (driver, readStream, meta)=>
          val result = cb(meta, readStream)
          Try { readStream.close() } match {
            case Success(_)=>
            case Failure(err)=>
              logger.error(s"Could not close file $sourceFile via driver $driver: ${err.getMessage}", err)
          }
          Future.fromTry(result)
      }).flatten
    }

    val destination = for {
      destFilePath <- destFile.getFullPath
      destStorageDriver <- getStorageDriverForFile(destFile)
    } yield (destFilePath, destStorageDriver)

    destination.flatMap({
      case (destFilePath, destDriver)=>
        withReadStream(sourceFile) { (sourceMeta,readStream)=>
          destDriver
            .writeDataToPath(destFilePath, destFile.version, readStream)
            .flatMap(_=> {
              //now that the copy completed successfully, we need to check that the file sizes actually match
              val destMeta = destDriver.getMetadata(destFilePath, destFile.version)
              (destMeta.get(Symbol("size")), sourceMeta.get(Symbol("size"))) match {
                case (None, _) =>
                  Failure(new RuntimeException(s"${sourceFile.filepath}: Could not get destination file size"))
                case (_, None)=>
                  Failure(new RuntimeException(s"${sourceFile.filepath}: Could not get source file size"))
                case (Some(destSizeString), Some(sourceSizeString))=>
                  logger.debug(s"${sourceFile.filepath}: Destination size is $destSizeString and source size is $sourceSizeString")
                  if(destSizeString==sourceSizeString) {
                    Success( () )
                  } else {
                    Failure(new RuntimeException(s"${sourceFile.filepath}: Copied file size $destSizeString did not match source size of $sourceSizeString"))
                  }
              }
            })
        }.map(_=>destFile.copy(hasContent=true))
    })
  }
  /*
  /**
    * Copies from the file represented by sourceFile to the (non-existing) file represented by destFile.
    * Both should have been saved to the database before calling this method.  The files do not need to be on the same
    * storage type
    * @param sourceFile - [[models.FileEntry]] instance representing file to copy from
    * @param destFile - [[models.FileEntry]] instance representing file to copy to
    * @param db - database instance, usually passed implicitly.
    * @return [[Future[Either[Seq[String],FileEntry]] - a future containing either a list of strings giving failure reasons or a
    *        new, updated [[models.FileEntry]] representing @destFile
    */
  def copyFile(sourceFile: FileEntry, destFile: FileEntry)
              (implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Either[Seq[String],FileEntry]] = {
    val storageDriversFuture = Future.sequence(Seq(sourceFile.storage,destFile.storage)).map(results=>{
      val successfulResults = results.flatten.flatMap(_.getStorageDriver)

      if(successfulResults.length==2){
        Right(successfulResults)
      } else {
        MDC.put("sourceFile", sourceFile.toString)
        MDC.put("destFile", destFile.toString)
        logger.debug(s"sourceFile: ${sourceFile.toString}")
        logger.debug(s"destFile: ${destFile.toString}")
        Left(Seq("Either source or destination was missing a storage or a storage driver"))
      }
    })

    val actualFilenamesFuture = Future.sequence(Seq(sourceFile.getFullPath,destFile.getFullPath))

    val bytesCopiedFuture = Future.sequence(Seq(storageDriversFuture, actualFilenamesFuture)).map(futures=>{
      val storageDrivers = futures.head.asInstanceOf[Either[Seq[String],Seq[StorageDriver]]]

      storageDrivers match {
        case Left(errors)=>Left(errors)
        case Right(actualStorageDrivers)=>
          val sourceStorageDriver = actualStorageDrivers.head
          val destStorageDriver = actualStorageDrivers(1)

          val sourceFullPath = futures(1).asInstanceOf[Seq[String]].head
          val destFullPath = futures(1).asInstanceOf[Seq[String]](1)

          logger.info(s"Copying from $sourceFullPath on $sourceStorageDriver to $destFullPath on $destStorageDriver")

          val sourceStreamTry = sourceStorageDriver.getReadStream(sourceFullPath, sourceFile.version)
          val destStreamTry = destStorageDriver.getWriteStream(destFullPath, destFile.version)

          doByteCopy(sourceStorageDriver,sourceStreamTry,destStreamTry,sourceFullPath, sourceFile.version, destFullPath)
      }
    })

    val checkFuture = bytesCopiedFuture.map({
      case Left(errors)=>Left(errors)
      case Right((bytesCopied,metaDict))=>
        logger.debug(s"Copied $bytesCopied bytes")
        //need to check if the number of bytes copied is the same as the source file. If so return Right() otherwise Left()
        val fileSize = metaDict(Symbol("size")).toLong
        logger.debug(s"Destination size is $fileSize")
        if(bytesCopied!=fileSize){
          Left(Seq(s"Copied file byte size $bytesCopied did not match source file $fileSize"))
        } else {
          Right( () )
        }
    })

    checkFuture.flatMap({
      case Left(errors)=>Future(Left(errors))
      case Right(nothing)=>
        destFile.updateFileHasContent.map({
          case Success(rowsUpdated)=>
            Right(destFile.copy(hasContent = true))
          case Failure(error)=>
            Left(Seq(error.toString))
        })
    })
  }
*/

  def findFile(targetFile: FileEntry)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    val futures = Future.sequence(Seq(targetFile.storage, targetFile.getFullPath))

    futures.map(futureResults=>{
      val maybeStorage = futureResults.head.asInstanceOf[Option[StorageEntry]]
      val fullPath = futureResults(1).asInstanceOf[String]

      val maybeStorageDriver = maybeStorage.flatMap(_.getStorageDriver)

      maybeStorageDriver match {
        case Some(storageDriver)=>
          storageDriver.pathExists(targetFile.filepath, targetFile.version)
        case None=>
          throw new RuntimeException(s"No storage driver defined for ${maybeStorage.map(_.repr).getOrElse("unknown storage")}")
      }
    })
  }

  def onStorageMetadata(targetFile: FileEntry)(implicit db:slick.jdbc.PostgresProfile#Backend#Database) = {
    targetFile.storage.map(maybeStorage=>{
      val maybeStorageDriver = maybeStorage.flatMap(_.getStorageDriver)

      maybeStorageDriver match {
        case Some(storageDriver)=>
          storageDriver.getMetadata(targetFile.filepath, targetFile.version)
        case None=>
          throw new RuntimeException(s"No storage driver defined for ${maybeStorage.map(_.repr).getOrElse("unknown storage")}")
      }
    })
  }
}
