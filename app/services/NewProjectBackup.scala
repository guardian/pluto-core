package services

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import drivers.StorageDriver
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

  def validateExistingBackups(sourceFile:FileEntry, potentialBackups:Seq[FileEntry], p:ProjectEntry, storageDrivers:Map[Int, StorageDriver]): Try[Either[String, Boolean]] = {
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
              Some( (backup.id.get, bMeta) )
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
            if(meta('size)==sourceMeta('size)) {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${mostRecent.map(_.version)} matches source, no backup required")
              Success(Right(true))
            } else {
              logger.info(s"Project ${p.projectTitle} (${p.id}) Most recent backup version ${mostRecent.map(_.version)} size mismatch ${sourceMeta('size)} vs ${meta('size)}, backup needed")
              Success(Right(false))
            }
          case None=>
            Success(Left(s"Project ${p.projectTitle} (${p.id})  most recent metadata was empty, could not check sizes"))
        }
    }
  }

  def conditionalBackup(projectAndFiles:(ProjectEntry, Seq[FileEntry]), storageDrivers:Map[Int, StorageDriver]):Future[Either[String, Option[FileEntry]]] = {
    import cats.implicits._
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

      validateExistingBackups(sourceFile, projectAndFiles._2.filter(_.backupOf.isDefined), p, storageDrivers) match {
        case Failure(err)=>Future.failed(err)
        case Success(Left(err))=>
          logger.error(s"Could not back up Project ${p.projectTitle} (${p.id}): $err")
          Future(Left(err))
        case Success(Right(false))=>
          logger.info(s"I would back up project ${p.projectTitle} (${p.id})")
          Future(Left("not implemented yet"))
        case Success(Right(true))=>
          logger.info(s"I would not back up project ${p.projectTitle} (${p.id}")
          Future(Right(None))
      }
    }
  }

  def backupProjects:Future[BackupResults] = {
    val parallelCopies = config.getOptional[Int]("backup.parallelCopies").getOrElse(1)

    loadStorageDrivers().flatMap(drivers=>
      ProjectEntry
        .scanProjectsForStatus(EntryStatus.InProduction)
        .map(p=>{
          logger.info(s"Checking project ${p.projectTitle} for backup")
          p
        })
        .mapAsync(parallelCopies)(p=>p.associatedFiles(allVersions = true).map((p, _)))
        .map(projectAndFiles=>{
          val p = projectAndFiles._1
          val f = projectAndFiles._2
          val backupsCount = f.count(_.backupOf.isDefined)
          logger.info(s"Project ${p.projectTitle} has ${f.length} files of which $backupsCount are backups")
          projectAndFiles
        })
        .mapAsync(parallelCopies)(projectAndFiles=>conditionalBackup(projectAndFiles, drivers))
        .toMat(Sink.fold(BackupResults.empty(0))((acc, elem)=>elem match {
          case Left(errs)=>
            logger.warn(s"Backup failed: ${errs}")
            acc.copy(totalCount = acc.totalCount+1, failedCount=acc.failedCount+1)
          case Right(Some(_))=>
            acc.copy(totalCount = acc.totalCount+1, successCount = acc.successCount+1)
          case Right(None)=>
            acc.copy(totalCount = acc.totalCount+1, notNeededCount=acc.notNeededCount+1)
        }))(Keep.right)
        .run()
    )

  }
}
