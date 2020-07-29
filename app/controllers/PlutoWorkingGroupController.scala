package controllers
import akka.actor.ActorRef
import javax.inject._
import models.{PlutoWorkingGroup, PlutoWorkingGroupRow, PlutoWorkingGroupSerializer}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsResult, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import akka.pattern.ask
import auth.BearerTokenAuth
import services.{CreateOperation, UpdateOperation}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class PlutoWorkingGroupController @Inject() (override val controllerComponents:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                             dbConfigProvider:DatabaseConfigProvider, cacheImpl:SyncCacheApi,
                                             @Named("rabbitmq-propagator") implicit val rabbitMqPropagator:ActorRef)
  extends GenericDatabaseObjectController[PlutoWorkingGroup] with PlutoWorkingGroupSerializer {

  implicit val timeout:akka.util.Timeout = 55 seconds

  implicit val db = dbConfigProvider.get[PostgresProfile].db
  implicit val cache:SyncCacheApi = cacheImpl

  override def selectall(startAt: Int, limit: Int): Future[Try[Seq[PlutoWorkingGroup]]] = db.run(
    TableQuery[PlutoWorkingGroupRow].drop(startAt).take(limit).sortBy(_.name.asc.nullsLast).result.asTry
  )

  override def selectid(requestedId: Int): Future[Try[Seq[PlutoWorkingGroup]]] = db.run(
    TableQuery[PlutoWorkingGroupRow].filter(_.id===requestedId).sortBy(_.name.asc.nullsLast).result.asTry
  )

  override def insert(entry: PlutoWorkingGroup, uid: String): Future[Try[Int]] = db.run(
    (TableQuery[PlutoWorkingGroupRow] returning TableQuery[PlutoWorkingGroupRow].map(_.id) += entry).asTry)
    .map ({
        case Success(newEntryId)=>
          //sendToRabbitMq(CreateOperation, id, rabbitMqPropagator)
          sendToRabbitMq(CreateOperation, entry.copy(id=Some(newEntryId)), rabbitMqPropagator)
          Success(newEntryId)
        case err@Failure(_)=>err
    })

  override def deleteid(requestedId: Int):Future[Try[Int]] = db.run(
    TableQuery[PlutoWorkingGroupRow].filter(_.id === requestedId).delete.asTry
  )

  override def dbupdate(itemId: Int, entry:PlutoWorkingGroup):Future[Try[Int]] = {
    val newRecord = entry.id match {
      case Some(id)=>entry
      case None=>entry.copy(id=Some(itemId))
    }
    db.run(TableQuery[PlutoWorkingGroupRow].filter(_.id===itemId).update(newRecord).asTry)
      .map(rows => {
        sendToRabbitMq(UpdateOperation, itemId, rabbitMqPropagator)
        rows
      })
  }

  /*these are handled through implict translation*/
  override def jstranslate(result:Seq[PlutoWorkingGroup]):Json.JsValueWrapper = result
  override def jstranslate(result:PlutoWorkingGroup):Json.JsValueWrapper = result

  override def validate(request: Request[JsValue]): JsResult[PlutoWorkingGroup] = request.body.validate[PlutoWorkingGroup]

}
