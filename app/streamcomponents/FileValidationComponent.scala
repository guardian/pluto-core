package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import models.{FileEntry, FileEntryDAO, FileEntryRow, ValidationJob, ValidationProblem}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}

class FileValidationComponent(dbConfigProvider:DatabaseConfigProvider, currentJob:ValidationJob)(implicit ec:ExecutionContext, injector:Injector, fileEntryDAO: FileEntryDAO) extends GeneralValidationComponent[FileEntryRow](dbConfigProvider) {
  private val logger = LoggerFactory.getLogger(getClass)
  override protected final val in:Inlet[FileEntry] = Inlet.create("FileValidationComponent.in")
  override protected final val out:Outlet[ValidationProblem] = Outlet.create("FileValidationComponent.out")
  private implicit val db = dbConfigProvider.get[PostgresProfile].db

  override def shape: FlowShape[FileEntry, ValidationProblem] = FlowShape.of(in, out)

  override def handleRecord(elem: FileEntry): Future[Option[ValidationProblem]] = {
    elem.validatePathExists.flatMap({
      case Left(err)=>
        Future(ValidationProblem.fromFileEntry(elem, currentJob, Some(s"Could not validate path: $err")))
      case Right(true)=>  //path does exist
        Future(None)
      case Right(false)=> //path does not exist
        elem.backupsCount().map(backupsCount=>{
          val isBackupMsg = if(elem.backupOf.isDefined) "This file is a backup." else s"This file is a master, there are $backupsCount backups in the system"

          val errMsg = s"${elem.filepath} on storage ${elem.storageId} does not exist. $isBackupMsg"
          ValidationProblem.fromFileEntry(elem, currentJob, Some(errMsg))
        })
    })
  }

  override def logError(elem: FileEntry, err: Throwable): Unit = logger.error(s"Could not verify file entry $elem: ${err.getMessage}", err)
}
