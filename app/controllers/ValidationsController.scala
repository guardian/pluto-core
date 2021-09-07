package controllers

import akka.actor.ActorRef
import auth.{BearerTokenAuth, Security}
import models.{ProjectEntrySerializer, ValidationJob, ValidationJobDAO, ValidationJobRequest, ValidationJobSerializer, ValidationJobStatus, ValidationProblem, ValidationProblemDAO}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import services.ValidateProject
import akka.pattern.ask

import java.util.UUID
import scala.concurrent.duration._
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class ValidationsController @Inject() (override val config:Configuration,
                                       override val cache: SyncCacheApi,
                                       cc:ControllerComponents,
                                       override val bearerTokenAuth:BearerTokenAuth,
                                       validationJobDAO:ValidationJobDAO,
                                       validationProblemDAO:ValidationProblemDAO,
                                       @Named("validate-project-actor") validateProjectActor:ActorRef)
                                      (implicit ec: ExecutionContext)
  extends AbstractController(cc) with Security with ProjectEntrySerializer with ValidationJobSerializer {

  import models.ValidationJobMappers._

  implicit val validationJobRequestReads = Json.reads[ValidationJobRequest]
  implicit val validationProblemWrites = Json.writes[ValidationProblem]

  def startValidation = IsAdminAsync[ValidationJobRequest](parse.json[ValidationJobRequest]) { uid=> request=>
    logger.info(s"Received validation request for ${request.body.validationType} from $uid")
    val newJob = ValidationJob(request.body.validationType, uid)
    logger.info(s"Validation request uuid is ${newJob.uuid}")
    validationJobDAO.writeJob(newJob)
      .map(writtenJob=>{
        validateProjectActor ! ValidateProject.RequestValidation(writtenJob)
        logger.info(s"Validation has started")
        Ok(Json.obj("status"->"ok", "entry"->writtenJob))
      })
      .recover({
        case err:Throwable=>
          logger.error(s"${newJob.uuid}:  Could not write to database: ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"error", "detail"->"could not write to database, see server logs"))
      })
  }

  def getJobs(userName:Option[String], status:Option[String], limit:Option[Int]) = IsAdminAsync {uid=> request=>
    Try { status.map(ValidationJobStatus.withName)} match {
      case Success(maybeStatus)=>
        validationJobDAO
          .queryJobs(userName, maybeStatus, limit.getOrElse(100))
        .map(results=>{
          val hitCount = results._1
          val items = results._2
          Ok(Json.obj("status"->"ok","totalCount"->hitCount, "jobs"->items))
        })
      case Failure(exception)=>
        Future(BadRequest(Json.obj("status"->"error","detail"->"invalid status value")))
    }
  }

  def faultsForJobId(jobId:String,from:Int=0, limit:Int=100) = IsAdminAsync {uid=> request=>
    val resultsFut = for {
      jobUUID    <- Future.fromTry(Try { UUID.fromString(jobId)})
      totalCount <- validationProblemDAO.faultCountForJob(jobUUID)
      records    <- validationProblemDAO.faultsForJobID(jobUUID, from, limit)
    } yield (totalCount, records)

    resultsFut
      .map(results=>Ok(Json.obj("status"->"ok","totalCount"->results._1,"entries"->results._2)))
      .recover({
        case err:Throwable=>
          logger.error(s"Could not look up faults for job ID ${jobId}: ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"error","detail"->"database lookup problem, see logs"))
      })
  }

  def jobDetails(jobId:String) = IsAdminAsync { uid =>
    request =>
      Try {
        UUID.fromString(jobId)
      } match {
        case Success(jobUUID) =>
          validationJobDAO
            .jobForUUID(jobUUID)
            .map({
              case None =>
                NotFound(Json.obj("status" -> "notfound", "detail" -> "invalid job id"))
              case Some(record) =>
                Ok(Json.toJson(record))
            })
            .recover({
              case err: Throwable =>
                logger.error(s"Could not get data for job ID $jobId: ${err.getMessage}", err)
                InternalServerError(Json.obj("status" -> "error", "detail" -> "database lookup problem, see logs"))
            })
        case Failure(err) =>
          Future(BadRequest(Json.obj("status" -> "error", "detail" -> "malformed job id")))
      }
  }
}
