package controllers

import akka.actor.ActorRef

import javax.inject.{Inject, Named, Singleton}
import akka.http.scaladsl.Http
import auth.{BearerTokenAuth, Security}
import exceptions.AlreadyExistsException
import helpers.PostrunSorter
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{ControllerComponents, Request}
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._
import play.api.libs.json._
import services.PostrunActionScanner

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

@Singleton
class PostrunActionController  @Inject() (override val controllerComponents:ControllerComponents,
                                          override val bearerTokenAuth:BearerTokenAuth,
                                          override implicit val config: Configuration, dbConfigProvider: DatabaseConfigProvider,
                                          cacheImpl:SyncCacheApi,
                                          @Named("postrun-action-scanner") postrunActionScanner:ActorRef)
  extends GenericDatabaseObjectController[PostrunAction] with PostrunActionSerializer with PostrunDependencySerializer with Security {

  implicit val cache:SyncCacheApi = cacheImpl
  val dbConfig = dbConfigProvider.get[PostgresProfile]

  override def selectid(requestedId: Int): Future[Try[Seq[PostrunAction]]] = dbConfig.db.run(
    TableQuery[PostrunActionRow].filter(_.id === requestedId).result.asTry
  )

  override def selectall(startAt: Int, limit: Int): Future[Try[(Int, Seq[PostrunAction])]] = dbConfig.db.run(
    TableQuery[PostrunActionRow].length.result.zip(
      TableQuery[PostrunActionRow].drop(startAt).take(limit).result
    )
  ).map(Success(_)).recover(Failure(_))

  override def jstranslate(result: PostrunAction): Json.JsValueWrapper = result

  override def jstranslate(result: Seq[PostrunAction]): Json.JsValueWrapper = result  //PostrunActionSerializer is implicitly called to do this

  override def deleteid(requestedId: Int) = dbConfig.db.run(
    TableQuery[PostrunActionRow].filter(_.id === requestedId).delete.asTry
  )

  override def validate(request: Request[JsValue]) = request.body.validate[PostrunAction]

  override def insert(entry: PostrunAction, uid:String) = dbConfig.db.run(
    (TableQuery[PostrunActionRow] returning TableQuery[PostrunActionRow].map(_.id) += entry).asTry)

  override def dbupdate(itemId:Int, entry:PostrunAction) = {
    val newRecord = entry.id match {
      case Some(id)=>entry
      case None=>entry.copy(id=Some(itemId))
    }
    dbConfig.db.run(
      TableQuery[PostrunActionRow].filter(_.id===itemId).update(newRecord).asTry
    )
  }

  def insertAssociation(postrunId: Int, projectTypeId: Int) = dbConfig.db.run(
    (TableQuery[PostrunAssociationRow] returning TableQuery[PostrunAssociationRow].map(_.id) += (projectTypeId, postrunId)).asTry
  )

  def removeAssociation(postrunId: Int, projectTypeId: Int) =dbConfig.db.run(
    TableQuery[PostrunAssociationRow].filter(_.postrunEntry===postrunId).filter(_.projectType===projectTypeId).delete.asTry
  )

  def associate(postrunId: Int, projectTypeId: Int) = IsAuthenticatedAsync {uid=> { request =>
    insertAssociation(postrunId, projectTypeId).map({
      case Success(newRowId) => Ok(Json.obj("status" -> "ok", "detail" -> s"added association with id $newRowId"))
      case Failure(error) => handleConflictErrors(error, "postrun association", isInsert=true)
    })
  }}

  def unassociate(postrunId: Int, projectTypeId: Int) = IsAuthenticatedAsync {uid=>{request=>
    removeAssociation(postrunId, projectTypeId) map {
      case Success(affectedRows)=>Ok(Json.obj("status"->"ok", "detail"->"removed association"))
      case Failure(error)=>handleConflictErrors(error, "postrun association", isInsert=true)
    }
  }}

  def insertDependency(entry: PostrunDependency) = dbConfig.db.run(
    (TableQuery[PostrunDependencyRow] returning TableQuery[PostrunDependencyRow].map(_.id) += entry).asTry
  )

  def deleteDependency(entry: PostrunDependency) = dbConfig.db.run(
    TableQuery[PostrunDependencyRow].filter(_.sourceAction === entry.sourceAction).filter(_.dependsOn === entry.dependsOn).delete.asTry
  )

  def selectDependencies(postrunId:Int) = dbConfig.db.run(
    TableQuery[PostrunDependencyRow].filter(_.sourceAction===postrunId).result.asTry
  )

  def listDependencies(postrunId: Int) = IsAdminAsync {uid=>{request=>
    selectDependencies(postrunId).map({
      case Success(dependencyList)=>Ok(Json.obj("status"->"ok", "result"->dependencyList))
      case Failure(error)=>
        logger.error(s"Could not list postrun dependencies for $postrunId: ", error)
        InternalServerError(Json.obj("status"->"error", "detail"->error.toString))
    })
  }}

  def addDependency(sourceId:Int, dependsOn:Int) = IsAdminAsync {uid=>{request=>
    insertDependency(PostrunDependency(None,sourceId,dependsOn)).map({
      case Success(newRowId) => Ok(Json.obj("status" -> "ok", "detail" -> s"added dependency with id $newRowId"))
      case Failure(error) => handleConflictErrors(error,"postrun dependency", isInsert=true)
    })
  }}

  def removeDependency(sourceId:Int, dependsOn:Int) = IsAdminAsync {uid=>{request=>
    deleteDependency(PostrunDependency(None,sourceId,dependsOn)).map({
      case Success(newRowId) => Ok(Json.obj("status" -> "ok", "detail" -> s"deleted dependency"))
      case Failure(error) => handleConflictErrors(error,"postrun dependency", isInsert=false)
    })
  }}

  def startScan = IsAdmin {uid=> request=>
    postrunActionScanner ! PostrunActionScanner.Rescan
    Ok(Json.obj("status"->"ok","detail"->"scan started"))
  }

  def testPostrunSort(projectTypeId:Int) = IsAuthenticatedAsync { uid=> request=>
    implicit val db = dbConfig.db

    val postrunAssociationJoinQuery = TableQuery[PostrunAssociationRow] joinLeft TableQuery[PostrunActionRow] on (_.postrunEntry===_.id)

    val sortedPostrunList = for {
      associationResults <- dbConfig.db.run(postrunAssociationJoinQuery.filter(_._1.projectType===projectTypeId).result)
      postrunDependencies <- PostrunDependencyGraph.loadAllById
      results <- Future(PostrunSorter.doSort(associationResults.map(_._2).collect({case Some(entry)=>entry}).toList, postrunDependencies))
    } yield (associationResults, postrunDependencies, results)

    sortedPostrunList.map(results=>{
      Ok(Json.obj("status"->"ok",
        "associated_postruns"->results._1.map(joined_row=>(joined_row._1._1, joined_row._1._2, joined_row._2.map(_.runnable))),
        "dependency_input"->results._2,
        "final_results"->results._3.map(_.runnable)
      ))
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not test the postrun sorting: ${err.getMessage}", err)
        InternalServerError(Json.obj("status"->"error","description"->err.getMessage,"trace"->err.getStackTrace.map(_.toString)))
    })
  }
}
