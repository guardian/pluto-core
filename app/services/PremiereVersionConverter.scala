package services

import akka.stream.{IOResult, Materializer}
import akka.stream.alpakka.xml.scaladsl.{XmlParsing, XmlWriting}
import akka.stream.alpakka.xml.{Attribute, StartElement}
import akka.stream.scaladsl.{Compression, FileIO, Keep}
import models._
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import postrun.{AdobeXml, RunXmlLint}
import slick.jdbc.PostgresProfile

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, MetaData, Node, UnprefixedAttribute}

class PremiereVersionConverter @Inject() (backupService:NewProjectBackup)(implicit fileEntryDAO:FileEntryDAO, ec:ExecutionContext, dbConfigProvider:DatabaseConfigProvider, mat:Materializer) extends AdobeXml {
  private final val logger = LoggerFactory.getLogger(getClass)

  def backupFile(fileEntry:FileEntry) = {
    logger.warn("starting backupFile")
    Future.sequence(Seq(
      backupFileToTemp(fileEntry),
      backupFileToStorage(fileEntry)
    ))
      .map(results=>{
        logger.warn(s"backupFile completed, results were $results")
        results
      })
      .map(_.head.asInstanceOf[Path])
  }

  def restoreFromBackup(backupFile:Path, fileEntry:FileEntry) = {
    logger.info(s"restoring $fileEntry file from backup")
    for {
      mainPath <- fileEntryDAO.getJavaPath(fileEntry)
      result <- newCopyFile(backupFile, mainPath)
      _ <- Future.fromTry({
        logger.info(s"Copied ${result.count} bytes from $backupFile to $mainPath")
        result.status
      })
    } yield result
  }

  def backupFileToStorage(fileEntry: FileEntry) = {
    implicit val db = dbConfigProvider.get[PostgresProfile].db
    logger.warn("in backupFileToStorage")
    for {
      projectStorage <- StorageEntryHelper.entryFor(fileEntry.storageId)
      backupStorage <- projectStorage.flatMap(_.backsUpTo).map(StorageEntryHelper.entryFor).getOrElse(Future(None))
      result <- backupStorage match {
        case Some(actualBackupStorage)=>
          logger.info(s"Creating an incremental backup for ${fileEntry.filepath} on storage ${actualBackupStorage.storageType} ${actualBackupStorage.id}")
          for {
            maybeProjectEntry <- ProjectEntry.projectForFileEntry(fileEntry)
            mostRecentBackup <- if (maybeProjectEntry.isDefined) maybeProjectEntry.get.mostRecentBackup(db, mat, actualBackupStorage.id) else Future(None)
            result <- backupService.performBackup(fileEntry, mostRecentBackup, actualBackupStorage).map(Some.apply)
          } yield result
        case None=>
          logger.warn(s"Project for ${fileEntry.filepath} is on a storage which has no backup configured. Cannot make an incremental backup for it.")
          Future(None)
      }
    } yield result
  }.map(result=>{
    logger.warn(s"completed backupFileToStorage, result was $result")
    result
  })

  /**
    * Makes a backup copy of the file pointed to by the given FileEntry in the system default temporary location
    * @param fileEntry FileEntry to back up
    * @return a Future, containing a File pointing to the backed-up location
    */
  def backupFileToTemp(fileEntry:FileEntry):Future[Path] = {
    logger.warn("in backupFileToTemp")
    for {
      inPath <- fileEntryDAO.getJavaPath(fileEntry)
      outPath <- Future({
        val tempFile = File.createTempFile(fileEntry.filepath, ".bak")
        Paths.get(tempFile.getPath)
      })
      result <- {
        logger.warn(s"about to run newCopyFile from $inPath to $outPath")
        newCopyFile(inPath, outPath)
          .map(result=>{
            logger.warn(s"newCopyFile completed with result $result")
            result
          })
      }
      _ <- Future.fromTry(result.status)
      rtn <- if(Files.size(inPath) != Files.size(outPath)) Future.failed(new RuntimeException(s"Local cache copy failed, expected length ${Files.size(inPath)} but got ${Files.size(outPath)}")) else Future(outPath)
    } yield rtn
  }


  /**
    * Returns a failed future indicating that no update is necessary if the current file and the target version are equal
    * @param file
    * @param targetVersion
    * @return
    */
  def checkExistingVersion(file:FileEntry, targetVersion: PremiereVersionTranslation):Future[Unit] =
    file.maybePremiereVersion match {
      case None=>Future.failed(new RuntimeException("The target file is not recognised as having a premiere version"))
      case Some(version)=>
        if(version==targetVersion.internalVersionNumber) {
          Future.failed(new RuntimeException("The target file is already at the requested version"))
        } else {
          Future( () )
        }
    }

  def tweakProjectVersionStreaming(targetFile:Path, backupFile:Path, currentVersion:Int, newVersion:PremiereVersionTranslation) = {
    def canFindAttribute(attribs:List[akka.stream.alpakka.xml.Attribute], key:String): Option[String] = {
      attribs.find(_.name == key).map(_.value)
    }
    logger.warn("in tweakProjectVersionStreaming")

    FileIO.fromPath(backupFile)
      .via(Compression.gunzip())
      .via(XmlParsing.parser)
      .map({
        case elem@StartElement("Project", attributesList, prefix, namespace, namespaceCtx)=>
          logger.debug(s"Found projectNode with attributes $attributesList")
          canFindAttribute(attributesList, "Version") match {
            case Some(oldVersion)=>
              if(oldVersion!=currentVersion.toString) {
                logger.warn(s"${targetFile.toString}: Expected current version of $currentVersion but got $oldVersion")
              }
              logger.info(s"Changing version from $oldVersion to ${newVersion.internalVersionNumber} in ${targetFile.toString}")
              val newAttributes = attributesList.filter(_.name!="Version") :+ Attribute("Version", newVersion.internalVersionNumber.toString)
              elem.copy(attributesList=newAttributes)
            case None=>
              elem
          }
        case other@_ => other

      })
      .via(XmlWriting.writer(StandardCharsets.UTF_8))
      .via(Compression.gzip)
      .toMat(FileIO.toPath(targetFile))(Keep.right)
      .run()
      .flatMap(result=>{
        logger.info(s"Output to ${targetFile.toString} completed. Wrote ${result.count} bytes")
        Future.fromTry(result.status)
      })
      .flatMap(_=>Future.fromTry(RunXmlLint.runXmlLint(targetFile.toAbsolutePath.toString)))
  }

  /**
    * Changes the internal version number for the given Premiere project `targetFile` to the new value given in `newVersion`.
    * Returns a failed future on error, or an empty Future on success.
   */
  def tweakProjectVersion(targetFile:File, backupFile:File, currentVersion:Int, newVersion:PremiereVersionTranslation) = Future.fromTry({
    for {
      xmlContent <- getXmlFromGzippedFile(targetFile.getAbsolutePath)
      updatedXml <- Try { new RuleTransformer(new PremiereVersionConverter.ProjectVersionTweaker(newVersion, currentVersion)).transform(xmlContent).head }
      _ <- putXmlToGzippedFile(targetFile.getAbsolutePath,Elem.apply(updatedXml.prefix, updatedXml.label, updatedXml.attributes, updatedXml.scope, false, updatedXml.child :_*))
      formattedResult <- RunXmlLint.runXmlLint(targetFile.getAbsolutePath)
    } yield formattedResult
  }).recoverWith({
    case err:Throwable=>
      logger.error(s"Could not update project version for ${targetFile.getAbsolutePath} to $newVersion: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
      logger.info(s"Restoring backup from $backupFile...")
      Try { FileUtils.copyFile(backupFile, targetFile) } match {
        case Success(_) => Future.failed(err)           //if the copy-back succeeds pass on the original error
        case Failure(copyErr)=> Future.failed(copyErr)  //if the copy-back fails pass on that error
      }
  })

  def newCopyFile(fromFile:Path, toFile:Path)(implicit mat:Materializer) : Future[IOResult] = {
    import java.nio.file.StandardOpenOption._
    FileIO.fromPath(fromFile).runWith(FileIO.toPath(toFile, Set(WRITE, TRUNCATE_EXISTING, SYNC)))
  }
}

object PremiereVersionConverter {
  private final val logger = LoggerFactory.getLogger(getClass)

  class ProjectVersionTweaker(newVersion:PremiereVersionTranslation, oldVersion:Int) extends RewriteRule {
    def canFindAttribute(attribs: MetaData, key: String): Boolean ={
      if(attribs.key==key){
        true
      } else {
        if(attribs.next!=null){
          canFindAttribute(attribs.next, key)
        } else {
          false
        }
      }
    }

    override def transform(n: Node): collection.Seq[Node] = n match {
      case Elem(prefix, "Project", attributes,scope,children@_*)=>
        logger.debug(s"Found projectNode with attributes $attributes")
        val updatedAttributes:MetaData =
          if(canFindAttribute(attributes, "Version")) {
            new UnprefixedAttribute("Version", newVersion.internalVersionNumber.toString, attributes.remove("Version"))
          } else {
            attributes
          }

        Elem.apply(prefix,"Project", updatedAttributes, scope, true,children:_*)
      case other=>other
    }
  }
}
