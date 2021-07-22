package controllers

import auth.{BearerTokenAuth, Security}
import models.{AuditAction, AuditLog, AuditLogRow, AuditLogSerializer}
import play.api.{Configuration, Logger}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import javax.inject.Inject
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

class AuditController @Inject() (override val controllerComponents:ControllerComponents,
                                 override val bearerTokenAuth:BearerTokenAuth,
                                 override implicit val config: Configuration,
                                 dbConfigProvider: DatabaseConfigProvider,
                                 override val cache:SyncCacheApi) extends AbstractController(controllerComponents) with Security with AuditLogSerializer {

  override val logger = Logger(getClass)

  private val db = dbConfigProvider.get[PostgresProfile].db

  def myLastActions(actionType:Option[String],startAt:Int=0,limit:Int=100) = IsAuthenticatedAsync { uid=> request=>
    val maybeType = actionType.flatMap(stringVal=>Try { AuditAction.withName(stringVal)}.toOption)

    val baseQuery = TableQuery[AuditLogRow].filter(_.username===uid.toLowerCase)
    val query = maybeType match {
      case Some(actionType)=>baseQuery.filter(_.actionType===actionType)
      case None=>baseQuery
    }

    val dataFuture = db.run(
      query.take(limit).drop(startAt).sortBy(_.at.desc).result
    )
    val totalCountFuture = db.run(
      query.size.result
    )

    Future.sequence(Seq(dataFuture, totalCountFuture)).map(results=>{
      val rows = results.head.asInstanceOf[Seq[AuditLog]]
      val count = results(1).asInstanceOf[Int]

      Ok(
        Json.obj("status"->"ok", "count"->count, "result"->rows)
      )
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not get last actions for user $uid: ${err.getMessage}", err)
        InternalServerError(Json.obj("status"->"error","detail"->err.getMessage))
    })
  }


}
