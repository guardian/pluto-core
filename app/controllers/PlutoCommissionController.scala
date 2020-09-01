package controllers

import java.sql.Timestamp
import java.time.Instant
import java.util.Date

import akka.actor.ActorRef
import auth.BearerTokenAuth
import exceptions.{AlreadyExistsException, BadDataException}
import helpers.AllowCORSFunctions
import javax.inject._
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.{HttpEntity, Status}
import play.api.libs.json.{JsError, JsResult, JsValue, Json}
import play.api.mvc.{ControllerComponents, EssentialAction, Request, ResponseHeader, Result}
import services.{CommissionStatusPropagator, CreateOperation, UpdateOperation}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class PlutoCommissionController @Inject()(override val controllerComponents:ControllerComponents,
                                          override val bearerTokenAuth:BearerTokenAuth,
                                          dbConfigProvider:DatabaseConfigProvider,
                                          cacheImpl:SyncCacheApi,
                                          config:Configuration,
                                         @Named("commission-status-propagator") commissionStatusPropagator:ActorRef,
                                         @Named("rabbitmq-propagator") rabbitMqPropagator:ActorRef)
  extends GenericDatabaseObjectControllerWithFilter[PlutoCommission,PlutoCommissionFilterTerms]
    with PlutoCommissionSerializer with PlutoCommissionFilterTermsSerializer {

    implicit val db = dbConfigProvider.get[PostgresProfile].db
    implicit val cache:SyncCacheApi = cacheImpl

    override def selectall(startAt: Int, limit: Int): Future[Try[(Int, Seq[PlutoCommission])]] = {
      val results: Future[(Int, Seq[PlutoCommission])] = db.run(
        TableQuery[PlutoCommissionRow].length.result.zip(
          TableQuery[PlutoCommissionRow].drop(startAt).take(limit).sortBy(_.title.asc).result
        )
      )

      results.flatMap { result => {
        val count=result._1
        val commissions=result._2 //wouldn't implicitly unpack for some reason!
        calculateProjectCount(commissions).map(counts => {
          commissions.foreach(commission => commission.projectCount = commission.id.flatMap(counts.get).orElse(Some(0)))
          Success((count,commissions))
        })
      }}.recover(Failure(_))
    }

    override def selectid(requestedId: Int): Future[Try[Seq[PlutoCommission]]] = db.run(
      TableQuery[PlutoCommissionRow].filter(_.id===requestedId).result.asTry
    )

    override def selectFiltered(startAt: Int, limit: Int, terms: PlutoCommissionFilterTerms): Future[Try[(Int, Seq[PlutoCommission])]] = {
      val basequery = terms.addFilterTerms {
        TableQuery[PlutoCommissionRow]
      }

      db.run(
        basequery.length.result.zip(
          basequery.drop(startAt).take(limit).sortBy(_.title.asc.nullsLast).result
        )
      ).map(Success(_)).recover(Failure(_))
    }

    def calculateProjectCount(entries: Seq[PlutoCommission]): Future[ListMap[Int, Int]] = {
      val commissionIds = entries.flatMap(entry => entry.id)
      db.run (
        TableQuery[ProjectEntryRow].filter(_.commission inSet commissionIds)
          .groupBy(_.commission)
          .map({ case (commissionId, group) => (commissionId, group.length) })
          .result
          .map(rows => rows.collect { case (Some(commissionId), count) => (commissionId, count) } )
          .map(ListMap.from)
      )
    }

    override def insert(entry: PlutoCommission, uid: String): Future[Try[Int]] = db.run(
      (TableQuery[PlutoCommissionRow] returning TableQuery[PlutoCommissionRow].map(_.id) +=
        entry.copy(updated = Timestamp.from(Instant.now())))
        .asTry)
      .map(id => {
        sendToRabbitMq(CreateOperation, id, rabbitMqPropagator)
        id
      })

    override def deleteid(requestedId: Int):Future[Try[Int]] = throw new RuntimeException("This is not supported")

    override def dbupdate(itemId: Int, entry:PlutoCommission):Future[Try[Int]] = throw new RuntimeException("This is not supported")

    /*these are handled through implict translation*/
    override def jstranslate(result:Seq[PlutoCommission]):Json.JsValueWrapper = result
    override def jstranslate(result:PlutoCommission):Json.JsValueWrapper = result

    override def validate(request: Request[JsValue]): JsResult[PlutoCommission] = request.body.validate[PlutoCommission]

    override def validateFilterParams(request: Request[JsValue]): JsResult[PlutoCommissionFilterTerms] = request.body.validate[PlutoCommissionFilterTerms]

    /**
      * respond to CORS options requests for login from vaultdoor
      * see https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request
      * @return
      */
    def listOptions = Action { request=>
        AllowCORSFunctions.checkCorsOrigins(config, request) match {
            case Right(allowedOrigin) =>
                val returnHeaders = Map(
                    "Access-Control-Allow-Methods" -> "PUT, OPTIONS",
                    "Access-Control-Allow-Origin" -> allowedOrigin,
                    "Access-Control-Allow-Headers" -> "content-type",
                )
                Result(
                    ResponseHeader(204, returnHeaders),
                    HttpEntity.NoEntity
                )
            case Left(other) =>
                logger.warn(s"Invalid CORS preflight request for authentication: $other")
                Forbidden("")
        }
    }

    private def updateStatusColumn(commissionId:Int, newValue:EntryStatus.Value, updated: Timestamp) = {
        import EntryStatusMapper._

        db.run {
            val q = for {c <- TableQuery[PlutoCommissionRow] if c.id === commissionId && c.updated === updated}
              yield (c.status, c.updated)
            q.update(newValue, Timestamp.from(Instant.now()))
        }
    }

    def exists(commissionId: Int): Future[Boolean] = db.run({
      TableQuery[PlutoCommissionRow].filter(_.id === commissionId).length.result
    }).map(_ > 0)

    def updateStatus(commissionId: Int) = IsAuthenticatedAsync(parse.json) {uid=> request=>
        import PlutoCommissionStatusUpdateRequestSerializer._
        request.body.validate[PlutoCommissionStatusUpdateRequest].fold(
            invalidErrs=>
                Future(BadRequest(Json.obj("status"->"bad_request","detail"->JsError.toJson(invalidErrs)))),
            requiredUpdate=>
                updateStatusColumn(commissionId, requiredUpdate.status, requiredUpdate.updated).flatMap(rowsUpdated=>{
                    if(rowsUpdated==0){
                        exists(commissionId).map({
                          case true => Conflict(Json.obj("status" -> "conflict", "detail" -> s"ETag did not match for $commissionId"))
                          case false => NotFound(Json.obj("status" -> "not_found", "detail" -> s"No commission with id $commissionId"))
                        })
                    } else {
                        if(rowsUpdated>1) logger.error(s"Status update request for commission $commissionId returned $rowsUpdated rows updated, expected 1! This indicates a database problem")
                        commissionStatusPropagator ! CommissionStatusPropagator.CommissionStatusUpdate(commissionId, requiredUpdate.status)
                        sendToRabbitMq(UpdateOperation, commissionId, rabbitMqPropagator).foreach(_ => ())
                        Future(Ok(Json.obj("status"->"ok","detail"->"commission status updated")))
                    }
                }).recover({
                    case err:Throwable=>
                    logger.error(s"Could not update status of commission $commissionId to ${requiredUpdate.status}: ", err)
                    InternalServerError(Json.obj("status"->"db_error","detail"->"Database error, see logs for details"))
                })
        )
    }
  }
