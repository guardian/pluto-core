package services.actors.creation

import java.util.UUID
import javax.inject.Inject
import org.slf4j.MDC
import akka.actor.Props
import akka.stream.Materializer
import exceptions.PostrunActionError
import helpers.StorageHelper
import models.{FileEntry, FileEntryDAO, ProjectEntry}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits.global

object CopySourceFile {
  def props = Props[CopySourceFile]
  import GenericCreationActor._
}

/**
  * copies the template file associated with the template provided in [[services.actors.creation.GenericCreationActor.NewProjectRequest]] to
  * the file given by [[services.actors.creation.GenericCreationActor.ProjectCreateTransientData.destFileEntry]].
  * On rollback, deletes the file given by [[services.actors.creation.GenericCreationActor.ProjectCreateTransientData.destFileEntry]]
  * and updates it to have no content again
  */
class CopySourceFile  @Inject() (dbConfigProvider:DatabaseConfigProvider, storageHelper:StorageHelper)(implicit injector:Injector, fileEntryDAO: FileEntryDAO) extends GenericCreationActor {
  override val persistenceId = "creation-get-storage-actor-" + self.path.name

  import CopySourceFile._
  import GenericCreationActor._
  private implicit val db=dbConfigProvider.get[JdbcProfile].db

  override def receive: Receive = {
    case copyRequest:NewProjectRequest=>
      doPersistedAsync(copyRequest) { (msg,originalSender)=>
        val rq = copyRequest.rq
        val savedFileEntry = copyRequest.data.destFileEntry.get
        MDC.put("copyRequest", copyRequest.toString)
        MDC.put("originalSender", originalSender.toString())

        logger.debug("persisted copy request event to journal, now performing")
        
        rq.destinationStorage.getStorageDriver match {
          case None=>
            logger.error(s"No storage driver was configured for ${rq.destinationStorage}")
            Future(Success(s"No storage driver was configured for ${rq.destinationStorage}")) //success here => do not retry
          case Some(storageDriver)=>
            MDC.put("storageDriver", storageDriver.toString)
            logger.info(s"Got storage driver: $storageDriver")
            rq.projectTemplate.file.flatMap(sourceFileEntry=>{
              MDC.put("sourceFileEntry", sourceFileEntry.toString)
              MDC.put("savedFileEntry", savedFileEntry.toString)
              logger.info(s"Copying from file $sourceFileEntry to $savedFileEntry")
              storageHelper.copyFile(sourceFileEntry, savedFileEntry)
            }).flatMap(copiedFileEntry=>{
                logger.debug(copiedFileEntry.toString)
                copiedFileEntry.saveSimple.map(_=>{
                  val updatedData = copyRequest.data.copy(destFileEntry = Some(copiedFileEntry))
                  originalSender ! StepSucceded(updatedData)
                })
            }).recover({
              case error:Throwable=>
                logger.error(error.getMessage, error)
                originalSender ! StepFailed(copyRequest.data, error)
                Success(s"No storage driver was configured for ${rq.destinationStorage}")
            })
        }
      }
    case rollbackRequest:NewProjectRollback=>
      doPersistedAsync(rollbackRequest) { (msg, originalSender) =>
        val rq = rollbackRequest.rq
        rollbackRequest.data.destFileEntry match {
          case Some(fileEntry)=>
            storageHelper.deleteFile(fileEntry).map({
              case Right(updateFileRef)=>
                val updatedData = rollbackRequest.data.copy(destFileEntry = Some(updateFileRef))
                originalSender ! StepSucceded(updatedData)
              case Left(errorString)=>
                originalSender ! StepFailed(rollbackRequest.data, new RuntimeException(errorString))
            })
          case None=>
            originalSender ! StepFailed(rollbackRequest.data, new RuntimeException("No file entry available to roll back"))
            Future(Success(s"No storage driver was configured for ${rq.destinationStorage}"))
        }
      }
    case _=>
      super.receive
  }
}
