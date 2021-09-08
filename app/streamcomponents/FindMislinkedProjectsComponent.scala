package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import models.{FileEntry, ProjectEntry, ProjectEntryRow, ProjectType, ValidationJob, ValidationProblem}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FindMislinkedProjectsComponent (dbConfigProvider:DatabaseConfigProvider, currentJob:ValidationJob)(implicit ec:ExecutionContext) extends GeneralValidationComponent[ProjectEntryRow] {
  private val logger = LoggerFactory.getLogger(getClass)

  private final val in:Inlet[ProjectEntry] = Inlet.create("FindMislinkedProjectsComponent.in")
  private final val out:Outlet[ValidationProblem] = Outlet.create("FindMislinkedProjectsComponent.out")

  override def shape: FlowShape[ProjectEntry, ValidationProblem] = FlowShape.of(in, out)

  def formatProblemList(lst:Seq[FileEntry]):String = {
    lst
      .map(entry=>s"${entry.filepath} on storage id ${entry.storageId}")
      .mkString(", ")
  }

  def validateProjectExtension(projectEntry:ProjectEntry, projectType:ProjectType, associatedFiles:Seq[FileEntry]):Option[ValidationProblem] = {
    projectType.fileExtension.map(_.toLowerCase) match {
      case None=>
        logger.warn(s"Can't check file extensions for project type ${projectType.id} ${projectType.name} because there is no file extension registered in the system for it")
        None
      case Some(xtn)=>
        val problemFiles = associatedFiles.filter(! _.filepath.toLowerCase.endsWith(xtn))
        if(problemFiles.isEmpty) {
          logger.info(s"No problems with ${projectEntry.id.getOrElse(0)} ${projectEntry.projectTitle}")
          None
        } else {
          logger.info(s"Found ${problemFiles.length} mislinked files on ${projectEntry.projectTitle}:")
          problemFiles.foreach(f=>{
            logger.info(s"\t${projectEntry.projectTitle}: ${f.filepath} on ${f.storageId}")
          })

          val problemMsg = s"${problemFiles.length} files with wrong extension: ${formatProblemList(problemFiles)}"
          ValidationProblem.fromProjectEntry(projectEntry, currentJob, Some(problemMsg))
        }
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private implicit val db = dbConfigProvider.get[PostgresProfile].db

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    setHandler(in, new AbstractInHandler {
      private var asyncInProgress = false
      private var shouldComplete = false

      val noProblemCb = createAsyncCallback[Unit](_=>{
        if(shouldComplete) {
          completeStage();
        } else {
          pull(in)
        }
      })

      val problemDetectedCb = createAsyncCallback[ValidationProblem](entry=>{
        push(out, entry)
        if(shouldComplete) completeStage()
      })

      val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

      override def onPush(): Unit = {
        val elem = grab(in)

        asyncInProgress = true
        val problemFut = for {
          typeInfo <- ProjectType.entryFor(elem.projectTypeId)
          associatedFiles <- elem.associatedFiles(true)
          result <- Future(validateProjectExtension(elem, typeInfo, associatedFiles))
        } yield result

        problemFut.onComplete({
          case Success(Some(problem))=>
            asyncInProgress = false
            problemDetectedCb.invoke(problem)
          case Success(None)=>
            asyncInProgress = false
            noProblemCb.invoke()
          case Failure(exception)=>
            asyncInProgress = false
            logger.error(s"Could not validate project ${elem.projectTitle} (${elem.id}): ${exception.getMessage}", exception)
            errorCb.invoke(exception)
        })
      }

      override def onUpstreamFinish(): Unit = {
        if(asyncInProgress) {
          shouldComplete = true
        } else {
          completeStage()
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        if(asyncInProgress) {
          shouldComplete = true
        } else {
          completeStage()
        }
      }
    })
  }
}
