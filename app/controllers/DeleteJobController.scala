package controllers

import javax.inject._
import auth.{BearerTokenAuth, Security}
import models.{DeleteJobDAO, DeleteJobSerializer}
import play.api.{Configuration, Logger}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}
import slick.jdbc.PostgresProfile
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DeleteJobController @Inject() (cc:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                          override implicit val config: Configuration,
                                          dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi)
  extends AbstractController(cc) with Security with DeleteJobSerializer {
  override val logger = Logger(getClass)

  implicit val cache = cacheImpl
  implicit val db = dbConfigProvider.get[PostgresProfile].db

  def deleted(startAt:Int, limit: Int) = IsAdminAsync {uid=>{request=>
    DeleteJobDAO.getJobs(startAt, limit).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","result"->results))
      case Failure(error)=>
        logger.error("Could not list deleted projects: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}
}