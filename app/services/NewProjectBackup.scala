package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.{StorageDriver, StorageMetadata}
import helpers.StorageHelper
import models.{EntryStatus, FileAssociationRow, FileEntry, FileEntryDAO, ProjectEntry, StorageEntry, StorageEntryHelper}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

@Singleton
class NewProjectBackup @Inject() (config:Configuration, dbConfigProvider: DatabaseConfigProvider, storageHelper:StorageHelper)
                                 (implicit mat:Materializer, fileEntryDAO:FileEntryDAO, injector: Injector){
  private val logger = LoggerFactory.getLogger(getClass)
  private implicit lazy val db = dbConfigProvider.get[PostgresProfile].db

  import NewProjectBackup._

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
            storageId=destStorage.id.get,
            version = prevDestEntry.version+1,
            mtime=Timestamp.from(Instant.now()),
            atime=Timestamp.from(Instant.now()),
            hasContent = false,
            hasLink = true)
          findAvailableVersion(destStorage, intendedTarget)
            .map(correctedTarget=>{
              logger.debug(s"Destination storage ${destStorage.id} ${destStorage.rootpath} supports versioning, nothing will be over-written. Target version number is ${correctedTarget.version+1}")
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

  private def getTargetFileEntry(sourceEntry:FileEntry, maybePrevDestEntry:Option[FileEntry], destStorage:StorageEntry):Future[FileEntry] = {
    for {
      targetDestEntry <- ascertainTarget(Some(sourceEntry), maybePrevDestEntry, destStorage)  //if our destStorage supports versioning, then we get a new entry here
      updatedEntryTry <- fileEntryDAO.save(targetDestEntry) //make sure that we get the updated database id of the file
      updatedDestEntry <- Future
        .fromTry(updatedEntryTry)
        .recoverWith({
          case err:org.postgresql.util.PSQLException=>
            logger.warn(s"While trying to make the target entry, caught exception of type ${err.getClass.getCanonicalName} with message ${err.getMessage}")
            if(err.getMessage.contains("duplicate key value violates unique constraint")) {
              logger.warn(s"Pre-existing file entry detected for ${targetDestEntry.filepath} v${targetDestEntry.version} on storage ${targetDestEntry.storageId}, recovering it")
              fileEntryDAO
                .singleEntryFor(targetDestEntry.filepath, targetDestEntry.storageId, targetDestEntry.version)
                .map({
                  case Some(entry)=>entry
                  case None=>
                    logger.error(s"Got a conflict exception when trying to create a new record for ${targetDestEntry.filepath} v${targetDestEntry.version} on storage ${targetDestEntry.storageId} but no previous record existed?")
                    throw new RuntimeException("Database conflict problem, see logs")
                })
            } else {
              Future.failed(err)
            }
        })
    } yield updatedDestEntry
  }

  /**
    * performs the backup operation
    * @param sourceEntry FileEntry representing the file to back up
    * @param maybePrevDestEntry an optional FileEntry representing the _previous_ incremental backup (if there was one).
    * @param destStorage storage to back up onto
    * @return a Future, containing a tuple of two FileEntries. The first is the "written" file, the second is the "source" file. The future
    *         fails on error.
    */
  def performBackup(sourceEntry:FileEntry, maybePrevDestEntry:Option[FileEntry], destStorage:StorageEntry) = {
    for {
      updatedDestEntry <- getTargetFileEntry(sourceEntry, maybePrevDestEntry, destStorage)
      results <- {
        logger.warn(s"Backing up ${sourceEntry.filepath} on storage ${sourceEntry.storageId} to ${updatedDestEntry.filepath} v${updatedDestEntry.version} on storage ${updatedDestEntry.storageId}")
        storageHelper.copyFile(sourceEntry, updatedDestEntry)
          .flatMap(fileEntry=>{
            //ensure that we save the record with `b_has_content` set to true
            fileEntryDAO.saveSimple(fileEntry).map(finalEntry=>(finalEntry, sourceEntry))
          })
          .recoverWith({
            case err:Throwable=>
              logger.error(s"Could not copy ${updatedDestEntry.filepath} on ${updatedDestEntry.storageId} from ${sourceEntry.filepath} on ${sourceEntry.storageId}: ${err.getMessage}",err)
              fileEntryDAO
                .deleteFromDisk(updatedDestEntry)
                .andThen(_=>fileEntryDAO.deleteRecord(updatedDestEntry))
                .flatMap(_=>Future.failed(new RuntimeException(err.toString)))
          })
      }
      _ <- makeProjectLink(results._2, results._1)
    } yield results
  }

  private def findMostRecentByFilesystem(potentialBackups:Seq[FileEntry], p:ProjectEntry, storageDrivers:Map[Int, StorageDriver]) = {
    //get a list of metadata in order of most recent file modification
    potentialBackups.map(backup=>{
      storageDrivers.get(backup.storageId) match {
        case Some(destDriver)=>
          val bMeta = destDriver.getMetadata(backup.filepath, backup.version)
          logger.debug(s"Project ${p.projectTitle} (${p.id}) backup file ${backup.filepath} v${backup.version} metadata: $bMeta")
          bMeta.map(m=>(backup, m) )
        case None=>
          logger.error(s"Project ${p.projectTitle} (${p.id}) Could not get a destination driver for storage ${backup.storageId} on file ${backup.filepath} v${backup.version}")
          None
      }
    })
      .collect({case Some(result)=>result})
      .sortBy(_._2.lastModified.toInstant.toEpochMilli)(Ordering.Long.reverse)
      .map(entries=>{
          logger.debug(s"Ordered entry: ${entries._1.filepath} ${entries._1.version} ${entries._2.lastModified} ${entries._2.size}")
          entries
        })
      .headOption
  }
  /**
    * Checks whether the given project file needs backing up
    * @param sourceFile FileEntry representing the file to potentially be backed up
    * @param potentialBackups **sorted** list of FileEntry representing the existing backups of the given sourceFile.
    *                         It is assumed that this is sorted in descending time order, i.e. most recent first and oldest
    *                         last.
    * @param p the ProjectEntry representing the project that the files belong to (for logging purposes)
    * @param storageDrivers cached Map of StorageDrivers, so we don't initialise a new one on every file
    * @return Success with a Right if a backup is required or a Left if no backup is required.
    *         In the "Backup Required" case, the Right could contain a FileEntry or None. If it's a FileEntry, that is
    *         representing the _most recent_ backup (which is now out-dated).
    *         If there is an error, a Failure is returned.
    */
  def validateExistingBackups(sourceFile:FileEntry, potentialBackups:Seq[FileEntry], p:ProjectEntry, storageDrivers:Map[Int, StorageDriver]): Try[Either[String, Option[FileEntry]]] = {
    storageDrivers.get(sourceFile.storageId) match {
      case None=>
        logger.error(s"Project ${p.projectTitle} (${p.id}) Could not get a storage driver for storage ${sourceFile.storageId} on file ${sourceFile.filepath}")
        Failure(new RuntimeException(s"Could not get a storage driver for storage ${sourceFile.storageId} on file ${sourceFile.filepath}"))
      case Some(sourceDriver)=>
        val sourceMeta = sourceDriver.getMetadata(sourceFile.filepath, sourceFile.version)
        logger.debug(s"Project ${p.projectTitle} (${p.id}) source file ${sourceFile.filepath} v${sourceFile.version} metadata: $sourceMeta")

        findMostRecentByFilesystem(potentialBackups, p, storageDrivers) match {
          case None=>
            logger.info(s"Project ${p.projectTitle} (${p.id})  most recent metadata was empty, could not check sizes. Assuming that the file is not present and needs backup")
            Success(Right(None)) //signal needs backup
          case Some( (fileEntry, meta) )=>
            logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup leads source file by ${getTimeDifference(sourceMeta, meta)}")
            if(meta.size==sourceMeta.get.size) {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${fileEntry.version} matches source, no backup required")
              Success(Left("No backup required"))
            } else {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${fileEntry.version} size mismatch ${sourceMeta.get.size} vs ${meta.size}, backup needed")
              Success(Right(Some(fileEntry)))
            }
        }
    }
  }

  /**
    * Checks whether we should back up the given file
    * @param projectAndFiles tuple consisting of the ProjectEntry and a list of all its files
    * @param storageDrivers internal, immutable StorageDrivers cache
    * @return a Future containing:
    *         - a Left if there was a problem and the file could not be backed up but the backup job should continue
    *         - a Right with `true` if the file was backed up
    *         - a Right with `false` if the file did not need backing up
    *         The returned future fails on a permanent error, this should be picked up with .recover and the backup job terminated
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

      validateExistingBackups(
        sourceFile,
        projectAndFiles._2.filter(_.backupOf.isDefined).sortBy(_.version)(Ordering.Int.reverse),
        p,
        storageDrivers
      ) match {
        case Failure(err)=>
          Future.failed(err)
        case Success(Left(msg))=>
          logger.info(s"Project ${p.projectTitle} (${p.id}): $msg")
          Future(Right(false))
        case Success(Right(maybeMostRecentBackup))=>
          logger.debug(s"I will back up project ${p.projectTitle} (${p.id})")
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
              if(sourceFile.storageId==destStorage.id.get) {
                Future.failed(new RuntimeException(s"Cannot back up ${p.projectTitle} (${p.id}) because storage ${sourceFile.storageId} is configured to back up to itself. This is not supported and can lead to data loss, please fix."))
              } else {
                performBackup(sourceFile, maybeMostRecentBackup, destStorage)
                  .map(_=>Right(true))
                  .recover({
                    case err: Throwable =>
                      Left(s"Cannot back up ${p.projectTitle} (${p.id}) because ${err.getMessage} occurred while copying ${sourceFile.filepath} v${sourceFile.version} from storage ${sourceFile.storageId}")
                  })
              }
          }

      }
    }
  }

  /**
    * Deletes all zero-length backups for the given project.  This deletes the files from disk via the storage driver,
    * deletes the ProjectFileAssociation and the FileEntry associated with the dodgy backup file.
    * @param projectAndFiles a 2-tuple consisting of the ProjectEntry representing the project and a list of all the
    *                        FileEntry objects associated with it
    * @param storageDrivers cached map of StorageDrivers, so we don't have to initialise a new one every time
    * @return a successful Future if all the invalid backups are removed or there were none to remove. A Failed future if
    *         there is a problem.
    */
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

  def backupProjects(onlyInProgress:Boolean):Future[BackupResults] = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)

    def getScanSource() = if(onlyInProgress) {
      ProjectEntry.scanProjectsForStatus(EntryStatus.InProduction)
    } else {
      ProjectEntry.scanAllProjects
    }

    for {
      drivers <- loadStorageDrivers()
      storages <- loadAllStorages()
      result <- getScanSource()
        .map(p => {
          logger.debug(s"Checking project ${p.projectTitle} for backup")
          p
        })
        .mapAsync(parallelCopies)(p => p.associatedFiles(allVersions = true).map((p, _)))
        .map(projectAndFiles => {
          val p = projectAndFiles._1
          val f = projectAndFiles._2
          val backupsCount = f.count(_.backupOf.isDefined)
          logger.debug(s"Project ${p.projectTitle} has ${f.length} files of which $backupsCount are backups")
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

object NewProjectBackup {
  case class BackupResults(storageId:Int, totalCount:Long, failedCount:Long, successCount:Long, notNeededCount:Long)
  object BackupResults {
    def empty(storageId:Int) = new BackupResults(storageId, 0,0,0,0)
  }
}