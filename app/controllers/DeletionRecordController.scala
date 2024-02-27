package controllers

import javax.inject._
import auth.{BearerTokenAuth, Security}
import models.{DeletionRecordDAO, DeletionRecordSerializer}
import play.api.{Configuration, Logger}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}
import slick.jdbc.PostgresProfile
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DeletionRecordController @Inject()(cc:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                         override implicit val config: Configuration,
                                         dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi)
  extends AbstractController(cc) with Security with DeletionRecordSerializer {
  override val logger = Logger(getClass)

  implicit val cache = cacheImpl
  implicit val db = dbConfigProvider.get[PostgresProfile].db

  def deleted(startAt:Int, limit: Int) = IsAdminAsync {uid=>{request=>
    DeletionRecordDAO.getRecords(startAt, limit).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","result"->results))
      case Failure(error)=>
        logger.error("Could not list deleted projects: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

  def record(id:Int) = IsAdminAsync {uid=>{request=>
    DeletionRecordDAO.getRecord(id).map({
      case Success(result)=>Ok(Json.obj("status"->"ok","result"->result))
      case Failure(error)=>
        logger.error("Could not get deleted project data: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}
}