package controllers

import akka.actor.ActorRef
import auth.BearerTokenAuth
import helpers.AllowCORSFunctions
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.HttpEntity
import play.api.libs.json.{JsError, JsResult, JsValue, Json}
import play.api.mvc.{ControllerComponents, Request, ResponseHeader, Result}
import services.{CommissionStatusPropagator, CreateOperation, UpdateOperation}
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import javax.inject._
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class PlutoCommissionController @Inject()(projectEntryController: ProjectEntryController,
                                          override val controllerComponents:ControllerComponents,
                                          override val bearerTokenAuth:BearerTokenAuth,
                                          dbConfigProvider:DatabaseConfigProvider,
                                          cacheImpl:SyncCacheApi,
                                          override implicit val config:Configuration,
                                         @Named("commission-status-propagator") commissionStatusPropagator:ActorRef,
                                         @Named("rabbitmq-propagator") rabbitMqPropagator:ActorRef)
  extends GenericDatabaseObjectControllerWithFilter[PlutoCommission,PlutoCommissionFilterTerms]
    with PlutoCommissionSerializer with PlutoCommissionFilterTermsSerializer {

    implicit val db = dbConfigProvider.get[PostgresProfile].db
    implicit val cache:SyncCacheApi = cacheImpl

    object SortDirection extends Enumeration {
      val desc, asc = Value
    }

    def withRequiredSort(query: =>Query[PlutoCommissionRow, PlutoCommission, Seq], sort:String, sortDirection:SortDirection.Value):Query[PlutoCommissionRow, PlutoCommission, Seq] = {
      import EntryStatusMapper._
      (sort, sortDirection) match {
        case ("created", SortDirection.desc) => query.sortBy(_.created.desc)
        case ("created", SortDirection.asc) => query.sortBy(_.created.asc)
        case ("title", SortDirection.desc) => query.sortBy(_.title.desc)
        case ("title", SortDirection.asc) => query.sortBy(_.title.asc)
        case ("workingGroupId", SortDirection.desc) => query.sortBy(_.workingGroup.desc)
        case ("workingGroupId", SortDirection.asc) => query.sortBy(_.workingGroup.asc)
        case ("status", SortDirection.desc) => query.sortBy(_.status.desc)
        case ("status", SortDirection.asc) => query.sortBy(_.status.asc)
        case ("owner", SortDirection.desc) => query.sortBy(_.owner.desc)
        case ("owner", SortDirection.asc) => query.sortBy(_.owner.asc)
        case ("projectCount", SortDirection.desc) => query.sortBy(_.created.desc)
        case ("projectCount", SortDirection.asc) => query.sortBy(_.created.asc)
        case _ =>
          logger.warn(s"Sort field $sort was not recognised, ignoring")
          query
      }
    }

    private def getSortDirection(directionString:String):Option[SortDirection.Value] = Try { SortDirection.withName(directionString) }.toOption

    def listFilteredAndSorted(startAt:Int, limit:Int, sort: String, sortDirection: String) = IsAuthenticatedAsync(parse.json) {uid=>{request=>
      this.validateFilterParams(request).fold(
        errors => {
          logger.error(s"errors parsing content: $errors")
          Future(BadRequest(Json.obj("status"->"error","detail"->JsError.toJson(errors))))
        },
        filterTerms => {
          this.selectFilteredAndSorted(startAt, limit, filterTerms, sort, getSortDirection(sortDirection).getOrElse(SortDirection.desc)).map({
            case Success((count,result))=>Ok(Json.obj("status" -> "ok","count"->count,"result"->this.jstranslate(result)))
            case Failure(error)=>
              logger.error(error.toString)
              InternalServerError(Json.obj("status"->"error", "detail"->error.toString))
          }
          )
        }
      )
    }}

    def selectall(startAt: Int, limit: Int): Future[Try[(Int, Seq[PlutoCommission])]] = {

      val results: Future[(Int, Seq[PlutoCommission])] = db.run(
        TableQuery[PlutoCommissionRow].length.result.zip(
          TableQuery[PlutoCommissionRow].sortBy(_.created.desc).drop(startAt).take(limit).result
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

      val results: Future[(Int, Seq[PlutoCommission])] = db.run(
        basequery.length.result.zip(
          basequery.sortBy(_.created.desc).drop(startAt).take(limit).result
        )
      )

      results.flatMap { result => {
        val count=result._1
        val commissions=result._2
        calculateProjectCount(commissions).map(counts => {
          commissions.foreach(commission => commission.projectCount = commission.id.flatMap(counts.get).orElse(Some(0)))
          Success((count,commissions))
        })
      }}.recover(Failure(_))
    }

  def selectFilteredAndSorted(startAt: Int, limit: Int, terms: PlutoCommissionFilterTerms, sort: String, sortDirection: SortDirection.Value): Future[Try[(Int, Seq[PlutoCommission])]] = {
    val basequery = terms.addFilterTerms {
      TableQuery[PlutoCommissionRow]
    }

    val results: Future[(Int, Seq[PlutoCommission])] = db.run(
      basequery.length.result.zip(
        withRequiredSort(basequery, sort, sortDirection).drop(startAt).take(limit).result
      )
    )

    results.flatMap { result => {
      val count=result._1
      val commissions=result._2
      calculateProjectCount(commissions).map(counts => {
        commissions.foreach(commission => commission.projectCount = commission.id.flatMap(counts.get).orElse(Some(0)))
        Success((count,commissions))
      })
    }}.recover(Failure(_))
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

    override def insert(entry: PlutoCommission, uid: String): Future[Try[Int]] = {
      val correctedEntry = entry.copy(owner = entry.owner.toLowerCase)
      db.run(
        (TableQuery[PlutoCommissionRow] returning TableQuery[PlutoCommissionRow].map(_.id) += correctedEntry).asTry)
        .map(id => {
          sendToRabbitMq(CreateOperation(), id, rabbitMqPropagator)
          id
        })
    }

    override def deleteid(requestedId: Int):Future[Try[Int]] = throw new RuntimeException("This is not supported")

    override def dbupdate(itemId: Int, entry:PlutoCommission):Future[Try[Int]] = {
      val newRecord = entry.id match {
        case Some(_)=>entry
        case None=>entry.copy(id=Some(itemId))
      }

      db.run(TableQuery[PlutoCommissionRow].filter(_.id===itemId).update(newRecord).asTry)
        .map(maybeRows=>{
          sendToRabbitMq(UpdateOperation(),newRecord,rabbitMqPropagator)
          maybeRows
        })
    }

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

    private def updateStatusColumn(commissionId:Int, newValue:EntryStatus.Value) = {
        import EntryStatusMapper._

        db.run {
            val q = for {c <- TableQuery[PlutoCommissionRow] if c.id === commissionId} yield c.status
            q.update(newValue)
        }
    }

  override def updateByAnyone(id: Int) = IsAuthenticatedAsync(parse.json) { uid =>
    request =>
      internalUpdate(id, request)
  }

  def internalUpdate(id: Int, request: Request[JsValue]) =
    this.validate(request).fold(
      errors => Future(BadRequest(Json.obj("status" -> "error", "detail" -> JsError.toJson(errors)))),
      validRecord => {
        val updateResult = this.dbupdate(id, validRecord)
        updateResult.flatMap { rowsUpdated =>
            projectEntryController.updateStatusByCommissionId(Some(id), validRecord.status).map { statusUpdateRows =>
              if (statusUpdateRows >= 0) {
                Ok(Json.obj("status" -> "ok", "detail" -> "Record and associated projects updated", "id" -> id))
              } else {
                InternalServerError(Json.obj("status" -> "error", "detail" -> s"Project status update failed"))
              }
            }.recover {
              case ex: Exception => InternalServerError(Json.obj("status" -> "error", "detail" -> ex.getMessage))
            }
        }.recover {
          case ex: Exception => InternalServerError(Json.obj("status" -> "error", "detail" -> ex.getMessage))
        }
      }
    )

    def updateStatus(commissionId: Int) = IsAuthenticatedAsync(parse.json) {uid=> request=>
        import PlutoCommissionStatusUpdateRequestSerializer._
        request.body.validate[PlutoCommissionStatusUpdateRequest].fold(
            invalidErrs=>
                Future(BadRequest(Json.obj("status"->"bad_request","detail"->JsError.toJson(invalidErrs)))),
            requiredUpdate=>
                updateStatusColumn(commissionId, requiredUpdate.status).map(rowsUpdated=>{
                    if(rowsUpdated==0){
                        NotFound(Json.obj("status"->"not_found","detail"->s"No commission with id $commissionId"))
                    } else {
                        if(rowsUpdated>1) logger.error(s"Status update request for commission $commissionId returned $rowsUpdated rows updated, expected 1! This indicates a database problem")
                        commissionStatusPropagator ! CommissionStatusPropagator.CommissionStatusUpdate(commissionId, requiredUpdate.status)
                        sendToRabbitMq(UpdateOperation(), commissionId, rabbitMqPropagator).foreach(_ => ())
                        Ok(Json.obj("status"->"ok","detail"->"commission status updated"))
                    }
                }).recover({
                    case err:Throwable=>
                    logger.error(s"Could not update status of commission $commissionId to ${requiredUpdate.status}: ", err)
                    InternalServerError(Json.obj("status"->"db_error","detail"->"Database error, see logs for details"))
                })
        )
    }
  }
