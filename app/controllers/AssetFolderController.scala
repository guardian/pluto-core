package controllers

import auth.{BearerTokenAuth, Security}
import javax.inject.Inject
import models.{ProjectMetadata, ProjectMetadataRow, ProjectMetadataSerializer}
import play.api.{Configuration, Logger}
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import slick.jdbc.PostgresProfile
import slick.lifted.TableQuery
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class AssetFolderController @Inject() (override val controllerComponents:ControllerComponents,
                                       override val bearerTokenAuth:BearerTokenAuth,
                                       override implicit val config: Configuration,
                                       dbConfigProvider: DatabaseConfigProvider,
                                       override val cache:SyncCacheApi) extends AbstractController(controllerComponents) with Security with ProjectMetadataSerializer {
  override val logger = Logger(getClass)

  private val db = dbConfigProvider.get[PostgresProfile].db

  /**
   * Recursively iterates through the given path trying to find one that matches a project.
   * If a match is found, then the future resolves to a Some with the metadata object.
   * If no match is found, the future resolves to None
   * If an error occurs, the future fails and must be caught with .recover
   * @param pathParts the path string, split out on / characters
   * @return a Future, containing an option with the metadata object if one is matching.
   */
  protected def recursiveSearch(pathParts:Array[String]):Future[Option[ProjectMetadata]] = {
    if(pathParts.isEmpty) return Future(None)

    val pathToCheck = pathParts.mkString("/")
    //Think this could be done much better, probably by getting a superset of data and then filtering it in-memory;
    //but I can't work out how to query for the relevant superset right now
    db.run(
      TableQuery[ProjectMetadataRow]
        .filter(_.key===ProjectMetadata.ASSET_FOLDER_KEY)
        .filter(_.value===pathToCheck)
        .result
    ).flatMap(results=>{
      if(results.isEmpty) {
        recursiveSearch(pathParts.dropRight(1))
      } else {
        if(results.length>1) {
          logger.warn(s"Found ${results.length} asset folders for $pathToCheck! Using the first.")
        }
        Future(Some(results.head))
      }
    })
  }

  def assetFolderForPath(path:String) = IsAuthenticatedAsync { uid=> request=>
    recursiveSearch(path.split("/")).map({
      case Some(projectMetadata)=>
        //this object matches the pre-existing pluto endpoint so should ensure compatibility
        Ok(Json.obj(
          "status"->"ok",
          "path"->projectMetadata.value.getOrElse("").toString,  //this field is marked optional in the model but must be set to match the query
          "project"->projectMetadata.projectRef.toString
        ))
      case None=>
        //this object matches the pre-existing pluto endpoint so should ensure compatibility
        NotFound(Json.obj(
          "status"->"notfound"
        ))
    }).recover({
      case err:Throwable=>
        logger.error(s"Could not look up asset folder for path $path: ", err)
        InternalServerError(Json.obj("status"->"db_error", "detail"->err.getMessage))
    })
  }

  def assetFolderForProject(projectId:Int) = IsAuthenticatedAsync { uid=> request=>
    db.run(
      TableQuery[ProjectMetadataRow]
        .filter(_.key===ProjectMetadata.ASSET_FOLDER_KEY)
        .filter(_.projectRef===projectId)
        .result
    ).map(results=>{
      val resultCount = results.length
      if(resultCount==0){
        NotFound(Json.obj("status"->"not_found","detail"->"No asset folder registered under that project id"))
      } else if(resultCount>1){
        logger.warn(s"Multiple asset folders found for project $projectId: $results")
        Ok(Json.obj("status"->"ok","warning"->"Multiple values exist", "result"->results.head))
      } else {
        Ok(Json.obj("status"->"ok","result"->results.head))
      }
    }).recover({
      case err: Throwable =>
        logger.error(s"Could not look up asset folder for project id $projectId: ", err)
        InternalServerError(Json.obj("status"->"error","detail"->err.getMessage))
    })
  }
}
