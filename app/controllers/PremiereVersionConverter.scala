package controllers

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink}
import auth.{BearerTokenAuth, Security}
import helpers.PostrunDataCache
import models.{DisplayedVersion, FileEntry, FileEntryDAO, FileEntrySerializer, PremiereVersionTranslation, PremiereVersionTranslationDAO, ProjectEntry}
import org.apache.commons.io.FileUtils
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import postrun.{AdobeXml, ExtractPremiereVersion}
import slick.jdbc.PostgresProfile

import java.io.File
import java.nio.file.Paths
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, MetaData, Node, Null, PrefixedAttribute, UnprefixedAttribute}
import scala.xml.transform.RewriteRule

@Singleton
class PremiereVersionConverter @Inject()(override val controllerComponents:ControllerComponents,
                                         override val bearerTokenAuth:BearerTokenAuth,
                                         override implicit val config: Configuration,
                                         override val cache:SyncCacheApi,
                                         fileEntryDAO:FileEntryDAO,
                                         converter:services.PremiereVersionConverter,
                                         premiereVersionTranslationDAO: PremiereVersionTranslationDAO,
                                         dbConfigProvider:DatabaseConfigProvider)
                                        (implicit ec:ExecutionContext, mat:Materializer) extends AbstractController(controllerComponents) with Security with AdobeXml with FileEntrySerializer
{
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  def changeVersion(projectFileId:Int, requiredDisplayVersion:String) = IsAuthenticatedAsync { uid=> request=>
    DisplayedVersion(requiredDisplayVersion) match {
      case None=>
        Future(BadRequest(Json.obj("status"->"bad_request","detail"->"invalid version number")))
      case Some(newVersion)=>
        val resultFut = for {
          targetVersion <- premiereVersionTranslationDAO
            .findDisplayedVersion(newVersion)
            .flatMap(results=>{
              if(results.isEmpty) {
                Future.failed(new RuntimeException("Version number is not recognised"))
              } else {
                logger.info(s"Target version is ${results.head}")
                Future(results.head)
              }
            })
          projectFile <- fileEntryDAO.entryFor(projectFileId).flatMap({
            case None=>Future.failed(new RuntimeException(s"project id $projectFileId does not exist"))
            case Some(f)=>Future(f)
          })
          projectFileJavaIO <- fileEntryDAO.getJavaFile(projectFile)
          _ <- converter.checkExistingVersion(projectFile, targetVersion) //breaks the chain if the versions are both the same
          backupFile <- converter.backupFile(projectFile)
          //.get is safe here because if it is None then `checkExistingVersion` will have failed the job
          _ <- converter.tweakProjectVersion(projectFileJavaIO, backupFile, projectFile.maybePremiereVersion.get, targetVersion)
          updatedProjectFile <- fileEntryDAO.saveSimple(projectFile.copy(maybePremiereVersion = Some(targetVersion.internalVersionNumber)))
        } yield (updatedProjectFile, projectFile)

        resultFut
          .map(afterAndBefore=>{
            logger.info(s"Project file ${afterAndBefore._1.filepath} has been updated to internal version number ${afterAndBefore._1.maybePremiereVersion} from ${afterAndBefore._2.maybePremiereVersion}")
            Ok(Json.obj("status"->"ok", "detail"->"Project updated", "entry"->afterAndBefore._1))
          })
        .recover({
          case err:Throwable=>
            logger.error(s"Could not update project file to version $newVersion: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
            BadRequest(Json.obj("status"->"error", "detail"->err.getMessage))
        })
    }
  }

  def lookupClientVersion(clientVersionString:String) = IsAuthenticatedAsync { uid=> request=>
    import models.PremiereVersionTranslationCodec._

    DisplayedVersion(clientVersionString) match {
      case Some(clientVersion) =>
        premiereVersionTranslationDAO
          .findDisplayedVersion(clientVersion)
          .map(results=>{
            Ok(Json.obj("status"->"ok", "count"->results.length, "result"->results))
          })
      case None =>
        Future(BadRequest(Json.obj("status" -> "error", "detail" -> "Invalid version string, should be of the form x.y.z")))
    }
  }

  def lookupInternalVersion(internalVersion:Int) = IsAuthenticatedAsync { uid=> request=>
    import models.PremiereVersionTranslationCodec._

    premiereVersionTranslationDAO
      .findInternalVersion(internalVersion)
      .map({
        case Some(v)=>Ok(Json.obj("status"->"ok","version"->v))
        case None=>NotFound(Json.obj("status"->"error","detail"->"unknown version number"))
      })
      .recover({
        case err:Throwable=>
          logger.error(s"Could not look up internal premiere version number $internalVersion: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
          InternalServerError(Json.obj("status"->"db_error", "detail"->"Database error, see server logs"))
      })
  }

  def runVersionScanner(projectTypeId:Int) = {
    val xtractor = new ExtractPremiereVersion

    ProjectEntry
      .scanProjectsForType(projectTypeId)
      .mapAsync(2)(p=>p.associatedFiles(allVersions=false).map(files=>(p, files)))
      .mapAsync(1)(content=>{
        val project = content._1
        val files = content._2
        Future
          .sequence(files.map(f=>fileEntryDAO.getFullPath(f)))
          .map(filePaths=>(project, filePaths zip files))
      })
      .mapAsync(1)(content=>{
        val project = content._1
        val filePaths = content._2
        for {
          extractedInfo <- Future.sequence(filePaths.map(filePair =>
            xtractor.postrun(filePair._1, project, null, PostrunDataCache(), None, None)
          )).map(_ zip filePaths)
          results <- Future(extractedInfo.map(info=>info._1 match {
            case Success(updatedCache)=>
              updatedCache.get("premiere_version") match {
                case Some(updatedVersion)=>Some(info._2._2.copy(maybePremiereVersion = Some(updatedVersion.toInt)))
                case None=>
                  throw new RuntimeException(s"${info._2._1}: ExtractPremiereVersion completed successfully but did not return any info, this should not happen")
              }
            case Failure(err)=>
              logger.warn(s"Could not get premiere version from ${info._2._1}: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
              None
          }))
        } yield results
      })
      .map(_.collect({case Some(fileEntry)=>fileEntry}))
      .mapAsync(4)(files=>{
        Future.sequence(
          files.map(fileEntryDAO.saveSimple)
        )
      })
      .toMat(Sink.ignore)(Keep.right)
      .run()
      .map(_=>{
        logger.info("Premiere version scan is now complete")
      })
      .recover({
        case err:Throwable=>
          logger.error(s"Premiere version scan failed: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
      })
  }

  def scanAllVersions(projectTypeId:Int) = IsAdmin { uid=> request=>
    runVersionScanner(projectTypeId)
    Ok(Json.obj("status"->"ok","detail"->"run started"))
  }
}
