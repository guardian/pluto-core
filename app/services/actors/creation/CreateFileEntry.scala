package services.actors.creation

import java.sql.Timestamp
import java.time.LocalDateTime
import javax.inject.Inject

import akka.actor.Props
import drivers.StorageDriver
import akka.pattern.ask
import exceptions.ProjectCreationError
import models.{FileEntry, ProjectRequestFull, ProjectType}
import org.slf4j.MDC
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object CreateFileEntry {
  case class DidCreateFileEntry(entry: FileEntry) extends CreationMessage
}

import scala.concurrent.ExecutionContext.Implicits.global

class CreateFileEntry @Inject() (dbConfigProvider:DatabaseConfigProvider) extends GenericCreationActor {
  override val persistenceId = "create-file-entry-" + self.path.name

  import GenericCreationActor._
  import CreateFileEntry._

  implicit val db = dbConfigProvider.get[JdbcProfile].db

  /**
    * Combines the provided filename with a (possibly) provided extension
    * @param filename filename
    * @param extension Option possibly containing a string of the file extension
    * @return Combined filename and extension. If no extension, filename returned unchanged; if the extension does not start with a
    *         dot then a dot is inserted between name and extension
    */
  private def makeFileName(filename:String,extension:Option[String]):String = {
    if(extension.isDefined){
      if(extension.get.startsWith("."))
        s"$filename${extension.get}"
      else
        s"$filename.${extension.get}"
    } else
      filename
  }

  /**
    * Either create a new file entry for the required destination file or retrieve a pre-exisiting one
    * @param rq ProjectRequestFull instance describing the project to be created
    * @param recordTimestamp time to record for creation
    * @param db implicitly provided database instance
    * @return a Future, containing a Try, containing a saved FileEntry instance if successful
    */
  def getDestFileFor(rq:ProjectRequestFull, recordTimestamp:Timestamp)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[FileEntry] =
    ProjectType.entryFor(rq.projectTemplate.projectTypeId).flatMap(projectType=>{
        FileEntry.entryFor(rq.filename, rq.destinationStorage.id.get, 1).map({
          case Success(filesList) =>
            if (filesList.isEmpty) {
              //no file entries exist already, create one (at version 1) and proceed
              FileEntry(None, makeFileName(rq.filename, projectType.fileExtension), rq.destinationStorage.id.get, "system", 1,
                recordTimestamp, recordTimestamp, recordTimestamp, hasContent = false, hasLink = false, None)
            } else {
              //a file entry does already exist, but may not have data on it
              if (filesList.length > 1)
                throw new ProjectCreationError(s"Multiple files exist for ${rq.filename} on ${rq.destinationStorage.repr}")
              else if (filesList.head.hasContent)
                throw new ProjectCreationError(s"File ${rq.filename} on ${rq.destinationStorage.repr} already has data")
              else
                filesList.head
            }
          case Failure(error) => throw error
        })
    })

  def removeDestFileFor(rq: ProjectRequestFull)(implicit db: slick.jdbc.PostgresProfile#Backend#Database): Future[Try[Boolean]] =
    ProjectType.entryFor(rq.projectTemplate.projectTypeId).flatMap(projectType=>{
        FileEntry.entryFor(makeFileName(rq.filename, projectType.fileExtension), rq.destinationStorage.id.get, 1).flatMap({
          case Success(filesList) =>
            filesList.headOption match {
              case None =>
                //no file entries exist, so nothing to remove
                logger.warn(s"No file entry exists for ${rq.filename} so I can't remove it in rollback")
                Future(Success(false))
              case Some(file) =>
                logger.info(s"Found file $file to delete")
                file.deleteSelf
                  .map(_=>Success(true))
                .recover({
                  case err:Throwable=>
                    logger.error(s"Could not remove invalid dastination file: ${err.getMessage}")
                    Failure(err)
                })
            }
          case Failure(err) =>
            Future(Failure(err))
        })
    })

  override def receive: Receive = {
    case entryRequest:NewProjectRequest=>
      val originalSender = sender()
      val recordTimestamp = Timestamp.valueOf(entryRequest.createTime.getOrElse(LocalDateTime.now()))
      val projectCreateData = entryRequest.data

      getDestFileFor(entryRequest.rq, recordTimestamp).flatMap(fileEntry=> {
        logger.info(s"Creating file $fileEntry")
        fileEntry.save.map({
          case Success(savedFileEntry) =>
            originalSender ! StepSucceded(projectCreateData.copy(destFileEntry = Some(savedFileEntry)))
          case Failure(error) =>
            MDC.put("fileEntry", fileEntry.toString)
            logger.error("Failed to create file", error)
            originalSender ! StepFailed(projectCreateData, error)
        })
      }).recover({
        case error:Throwable=>
          MDC.put("entryRequest", entryRequest.toString)
          logger.error("Could not create destination file record", error)
          originalSender ! StepFailed(projectCreateData, error)
      })

    case rollbackRequest:NewProjectRollback=>
      val originalSender = sender()
      val projectCreateData = rollbackRequest.data

      removeDestFileFor(rollbackRequest.rq).map({
        case Success(_)=>
          originalSender ! StepSucceded(projectCreateData.copy(destFileEntry = None))
        case Failure(error)=>
          logger.error("Could not remove destination file record in rollback", error)
          originalSender ! StepFailed(projectCreateData.copy(destFileEntry = None), error)
      })
    case _=>
      super.receive
  }
}
