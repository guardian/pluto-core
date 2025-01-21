package controllers

import auth.{BearerTokenAuth, Security}
import models.{MissingAssetFileEntryDAO, MissingAssetFileEntrySerializer}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}
import play.api.{Configuration, Logger}
import slick.jdbc.PostgresProfile
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

@Singleton
class MissingFilesController @Inject()(cc:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                       override implicit val config: Configuration,
                                       dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi, missingAssetFileEntryDAO: MissingAssetFileEntryDAO)
  extends AbstractController(cc) with Security with MissingAssetFileEntrySerializer {
  override val logger = Logger(getClass)

  implicit val cache = cacheImpl
  implicit val db = dbConfigProvider.get[PostgresProfile].db

  def missing(project:Int) = IsAuthenticatedAsync {uid=>{request=>
    MissingAssetFileEntryDAO.getRecords(project).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","results"->results))
      case Failure(error)=>
        logger.error("Could not list missing files: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

  def removeWarning(project:Int) = IsAuthenticatedAsync {uid=>{request=>
    MissingAssetFileEntryDAO.getRecords(project).map({
      case Success(results)=>
        results.map(record =>
          missingAssetFileEntryDAO.deleteRecord(record)
        )
        Ok(Json.obj("status"->"ok"))
      case Failure(error)=>
        logger.error("Could not delete missing file records: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}
}