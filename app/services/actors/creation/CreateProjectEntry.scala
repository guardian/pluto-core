package services.actors.creation

import java.time.ZonedDateTime
import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import exceptions.PostrunActionError
import models.messages.NewProjectCreated
import models.{PlutoCommission, ProjectEntry, ProjectTemplate, ProjectType}
import org.slf4j.MDC
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class CreateProjectEntry @Inject() (dbConfigProvider:DatabaseConfigProvider) extends GenericCreationActor {
  override val persistenceId = "creation-project-entry-actor-" + self.path.name

  import GenericCreationActor._
  private implicit val db=dbConfigProvider.get[JdbcProfile].db


  def validateNewProjectRequest(createRequest:NewProjectRequest):Try[NewProjectRequest] = {
    if(createRequest.data.destFileEntry.isDefined &&
      createRequest.data.destFileEntry.get.hasContent &&
      createRequest.data.createdProjectEntry.isEmpty)
      Success(createRequest)
    else
      Failure(new RuntimeException(s"New project request failed to validate"))
  }

  def validateRollbackRequest(createRequest:NewProjectRollback):Try[NewProjectRollback] = {
    if(createRequest.data.destFileEntry.isDefined &&
      createRequest.data.destFileEntry.get.hasContent &&
      createRequest.data.createdProjectEntry.isDefined)
      Success(createRequest)
    else
      Failure(new RuntimeException(s"Rollback request failed to validate"))
  }

  override def receive: Receive = {
    /**
      * create a [[ProjectEntry]] record for the given creation request
      */
    case createRequest:NewProjectRequest=>
      doPersistedAsync(createRequest) { (msg, originalSender)=>
        MDC.put("createRequest", createRequest.toString)
        MDC.put("originalSender", originalSender.toString())
        logger.debug("persisted create project entry request event to journal, now performing")

        validateNewProjectRequest(createRequest) match {
          case Success(validatedRequest) =>
            val writtenFile = createRequest.data.destFileEntry.get
            val rq = createRequest.rq
            val createTime = createRequest.createTime
            logger.info(s"Creating new project entry from $writtenFile")
            ProjectEntry
              .createFromFile(writtenFile, rq.projectTemplate, rq.title, createTime, rq.user.toLowerCase(), rq.workingGroupId, rq.commissionId, rq.existingVidispineId, rq.deletable, rq.deep_archive, rq.sensitive, rq.productionOffice, rq.obitProject, false)
              .map({
                case Success(createdProjectEntry) =>
                  logger.info(s"Project entry created as id ${createdProjectEntry.id}")

                  originalSender ! StepSucceded(updatedData = createRequest.data.copy(createdProjectEntry = Some(createdProjectEntry)))
                  Success(rq.projectTemplate)
                case Failure(error) =>
                  logger.error("Could not create project file: ", error)
                  originalSender ! StepFailed(createRequest.data, error)
                  Success("Could not create project file: ")
              })
          case Failure(validationError) =>
            logger.error("Can't execute CreateProjectEntry", validationError)
            originalSender ! StepFailed(createRequest.data, validationError)
            Future(Success(validationError.toString))
        }
      }

    /**
      * Remove the [[ProjectEntry]] record associated with the given rollback request
      */
    case rollbackRequest:NewProjectRollback=>
      doPersistedAsync(rollbackRequest) { (msg, originalSender)=>
        MDC.put("rollbackRequest", rollbackRequest.toString)
        MDC.put("originalSender", originalSender.toString())
        validateRollbackRequest(rollbackRequest) match {
          case Success(validatedRequest) =>
            rollbackRequest.data.createdProjectEntry match {
              case Some(createdProjectEntry) =>
                logger.info(s"Rolling back created project file entry $createdProjectEntry, deleting it from database")
                createdProjectEntry.removeFromDatabase.map({
                  case Success(_) =>
                      originalSender ! StepSucceded(rollbackRequest.data.copy(createdProjectEntry = None))
                  case Failure(error) =>
                    originalSender ! StepFailed(rollbackRequest.data, error)
                })
              case None =>
                logger.error("No project entry present in request so I can't delete")
                val exc = new RuntimeException("No project entry present in request so I can't delete")
                originalSender ! StepFailed(rollbackRequest.data, exc)
                Future(Failure(exc))
            }
          case Failure(validationError) =>
            logger.error("Can't rollback CreateProjectEntry", validationError)
            originalSender ! StepFailed(rollbackRequest.data, validationError)
            Future(Failure(validationError))
        }
      }
    case _=>
      super.receive
  }
}
