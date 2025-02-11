package controllers

import akka.actor.ActorRef
import akka.pattern.ask
import auth.{BearerTokenAuth, Security}
import exceptions.RecordNotFoundException
import helpers.{AllowCORSFunctions, S3Helper}
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.HttpEntity
import play.api.inject.Injector
import play.api.libs.json.{JsError, JsResult, JsValue, Json, Writes}
import play.api.mvc._
import services.RabbitMqDeliverable.DeliverableEvent
import services.RabbitMqPropagator.ChangeEvent
import services.RabbitMqSend.FixEvent
import services.actors.Auditor
import services.actors.creation.GenericCreationActor.{NewProjectRequest, ProjectCreateTransientData}
import services.actors.creation.{CreationMessage, GenericCreationActor}
import services.{CreateOperation, UpdateOperation}
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.io.File
import java.nio.file.Paths
import java.time.ZonedDateTime
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import vidispine.{VSOnlineOutputMessage, VidispineCommunicator, VidispineConfig}
import mes.OnlineOutputMessage
import mess.InternalOnlineOutputMessage
import akka.actor.ActorSystem
import akka.stream.Materializer
import java.util.concurrent.{Executors, TimeUnit}
import de.geekonaut.slickmdc.MdcExecutionContext
import services.RabbitMqSAN.SANEvent
import com.om.mxs.client.japi.Vault
import akka.stream.scaladsl.{Keep, Sink, Source}
import mxscopy.streamcomponents.OMFastContentSearchSource
import mxscopy.models.ObjectMatrixEntry
import matrixstore.MatrixStoreEnvironmentConfigProvider
import mxscopy.MXSConnectionBuilderImpl
import mxscopy.MXSConnectionBuilder
import services.RabbitMqMatrix.MatrixEvent
import java.util.Date
import java.sql.Timestamp
import helpers.StorageHelper

@Singleton
class ProjectEntryController @Inject() (@Named("project-creation-actor") projectCreationActor:ActorRef,
                                        override implicit val config: Configuration,
                                        dbConfigProvider: DatabaseConfigProvider,
                                        cacheImpl:SyncCacheApi,
                                        @Named("rabbitmq-propagator") implicit val rabbitMqPropagator:ActorRef,
                                        @Named("rabbitmq-send") rabbitMqSend:ActorRef,
                                        @Named("rabbitmq-deliverable") rabbitMqDeliverable:ActorRef,
                                        @Named("rabbitmq-san") rabbitMqSAN:ActorRef,
                                        @Named("rabbitmq-matrix") rabbitMqMatrix:ActorRef,
                                        @Named("auditor") auditor:ActorRef,
                                        override val controllerComponents:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                                        storageHelper:StorageHelper)
                                       (implicit fileEntryDAO:FileEntryDAO, assetFolderFileEntryDAO:AssetFolderFileEntryDAO, injector: Injector, mat: Materializer)
  extends GenericDatabaseObjectControllerWithFilter[ProjectEntry,ProjectEntryFilterTerms]
    with ProjectEntrySerializer with ProjectRequestSerializer with ProjectEntryFilterTermsSerializer
    with UpdateTitleRequestSerializer with FileEntrySerializer with AssetFolderFileEntrySerializer
    with Security
{
  override implicit val cache:SyncCacheApi = cacheImpl

  val dbConfig = dbConfigProvider.get[PostgresProfile]
  implicit val implicitConfig = config

  override def deleteid(requestedId: Int) = dbConfig.db.run(
    TableQuery[ProjectEntryRow].filter(_.id === requestedId).delete.asTry
  )

  override def selectid(requestedId: Int):Future[Try[Seq[ProjectEntry]]] = dbConfig.db.run(
    TableQuery[ProjectEntryRow].filter(_.id === requestedId).result.asTry
  )

  protected def selectVsid(vsid: String):Future[Try[Seq[ProjectEntry]]] = dbConfig.db.run(
    TableQuery[ProjectEntryRow].filter(_.vidispineProjectId === vsid).result.asTry
  )

  override def dbupdate(itemId:Int, entry:ProjectEntry) :Future[Try[Int]] = {
    logger.info(s"Updating project id ${itemId} and status ${entry.status}")
    val newRecord = entry.id match {
      case Some(id)=>entry
      case None=>entry.copy(id=Some(itemId))
    }

    dbConfig.db.run(TableQuery[ProjectEntryRow].filter(_.id===itemId).update(newRecord).asTry)
      .map(rows => {
        sendToRabbitMq(UpdateOperation(), itemId, rabbitMqPropagator)
        rows
      })
  }

  override def notifyRequested[T](requestedId: Int, username: String, request: Request[T]): Unit = {
    request.headers.get("User-Agent") match {
      case None=>
      case Some(userAgent)=>
        if(userAgent.contains("Mozilla")) { //we are only interested in logging requests that came from a browser, otherwise the log would fill with the automated requests
          auditor ! Auditor.LogEvent(
            username,
            AuditAction.ViewProjectPage,
            requestedId,
            ZonedDateTime.now(),
            Some(userAgent)
          )
        }
    }
  }

  /**
    * Fully generic container method to process an update request
    * @param requestedId an ID to identify what should be updated, this is passed to `selector`
    * @param selector a function that takes `requestedId` and returns a Future, containing a Try, containing a sequence of ProjectEntries
    *                 that correspond to the provided ID
    * @param f a function to perform the actual update.  This is only called if selector returns a valid sequence of at least one ProjectEntry,
    *          and is called for each ProjectEntry in the sequence that `selector` returns.
    *          It should return a Future containing a Try containing the number of rows updated.
    * @tparam T the data type of `requestedId`
    * @return A Future containing a sequnce of results for each invokation of f. with either a Failure indicating why
    *         `f` was not called, or a Success with the result of `f`
    */
  def doUpdateGenericSelector[T](requestedId:T, selector:T=>Future[Try[Seq[ProjectEntry]]])(f: ProjectEntry=>Future[Try[Int]]):Future[Seq[Try[Int]]] = selector(requestedId).flatMap({
    case Success(someSeq)=>
        if(someSeq.isEmpty)
          Future(Seq(Failure(new RecordNotFoundException(s"No records found for id $requestedId"))))
        else
          Future.sequence(someSeq.map(f))
    case Failure(error)=>Future(Seq(Failure(error)))
  })

  /**
    * Most updates are done with the primary key, this is a convenience method to call [[doUpdateGenericSelector]]
    * with the appropriate selector and data type for the primary key
    * @param requestedId integer primary key value identifying what should be updated
    * @param f a function to perform the actual update. See [[doUpdateGenericSelector]] for details
    * @return see [[doUpdateGenericSelector]]
    */
  def doUpdateGeneric(requestedId:Int)(f: ProjectEntry=>Future[Try[Int]]) = doUpdateGenericSelector[Int](requestedId,selectid)(f)

  /**
    * Update the vidisipineId on a data record
    * @param requestedId primary key of the record to update
    * @param newVsid new vidispine ID. Note that this is an Option[String] as the id can be null
    * @return a Future containing a Try containing an Int describing the number of records updated
    */
  def doUpdateVsid(requestedId:Int, newVsid:Option[String]):Future[Seq[Try[Int]]] = doUpdateGeneric(requestedId){ record=>
    val updatedProjectEntry = record.copy (vidispineProjectId = newVsid)
    dbConfig.db.run (
      TableQuery[ProjectEntryRow].filter (_.id === requestedId).update (updatedProjectEntry).asTry
    )
    .map(rows => {
      sendToRabbitMq(UpdateOperation(), requestedId, rabbitMqPropagator)
      rows
    })
  }

  /**
    * generic code for an endpoint to update the title
    * @param requestedId identifier of the record to update
    * @param updater function to perform the actual update.  This is passed requestedId and a string to change the title to
    * @tparam T type of @reqestedId
    * @return a Future[Response]
    */
  def genericUpdateTitleEndpoint[T](requestedId:T)(updater:(T,String)=>Future[Seq[Try[Int]]]) = IsAuthenticatedAsync(parse.json) {uid=>{request=>
    request.body.validate[UpdateTitleRequest].fold(
      errors=>
        Future(BadRequest(Json.obj("status"->"error", "detail"->JsError.toJson(errors)))),
      updateTitleRequest=> {
        val results = updater(requestedId, updateTitleRequest.newTitle).map(_.partition(_.isSuccess))

        results.map(resultTuple => {
          val failures = resultTuple._2
          val successes = resultTuple._1

          if (failures.isEmpty)
            Ok(Json.obj("status" -> "ok", "detail" -> s"${successes.length} record(s) updated"))
          else
            genericHandleFailures(failures, requestedId)
        })
      }
    )
  }}

  /**
    * endpoint to update project title field of record based on primary key
    * @param requestedId
    * @return
    */
  def updateTitle(requestedId:Int) = genericUpdateTitleEndpoint[Int](requestedId) { (requestedId,newTitle)=>
    doUpdateGeneric(requestedId) {record=>
      val updatedProjectEntry = record.copy (projectTitle = newTitle)
      dbConfig.db.run (
        TableQuery[ProjectEntryRow].filter (_.id === requestedId).update (updatedProjectEntry).asTry
      )
      .map(rows => {
        sendToRabbitMq(UpdateOperation(), requestedId, rabbitMqPropagator)
        rows
      })
    }
  }

  /**
    * endoint to update project title field of record based on vidispine id
    * @param vsid
    * @return
    */
  def updateTitleByVsid(vsid:String) = genericUpdateTitleEndpoint[String](vsid) { (vsid,newTitle)=>
    doUpdateGenericSelector[String](vsid,selectVsid) { record=> //this lambda function is called once for each record
      val updatedProjectEntry = record.copy(projectTitle = newTitle)
      dbConfig.db.run(
        TableQuery[ProjectEntryRow].filter(_.id === record.id.get).update(updatedProjectEntry).asTry
      )
        .map(rows => {
          sendToRabbitMq(UpdateOperation(), record, rabbitMqPropagator)
          rows
        })
    }
  }


  def genericHandleFailures[T](failures:Seq[Try[Int]], requestedId:T) = {
    val notFoundFailures = failures.filter(_.failed.get.getClass==classOf[RecordNotFoundException])

    if(notFoundFailures.length==failures.length) {
      NotFound(Json.obj("status" -> "error", "detail" -> s"no records found for $requestedId"))
    } else {
      InternalServerError(Json.obj("status" -> "error", "detail" -> failures.map(_.failed.get.toString)))
    }
  }

  def filesList(requestedId: Int, allVersions: Boolean) = IsAuthenticatedAsync {uid=>{request=>
    implicit val db = dbConfig.db

    selectid(requestedId).flatMap({
      case Failure(error)=>
        logger.error(s"could not list files from project ${requestedId}",error)
        Future(InternalServerError(Json.obj("status"->"error","detail"->error.toString)))
      case Success(someSeq)=>
        someSeq.headOption match { //matching on pk, so can only be one result
          case Some(projectEntry)=>
            projectEntry.associatedFiles(allVersions).map(fileList=>Ok(Json.obj("status"->"ok","files"->fileList)))
          case None=>
            Future(NotFound(Json.obj("status"->"error","detail"->s"project $requestedId not found")))
        }
    })
  }}

  def withRequiredSort(query: =>Query[ProjectEntryRow, ProjectEntry, Seq], sort:String, sortDirection:SortDirection.Value):Query[ProjectEntryRow, ProjectEntry, Seq] = {
    import EntryStatusMapper._
    (sort, sortDirection) match {
      case ("created", SortDirection.desc) => query.sortBy(_.created.desc)
      case ("created", SortDirection.asc) => query.sortBy(_.created.asc)
      case ("title", SortDirection.desc) => query.sortBy(_.projectTitle.desc)
      case ("title", SortDirection.asc) => query.sortBy(_.projectTitle.asc)
      case ("workingGroupId", SortDirection.desc) => query.sortBy(_.workingGroup.desc)
      case ("workingGroupId", SortDirection.asc) => query.sortBy(_.workingGroup.asc)
      case ("status", SortDirection.desc) => query.sortBy(_.status.desc)
      case ("status", SortDirection.asc) => query.sortBy(_.status.asc)
      case ("user", SortDirection.desc) => query.sortBy(_.user.desc)
      case ("user", SortDirection.asc) => query.sortBy(_.user.asc)
      case ("commissionId", SortDirection.desc) => query.sortBy(_.commission.desc)
      case ("commissionId", SortDirection.asc) => query.sortBy(_.commission.asc)
      case _ =>
        logger.warn(s"Sort field $sort was not recognised, ignoring")
        query
    }
  }

  def listFilteredAndSorted(startAt:Int, limit:Int, sort: String, sortDirection: String) = IsAuthenticatedAsync(parse.json) {uid=>{request=>
    this.validateFilterParams(request).fold(
      errors => {
        logger.error(s"Errors parsing content: $errors")
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

  def selectall(startAt:Int, limit:Int) = dbConfig.db.run(
    TableQuery[ProjectEntryRow].length.result.zip(
      TableQuery[ProjectEntryRow].sortBy(_.created.desc).drop(startAt).take(limit).result
    )
  ).map(Success(_)).recover(Failure(_))

  override def selectFiltered(startAt: Int, limit: Int, terms: ProjectEntryFilterTerms): Future[Try[(Int, Seq[ProjectEntry])]] = {
    val basequery = terms.addFilterTerms {
      TableQuery[ProjectEntryRow]
    }

    dbConfig.db.run(
      basequery.length.result.zip(
        basequery.sortBy(_.created.desc).drop(startAt).take(limit).result
      )
    ).map(Success(_)).recover(Failure(_))
  }

  def selectFilteredAndSorted(startAt: Int, limit: Int, terms: ProjectEntryFilterTerms, sort: String, sortDirection: SortDirection.Value): Future[Try[(Int, Seq[ProjectEntry])]] = {
    val basequery = terms.addFilterTerms {
      TableQuery[ProjectEntryRow]
    }

    dbConfig.db.run(
      basequery.length.result.zip(
        withRequiredSort(basequery, sort, sortDirection).drop(startAt).take(limit).result
      )
    ).map(Success(_)).recover(Failure(_))
  }

  override def jstranslate(result: Seq[ProjectEntry]):Json.JsValueWrapper = result
  override def jstranslate(result: ProjectEntry):Json.JsValueWrapper = result  //implicit translation should handle this

  /*this is pointless because of the override of [[create]] below, so it should not get called,
   but is needed to conform to the [[GenericDatabaseObjectController]] protocol*/
  override def insert(entry: ProjectEntry,uid:String) = Future(Failure(new RuntimeException("ProjectEntryController::insert should not have been called")))

  override def validate(request:Request[JsValue]) = request.body.validate[ProjectEntry]

  override def validateFilterParams(request: Request[JsValue]): JsResult[ProjectEntryFilterTerms] = request.body.validate[ProjectEntryFilterTerms]

  private val vsidValidator = "^\\w{2}-\\d+$".r

  def getByVsid(vsid:String) = IsAuthenticatedAsync { uid=> request=>
    if(vsidValidator.matches(vsid)) {
      dbConfig.db.run {
        TableQuery[ProjectEntryRow].filter(_.vidispineProjectId===vsid).sortBy(_.created.desc).result
      }.map(_.headOption match {
        case Some(projectRecord)=>
          Ok(Json.obj("status"->"ok","result"->projectRecord))
        case None=>
          NotFound(Json.obj("status"->"notfound","detail"->"No project with that VSID"))
      }).recover({
        case err:Throwable=>
          logger.error(s"Could not look up VSID $vsid: ", err)
          InternalServerError(Json.obj("status"->"error","detail"->"Database error looking up record, see server logs"))
      })
    } else {
      Future(BadRequest(Json.obj("status"->"bad_request","detail"->"Malformed vidispine ID")))
    }
  }

  def createFromFullRequest(rq:ProjectRequestFull) = {
    implicit val timeout:akka.util.Timeout = 60.seconds

    val initialData = ProjectCreateTransientData(None, None, None)

    val msg = NewProjectRequest(rq,None,initialData)
    (projectCreationActor ? msg).mapTo[CreationMessage].map({
      case GenericCreationActor.ProjectCreateSucceeded(succeededRequest, projectEntry)=>
        logger.info(s"Created new project: $projectEntry")
        sendToRabbitMq(CreateOperation(), projectEntry, rabbitMqPropagator)
        Ok(Json.obj("status"->"ok","detail"->"created project", "projectId"->projectEntry.id.get))
      case GenericCreationActor.ProjectCreateFailed(failedRequest, error)=>
        logger.error("Could not create new project", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }

  override def create = IsAuthenticatedAsync(parse.json) {uid=>{ request =>
    implicit val db = dbConfig.db

    request.body.validate[ProjectRequest].fold(
      errors=>
        Future(BadRequest(Json.obj("status"->"error","detail"->JsError.toJson(errors)))),
      projectRequest=> {
        val fullRequestFuture=projectRequest.hydrate
        fullRequestFuture.flatMap({
          case None=>
            Future(BadRequest(Json.obj("status"->"error","detail"->"Invalid template or storage ID")))
          case Some(rq)=>
            createFromFullRequest(rq)
        })
      })
  }}

  def getDistinctOwnersList:Future[Try[Seq[String]]] = {
    //work around distinctOn bug - https://github.com/slick/slick/issues/1712
    dbConfig.db.run(sql"""select distinct(s_user) from "ProjectEntry" where s_user not like '%|%'""".as[String].asTry)
  }

  def distinctOwners = IsAuthenticatedAsync {uid=>{request=>
    getDistinctOwnersList.map({
      case Success(ownerList)=>
        Ok(Json.obj("status"->"ok","result"->ownerList))
      case Failure(error)=>
        logger.error("Could not look up distinct project owners: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

  /**
    * respond to CORS options requests for login from vaultdoor
    * see https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request
    * @return
    */
  def searchOptions = Action { request=>
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

  def projectWasOpened(id: Int): EssentialAction = IsAuthenticatedAsync { uid=> request =>
    import models.EntryStatusMapper._

    def updateProject() = TableQuery[ProjectEntryRow]
      .filter(_.id === id)
      .filter(_.status === EntryStatus.New)
      .map(_.status)
      .update(EntryStatus.InProduction)
      .map(rows => {
        if (rows > 0) {
          sendToRabbitMq(UpdateOperation(), id, rabbitMqPropagator)
        }
      })

    def updateCommission(commissionId: Option[Int]) = TableQuery[PlutoCommissionRow]
      .filter(_.id === commissionId)
      .filter(_.status === EntryStatus.New)
      .map(_.status)
      .update(EntryStatus.InProduction).flatMap(rows => {
      if (rows > 0) {
        TableQuery[PlutoCommissionRow].filter(_.id === commissionId).result.map({
          case Seq() =>
            logger.error(s"Failed to update commission, commission not updated: $commissionId")
            throw new IllegalStateException(s"Failed to update commission, commission not updated: $commissionId")
          case Seq(commission) =>
            val commissionsSerializer = new PlutoCommissionSerializer {}
            implicit val commissionsWrites: Writes[PlutoCommission] = commissionsSerializer.plutoCommissionWrites
            rabbitMqPropagator ! ChangeEvent(Seq(commissionsWrites.writes(commission)), getItemType(commission), UpdateOperation())
          case _ =>
            logger.error(s"Failed to update commission, multiple commissions updated: $commissionId")
            throw new IllegalStateException(s"Failed to update commission, multiple commissions updated: $commissionId")
        })
      } else {
        DBIOAction.successful(())
      }
    })

    auditor ! Auditor.LogEvent(uid, AuditAction.OpenProject, id, ZonedDateTime.now(), request.headers.get("User-Agent"))

    dbConfig.db.run(
        TableQuery[ProjectEntryRow]
          .filter(_.id === id)
          .result
          .flatMap(result => {
            val acts = result match {
              case Seq() => DBIOAction.successful(NotFound)
              case Seq(project: ProjectEntry) =>
                DBIO.seq(updateProject(), updateCommission(project.commissionId)).map(_ => Ok)
              case _ =>
                logger.error(s"Database inconsistency, multiple projects found for id=$id")
                DBIOAction.successful(InternalServerError)
            }
            acts
          })
    ).recover({
      case err: Throwable =>
        logger.error("Failed to mark project as opened", err)
        InternalServerError(Json.obj("status" -> "error", "detail" -> "Failed to mark project as opened"))
    })
  }

  private def updateStatusColumn(projectId:Int, newValue:EntryStatus.Value) = {
    import EntryStatusMapper._

    dbConfig.db.run {
      val q = for {c <- TableQuery[ProjectEntryRow] if c.id === projectId} yield c.status
      q.update(newValue)
    }
  }

  def updateStatus(projectId: Int) = IsAuthenticatedAsync(parse.json) {uid=> request=>
    import PlutoCommissionStatusUpdateRequestSerializer._
    request.body.validate[PlutoCommissionStatusUpdateRequest].fold(
      invalidErrs=>
        Future(BadRequest(Json.obj("status"->"bad_request","detail"->JsError.toJson(invalidErrs)))),
      requiredUpdate=>
        updateStatusColumn(projectId, requiredUpdate.status).map(rowsUpdated=>{
          if(rowsUpdated==0){
            NotFound(Json.obj("status"->"not_found","detail"->s"No project with id $projectId"))
          } else {
            if(rowsUpdated>1) logger.error(s"Status update request for project $projectId returned $rowsUpdated rows updated, expected 1! This indicates a database problem")
            auditor ! Auditor.LogEvent(uid, AuditAction.ChangeProjectStatus, projectId, ZonedDateTime.now, request.headers.get("User-Agent"))
            sendToRabbitMq(UpdateOperation(), projectId, rabbitMqPropagator).foreach(_ => ())
            Ok(Json.obj("status"->"ok","detail"->"Project status updated"))
          }
        }).recover({
          case err:Throwable=>
            logger.error(s"Could not update status of project $projectId to ${requiredUpdate.status}: ", err)
            InternalServerError(Json.obj("status"->"db_error","detail"->"Database error, see logs for details"))
        })
    )
  }

  def queryUsersForAutocomplete(prefix:String, limit:Option[Int]) = IsAuthenticatedAsync { uid=> request=>
    implicit val db = dbConfig.db
    implicit val ordering = Ordering.String
    ProjectEntry.listUsers(prefix, limit.getOrElse(10))
      .map(results=>{
        Ok(Json.obj("status"->"ok","users"->results.sorted))
      })
      .recover({
        case err:Throwable=>
          logger.error(s"Could not look up users with prefix $prefix and limit ${limit.getOrElse(10)}: ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"db_error", "detail"->"Database error, see logs for details"))
      })
  }

  def isUserKnown(uname:String) = IsAuthenticatedAsync { uid=> request=>
    implicit val db = dbConfig.db

    ProjectEntry.isUserKnown(uname)
      .map(result=>Ok(Json.obj("status"->"ok", "known"->result)))
      .recover(err=>{
        logger.error(s"Could not check if '$uname' is known: ${err.getMessage}", err)
        InternalServerError(Json.obj("status"->"error", "detail"->"Database error, see logs for details"))
      })
  }

  object SortDirection extends Enumeration {
    val desc, asc = Value
  }

  private def getSortDirection(directionString:String):Option[SortDirection.Value] = Try { SortDirection.withName(directionString) }.toOption

  def obitsListSorted(name:Option[String], startAt:Int, limit:Int, sort: String, sortDirection: String) = IsAuthenticatedAsync { uid => request =>
    implicit val db = dbConfig.db

    val baseQuery = name match {
      case None=>
        TableQuery[ProjectEntryRow].filter(_.isObitProject.nonEmpty)
      case Some(obitName)=>
        TableQuery[ProjectEntryRow].filter(_.isObitProject.toLowerCase like s"%$obitName%")
    }

    val sortedQuery = (sort, getSortDirection(sortDirection).getOrElse(SortDirection.asc)) match {
      case ("created", SortDirection.desc) => baseQuery.sortBy(_.created.desc)
      case ("created", SortDirection.asc) => baseQuery.sortBy(_.created.asc)
      case ("title", SortDirection.desc) => baseQuery.sortBy(_.projectTitle.desc)
      case ("title", SortDirection.asc) => baseQuery.sortBy(_.projectTitle.asc)
      case ("isObitProject", SortDirection.desc) => baseQuery.sortBy(_.isObitProject.desc)
      case ("isObitProject", SortDirection.asc) => baseQuery.sortBy(_.isObitProject.asc)
      case _ =>
        logger.warn(s"Sort field $sort was not recognised, ignoring.")
        baseQuery
    }

    db.run(
      for {
        content <- sortedQuery.drop(startAt).take(limit).result
        count <- sortedQuery.length.result
      } yield (content, count)
    )
      .map(results=>Ok(Json.obj("status"->"ok","count"->results._2,"result"->jstranslate(results._1))))
      .recover({
        case err:Throwable=>
          logger.error(s"Could not query database for obituaries: ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"error", "detail"->"Database error, see logs for details"))
      })
  }

  /**
    * Returns a JSON object containing a list of strings for names of valid obituaries startig with the given prefix.
    * If no prefix is supplied, then everything is returned (up to the given limit)
    * @param prefix optional prefix to limit the search to
    * @param limit don't return more than this number of results
    * @return
    */
  def findAvailableObits(prefix:Option[String], limit:Int) = IsAuthenticatedAsync { uid=> request=>
    implicit val db = dbConfig.db
    implicit val ordering = Ordering.String
    ProjectEntry.listObits(prefix.getOrElse(""), limit)
      .map(results=>{
        Ok(Json.obj("status"->"ok","obitNames"->results.sorted))
      })
      .recover({
        case err:Throwable=>
          logger.error(s"Could not look up obituaries with prefix $prefix and limit ${limit}: ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"db_error", "detail"->"Database error, see logs for details"))
      })
  }

  def assetFolderForProject(projectId:Int) = {
    implicit val db = dbConfig.db
    db.run(
      TableQuery[ProjectMetadataRow]
        .filter(_.key===ProjectMetadata.ASSET_FOLDER_KEY)
        .filter(_.projectRef===projectId)
        .result
    ).map(results=>{
      val resultCount = results.length
      if(resultCount==0){
        logger.error("No asset folder registered under that project id.")
      } else if(resultCount>1){
        logger.warn(s"Multiple asset folders found for project $projectId: $results")
      } else {
        results.head.value.getOrElse("")
      }
    }).recover({
      case err: Throwable =>
        logger.error(s"Could not look up asset folder for project id $projectId: ", err)
    })
  }

  def fixPermissions(projectId: Int) = IsAuthenticatedAsync {uid=> request=>
    val assetFolderString = Await.result(assetFolderForProject(projectId), Duration.Inf).toString
    val fileName = Paths.get(assetFolderString).getFileName.toString
    val parentDir = Paths.get(assetFolderString).getParent.toString
    rabbitMqSend ! FixEvent(true,false,fileName,parentDir)
    Future(Ok(Json.obj("status"->"ok","detail"->"Fix permissions run.")))
  }

  def deleteDataRunner(projectId: Int, delay: Int, pluto: Boolean, file: Boolean, backups: Boolean, pTR: Boolean, deliverables: Boolean, sAN: Boolean, matrix: Boolean, s3: Boolean, buckets: Array[String], bucketBooleans: Array[Boolean]): Unit = {
    def deleteFileJob() = Future {
      if (file) {
        implicit val db = dbConfig.db
        ProjectEntry.entryForId(projectId).map({
          case Success(projectEntry: ProjectEntry) =>
            projectEntry.associatedFiles(false).map(fileList => {
              fileList.map(entry => {
                logger.info(s"Attempting to delete the file at: ${entry.filepath}")
                fileEntryDAO
                  .deleteFromDisk(entry)
                  .andThen(_ => fileEntryDAO.deleteRecord(entry))
                if(entry.filepath.endsWith(".cpr")) {
                  db.run(
                    TableQuery[ProjectMetadataRow]
                      .filter(_.key===ProjectMetadata.ASSET_FOLDER_KEY)
                      .filter(_.projectRef===projectId)
                      .result
                  ).map(results=>{
                    val resultCount = results.length
                    if(resultCount==0){
                      logger.info(s"No asset folder registered for that project id.")
                    } else {
                      logger.info(s"Found the asset folder at: ${results.head.value.get} Attempting to delete any Cubase files present." )
                      for {
                        files <- Option(new File(results.head.value.get).listFiles)
                        file <- files if file.getName.endsWith(".cpr")
                      } file.delete()
                    }
                  }).recover({
                    case err: Throwable =>
                      logger.error(s"Could not look up asset folder for project id $projectId: ", err)
                  })
                }
                })
              }
            )
          case Failure(error) =>
            logger.error(s"Could not look up project entry for ${projectId}: ", error)
        })
      }
    }

    def deleteBackupsJob() = Future {
      if (backups) {
        implicit val db = dbConfig.db
        ProjectEntry.entryForId(projectId).map({
          case Success(projectEntry: ProjectEntry) =>
            logger.info(s"About to attempt to delete any backups present for project ${projectId}")
            projectEntry.associatedFiles(true).map(fileList => {
              fileList.map(entry => {
                entry.backupOf match {
                  case Some(value) =>
                    logger.info(s"Attempting to delete the file at: ${entry.filepath}")
                    fileEntryDAO
                      .deleteFromDisk(entry)
                      .andThen(_ => fileEntryDAO.deleteRecord(entry))
                  case None =>
                    logger.info(s"Ignoring non-backup file at ${entry.filepath}")
                }
              })
            }
            )
            projectEntry.associatedAssetFolderFiles(true, implicitConfig).map(fileList => {
              fileList.map(entry => {
                if (entry.storageId == config.get[Int]("asset_folder_backup_storage")) {
                  logger.info(s"Attempting to delete the file at: ${entry.filepath}")
                  assetFolderFileEntryDAO
                    .deleteFromDisk(entry)
                    .andThen(_ => assetFolderFileEntryDAO.deleteRecord(entry))
                } else {
                  logger.info(s"Ignoring non-backup file at ${entry.filepath}")
                }
              })
            }
            )
          case Failure(error) =>
            logger.error(s"Could not look up project entry for ${projectId}: ", error)
        })
      }
    }

    val xtensionXtractor="^(.*)\\.([^.]+)$".r

    def removeProjectFileExtension(projectFileName:String) = projectFileName match {
      case xtensionXtractor(barePath,_)=>barePath
      case _=>
        logger.warn(s"The project file '$projectFileName' does not appear to have a file extension")
        projectFileName
    }

    def deletePTRJob() = Future {
      if (pTR) {
        implicit val db = dbConfig.db
        ProjectEntry.entryForId(projectId).map({
          case Success(projectEntry: ProjectEntry) =>
            projectEntry.associatedFiles(false).map(fileList => {
              fileList.map(entry => {
                fileEntryDAO
                  .storage(entry)
                  .andThen({
                    case Success(storageTry) =>
                      storageTry match {
                        case Some(storage) =>
                          val targetFilePath = storage.rootpath.get + "/" + removeProjectFileExtension(entry.filepath) + ".ptr"
                          logger.info(s"Attempting to delete a possible file at: ${targetFilePath}")
                          new File(targetFilePath).delete()
                        case None =>
                          logger.info(s"Attempt at loading storage data failed.")
                      }
                    case Failure(err)=>
                      logger.error(s"Attempt at loading storage data failed.", err)
                  })
              })
            }
            )
          case Failure(error) =>
            logger.error(s"Could not look up project entry for ${projectId}: ", error)
        })
      }
    }

    def deleteDeliverables() = Future {
      if (deliverables) {
        rabbitMqDeliverable ! DeliverableEvent(projectId)
      }
    }

    def deleteS3() = Future {
      if (s3) {
        for((bucket,i) <- buckets.view.zipWithIndex) {
          if (bucketBooleans(i)) {
            val assetFolderString = Await.result(assetFolderForProject(projectId), Duration.Inf).toString
            logger.info(s"Asset folder for project: $assetFolderString")
            if (assetFolderString == "") {
              logger.warn(s"No asset folder found for project. Can not attempt to delete data from S3.")
            } else {
              implicit lazy val s3helper: S3Helper = helpers.S3Helper.createFromBucketName(bucket).toOption.get
              val assetFolderBasePath = config.get[String]("postrun.assetFolder.basePath")
              val keyForSearch = assetFolderString.replace(s"$assetFolderBasePath/", "")
              if (!keyForSearch.matches(".*?\\/.*?\\/.*?\\_.*?")) {
                logger.warn(s"Key for search does not match the expected format. Can not attempt to delete data from S3.")
              } else {
                logger.info(s"About to attempt to delete any data in the S3 bucket: ${bucket}")
                val bucketObjectData = s3helper.listBucketObjects(keyForSearch)
                for (s3Object <- bucketObjectData) {
                  if (s"$keyForSearch/" != s3Object.key) {
                    logger.info(s"Found S3 key: ${s3Object.key}")
                    val objectVersions = s3helper.listObjectsVersions(s3Object)
                    for (version <- objectVersions) {
                      logger.info(s"Found version: ${version.versionId()} for key: ${version.key()}")
                      val deleteOutcome = s3helper.deleteObject(s3Object, version.versionId())
                      logger.info(s"Delete response was: $deleteOutcome")
                    }
                  }
                }
                val bucketObjectDataFolder = s3helper.listBucketObjects(keyForSearch)
                for (s3Object <- bucketObjectDataFolder) {
                  if (s"$keyForSearch/" == s3Object.key) {
                    logger.info(s"Found S3 key: ${s3Object.key}")
                    val objectVersionsFolder = s3helper.listObjectsVersions(s3Object)
                    for (version <- objectVersionsFolder) {
                      logger.info(s"Found version: ${version.versionId()} for key: ${version.key()}")
                      val deleteOutcomeFolder = s3helper.deleteObject(s3Object, version.versionId())
                      logger.info(s"Delete response was: $deleteOutcomeFolder")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    def onlineFilesByProject(vidispineCommunicator: VidispineCommunicator, projectId: Int): Future[Seq[OnlineOutputMessage]] = {
      vidispineCommunicator.getFilesOfProject(projectId)
        .map(_.filterNot(isBranding).map(InternalOnlineOutputMessage.toOnlineOutputMessage))
    }

    def isBranding(item: VSOnlineOutputMessage): Boolean = item.mediaCategory.toLowerCase match {
      case "branding" => true // Case insensitive
      case _ => false
    }

    def deleteSAN() = Future {
      if (sAN) {
        Thread.sleep(delay)
        logger.info(s"About to attempt to delete any SAN data present for project ${projectId}")
        implicit val db = dbConfig.db
        DeleteJobDAO.getOrCreate(projectId, "Started")
        lazy val vidispineConfig = VidispineConfig.fromEnvironment.toOption.get
        implicit lazy val executionContext = new MdcExecutionContext(
          ExecutionContext.fromExecutor(
            Executors.newWorkStealingPool(10)
          )
        )
        implicit lazy val actorSystem:ActorSystem = ActorSystem("pluto-core-delete", defaultExecutionContext=Some(executionContext))
        implicit lazy val mat:Materializer = Materializer(actorSystem)
        implicit lazy val vidispineCommunicator = new VidispineCommunicator(vidispineConfig)
        val vidispineMethodOut = Await.result(onlineFilesByProject(vidispineCommunicator, projectId), 120.seconds)
        vidispineMethodOut.map(onlineOutputMessage => {
          if (onlineOutputMessage.projectIds.length > 2) {
            logger.info(s"Refusing to attempt to delete Vidispine item ${onlineOutputMessage.vidispineItemId.get} as it is referenced by more than one project.")
            ItemDeleteDataDAO.getOrCreate(projectId, onlineOutputMessage.vidispineItemId.get)
          } else {
            logger.info(s"About to attempt to send a message to delete Vidispine item ${onlineOutputMessage.vidispineItemId.get}")
            rabbitMqSAN ! SANEvent(onlineOutputMessage)
          }
        })
        Thread.sleep(1000)
        DeleteJobDAO.getOrCreate(projectId, "Finished")
      }
    }

    def nearlineFilesByProject(vault: Vault, projectId: String): Future[Seq[OnlineOutputMessage]] = {
      val sinkFactory = Sink.seq[OnlineOutputMessage]
      Source.fromGraph(new OMFastContentSearchSource(vault,
        s"""GNM_PROJECT_ID:\"$projectId\"""",
        Array("MXFS_PATH", "MXFS_FILENAME", "GNM_PROJECT_ID", "GNM_TYPE", "__mxs__length")
      )
      ).filterNot(isBrandingMatrix)
        .map(InternalOnlineOutputMessage.toOnlineOutputMessage)
        .toMat(sinkFactory)(Keep.right)
        .run()
    }

    def isBrandingMatrix(entry: ObjectMatrixEntry): Boolean = entry.stringAttribute("GNM_TYPE") match {
      case Some(gnmType) =>
        gnmType.toLowerCase match {
          case "branding" => true // Case insensitive
          case _ => false
        }
      case _ => false
    }

    def searchAssociatedNearlineMedia(projectId: Int, vault: Vault): Future[Seq[OnlineOutputMessage]] = {
      nearlineFilesByProject(vault, projectId.toString)
    }

    def getNearlineResults(projectId: Int, nearlineVaultId: String, matrixStore: MXSConnectionBuilderImpl): Future[Either[String, Seq[OnlineOutputMessage]]] =
      matrixStore.withVaultFuture(nearlineVaultId) { vault =>
        searchAssociatedNearlineMedia(projectId, vault).map(Right.apply)
      }

    def deleteMatrix() = Future {
      if (matrix) {
        Thread.sleep(delay)
        logger.info(s"About to attempt to delete any Object Matrix data present for project ${projectId}")
        implicit val db = dbConfig.db
        MatrixDeleteJobDAO.getOrCreate(projectId, "Started")
        lazy val matrixStoreConfig = new MatrixStoreEnvironmentConfigProvider().get() match {
          case Left(err)=>
            logger.error(s"Could not initialise due to incorrect matrix-store config: $err")
            sys.exit(1)
          case Right(config)=>config
        }
        implicit lazy val executionContext = new MdcExecutionContext(
          ExecutionContext.fromExecutor(
            Executors.newWorkStealingPool(10)
          )
        )
        implicit lazy val actorSystem:ActorSystem = ActorSystem("pluto-core-delete-matrix", defaultExecutionContext=Some(executionContext))
        implicit lazy val mat:Materializer = Materializer(actorSystem)
        val connectionIdleTime = sys.env.getOrElse("CONNECTION_MAX_IDLE", "750").toInt
        implicit val matrixStore = new MXSConnectionBuilderImpl(
          hosts = matrixStoreConfig.hosts,
          accessKeyId = matrixStoreConfig.accessKeyId,
          accessKeySecret = matrixStoreConfig.accessKeySecret,
          clusterId = matrixStoreConfig.clusterId,
          maxIdleSeconds = connectionIdleTime
        )
        val matrixMethodOut = Await.result(getNearlineResults(projectId, matrixStoreConfig.nearlineVaultId, matrixStore), 120.seconds)
        matrixMethodOut match {
          case Right(nearlineResults) =>
            nearlineResults.map(onlineOutputMessage => {
              if (onlineOutputMessage.projectIds.length > 2) {
                logger.info(s"Refusing to attempt to delete Object Matrix data for object ${onlineOutputMessage.nearlineId.get} as it is referenced by more than one project.")
                MatrixDeleteDataDAO.getOrCreate(projectId, onlineOutputMessage.nearlineId.get)
              } else {
                logger.info(s"About to attempt to send a message to delete Object Matrix data for object ${onlineOutputMessage.nearlineId.get}")
                rabbitMqMatrix ! MatrixEvent(onlineOutputMessage)
              }
            })
          case Left(something) =>
            logger.info(s"No Object Matrix data was found to process.")
        }
        MatrixDeleteJobDAO.getOrCreate(projectId, "Finished")
      }
    }

    def makeDeletionRecord() = Future {
      implicit val db = dbConfig.db

      var user = ""
      val currentDate = new Date()
      val timestampOfNow = new Timestamp(currentDate.getTime)
      var created = timestampOfNow
      var workingGroupName = ""

      ProjectEntry.entryForId(projectId).map({
        case Success(projectEntry: ProjectEntry) =>
          user = projectEntry.user
          created = projectEntry.created
          projectEntry.getWorkingGroup.map({
            case Some(workingGroup: PlutoWorkingGroup) =>
              workingGroupName = workingGroup.name
              DeletionRecordDAO.getOrCreate(projectId, user, timestampOfNow, created, workingGroupName)
            case None =>
              logger.error(s"Could not get working group name for project ${projectId}")
              DeletionRecordDAO.getOrCreate(projectId, user, timestampOfNow, created, "Unknown")
          })
        case Failure(error) =>
          logger.error(s"Could not look up project entry for ${projectId}: ", error)
          Left(error.toString)
      })
    }

    val f = for {
      f1 <- makeDeletionRecord()
      f2 <- deletePTRJob()
      f3 <- deleteFileJob()
      f4 <- deleteBackupsJob()
      f5 <- deleteDeliverables()
      f6 <- deleteS3()
      f7 <- deleteMatrix()
      f8 <- deleteSAN()
    } yield List(f1, f2, f3, f4, f5, f6, f7, f8)
    if (pluto) {
      Thread.sleep(800)
      implicit val db = dbConfig.db
      ProjectMetadata.deleteAllMetadataFor(projectId).map({
        case Success(rows) =>
          logger.info(s"Attempt at removing project metadata worked.")
        case Failure(err) =>
          logger.error(s"Could not delete metadata", err)
      })
      ProjectEntry.entryForId(projectId).map({
        case Success(projectEntry: ProjectEntry) =>
          projectEntry.removeFromDatabase.map({
            case Success(_) =>
              logger.info(s"Attempt at removing project record worked.")
            case Failure(error) =>
              logger.error(s"Attempt at removing project record failed with error: ${error}")
          })
        case Failure(error) =>
          logger.error(s"Could not look up project entry for ${projectId}: ", error)
          Left(error.toString)
      })
    }
  }

  def deleteData(projectId: Int) = IsAdmin { uid =>
    request =>
    logger.info(s"Got a delete data request for project ${projectId}.")
    logger.info(s"Pluto value is: ${request.body.asJson.get("pluto")}")
    logger.info(s"File value is: ${request.body.asJson.get("file")}")
    logger.info(s"Backups value is: ${request.body.asJson.get("backups")}")
    logger.info(s"PTR value is: ${request.body.asJson.get("PTR")}")
    logger.info(s"Deliverables value is: ${request.body.asJson.get("deliverables")}")
    logger.info(s"SAN value is: ${request.body.asJson.get("SAN")}")
    logger.info(s"Matrix value is: ${request.body.asJson.get("matrix")}")
    logger.info(s"S3 value is: ${request.body.asJson.get("S3")}")
    logger.info(s"Buckets value is: ${request.body.asJson.get("buckets")}")
    logger.info(s"Bucket Booleans value is: ${request.body.asJson.get("bucketBooleans")}")
    deleteDataRunner(projectId, 0, request.body.asJson.get("pluto").toString().toBoolean, request.body.asJson.get("file").toString().toBoolean, request.body.asJson.get("backups").toString().toBoolean, request.body.asJson.get("PTR").toString().toBoolean, request.body.asJson.get("deliverables").toString().toBoolean, request.body.asJson.get("SAN").toString().toBoolean, request.body.asJson.get("matrix").toString().toBoolean, request.body.asJson.get("S3").toString().toBoolean, request.body.asJson.get("buckets").validate[Array[String]].get, request.body.asJson.get("bucketBooleans").validate[Array[Boolean]].get)
    Ok(Json.obj("status"->"ok","detail"->"Delete data run."))
  }

  def deleteJob(projectId: Int) = IsAdminAsync { uid => request =>
    dbConfig.db.run(
      TableQuery[DeleteJob].filter(_.projectEntry===projectId).result
    ).map(_.headOption match {
    case Some(jobRecord)=>
      Ok(Json.obj("status"->"ok","job_status"->jobRecord.status))
    case None=>
      NotFound(Json.obj("status"->"notfound","detail"->s"No job with project id: $projectId"))
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not look up project $projectId: ", err)
        InternalServerError(Json.obj("status"->"error","detail"->"Database error looking up job, see server logs"))
    })
  }

  def matrixDeleteJob(projectId: Int) = IsAdminAsync { uid => request =>
    dbConfig.db.run(
      TableQuery[MatrixDeleteJob].filter(_.projectEntry===projectId).result
    ).map(_.headOption match {
      case Some(jobRecord)=>
        Ok(Json.obj("status"->"ok","job_status"->jobRecord.status))
      case None=>
        NotFound(Json.obj("status"->"notfound","detail"->s"No job with project id: $projectId"))
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not look up project $projectId: ", err)
        InternalServerError(Json.obj("status"->"error","detail"->"Database error looking up job, see server logs"))
    })
  }

  def getProjectsForCommission(commission: Int) = dbConfig.db.run(
    TableQuery[ProjectEntryRow].filter(_.commission===commission).sortBy(_.created.desc).result
  ).map(Success(_)).recover(Failure(_))

  def deleteCommissionData(commissionId: Int) = IsAdmin { uid =>
    request =>
      logger.info(s"Got a delete data request for commission ${commissionId}.")
      logger.info(s"Commission value is: ${request.body.asJson.get("commission")}")
      logger.info(s"Pluto value is: ${request.body.asJson.get("pluto")}")
      logger.info(s"File value is: ${request.body.asJson.get("file")}")
      logger.info(s"Backups value is: ${request.body.asJson.get("backups")}")
      logger.info(s"PTR value is: ${request.body.asJson.get("PTR")}")
      logger.info(s"Deliverables value is: ${request.body.asJson.get("deliverables")}")
      logger.info(s"SAN value is: ${request.body.asJson.get("SAN")}")
      logger.info(s"Matrix value is: ${request.body.asJson.get("matrix")}")
      logger.info(s"S3 value is: ${request.body.asJson.get("S3")}")
      logger.info(s"Buckets value is: ${request.body.asJson.get("buckets")}")
      logger.info(s"Bucket Booleans value is: ${request.body.asJson.get("bucketBooleans")}")

      implicit val db = dbConfig.db

      getProjectsForCommission(commissionId).map({
        case Success(result)=>
          result.map((project) => {
            logger.info(s"Found project ${project.id.get}.")
            deleteDataRunner(project.id.get, 400, request.body.asJson.get("pluto").toString().toBoolean, request.body.asJson.get("file").toString().toBoolean, request.body.asJson.get("backups").toString().toBoolean, request.body.asJson.get("PTR").toString().toBoolean, request.body.asJson.get("deliverables").toString().toBoolean, request.body.asJson.get("SAN").toString().toBoolean, request.body.asJson.get("matrix").toString().toBoolean, request.body.asJson.get("S3").toString().toBoolean, request.body.asJson.get("buckets").validate[Array[String]].get, request.body.asJson.get("bucketBooleans").validate[Array[Boolean]].get)
          })
          if (request.body.asJson.get("commission").toString().toBoolean) {
            Thread.sleep(1400)

            PlutoCommission.forId(commissionId).map({
              case Some(plutoCommission: PlutoCommission) =>
                plutoCommission.removeFromDatabase.map({
                  case Success(_) =>
                    logger.info(s"Attempt at removing commission record worked.")
                  case Failure(error) =>
                    logger.error(s"Attempt at removing commission record failed with error: ${error}")
                })
              case None =>
                logger.error(s"Could not look up commission entry for ${commissionId}: ")
            })
          }
        case Failure(error)=>
          logger.error(error.toString)
      })

      Ok(Json.obj("status"->"ok","detail"->"Delete data run."))
  }

  def assetFolderFilesList(requestedId: Int, allVersions: Boolean) = IsAuthenticatedAsync {uid=>{request=>
    implicit val db = dbConfig.db

    selectid(requestedId).flatMap({
      case Failure(error)=>
        logger.error(s"Could not list files from project ${requestedId}",error)
        Future(InternalServerError(Json.obj("status"->"error","detail"->error.toString)))
      case Success(someSeq)=>
        someSeq.headOption match { //matching on pk, so can only be one result
          case Some(projectEntry)=>
            projectEntry.associatedAssetFolderFiles(allVersions, implicitConfig).map(fileList=>Ok(Json.obj("status"->"ok","files"->fileList)))
          case None=>
            Future(NotFound(Json.obj("status"->"error","detail"->s"project $requestedId not found")))
        }
    })
  }}

  def fileDownload(requestedId: Int) = IsAuthenticatedAsync {uid=>{request=>
    implicit val db = dbConfig.db

    selectid(requestedId).flatMap({
      case Failure(error)=>
        logger.error(s"Could not download file for project ${requestedId}",error)
        Future(InternalServerError(Json.obj("status"->"error","detail"->error.toString)))
      case Success(someSeq)=>
        someSeq.headOption match {
          case Some(projectEntry)=>

            val fileData = for {
              f1 <- projectEntry.associatedFiles(false).map(fileList=>fileList(0))
              f2 <- f1.getFullPath
            } yield (f1, f2)

            val (fileEntry, fullPath) = (fileData.map(_._1), fileData.map(_._2))

            val fileEntryData = Await.result(fileEntry, Duration(10, TimeUnit.SECONDS))
            val fullPathData = Await.result(fullPath, Duration(10, TimeUnit.SECONDS))

            Future(Ok.sendFile(
               content = new java.io.File(fullPathData),
               fileName = _ => Some(fileEntryData.filepath)
             ))
          case None=>
            Future(NotFound(Json.obj("status"->"error","detail"->s"Project $requestedId not found")))
        }
    })
  }}

  def restoreBackup(requestedId: Int, requestedVersion: Int) = IsAdminAsync {uid=>{request=>
    implicit val db = dbConfig.db

    selectid(requestedId).flatMap({
      case Failure(error)=>
        logger.error(s"Could not restore file for project ${requestedId}",error)
        Future(InternalServerError(Json.obj("status"->"error","detail"->error.toString)))
      case Success(someSeq)=>
        someSeq.headOption match {
          case Some(projectEntry)=>
            val fileData = for {
              f1 <- projectEntry.associatedFiles(false).map(fileList=>fileList(0))
            } yield (f1)
            val fileToSaveOver = Await.result(fileData, Duration(10, TimeUnit.SECONDS))
            val fileDataTwo = for {
              f2 <- projectEntry.associatedFiles(true).map(fileList=>fileList)
            } yield (f2)
            val fileEntryDataTwo = Await.result(fileDataTwo, Duration(10, TimeUnit.SECONDS))
            var versionFound = 0
            var filePlace = 0
            val timestamp = dateTimeToTimestamp(ZonedDateTime.now())
            var fileToLoad = FileEntry(None, "", 1, "", 1, timestamp, timestamp, timestamp, false, false, None, None)

            while (versionFound == 0) {
              if ((!fileEntryDataTwo(filePlace).backupOf.isEmpty) && (fileEntryDataTwo(filePlace).version == requestedVersion)) {
                fileToLoad = fileEntryDataTwo(filePlace)
                versionFound = 1
              }
              filePlace = filePlace + 1
            }
            logger.debug(s"Copying from file $fileToLoad to $fileToSaveOver")
            storageHelper.copyFile(fileToLoad, fileToSaveOver)
            Future(Ok(Json.obj("status"->"okay","detail"->s"Restored file for project $requestedId from version $requestedVersion")))
          case None=>
            Future(NotFound(Json.obj("status"->"error","detail"->s"Project $requestedId not found")))
        }
    })
  }}
}
