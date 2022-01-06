package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.{StorageDriver, StorageMetadata}
import helpers.StorageHelper
import models.{EntryStatus, FileEntry, ProjectEntry, StorageEntry, StorageEntryHelper}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import services.ProjectBackup.BackupResults
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

@Singleton
class NewProjectBackup @Inject() (config:Configuration, dbConfigProvider: DatabaseConfigProvider, storageHelper:StorageHelper)(implicit mat:Materializer, injector:Injector){
  private val logger = LoggerFactory.getLogger(getClass)
  private implicit lazy val db = dbConfigProvider.get[PostgresProfile].db

  /**
    * initiates a StorageDriver for every storage in the system
    * @return
    */
  def loadStorageDrivers(): Future[Map[Int, StorageDriver]] = {
    StorageEntryHelper.allStorages.flatMap({
      case Failure(err)=>
        Future.failed(err)
      case Success(storages)=>
        Future(
          storages
            .map(e=>(e.id, e.getStorageDriver))
            .collect({case (Some(id), Some(drv))=>(id, drv)})
            .toMap
        )
    })
  }

  /**
    * Caches all of the storages in-memory from the database
    * @return a map relating the storage ID to the full record.
    */
  def loadAllStorages():Future[Map[Int, StorageEntry]] = {
    StorageEntryHelper.allStorages.flatMap({
      case Success(entries)=>
        Future(entries.map(s=>(s.id.get, s)).toMap)
      case Failure(err)=>
        Future.failed(err)
    })
  }

  val timeSuffixes = Seq("seconds","minutes","hours","days")
  def getTimeDifference(maybeSourceMeta:Option[StorageMetadata], destMeta:StorageMetadata) =
    maybeSourceMeta match {
      case Some(sourceMeta) =>
        val secondsDelta = Duration.between(sourceMeta.lastModified, destMeta.lastModified).getSeconds
        if (secondsDelta < 60) {
          s"$secondsDelta seconds"
        } else {
          val minsDelta = secondsDelta / 60.0
          if (minsDelta < 60) {
            s"$minsDelta minutes"
          } else {
            val hoursDelta = minsDelta / 60
            if (hoursDelta < 24) {
              s"$hoursDelta hours"
            } else {
              val daysDelta = hoursDelta / 24.0
              s"$daysDelta days"
            }
          }
        }
      case None=>
        "Can't get time difference, no source metadata present"
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
          val intendedTarget = prevDestEntry.copy(id=None,
            version = prevDestEntry.version+1,
            mtime=Timestamp.from(Instant.now()),
            atime=Timestamp.from(Instant.now()),
            hasContent = false,
            hasLink = true)
          findAvailableVersion(destStorage, intendedTarget)
            .map(correctedTarget=>{
              logger.info(s"Destination storage ${destStorage.id} ${destStorage.rootpath} supports versioning, nothing will be over-written. Target version number is ${correctedTarget.version+1}")
              correctedTarget
            })
        } else {
          logger.warn(s"Backup destination storage ${destStorage.id} ${destStorage.rootpath} does not support versioning, so last backup will get over-written")
          Future(prevDestEntry.copy(
            mtime=Timestamp.from(Instant.now()),
            atime=Timestamp.from(Instant.now())
          ))
        }
      case (Some(sourceEntry), None)=>
        logger.debug(s"${sourceEntry.filepath}: no prev dest entry")
        Future(
          sourceEntry.copy(id=None,
            storageId=destStorage.id.get,
            version=1,
            mtime=Timestamp.from(Instant.now()),
            atime=Timestamp.from(Instant.now()),
            hasContent=false,
            hasLink=true,
            backupOf = sourceEntry.id)
        )
      case (None, _)=>
        throw new RuntimeException("Can't back up as source file was not found")  //fail the Future
    }
  }

  def performBackup(sourceEntry:FileEntry, maybePrevDestEntry:Option[FileEntry], destStorage:StorageEntry) = {
    val targetFileEntry = for {
      targetDestEntry <- ascertainTarget(Some(sourceEntry), maybePrevDestEntry, destStorage)  //if our destStorage supports versioning, then we get a new entry here
      updatedEntryTry <- targetDestEntry.save
      updatedDestEntry <- Future.fromTry(updatedEntryTry) //make sure that we get the updated database id of the file
    } yield updatedDestEntry

    targetFileEntry.flatMap(updatedDestEntry=>{
      logger.warn(s"Backing up ${sourceEntry.filepath} on storage ${sourceEntry.storageId} to ${updatedDestEntry.filepath} v${updatedDestEntry.version} on storage ${updatedDestEntry.storageId}")
      storageHelper.copyFile(sourceEntry, updatedDestEntry)
        .flatMap(fileEntry=>{
          //ensure that we save the record with `b_has_content` set to true
          fileEntry.saveSimple.map(finalEntry=>(finalEntry, sourceEntry))
        })
        .recoverWith({
          case err:Throwable=>
            logger.error(s"Could not copy ${updatedDestEntry.filepath} on ${updatedDestEntry.storageId} from ${sourceEntry.filepath} on ${sourceEntry.storageId}: ${err.getMessage}",err)
            updatedDestEntry
              .deleteFromDisk
              .andThen(_=>updatedDestEntry.deleteSelf)
              .flatMap(_=>Future.failed(new RuntimeException(err.toString)))
        })
    })
  }

  def validateExistingBackups(sourceFile:FileEntry, potentialBackups:Seq[FileEntry], p:ProjectEntry, storageDrivers:Map[Int, StorageDriver]): Try[Either[String, Option[FileEntry]]] = {
    storageDrivers.get(sourceFile.storageId) match {
      case None=>
        logger.error(s"Project ${p.projectTitle} (${p.id}) Could not get a storage driver for storage ${sourceFile.storageId} on file ${sourceFile.filepath}")
        Failure(new RuntimeException(s"Could not get a storage driver for storage ${sourceFile.storageId} on file ${sourceFile.filepath}"))
      case Some(sourceDriver)=>
        val sourceMeta = sourceDriver.getMetadata(sourceFile.filepath, sourceFile.version)
        logger.info(s"Project ${p.projectTitle} (${p.id}) source file ${sourceFile.filepath} v${sourceFile.version} metadata: $sourceMeta")

        val backupFilesMetadata = potentialBackups.map(backup=>{
          storageDrivers.get(backup.storageId) match {
            case Some(destDriver)=>
              val bMeta = destDriver.getMetadata(backup.filepath, backup.version)
              logger.info(s"Project ${p.projectTitle} (${p.id}) backup file ${backup.filepath} v${backup.version} metadata: $bMeta")
              bMeta.map(m=>(backup.id.get, m) )
            case None=>
              logger.error(s"Project ${p.projectTitle} (${p.id}) Could not get a destination driver for storage ${backup.storageId} on file ${backup.filepath} v${backup.version}")
              None
          }
        }).collect({case Some(result)=>result}).toMap

        val mostRecent = potentialBackups.headOption
        val mostRecentMeta = mostRecent.flatMap(f=>backupFilesMetadata.get(f.id.get))

        logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup is version ${mostRecent.map(_.version)} with metadata $mostRecentMeta")
        mostRecentMeta match {
          case Some(meta)=>
            logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup lags source file by ${getTimeDifference(sourceMeta, meta)}")
            if(meta.size==sourceMeta.get.size) {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${mostRecent.map(_.version)} matches source, no backup required")
              Success(Left("No backup required"))
            } else {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${mostRecent.map(_.version)} size mismatch ${sourceMeta.get.size} vs ${meta.size}, backup needed")
              Success(Right(mostRecent))
            }
          case None=>
            logger.info(s"Project ${p.projectTitle} (${p.id})  most recent metadata was empty, could not check sizes. Assuming that the file is not present and needs backup")
            Success(Right(None)) //signal needs backup
        }
    }
  }

  /**
    * Checks whether we should back up the given file
    * @param projectAndFiles tuple consisiting of the ProjectEntry and a list of all its files
    * @param storageDrivers internal, immutable StorageDrivers cache
    * @return a Future containing:
    *         - a Left if there was a problem and the file could not be backed up
    *         - a Right with `true` if the file was backed up
    *         - a Right with `false` if the file did not need backing up
    *         The returned future fails on a permanent error, this should be picked up with .recover
    */
  def conditionalBackup(projectAndFiles:(ProjectEntry, Seq[FileEntry]), storageDrivers:Map[Int, StorageDriver], storages:Map[Int, StorageEntry]):Future[Either[String, Boolean]] = {
    val p = projectAndFiles._1
    val nonBackupFiles = projectAndFiles._2.filter(_.backupOf.isEmpty)
    if(nonBackupFiles.isEmpty) {
      logger.warn(s"Project ${p.projectTitle} (${p.id}) has no current file")
      Future(Left(s"Project ${p.projectTitle} (${p.id}) has no current file"))
    } else {
      if(nonBackupFiles.length>1) {
        logger.warn(s"Project ${p.projectTitle} (${p.id}) has multiple non-backup files:")
        nonBackupFiles.foreach(f=>logger.warn(s"\t${p.projectTitle} (${p.id}) ${f.filepath} on storage ${f.storageId}"))
      }
      val sourceFile = nonBackupFiles.head

      validateExistingBackups(sourceFile, projectAndFiles._2.filter(_.backupOf.isDefined).sortBy(_.version)(Ordering.Int.reverse), p, storageDrivers) match {
        case Failure(err)=>
          Future.failed(err)
        case Success(Left(msg))=>
          logger.debug(s"Project ${p.projectTitle} (${p.id}): $msg")
          Future(Right(false))
        case Success(Right(maybeMostRecentBackup))=>
          logger.info(s"I will back up project ${p.projectTitle} (${p.id})")
          val maybeDestStorage = for {
            sourceStorage <- storages.get(sourceFile.storageId)
            destStorageId <- sourceStorage.backsUpTo
            destStorage <- storages.get(destStorageId)
          } yield destStorage

          maybeDestStorage match {
            case None=>
              Future(
                Left(s"Cannot back up ${p.projectTitle} (${p.id}) because either the source file id ${sourceFile.storageId} is not valid or there is no backup storage configured for it")
              )
            case Some(destStorage)=>
              performBackup(sourceFile, maybeMostRecentBackup, destStorage)
              .map(results => {
                val copiedDest = results._1
                val copiedSource = results._2
                  logger.info(s"Copied ${copiedSource.filepath} v${copiedSource.version} on storage ${copiedSource.storageId} to ${copiedDest.filepath} v${copiedDest.version} on storage ${copiedDest.storageId}")
                  Right(true)
              })
              .recover({
                case err:Throwable=>
                  Left(s"Cannot back up ${p.projectTitle} (${p.id}) because ${err.getMessage} occurred while copying ${sourceFile.filepath} v${sourceFile.version} from storage ${sourceFile.storageId}")
              })
          }

      }
    }
  }

  def fixInvalidBackupsFor(projectAndFiles:(ProjectEntry, Seq[FileEntry]), storageDrivers:Map[Int, StorageDriver]):Future[Seq[Unit]] = {
    val p = projectAndFiles._1
    val backupFiles = projectAndFiles._2.filter(_.backupOf.isDefined)

    val zeroLengthBackups = backupFiles.filter(fileEntry=>{
      storageDrivers.get(fileEntry.storageId) match {
        case None=>
          logger.error(s"Could not get a storage driver for ${fileEntry.filepath} on storage id ${fileEntry.storageId}")
          false
        case Some(driver)=>
          driver.getMetadata(fileEntry.filepath, fileEntry.version) match {
            case None=>
              logger.error(s"Could not get metadata for ${fileEntry.filepath} v ${fileEntry.version} on storage id ${fileEntry.storageId} with driver ${driver.getClass.getCanonicalName}")
              false
            case Some(meta)=>
              if(meta.size==0) {
                logger.info(s"Found dodgy backup: $meta")
              }
              meta.size==0
          }
      }
    })

    logger.info(s"Project ${p.projectTitle} (${p.id}) has ${zeroLengthBackups.length} zero-length backups")

    Future.sequence(
      zeroLengthBackups.map(fileEntry=>{
        storageDrivers.get(fileEntry.storageId) match {
          case None=>
            logger.error(s"Could not get a storage driver for ${fileEntry.filepath} on storage id ${fileEntry.storageId}")
            Future.failed(new RuntimeException("Could not get a storage driver on the second pass, this should not happen!"))
          case Some(driver)=>
            if(fileEntry.storageId!=2) { //TEMPORARY HACK
              logger.info(s"Deleting zero-length backup ${fileEntry.filepath} on storage id ${fileEntry.storageId}")
              if (driver.deleteFileAtPath(fileEntry.filepath, fileEntry.version)) {
                logger.info(s"Deleting zero-length backup entry ${fileEntry.id}")
                fileEntry.deleteSelf
              } else {
                Future.failed(new RuntimeException(s"Could not delete file ${fileEntry.filepath} on storage id ${fileEntry.storageId}"))
              }
            } else {
              Future( () )
            }
        }
      })
    )
  }

  def nukeInvalidBackups:Future[BackupResults] = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)
    loadStorageDrivers().flatMap(drivers=>
      ProjectEntry
        .scanAllProjects
        .map(p=>{
          logger.info(s"Checking project ${p.projectTitle} for invalid backups")
          p
        })
        .mapAsync(1)(p=>p.associatedFiles(allVersions = true).map((p, _)))
        .map(projectAndFiles=>{
          val p = projectAndFiles._1
          val f = projectAndFiles._2
          val backupsCount = f.count(_.backupOf.isDefined)
          logger.info(s"Project ${p.projectTitle} has ${f.length} files of which $backupsCount are backups")
          projectAndFiles
        })
        .mapAsync(1)(projectAndFiles=>fixInvalidBackupsFor(projectAndFiles, drivers))
        .toMat(Sink.fold(BackupResults.empty(0))((acc, results)=>{
          acc.copy(successCount = acc.successCount+results.length)
        }))(Keep.right)
        .run()
    )
  }

  def backupProjects:Future[BackupResults] = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)

    for {
      drivers <- loadStorageDrivers()
      storages <- loadAllStorages()
      result <- ProjectEntry
        .scanProjectsForStatus(EntryStatus.InProduction)
        .map(p => {
          logger.info(s"Checking project ${p.projectTitle} for backup")
          p
        })
        .mapAsync(parallelCopies)(p => p.associatedFiles(allVersions = true).map((p, _)))
        .map(projectAndFiles => {
          val p = projectAndFiles._1
          val f = projectAndFiles._2
          val backupsCount = f.count(_.backupOf.isDefined)
          logger.info(s"Project ${p.projectTitle} has ${f.length} files of which $backupsCount are backups")
          projectAndFiles
        })
        .mapAsync(parallelCopies)(projectAndFiles => conditionalBackup(projectAndFiles, drivers, storages))
        .toMat(Sink.fold(BackupResults.empty(0))((acc, elem) => elem match {
          case Left(errs) =>
            logger.warn(s"Backup failed: ${errs}")
            acc.copy(totalCount = acc.totalCount + 1, failedCount = acc.failedCount + 1)
          case Right(true) =>
            acc.copy(totalCount = acc.totalCount + 1, successCount = acc.successCount + 1)
          case Right(false) =>
            acc.copy(totalCount = acc.totalCount + 1, notNeededCount = acc.notNeededCount + 1)
        }))(Keep.right)
        .run()
    } yield result
  }
}
