package controllers

import akka.actor.ActorRef
import auth.{BearerTokenAuth, Security}
import models.{ProjectEntrySerializer, ValidationJob, ValidationJobDAO, ValidationJobRequest, ValidationJobSerializer, ValidationJobStatus}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import services.ValidateProject
import akka.pattern.ask

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
                                       @Named("validate-project-actor") validateProjectActor:ActorRef)
                                      (implicit ec: ExecutionContext)
  extends AbstractController(cc) with Security with ProjectEntrySerializer with ValidationJobSerializer {

  import models.ValidationJobMappers._

  implicit val validationJobRequestReads = Json.reads[ValidationJobRequest]

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
}
