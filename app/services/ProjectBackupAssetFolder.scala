package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.{StorageDriver, StorageMetadata}
import helpers.StorageHelper
import models._
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile

import java.nio.file.{Files, Paths}
import java.sql.Timestamp
import java.time.{Duration, Instant, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import java.io.File
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class ProjectBackupAssetFolder @Inject()(config:Configuration, dbConfigProvider: DatabaseConfigProvider, storageHelper:StorageHelper)
                                        (implicit mat:Materializer, fileEntryDAO:FileEntryDAO, assetFolderFileEntryDAO: AssetFolderFileEntryDAO, injector: Injector){
  private val logger = LoggerFactory.getLogger(getClass)
  private implicit lazy val db = dbConfigProvider.get[PostgresProfile].db

  import ProjectBackupAssetFolder._

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
    * @param intendedTarget AssetFolderFileEntry with `version` set to the initial estimate of what the version should be
    */
  protected def findAvailableVersion(destStorage:StorageEntry, intendedTarget:AssetFolderFileEntry) = {
    destStorage.getStorageDriver match {
      case Some(driver)=>
        implicit val drv:StorageDriver = driver
        def findAvailable(target:AssetFolderFileEntry):Future[AssetFolderFileEntry] = {
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
    * returns a AssetFolderFileEntry indicating a target file to write.
    * This is guaranteed to be on the destination storage given.
    *   - if the destination storage supports versioning, then it is guaranteed not to exist yet (previous dest entry with the version field incremented).
    *   - If the destination storage does NOT support versioning, then it will be identical to the "previous" dest entry provided
    *   - if there is no "previous" destination that a new entry will be created from the source entry's metadata
    *   - if the Source entry does not exist then that's an error
    *
    * @param maybeSourceFileEntry option containing the source file entry
    * @param maybePrevDestEntry optional destination of the previous iteration
    * @param destStorage destination storage
    * @return a Future containing a AssetFolderFileEntry to write to.  This should be saved to the database before proceeding to write.
    */
  def ascertainTarget(maybeSourceFileEntry:Option[AssetFolderFileEntry], maybePrevDestEntry:Option[AssetFolderFileEntry], destStorage:StorageEntry):Future[AssetFolderFileEntry] = {
    logger.warn(s"In ascertainTarget. maybePrevDestEntry - ${maybePrevDestEntry}")
    (maybeSourceFileEntry, maybePrevDestEntry) match {
      case (Some(sourceEntry), Some(prevDestEntry))=>
        logger.debug(s"${sourceEntry.filepath}: prevDestEntry is $prevDestEntry")
        if(destStorage.supportsVersions) {
          val intendedTarget = prevDestEntry.copy(id=None,
            storageId=destStorage.id.get,
            version = prevDestEntry.version+1,
            mtime=Timestamp.from(Instant.now()),
            atime=Timestamp.from(Instant.now()),
            backupOf = sourceEntry.id) // check
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
            backupOf = sourceEntry.id)
        )
      case (None, _)=>
        throw new RuntimeException("Can't back up as source file was not found")  //fail the Future
    }
  }

  private def getTargetFileEntry(sourceEntry:AssetFolderFileEntry, maybePrevDestEntry:Option[AssetFolderFileEntry], destStorage:StorageEntry):Future[AssetFolderFileEntry] = {
    logger.warn(s"In getTargetFileEntry. maybePrevDestEntry: ${maybePrevDestEntry}")
    for {
      targetDestEntry <- ascertainTarget(Some(sourceEntry), maybePrevDestEntry, destStorage)  //if our destStorage supports versioning, then we get a new entry here
      updatedEntryTry <- assetFolderFileEntryDAO.save(targetDestEntry) //make sure that we get the updated database id of the file
      updatedDestEntry <- Future
        .fromTry(updatedEntryTry)
        .recoverWith({
          case err:org.postgresql.util.PSQLException=>
            logger.warn(s"While trying to make the target entry, caught exception of type ${err.getClass.getCanonicalName} with message ${err.getMessage}")
            if(err.getMessage.contains("duplicate key value violates unique constraint")) {
              logger.warn(s"Pre-existing file entry detected for ${targetDestEntry.filepath} v${targetDestEntry.version} on storage ${targetDestEntry.storageId}, recovering it")
              assetFolderFileEntryDAO
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

  def getAssetFolderProjectFilePaths(directoryName: String): Array[String] = {
    return new File(directoryName).listFiles.
      filter { f => f.isFile && (f.getName.endsWith(".cpr") || f.getName.endsWith(".sesx") || f.getName.endsWith(".bak")) }.
      map(_.getAbsolutePath)
  }

  def getMostRecentEntryForProject(projectId: Int, storage: Int, filePath: String): Future[AssetFolderFileEntry] = {
    assetFolderFileEntryDAO.entryForLatestVersionByProject(projectId, storage, filePath).map {
      _.get
    }
  }

  def findMostRecentBackup(potentialBackups:Seq[AssetFolderFileEntry], p:ProjectEntry, storageDrivers:Map[Int, StorageDriver]) = {
    potentialBackups.map(backup=>{
      storageDrivers.get(backup.storageId) match {
        case Some(destDriver)=>
          val bMeta = destDriver.getMetadata(backup.filepath, backup.version)
          logger.debug(s"Project ${p.projectTitle} (${p.id.get}) backup file ${backup.filepath} v${backup.version} metadata: $bMeta")
          bMeta.map(m=>(backup, m) )
        case None=>
          logger.error(s"Project ${p.projectTitle} (${p.id.get}) Could not get a destination driver for storage ${backup.storageId} on file ${backup.filepath} v${backup.version}")
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

  def shouldCopy(readPath: String, writePath: String, projectId: Int, storage: Int, p: ProjectEntry, storageDrivers:Map[Int, StorageDriver], sourceFile: AssetFolderFileEntry ): Boolean = {
    val mostRecentEntry = Await.result(getMostRecentEntryForProject(projectId, storage, sourceFile.filepath), 10 seconds)
    logger.debug(s"Most recent version for project $projectId is ${mostRecentEntry.version}")
    val sourceMetaTwo = storageDrivers.get(sourceFile.storageId) match {
      case Some(sourceDriver) =>
        sourceDriver.getMetadata(sourceFile.filepath, sourceFile.version)
    }

    findMostRecentBackup(Seq(mostRecentEntry), p, storageDrivers) match {
      case None =>
        logger.info(s"Project ${p.projectTitle} (${p.id.get}) most recent metadata was empty, could not check sizes. Assuming that the file is not present and needs backup")
        true
      case Some((fileEntry, meta)) =>
        logger.info(s"Project ${p.projectTitle} (${p.id.get}) Most recent backup leads source file by ${getTimeDifference(sourceMetaTwo, meta)}")
        if (meta.size == sourceMetaTwo.get.size) {
          logger.info(s"Project ${p.projectTitle} (${p.id.get}) Most recent backup version ${fileEntry.version} matches source, no backup required")
          false
        } else {
          logger.info(s"Project ${p.projectTitle} (${p.id.get}) Most recent backup version ${fileEntry.version} size mismatch ${sourceMetaTwo.get.size} vs ${meta.size}, backup needed")
          true
        }
    }
  }

  def backupProjects(onlyByType:Boolean):Future[BackupResults] = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)
    val makeFoldersSetting = config.getOptional[Boolean]("asset_folder_backup_make_folders").getOrElse(true)

    var backupTypes = Array(2,3,4,6)
    val backupTypesSequence = config.getOptional[Seq[Int]]("asset_folder_backup_types").getOrElse(None)
    if (backupTypesSequence != None) {
      backupTypes = backupTypesSequence.iterator.toArray
    }

    def getScanSource() = if(onlyByType) {
      ProjectEntry.scanProjectsForTypes(backupTypes)
    } else {
      ProjectEntry.scanAllProjects
    }

    def getAssetFolder(id:Int) = ProjectMetadata.entryFor(id, ProjectMetadata.ASSET_FOLDER_KEY)

    def getDestFileFor(filePath:String, recordTimestamp:Timestamp, projectId:Option[Int], assetFolderStorage:Int)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[AssetFolderFileEntry] =

        assetFolderFileEntryDAO.entryFor(filePath,1).map({
          case Success(filesList) =>
            if (filesList.isEmpty) {
              //No file entries exist already, create one (at version 1) and proceed
              assetFolderFileEntryDAO.save(AssetFolderFileEntry(None, filePath, assetFolderStorage, 1, recordTimestamp, recordTimestamp, recordTimestamp, projectId, backupOf = None))
              AssetFolderFileEntry(None, filePath, assetFolderStorage, 1, recordTimestamp, recordTimestamp, recordTimestamp, projectId, backupOf = None)
            } else {
                filesList.head
            }
          case Failure(error) => throw error
        })


    for {
      drivers <- loadStorageDrivers()
      storages <- loadAllStorages()
      result <- getScanSource()
        .map(p => {
          logger.debug(s"Checking project ${p.projectTitle} for backup. Type: ${p.projectTypeId}")
          p
        })
        .mapAsync(parallelCopies)(p => {
          logger.debug(s"Got project id.: ${p.id.get}")
          getAssetFolder(p.id.get).onComplete(folderData => folderData match {
            case Failure(error) =>
              logger.debug(s"Attempt at getting asset folder path failed: $error")
            case Success(assetFolderData) =>
              try {
                logger.debug(s"Got asset folder path: ${assetFolderData.get.value.get}")
                val fileArray = getAssetFolderProjectFilePaths(assetFolderData.get.value.get)
                logger.debug(s"Got file array: ${fileArray.mkString}")
                fileArray.foreach(filePath => {
                  logger.debug(s"File path: $filePath")
                  val assetFolderStorage = config.getOptional[Int]("asset_folder_storage").getOrElse(1)
                  logger.debug(s"Storage to access: $assetFolderStorage")
                  val storageObject = StorageEntryHelper.entryFor(assetFolderStorage).onComplete(storageData => storageData match {
                    case Failure(error) =>
                      logger.debug(s"Attempt at getting storage data failed: $error")
                    case Success(storageData) =>
                      val rootPath = storageData.get.rootpath.get
                      logger.debug(s"Root: $rootPath")
                      val fixedPath = filePath.replace(rootPath,"")
                      val recordTimestamp = Timestamp.valueOf(LocalDateTime.now())
                      val assetFolderFileDest = getDestFileFor(fixedPath, recordTimestamp, p.id, assetFolderStorage)
                      val assetFolderBackupStorage = config.getOptional[Int]("asset_folder_backup_storage").getOrElse(1)
                      logger.debug(s"Storage to use: $assetFolderBackupStorage")

                      assetFolderFileDest.map(fileEntry=> {
                        val mostRecentEntryTwo = Await.result(getMostRecentEntryForProject(p.id.get, assetFolderBackupStorage, fixedPath), 10 seconds)
                        getTargetFileEntry(fileEntry, Some(mostRecentEntryTwo), storages.get(assetFolderBackupStorage).get).onComplete(fileDest => fileDest match {
                          case Failure(error) =>
                            logger.debug(s"Attempt at getting file data failed: $error")
                          case Success(destData) =>
                            destData.getFullPath.onComplete {
                              case Failure(exception) =>
                                logger.debug(s"Fail: $exception")
                              case Success(pathData) =>
                                val storageSupportsVersions = storages(assetFolderBackupStorage).supportsVersions
                                logger.debug(s"Back up storage supports versions: $storageSupportsVersions")
                                var attemptCopy = false
                                if (storageSupportsVersions) {
                                  attemptCopy = shouldCopy(filePath, pathData, p.id.get, assetFolderBackupStorage, p, drivers, fileEntry)
                                } else {
                                  attemptCopy = true
                                }
                                if (attemptCopy) {
                                  if (makeFoldersSetting) {
                                    logger.debug(s"Write path: $pathData")
                                    val pathFolder = Paths.get(pathData).getParent.toString
                                    logger.debug(s"Write folder: $pathFolder")
                                    Files.createDirectories(Paths.get(pathFolder))
                                  }
                                  storageHelper.copyAssetFolderFile(fileEntry, destData)
                                }
                            }
                        })
                      })
                  })
                })
              } catch {
                case e: java.lang.NullPointerException => logger.debug(s"Could not find any project files to process.")
                case e: java.util.NoSuchElementException => logger.debug(s"Could not find an asset folder path.")
              }
          })
          Thread.sleep(800)
          Future(Right(true))
        })
        .toMat(Sink.fold(BackupResults.empty(0))((acc, elem) => elem match {
          case Right(true) =>
            acc.copy(totalCount = acc.totalCount + 1, successCount = acc.successCount + 1)
          case Right(false) =>
            acc.copy(totalCount = acc.totalCount + 1, notNeededCount = acc.notNeededCount + 1)
        }))(Keep.right)
        .run()
    } yield result
  }
}

object ProjectBackupAssetFolder {
  case class BackupResults(storageId:Int, totalCount:Long, failedCount:Long, successCount:Long, notNeededCount:Long)
  object BackupResults {
    def empty(storageId:Int) = new BackupResults(storageId, 0,0,0,0)
  }
}