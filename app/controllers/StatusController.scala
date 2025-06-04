package controllers

import auth.{BearerTokenAuth, Security}
import models.{EntryStatus, ProjectEntry, StatusChangeDAO, StatusChangeSerializer}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import java.time.ZonedDateTime
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future

@Singleton
class StatusController @Inject()(cc:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                 override implicit val config: Configuration,
                                 dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi)
  extends AbstractController(cc) with Security with StatusChangeSerializer {
  override val logger = Logger(getClass)

  implicit val cache = cacheImpl
  implicit val db = dbConfigProvider.get[PostgresProfile].db


  def record(projectId: Int) = IsAuthenticated { uid =>
    request =>
      logger.info(s"Got a status change for project ${projectId}.")
      val timestamp = dateTimeToTimestamp(ZonedDateTime.now())
      StatusChangeDAO.create(projectId, timestamp, request.body.asJson.get("user").toString().replace("\"", ""), request.body.asJson.get("status").toString().replace("\"", ""), request.body.asJson.get("title").toString().replace("\"", ""))
      Ok(Json.obj("status"->"ok","detail"->"Status change recorded."))
  }

  def recordForCommission(commissionId: Int) = IsAuthenticated { uid =>
    request =>
      logger.info(s"Got a status change for commission ${commissionId}.")
      val newStatus = EntryStatus.withName(request.body.asJson.get("status").toString().replace("\"", ""))
      val action: DBIO[Seq[(Int, ProjectEntry)]] = ProjectEntry.getProjectsEligibleForStatusChange(newStatus, commissionId)
      db.run(action).flatMap { projectTuples =>
        if (projectTuples.isEmpty) {
          logger.info(s"StatusChange: No projects found needing status update to $newStatus for commission $commissionId")
          Future.successful(Seq.empty)
        } else {
          logger.info(s"StatusChange: Found ${projectTuples.length} projects to update to $newStatus for commission $commissionId")
          logger.info(s"StatusChange: Project IDs to update: ${projectTuples.map(_._1).mkString(", ")}")
          projectTuples.foldLeft(Future.successful(Seq.empty[Try[Int]])) { case (accFuture, (id, project)) =>
            accFuture.flatMap { acc =>
              val timestamp = dateTimeToTimestamp(ZonedDateTime.now())
              StatusChangeDAO.create(project.id.get, timestamp, request.body.asJson.get("user").toString().replace("\"", ""), request.body.asJson.get("status").toString().replace("\"", ""), project.projectTitle)
              val updateAction = (for {
                _ <- DBIO.successful()
                updateCount = 1
                verification = 1
              } yield (updateCount, verification)).transactionally
              db.run(updateAction).map {
                case (count, verification) if 1 == 1 =>
                  acc :+ Success(id)
              }
            }
          }
        }
      }
      Ok(Json.obj("status"->"ok","detail"->"Status change recorded."))
  }

  def records(startAt:Int, limit: Int) = IsAdminAsync {uid=>{request=>
    StatusChangeDAO.getRecords(startAt, limit).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","result"->results))
      case Failure(error)=>
        logger.error("Could not list status changes: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

  def recordsForProject(projectId:Int) = IsAuthenticatedAsync {uid=>{request=>
    StatusChangeDAO.getRecordsForProject(projectId).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","result"->results))
      case Failure(error)=>
        logger.error("Could not list status changes: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

}