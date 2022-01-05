package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.StorageDriver
import helpers.StorageHelper
import models.{EntryStatus, FileAssociation, FileAssociationRow, FileEntry, ProjectEntry, StorageEntry, StorageEntryHelper, StorageEntryRow}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import services.ProjectBackup.BackupResults
import slick.jdbc.PostgresProfile

import java.nio.file.{Path, Paths}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


@Singleton
class ProjectBackup @Inject()(config:Configuration, dbConfigProvider: DatabaseConfigProvider, storageHelper:StorageHelper)(implicit mat:Materializer, injector:Injector) {
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
    * Checks to find the next available (not existing) version number on the storage
    * @param destStorage destination storage that FileEntry will be written to. The storage driver associated with this
    *                    storage is then used for lookup.
    * @param intendedTarget FileEntry with `version` set to the initial estimate of what the version should be
    */
  protected def findAvailableVersion(destStorage:StorageEntry, intendedTarget:FileEntry) = {
    destStorage.getStorageDriver match {
      case Some(driver)=>
        implicit val drv:StorageDriver = driver
        def findAvailable(target:FileEntry):Future[FileEntry] = {
          target.validatePathExistsDirect.flatMap({
            case true=>
              logger.debug(s"${target.filepath} ${target.version} exists on ${destStorage}, trying next version")
              findAvailable(target.copy(version = target.version+1))
            case false=>
              logger.debug(s"${target.filepath} ${target.version} does not exist on $destStorage")
              Future(target)
          })
        }
        findAvailable(intendedTarget)
      case None=>Future.failed(new RuntimeException(s"No storage driver available for storage ${destStorage.id}"))
    }
  }

  /**
    * returns a FileEntry indicating a target file to write.
    * This is guaranteed to be on the destination storage given.
    *   - if the destination storage supports versioning, then it is guaranteed not to exist yet (previous dest entry with the version field incremented).
    *   - If the destination storage does NOT support versioning, then it will be identical to the "previous" dest entry provided
    *   - if there is no "previous" destination that a new entry will be created from the source entry's metadata
    *   - if the Source entry does not exist then that's an error
    *
    * @param maybeSourceFileEntry option containing the source file entry
    * @param maybePrevDestEntry optional destination of the previous iteration
    * @param destStorage destination storage
    * @return a Future containing a FileEntry to write to.  This should be saved to the database before proceeding to write.
    */
  def ascertainTarget(maybeSourceFileEntry:Option[FileEntry], maybePrevDestEntry:Option[FileEntry], destStorage:StorageEntry):Future[FileEntry] = {
    (maybeSourceFileEntry, maybePrevDestEntry) match {
      case (Some(sourceEntry), Some(prevDestEntry))=>
        logger.debug(s"${sourceEntry.filepath}: prevDestEntry is $prevDestEntry")
        if(destStorage.supportsVersions) {
          val intendedTarget = prevDestEntry.copy(id=None, version = prevDestEntry.version+1, hasContent = false, hasLink = true)
          findAvailableVersion(destStorage, intendedTarget)
            .map(correctedTarget=>{
              logger.info(s"Destination storage ${destStorage.id} ${destStorage.rootpath} supports versioning, nothing will be over-written. Target version number is ${correctedTarget.version+1}")
              correctedTarget
            })
        } else {
          logger.warn(s"Backup destination storage ${destStorage.id} ${destStorage.rootpath} does not support versioning, so last backup will get over-written")
          Future(prevDestEntry)
        }
      case (Some(sourceEntry), None)=>
        logger.debug(s"${sourceEntry.filepath}: no prev dest entry")
        Future(
          sourceEntry.copy(id=None, storageId=destStorage.id.get,version=1, hasContent=false, hasLink=true,backupOf = sourceEntry.id)
        )
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
    import cats.implicits._
    maybeSourceEntry
      .map(_.validatePathExistsDirect(db, sourceStorageDriver))
      .sequence
      .flatMap(maybeExists=> {
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

            if(maybeExists.contains(false)) {
              logger.warn(s"Known file ${sourceEntry} does not exist on disk!")
              Future(false)
            } else if(sourceSizeStr.isEmpty || sourceMod.isEmpty) {
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
      })
  }

  /**
    * adds a row to the FileAssociationRow table to associate the new backup with the same project as
    * the source file.
    * the `destEntry` MUST have been saved, so it has an `id` attribute set. If not the Future will fail.
    * if the `sourceEntry` does not have an existing project association nothing will be done.
    * @param sourceEntry FileEntry instance that is being copied from
    * @param destEntry FileEntry instance that has just been copied to
    * @return a Future, with an Option contianing the number of changed rows if an action was taken.
    */
  def makeProjectLink(sourceEntry:FileEntry, destEntry:FileEntry) = {
    import slick.jdbc.PostgresProfile.api._
    import cats.implicits._

    def addRow(forProjectId:Int) = db.run {
      TableQuery[FileAssociationRow] += (forProjectId, destEntry.id.get)
    }

    sourceEntry.id match {
      case Some(sourceId) =>
        for {
          existingLink <- db.run(TableQuery[FileAssociationRow].filter(_.fileEntry === sourceId).result)
          result <- existingLink
            .headOption
            .map(existingAssociation=>addRow(existingAssociation._1))
            .sequence //convert Option[Future[A]] into Future[Option[A]] via cats
        } yield result
      case None=>
        logger.debug(s"File $sourceEntry is not linked to any project")
        Future(None)
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
    *         - a tuple of two FileEntrues of the newly copied backup file and the source, respectfully, if the file did need to be copied.
    */
  def performBackup(sourceEntry:FileEntry, sourceStorage:StorageEntry, destStorage:StorageEntry):Future[Either[String, Option[(FileEntry, FileEntry)]]] = {
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

              //logger.warn(s"Test, not backing up ${sourceEntry.filepath}")
              targetFileEntry.flatMap(updatedDestEntry=>{
                storageHelper.copyFile(sourceEntry, updatedDestEntry)
                  .flatMap(fileEntry=>{
                    //ensure that we save the record with `b_has_content` set to true
                    fileEntry.saveSimple.map(finalEntry=>Right(Some(finalEntry, sourceEntry)))
                  })
                  .recoverWith({
                    case err:Throwable=>
                      logger.error(s"Could not copy ${updatedDestEntry.filepath} on ${updatedDestEntry.storageId} from ${sourceEntry.filepath} on ${sourceEntry.storageId}: ${err.getMessage}",err)
                      updatedDestEntry
                        .deleteFromDisk
                        .andThen(_=>updatedDestEntry.deleteSelf)
                        .map(_=>Left(err.toString))
                  })
              })
              //Future(Right(None))
            } else {
              logger.debug(s"Backup of ${sourceEntry.filepath} not needed because it has not changed.")
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
        //FileEntry.scanAllFiles(Some(forStorageId))
        ProjectEntry.scanProjectsForStatus(EntryStatus.InProduction)
          .mapAsync(parallelCopies)(_.associatedFiles(allVersions=false).map(_.headOption))
          .mapAsync(parallelCopies)({
            case None=>
              logger.info("Project had no current version")
              Future(Right(None))
            case Some(entry)=>
              performBackup(entry, storageEntry, destStorageEntry)
          })
          .mapAsync(parallelCopies)({
            case err@Left(_)=>Future(err)
            case Right(None)=>Future(Right(None))
            case Right(Some((destEntry, sourceEntry)))=>
              makeProjectLink(sourceEntry, destEntry)
              .map(_=>Right(Some(destEntry)))
              .recover({
                case err:Throwable=>
                  Left(err.toString)
              })
          })
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

  def backupProjects:Future[Seq[BackupResults]] = {
    import slick.jdbc.PostgresProfile.api._

    if(sys.env.get("DISABLE_BACKUPS").contains("true")) {
      logger.warn("Project backups are currently disabled.  Remove the DISABLE_BACKUPS environment variable to enable again")
      return Future(Seq())
    }

    def storagesToBackup = db.run {
      TableQuery[StorageEntryRow]
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