package postrun
import helpers.{AsyncFileWriter, PostrunDataCache}
import models.{PlutoCommission, PlutoWorkingGroup, ProjectEntry, ProjectType}
import org.slf4j.LoggerFactory

import java.nio.file.{Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class CreatePointerFile extends PojoPostrun {
  private val logger = LoggerFactory.getLogger(getClass)
  private val xtensionXtractor="^(.*)\\.([^.]+)$".r

  protected def removeProjectFileExtension(projectFileName:String) = projectFileName match {
    case xtensionXtractor(barePath,_)=>barePath
    case _=>
      logger.warn(s"The project file '$projectFileName' does not appear to have a file extension")
      projectFileName
  }

  protected def writePointerFile(filePath:Path, content:String) = {
    val buf = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8))
    AsyncFileWriter
      .writeFileAsync(filePath, buf)
      .map(bytesWritten=>{
        if(bytesWritten==buf.array().length) {
          bytesWritten
        } else {
          throw new RuntimeException(s"Incorrect write for pointer file, expected ${buf.array().length} bytes got $bytesWritten")
        }
      })
  }

  override def postrun(projectFileName: String,
                       projectEntry: ProjectEntry,
                       projectType: ProjectType,
                       dataCache: PostrunDataCache,
                       workingGroupMaybe: Option[PlutoWorkingGroup],
                       commissionMaybe: Option[PlutoCommission]): Future[Try[PostrunDataCache]] = {
    dataCache.get("created_asset_folder") match {
      case None =>
        Future(Failure(new RuntimeException("No created_asset_folder value in data cache. This action must depend on make_asset_folder.")))
      case Some(assetFolderPath) =>
        val targetFilePath = Paths.get(removeProjectFileExtension(projectFileName) + ".ptr")
        logger.info(s"Creating pointer file for $assetFolderPath at $targetFilePath")
        writePointerFile(targetFilePath, assetFolderPath)
          .map(_=>Success(dataCache))
          .recover({
            case err:Throwable=>
              logger.error(s"Could not write $targetFilePath: ${err.getMessage}", err)
              Failure(err)
          })
    }

  }
}
