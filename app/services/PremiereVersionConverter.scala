package services

import models.{FileEntry, FileEntryDAO, PremiereVersionTranslation}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import postrun.AdobeXml

import java.io.File
import java.nio.file.Paths
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.transform.RewriteRule
import scala.xml.{Elem, MetaData, Node, UnprefixedAttribute}

class PremiereVersionConverter @Inject() (implicit fileEntryDAO:FileEntryDAO, ec:ExecutionContext) extends AdobeXml {
  private final val logger = LoggerFactory.getLogger(getClass)

  /**
    * Makes a backup copy of the file pointed to by the given FileEntry in the system default temporary location
    * @param fileEntry FileEntry to back up
    * @return a Future, containing a File pointing to the backed-up location
    */
  def backupFile(fileEntry:FileEntry) = for {
    sourceFile <- fileEntryDAO.getJavaFile(fileEntry)
    result <- Future.fromTry({
      val p = Paths.get(fileEntry.filepath)
      val tempFile = File.createTempFile(p.getFileName.toString, ".bak")

      Try {
        FileUtils.copyFile(sourceFile, tempFile)
      }.map(_=>tempFile)
    })
  } yield result

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

        Elem.apply(prefix,"Project", updatedAttributes, scope, true,children:_*)  //pass it on unchanged
      case other=>other
    }
  }

  /**
    * Changes the internal version number for the given Premiere project `targetFile` to the new value given in `newVersion`.
    * Returns a failed future on error, or an empty Future on success.
   */
  def tweakProjectVersion(targetFile:File, backupFile:File, currentVersion:Int, newVersion:PremiereVersionTranslation) = Future.fromTry({
    for {
      xmlContent <- getXmlFromGzippedFile(targetFile.getAbsolutePath)
      updatedXml <- Try { new ProjectVersionTweaker(newVersion, currentVersion).transform(xmlContent).head }
      writeResult <- putXmlToGzippedFile(targetFile.getAbsolutePath,Elem.apply(updatedXml.prefix, updatedXml.label, updatedXml.attributes, updatedXml.scope, false, updatedXml.child :_*))
    } yield writeResult
  }).recoverWith({
    case err:Throwable=>
      logger.error(s"Could not update project version for ${targetFile.getAbsolutePath} to $newVersion: ${err.getClass.getCanonicalName} ${err.getMessage}", err)
      logger.info(s"Restoring backup from $backupFile...")
      Try { FileUtils.copyFile(backupFile, targetFile) } match {
        case Success(_) => Future.failed(err)           //if the copy-back succeeds pass on the original error
        case Failure(copyErr)=> Future.failed(copyErr)  //if the copy-back fails pass on that error
      }
  })
}
