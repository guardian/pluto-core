package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import auth.{BearerTokenAuth, Security}
import javax.inject.{Inject, Singleton}
import models.datamigration.{CommissionUpdateRequest, ProjectsUpdateRequest}
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import services.DataMigration
import services.migrationcomponents.VSUserCache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

@Singleton
class DataMigrationController @Inject()
  (override val controllerComponents:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth, config:Configuration, override val cache:SyncCacheApi)
  (implicit dbProvider:DatabaseConfigProvider, system:ActorSystem, mat:Materializer)
  extends AbstractController(controllerComponents) with Security {

  def updateNewCommissionFields = IsAdminAsync(parse.json) { user=> request=>
    import CommissionUpdateRequest._  //import the codec information for unmarshalling
    logger.info(s"Received migration request from $user")
    request.body.validate[CommissionUpdateRequest].fold(
      errors=> Future(BadRequest(Json.obj("status"->"invalid_request","detail"->errors.toString()))),
      valid=> {
        val cacheFile = config.getOptional[String]("datamigration.vidispineUsersFile").getOrElse("filepath-not-set")
        VSUserCache.initialize(cacheFile) match {
          case Success(vsUserCache)=>
            logger.info(s"Loaded in ${vsUserCache.size} cached Vidispine user records")
            val m = new DataMigration(valid.vsBaseUri, valid.vsUser, valid.vsPasswd, valid.vsSiteId, vsUserCache)
            m.migrateCommissionsData.onComplete({
              case Success(recordCount)=>
                logger.info(s"Migration run successfully completed on $recordCount commissions")
              case Failure(err)=>
                logger.error(s"Migration run failed: ", err)
            })
            Future(Ok(Json.obj("status"->"ok","detail"->"Migration operation started, see server logs for details")))
          case Failure(err)=>
            logger.error(s"Could not load VS user cache from $cacheFile: ", err)
            Future(InternalServerError(Json.obj("status"->"error","detail"->"Could not load VS user cache, see server logs for details")))
        }
      }
    )
  }

  def updateProjects = IsAdminAsync(parse.json) { user=> request=>
    import ProjectsUpdateRequest._
    logger.info(s"Received projects migration request from $user")
    request.body.validate[ProjectsUpdateRequest].fold(
      errors=> Future(BadRequest(Json.obj("status"->"invalid_request","detail"->errors.toString()))),
      valid=>{
        val cacheFile = config.getOptional[String]("datamigration.vidispineUsersFile").getOrElse("filepath-not-set")
        VSUserCache.initialize(cacheFile) match {
          case Success(vsUserCache) =>
            logger.info(s"Loaded in ${vsUserCache.size} cached Vidispine user records")
            val m = new DataMigration(valid.vsBaseUri, valid.vsUser, valid.vsPasswd, valid.vsSiteId, vsUserCache)
            m.migrateProjectsData(valid.projectTypeId).onComplete({
              case Success(recordCount) =>
                logger.info(s"Migration run successfully completed on $recordCount projects")
              case Failure(err) =>
                logger.error("Migration run failed: ", err)
            })
            Future(Ok(Json.obj("status" -> "ok", "detail" -> "Migration operation started, see server logs for details")))
          case Failure(err) =>
            logger.error(s"Could not load VS user cache from $cacheFile: ", err)
            Future(InternalServerError(Json.obj("status" -> "error", "detail" -> "Could not load VS user cache, see server logs for details")))
      }
    })
  }
}
