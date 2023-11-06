package controllers

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import auth.BearerTokenAuth
import exceptions.{AlreadyExistsException, BadDataException}
import helpers.StorageHelper
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc._
import services.NewProjectBackup
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}




class Files @Inject() (backupService:NewProjectBackup, temporaryFileCreator: play.api.libs.Files.TemporaryFileCreator, override val controllerComponents:ControllerComponents,
                       override val bearerTokenAuth:BearerTokenAuth,
                       override implicit val config: Configuration, dbConfigProvider: DatabaseConfigProvider, cacheImpl:SyncCacheApi, storageHelper:StorageHelper)
                      (implicit mat:Materializer, fileEntryDAO:FileEntryDAO)
  extends GenericDatabaseObjectControllerWithFilter[FileEntry,FileEntryFilterTerms]
    with FileEntrySerializer with FileEntryFilterTermsSerializer
    with ProjectEntrySerializer with ProjectTemplateSerializer {

  implicit val cache:SyncCacheApi = cacheImpl

  val dbConfig = dbConfigProvider.get[PostgresProfile]
  implicit val db = dbConfig.db

  override def deleteid(requestedId: Int) = dbConfig.db.run(
    TableQuery[FileEntryRow].filter(_.id === requestedId).delete.asTry
  )

  override def selectid(requestedId: Int) = {
    dbConfig.db.run(
      TableQuery[FileEntryRow].filter(_.id === requestedId).result.asTry
    )
  }

  override def selectall(startAt:Int, limit:Int) = dbConfig.db.run(
    TableQuery[FileEntryRow].length.result.zip(
      TableQuery[FileEntryRow].drop(startAt).take(limit).result
    )
  )
    .map(Success(_))
    .recover(Failure(_))

  override def validateFilterParams(request: Request[JsValue]): JsResult[FileEntryFilterTerms] = request.body.validate[FileEntryFilterTerms]

  override def selectFiltered(startAt: Int, limit: Int, terms: FileEntryFilterTerms): Future[Try[(Int, Seq[FileEntry])]] = {
    val basequery = terms.addFilterTerms {
      TableQuery[FileEntryRow]
    }

    dbConfig.db.run(
      basequery.length.result.zip(
        basequery.drop(startAt).take(limit).result
      )
    )
      .map(result=>Success(result))
      .recover(err=>Failure(err))
  }

  override def jstranslate(result: Seq[FileEntry]) = result //implicit translation should handle this
  override def jstranslate(result: FileEntry) = result //implicit translation should handle this

  override def insert(entry: FileEntry,uid:String):Future[Try[Int]] = {
    /* only allow a record to be created if no files already exist with that path on that storage */
    fileEntryDAO.allVersionsFor(entry.filepath,entry.storageId).flatMap({
      case Success(fileList)=>
        entry.storage.flatMap({
          case None=>
            Future(Failure(new BadDataException("No storage was specified")))
          case Some(storage)=>
            if(storage.supportsVersions && !fileList.exists(_.version==entry.version)){ //versioning enabled and there is no file already existing with the given version
              val updatedEntry = entry.copy(user = uid)
              dbConfig.db.run(
                (TableQuery[FileEntryRow] returning TableQuery[FileEntryRow].map(_.id) += updatedEntry).asTry
              )
            } else if(storage.supportsVersions) {                                       //versioning enabled and there is a file already existing with the given version
              Future(Failure(new AlreadyExistsException(s"A file already exists at ${entry.filepath} on storage ${entry.storageId}", fileList.headOption.map(_.version+1).getOrElse(1))))
            } else {                                                                    //versioning not enabled
              if(fileList.isEmpty){   //no conflicting file
                val updatedEntry = entry.copy(user = uid)
                dbConfig.db.run(
                  (TableQuery[FileEntryRow] returning TableQuery[FileEntryRow].map(_.id) += updatedEntry).asTry
                )
              } else {
                Future(Failure(new AlreadyExistsException(s"A file already exists at ${entry.filepath} on storage ${entry.storageId} and versioning is not enabled",1)))
              }
            }
        })
      case Failure(error)=>Future(Failure(error))
    })

  }

  override def dbupdate(itemId:Int, entry:FileEntry) = {
    val newRecord = entry.id match {
      case Some(id)=>entry
      case None=>entry.copy(id=Some(itemId))
    }

    dbConfig.db.run(
      TableQuery[FileEntryRow].filter(_.id===itemId).update(newRecord).asTry
    )
  }

  override def validate(request: Request[JsValue]) = request.body.validate[FileEntry]

  def uploadContent(requestedId: Int) = IsAuthenticatedAsync(parse.anyContent) {uid=>{ request =>
    implicit val db = dbConfig.db

    request.body.asRaw match {
      case Some(buffer) =>
        dbConfig.db.run(
          TableQuery[FileEntryRow].filter(_.id === requestedId).result.asTry
        ).flatMap({
          case Success(rows: Seq[FileEntry]) =>
            if (rows.isEmpty) {
              logger.error(s"File with ID $requestedId not found")
              Future(NotFound(Json.obj("status" -> "error", "detail" -> s"File with ID $requestedId not found")))
            } else {
              val fileRef = rows.head
              //get the storage reference for the file
              if(fileRef.hasContent)
                Future(BadRequest(Json.obj("status"->"error","detail"->"This file already has content.")))
              else
                fileEntryDAO.writeToFile(fileRef,buffer)
                  .map(_=>Ok(Json.obj("status" -> "ok", "detail" -> "File has been written.")))
                  .recover({
                    case error:Throwable =>
                      InternalServerError(Json.obj("status" -> "error", "detail" -> error.toString))
                 })
            }
          case Failure(error) =>
            logger.error(s"Could not get file to write: ${error.toString}")
            Future(InternalServerError(Json.obj("status" -> "error", "detail" -> s"Could not get file to write: ${error.toString}")))
        })
      case None =>
        Future(BadRequest(Json.obj("status" -> "error", "detail" -> "No upload payload")))
    }
  }}


  def updateContent(requestedId: Int) = IsAuthenticatedAsync(parse.multipartFormData) { uid => { request =>
    implicit val db = dbConfig.db

    val sha256Option = request.body.dataParts.get("sha256").flatMap(_.headOption)

    def calculateSha256(bytes: Array[Byte]): Future[String] = {
      val md = MessageDigest.getInstance("SHA-256")
      val hashBytes = md.digest(bytes)
      Future.successful(hashBytes.map("%02x".format(_)).mkString)
    }

    request.body.file("file") match {
      case Some(filePart) =>
        val fileBytes = Files.readAllBytes(filePart.ref.path)
        calculateSha256(fileBytes).flatMap { calculatedSha =>
          if (sha256Option.contains(calculatedSha)) {
            db.run(
              TableQuery[FileEntryRow].filter(_.id === requestedId).result.headOption.asTry
            ).flatMap {
              case Success(Some(fileEntry: FileEntry)) =>
                // First, attempt to backup the file
                backupFile(fileEntry).flatMap { backupPath =>
                  // Now that the backup has succeeded, proceed with the update
                  val tempFile = temporaryFileCreator.create("temp", "upload")
                  Files.write(tempFile.path, fileBytes)

                  val playTempFile = temporaryFileCreator.create("prefix", "suffix")
                  Files.write(playTempFile.path, fileBytes) // Write bytes to the temporary file created by TemporaryFileCreator
                  val fileSize = fileBytes.length // Get the size of the file in bytes
                  val buffer = new play.api.mvc.RawBuffer(fileSize, temporaryFileCreator, ByteString(fileBytes))
                  logger.info("About to update file...")
                  fileEntryDAO.writeToFile(fileEntry, buffer).map { _ =>
                    Ok(Json.obj("status" -> "ok", "detail" -> "File content has been updated."))
                  }
                }.recover { case error: Throwable =>
                  InternalServerError(Json.obj("status" -> "error", "detail" -> s"Backup failed: ${error.toString}"))
                }
              case Success(None) =>
                Future(NotFound(Json.obj("status" -> "error", "detail" -> s"File with ID $requestedId not found")))
              case Failure(error) =>
                Future(InternalServerError(Json.obj("status" -> "error", "detail" -> error.toString)))
            }
          } else {
            Future(BadRequest(Json.obj("status" -> "error", "detail" -> s"SHA256 checksum does not match - $sha256Option - $calculatedSha")))
          }
        }

      case None =>
        Future(BadRequest(Json.obj("status" -> "error", "detail" -> "No file provided")))
    }
  }
  }

  def backupFile(fileEntry: FileEntry) = {
    logger.warn("starting backupFile")
    Future.sequence(Seq(
      backupFileToTemp(fileEntry),
      backupFileToStorage(fileEntry)
    ))
      .map(results => {
        logger.warn(s"backupFile completed, results were $results")
        results
      }).map {
      case Some((_, path)) :: _ => path // Extract the Path from the tuple inside the Some
      case _ => throw new Exception("No backup path found")
    }
  }

  def backupFileToStorage(fileEntry: FileEntry) = {
    implicit val db = dbConfigProvider.get[PostgresProfile].db
    logger.warn("in backupFileToStorage")
    for {
      projectStorage <- StorageEntryHelper.entryFor(fileEntry.storageId)
      backupStorage <- projectStorage.flatMap(_.backsUpTo).map(StorageEntryHelper.entryFor).getOrElse(Future(None))
      result <- backupStorage match {
        case Some(actualBackupStorage) =>
          logger.info(s"Creating an incremental backup for ${fileEntry.filepath} on storage ${actualBackupStorage.storageType} ${actualBackupStorage.id}")
          for {
            maybeProjectEntry <- ProjectEntry.projectForFileEntry(fileEntry)
            mostRecentBackup <- if (maybeProjectEntry.isDefined) maybeProjectEntry.get.mostRecentBackup else Future(None)
            result <- backupService.performBackup(fileEntry, mostRecentBackup, actualBackupStorage).map(Some.apply)
          } yield result
        case None =>
          logger.warn(s"Project for ${fileEntry.filepath} is on a storage which has no backup configured. Cannot make an incremental backup for it.")
          Future(None)
      }
    } yield result
  }.map(result => {
    logger.warn(s"completed backupFileToStorage, result was $result")
    result
  })

  def backupFileToTemp(fileEntry: FileEntry): Future[Path] = {
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
          .map(result => {
            logger.warn(s"newCopyFile completed with result $result")
            result
          })
      }
      _ <- Future.fromTry(result.status)
      rtn <- if (Files.size(inPath) != Files.size(outPath)) Future.failed(new RuntimeException(s"Local cache copy failed, expected length ${Files.size(inPath)} but got ${Files.size(outPath)}")) else Future(outPath)
    } yield rtn
  }

  def newCopyFile(fromFile: Path, toFile: Path)(implicit mat: Materializer): Future[IOResult] = {
    import java.nio.file.StandardOpenOption._
    FileIO.fromPath(fromFile).runWith(FileIO.toPath(toFile, Set(WRITE, TRUNCATE_EXISTING, SYNC)))
  }

  def deleteFromDisk(requestedId:Int, targetFile:FileEntry, deleteReferenced: Boolean, isRetry:Boolean=false):Future[Result] = deleteid(requestedId).flatMap({
    case Success(rowCount)=>
      storageHelper.deleteFile(targetFile).flatMap({
        case Right(updatedFile) =>
          targetFile.getFullPath.map(fullpath=> {
            Ok(Json.obj("status" -> "ok", "detail" -> "deleted", "filepath" -> fullpath, "id" -> requestedId))
          })
        case Left(errorString) =>
          targetFile.getFullPath.map(fullpath=>{
            logger.error(s"Could not delete on-disk file $fullpath")
            InternalServerError(Json.obj("status" -> "error", "detail" -> errorString, "filepath" -> fullpath, "id"->requestedId))
          })
      })
    case Failure(error)=>Future(handleConflictErrorsAdvanced(error){
        Conflict(Json.obj("status"->"error","detail"->"This file is still referenced by other things"))
    })
  })

  def delete(requestedId: Int, deleteReferenced: Boolean) = IsAdminAsync {uid=>{ request =>
    selectid(requestedId).flatMap({
      case Success(rowSeq)=>
        rowSeq.headOption match {
          case Some(targetFile)=>
            deleteFromDisk (requestedId, targetFile, deleteReferenced)
          case None=>
            logger.error("No file found")
            Future(NotFound(Json.obj("status"->"error", "detail"->s"nothing found in database for $requestedId")))
        }
      case Failure(error)=>
        logger.error("Could not look up file id: ", error)
        Future(InternalServerError(Json.obj("status"->"error", "detail"->"could not look up file id", "error"->error.toString)))
    })
  }}

  def references(requestedId: Int) = IsAdminAsync {uid=>{request=>
    Future.sequence(Seq(FileAssociation.projectsForFile(requestedId),ProjectTemplate.templatesForFileId(requestedId))).map(resultSeq=>{
      val triedProjectsList = resultSeq.head.asInstanceOf[Try[Seq[ProjectEntry]]]
      val triedTemplatesList = resultSeq(1).asInstanceOf[Try[Seq[ProjectTemplate]]]

      if(triedProjectsList.isSuccess && triedTemplatesList.isSuccess)
        Ok(Json.obj("status"->"ok","projects"->triedProjectsList.get, "templates"->triedTemplatesList.get))
      else
        InternalServerError(Json.obj("status"->"error",
          "projectsError"->triedProjectsList.failed.getOrElse("").toString,
          "templatesError"->triedTemplatesList.failed.getOrElse("").toString
        ))
    }
    )
  }}

  def getDistinctOwnersList:Future[Try[Seq[String]]] = {
    //work around distinctOn bug - https://github.com/slick/slick/issues/1712
    db.run(sql"""select distinct(s_user) from "FileEntry"""".as[String].asTry)
  }

  def distinctOwners = IsAuthenticatedAsync {uid=>{request=>
    getDistinctOwnersList.map({
      case Success(ownerList)=>
        Ok(Json.obj("status"->"ok","result"->ownerList))
      case Failure(error)=>
        logger.error("Could not look up distinct file owners: ", error)
        InternalServerError(Json.obj("status"->"error","detail"->error.toString))
    })
  }}

  def checkOnDisk(fileId:Int) = IsAuthenticatedAsync {uid=>{request=>
    selectid(fileId).flatMap({
      case Success(rows)=>
        if(rows.isEmpty){
          Future(NotFound(Json.obj("status"->"notfound")))
        } else {
          storageHelper.findFile(rows.head).map(result=>Ok(Json.obj("status"->"ok","found"->result)))
        }
      case Failure(err)=>
        Future(InternalServerError(Json.obj("status"->"error", "detail"->err.getMessage)))
    })
  }}

  def fileMetadata(fileId:Int) = IsAuthenticatedAsync {uid=>{request=>
    selectid(fileId).flatMap({
      case Success(rows)=>
        if(rows.isEmpty){
          Future(NotFound(Json.obj("status"->"notfound")))
        } else {
          storageHelper
            .onStorageMetadata(rows.head)
            .map(result=>Ok(Json.obj(
              "status"->"ok",
              "metadata"->Json.obj(
                "size"->result.map(_.size),
                "lastModified"->result.map(_.lastModified.format(DateTimeFormatter.ISO_DATE_TIME))
              )
            )))
        }
      case Failure(err)=>
        Future(InternalServerError(Json.obj("status"->"error", "detail"->err.getMessage)))
    })
  }}

  def projectEntryForFile(fileId:Int)(implicit db: slick.jdbc.PostgresProfile#Backend#Database):Future[Seq[ProjectEntry]] = {
    val query = for {
      (assocRow, projectEntry) <- TableQuery[FileAssociationRow] join TableQuery[ProjectEntryRow] on (_.projectEntry===_.id) if assocRow.fileEntry===fileId
    } yield projectEntry

    db.run(
      query.result
    )
  }

  def projectFromFile(filename:String, startAt:Int, limit:Int, includeBackups:Boolean) = IsAuthenticatedAsync(parse.anyContent) {uid=>{ request =>
    implicit val db = dbConfig.db
    val baseQuery = TableQuery[FileEntryRow].filter(_.filepath===filename)
    val filteredQuery = if(includeBackups) baseQuery else baseQuery.filter(_.backupOf.isEmpty)
    dbConfig.db.run(
      filteredQuery.sortBy(_.version.desc.nullsLast).drop(startAt).take(limit).result.asTry
    ).flatMap({
      case Success(rows: Seq[FileEntry]) =>
        if (rows.isEmpty) {
          logger.error(s"File with name $filename not found")
          Future(NotFound(Json.obj("status" -> "error", "detail" -> s"File with name $filename not found")))
        } else {
          val projectEntriesFut:Future[Seq[Option[ProjectEntry]]] = Future.sequence(rows.map(_.id).collect({case Some(id)=>id}).map(projectEntryForFile).map(_.map(_.headOption)))
          projectEntriesFut.map(json_output=>Ok(Json.obj("status" -> "ok", "detail" -> s"File found for $filename", "file_data" -> rows, "project_data" -> json_output.collect({case Some(record)=>record}))))
        }
      case Failure(error) =>
        logger.error(s"Could not look up file: ${error.toString}")
        Future(InternalServerError(Json.obj("status" -> "error", "detail" -> s"Could not look up file: ${error.toString}")))
    })
  }}

}

case class RenameFileRequest(newName: String)

