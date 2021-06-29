package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.StorageDriver
import helpers.StorageHelper
import models.{FileEntry, StorageEntry, StorageEntryHelper, StorageEntryRow}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import services.ProjectBackup.BackupResults
import slick.jdbc.PostgresProfile
import slick.lifted
import slick.lifted.TableQuery

import java.nio.file.{Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


@Singleton
class ProjectBackup @Inject()(config:Configuration, dbConfigProvider: DatabaseConfigProvider, storageHelper:StorageHelper)(implicit mat:Materializer) {
  private val logger = LoggerFactory.getLogger(getClass)
  private lazy implicit val db = dbConfigProvider.get[PostgresProfile].db

  /**
    * scala-safe version. Relativizes the given originalPath against the rootPath
    * @param originalPath java.nio.Path of the path to relativize
    * @param rootPath java.nio.Path to relativize against
    * @return a Try with the results of the operation.
    */
  protected def relativeFilePath(originalPath:Path, rootPath:Path) = Try {
    rootPath.relativize(originalPath)
  }

  /**
    * returns a FileEntry indicating a target file to write.
    * this is guaranteed to be on the destination storage given
    * if the destination storage supports versioning, then it is guaranteed not to exist yet (previous dest entry with the version field incremented).
    * If the destination storage does NOT support versioning, then it will be identical to the "previous" dest entry provided
    * if there is no "previous" destination that a new entry will be created from the source entry's metadata
    * if the Source entry does not exist then that's an error
    * @param maybeSourceFileEntry option containing the source file entry
    * @param maybePrevDestEntry optional destination of the previous iteration
    * @param destStorage destination storage
    * @return a Future containing a FileEntry to write to.  This should be saved to the database before proceeding to write.
    */
  def ascertainTarget(maybeSourceFileEntry:Option[FileEntry], maybePrevDestEntry:Option[FileEntry], destStorage:StorageEntry):Future[FileEntry] = Future {
    (maybeSourceFileEntry, maybePrevDestEntry) match {
      case (Some(_), Some(prevDestEntry))=>
        if(destStorage.supportsVersions) {
          logger.info(s"Destination storage ${destStorage.id} ${destStorage.rootpath} supports versioning, nothing will be over-written. Target version number is ${prevDestEntry.version+1}")
          prevDestEntry.copy(id=None, version = prevDestEntry.version+1, hasContent = false, hasLink = true)
        } else {
          logger.warn(s"Backup destination storage ${destStorage.id} ${destStorage.rootpath} does not support versioning, so last backup will get over-written")
          prevDestEntry
        }
      case (Some(sourceEntry), None)=>
        sourceEntry.copy(id=None, storageId=destStorage.id.get,version=1, hasContent=false, hasLink=true,backupOf = sourceEntry.id)
      case (None, _)=>
        throw new RuntimeException("Can't back up as source file was not found")  //fail the Future
    }
  }

  private def extractModTime(sourceMeta:Map[Symbol, String], sourceEntry:FileEntry) = Try { sourceMeta.get(Symbol("lastModified")).map(_.toLong) } match {
    case Success(Some(result))=>
      logger.debug(s"${sourceEntry.filepath} on ${sourceEntry.storageId} mtime is $result")
      Some(result)
    case Success(None)=>
      logger.debug(s"${sourceEntry.filepath} on ${sourceEntry.storageId} mtime is None")
      None
    case Failure(err)=>
      logger.error(s"Could not get mtime for ${sourceEntry.filepath} on ${sourceEntry.storageId}: ${err.getMessage}")
      None
  }

  /**
    * checks whether the given FileEntries actually need a backup.
    *
    * if the source does not exist, that's an error
    *
    * if the destination does not exist, then always true
    *
    * if both exist, then the actual metadata from the storage driver is queried.  if either the size or the last-modified times
    * do not match then true; otherwise false
    *
    * the return value is wrapped in a Future for ease of composition
    * @param maybeSourceEntry optional FileEntry representing the source media
    * @param maybePrevDestEntry optional FileEntry representing the (last iteration) destination media
    * @param sourceStorageDriver driver instance for the source storag
    * @param destStorageDriver driver instance for the destination storage
    * @return a boolean indicating whether copying is needed, wrapped in a Future
    */
  def checkNeedsBackup(maybeSourceEntry:Option[FileEntry], maybePrevDestEntry:Option[FileEntry], sourceStorageDriver:StorageDriver, destStorageDriver:StorageDriver) = {
    (maybeSourceEntry, maybePrevDestEntry) match {
      case (None, _)=>  //if there is no source we can't continue
        Future.failed(new RuntimeException("There was no source file to back up"))
      case (_, None)=>  //if there is no destination, then we definitely need backup
        Future(true)
      case (Some(sourceEntry), Some(destEntry))=>
        val sourceMeta = sourceStorageDriver.getMetadata(sourceEntry.filepath, sourceEntry.version)
        val destMeta = destStorageDriver.getMetadata(destEntry.filepath, destEntry.version)
        val sourceSizeStr = sourceMeta.get(Symbol("size"))
        val sourceMod = extractModTime(sourceMeta, sourceEntry)
        val destSizeStr = destMeta.get(Symbol("size"))
        val destMod = extractModTime(destMeta, destEntry)

        logger.debug(s"${sourceEntry.filepath} version ${sourceEntry.version} on ${sourceEntry.storageId} has size $sourceSizeStr and last modified $sourceMod")
        logger.debug(s"${destEntry.filepath} version ${destEntry.version} on ${destEntry.storageId} has size $destSizeStr and last modified $destMod")

        if(sourceSizeStr.isEmpty || sourceMod.isEmpty) {
          Future.failed(new RuntimeException(s"Could not get one or both of file size and mod time from source storage for ${sourceEntry.filepath} on storage id ${sourceEntry.storageId}"))
        } else if(destSizeStr.isEmpty || destMod.isEmpty) {
          logger.warn(s"Got destination size ${destSizeStr} and destination modtime ${destMod} which is not correct. Forcing backup.")
          Future(true)
        } else if(sourceSizeStr!=destSizeStr) { //file size mismatch - always backup
          Future(true)
        } else if(sourceMod.get > destMod.get) { //file sizes do match, but if the source has been modified since the backup copy it anyway
          Future(true)
        } else {                                //if we get here, then the sizes match and the source modtime is equal or earlier than the backup modtime
          Future(false)
        }
    }
  }

  /**
    * tries to perform a backup of the given file path from the given source storage.
    * @param sourceEntry FileEntry that might need to be backed up
    * @param sourceStorage StorageEntry that holds the source
    * @param destStorage StorageEntry that will be copied to
    * @return a Future, which contains either:
    *         - a string-sequence of copy errors on failure;
    *         - None if the file did not need to be copied;
    *         - a FileEntry of the newly copied backup file if it did need to be copied.
    */
  def performBackup(sourceEntry:FileEntry, sourceStorage:StorageEntry, destStorage:StorageEntry):Future[Either[String, Option[FileEntry]]] = {
    (sourceStorage.getStorageDriver, destStorage.getStorageDriver) match {
      case (Some(sourceStorageDriver), Some(destStorageDriver)) =>
        val checkFuture = for {
          maybePrevDestEntry <- sourceEntry.backups(destStorage.id, 0, 1).map(_.headOption)
          shouldCopy <- checkNeedsBackup(Some(sourceEntry), maybePrevDestEntry, sourceStorageDriver, destStorageDriver)
        } yield (maybePrevDestEntry, shouldCopy)

        checkFuture.flatMap({
          case (maybePrevDestEntry, shouldCopy)=>
            if(shouldCopy) {
              logger.info(s"Starting backup of ${sourceEntry.filepath} from storage ID ${sourceStorage.id} to ${destStorage.id}")
              val targetFileEntry = for {
                targetDestEntry <- ascertainTarget(Some(sourceEntry), maybePrevDestEntry, destStorage)
                updatedEntryTry <- targetDestEntry.save
                updatedDestEntry <- Future.fromTry(updatedEntryTry) //make sure that we get the updated database id of the file
              } yield updatedDestEntry

              targetFileEntry.flatMap(updatedDestEntry=>{
                storageHelper.copyFile(sourceEntry, updatedDestEntry)
                  .map(fileEntry=>Right(Some(fileEntry)))
                  .recoverWith({
                    case err:Throwable=>
                      logger.error(s"Could not copy ${updatedDestEntry.filepath} on ${updatedDestEntry.storageId} from ${sourceEntry.filepath} on ${sourceEntry.storageId}: ${err.getMessage}",err)
                      updatedDestEntry
                        .deleteFromDisk
                        .andThen(_=>updatedDestEntry.deleteSelf)
                        .map(_=>Left(err.toString))
                  })
              })
            } else {
              logger.info(s"Backup of ${sourceEntry.filepath} not needed because it has not changed.")
              Future(Right(None))
            }
        })
      case _ =>
        Future.failed(new RuntimeException(s"Missing storage driver for either source id ${sourceStorage.id} or dest id ${destStorage.id}"))
    }
  }

  def getSourceAndDestStorages(sourceStorageId:Int) = {
    StorageEntryHelper.entryFor(sourceStorageId).flatMap({
      case Some(sourceStorage)=>
        sourceStorage.backsUpTo match {
          case Some(destStorageId)=>
            StorageEntryHelper.entryFor(destStorageId).map(destStorage=>(sourceStorage, destStorage))
          case None=>
            Future.failed(new RuntimeException(s"The source storage $sourceStorageId has no backup configured"))
        }
      case None=>
        Future.failed(new RuntimeException(s"There is no source storage with the ID of $sourceStorageId."))
    })
  }

  /**
    * scans the database for every known file on the given storage ID and tries to back it up to the configured backup storage.
    * returns a Future that completes when the backup is done, or fails if there is a problem.
    * @param forStorageId storage ID to back up
    * @return a Future, which contains a backup report showing the overall status of the backup
    */
  def fullStorageBackup(forStorageId:Int) = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)
    logger.debug(s"Starting full backup for storage ID $forStorageId with $parallelCopies parallel copies")

    getSourceAndDestStorages(forStorageId).flatMap({
      case (storageEntry, Some(destStorageEntry))=>
        logger.debug(s"Source storage is ${storageEntry.storageType} with ID ${storageEntry.id}, dest storage is ${storageEntry.storageType} with ID ${storageEntry.id}")
        FileEntry.scanAllFiles(Some(forStorageId))
          .mapAsync(parallelCopies)(entry=>performBackup(entry, storageEntry, destStorageEntry))
          .toMat(Sink.fold(BackupResults.empty(forStorageId))((acc, elem)=>elem match {
            case Left(errs)=>
              logger.warn(s"Backup failed: ${errs}")
              acc.copy(totalCount = acc.totalCount+1, failedCount=acc.failedCount+1)
            case Right(Some(_))=>
              acc.copy(totalCount = acc.totalCount+1, successCount = acc.successCount+1)
            case Right(None)=>
              acc.copy(totalCount = acc.totalCount+1, notNeededCount=acc.notNeededCount+1)
          }))(Keep.right)
          .run()
      case (storageEntry: StorageEntry, None)=>
        Future.failed(new RuntimeException(s"There is no destination storage with the ID of ${storageEntry.backsUpTo}"))
    })
  }

  def backupProjects = {
    import slick.jdbc.PostgresProfile.api._

    def storagesToBackup = db.run {
      lifted.TableQuery[StorageEntryRow]
        .filter(_.backsUpTo.isDefined)
        .map(_.id)
        .result
    }

    val fullBackupFuture = for {
      storageSeq <- storagesToBackup
      results <- Future.sequence(storageSeq.map(fullStorageBackup))
    } yield results

    fullBackupFuture.map(results=>{
      if(results.isEmpty) {
        logger.error("There were no storages configured for backup.  You should set a backup storage on your primary project storage in the Admin")
      }
      results
    })
  }
}

object ProjectBackup {
  case class BackupResults(storageId:Int, totalCount:Long, failedCount:Long, successCount:Long, notNeededCount:Long)
  object BackupResults {
    def empty(storageId:Int) = new BackupResults(storageId, 0,0,0,0)
  }
}