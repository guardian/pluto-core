package controllers

import javax.inject._
import auth.{BearerTokenAuth, Security}
import models.{ItemDeleteDataDAO, ItemDeleteDataSerializer}
import play.api.{Configuration, Logger}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}
import slick.jdbc.PostgresProfile
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class ItemDeleteDataController @Inject() (cc:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                    override implicit val config: Configuration,
                                    dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi)
  extends AbstractController(cc) with Security with ItemDeleteDataSerializer {
  override val logger = Logger(getClass)

  implicit val cache = cacheImpl
  implicit val db = dbConfigProvider.get[PostgresProfile].db


  def listForProject(projectId: Int) = IsAdminAsync {uid=>{request=>
    ItemDeleteDataDAO.itemsForProject(projectId).map({
      case Success(results)=>Ok(Json.obj("status"->"ok","results"->results))
      case Failure(error)=>
        logger.error("Could not list items that will not be deleted: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}
}

