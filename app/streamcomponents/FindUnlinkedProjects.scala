package streamcomponents

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import models.{FileAssociationRow, ProjectEntry, ProjectEntryRow, ValidationJob, ValidationProblem}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{JdbcBackend, PostgresProfile}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FindUnlinkedProjects(dbConfigProvider:DatabaseConfigProvider, currentJob:ValidationJob)(implicit ec:ExecutionContext) extends GeneralValidationComponent[ProjectEntryRow](dbConfigProvider) {
  private val logger = LoggerFactory.getLogger(getClass)
  override protected final val in:Inlet[ProjectEntry] = Inlet.create("FindUnlinkedProjects.in")
  override protected final val out:Outlet[ValidationProblem] = Outlet.create("FindUnlinkedProjects.out")
  private implicit lazy val db: JdbcBackend#DatabaseDef = dbConfigProvider.get[PostgresProfile].db

  override def shape: FlowShape[ProjectEntry, ValidationProblem] = FlowShape.of(in, out)

  def getRecordCount(projectId:Int)(implicit db:JdbcBackend#DatabaseDef) = db.run(
    TableQuery[FileAssociationRow].filter(_.projectEntry===projectId).length.result
  )

  override def handleRecord(elem: ProjectEntry): Future[Option[ValidationProblem]] = {
    getRecordCount(elem.id.get)
      .map(fileCount=>{
        if(fileCount>0) {
          logger.info(s"No problem found with ${elem.id.getOrElse(0)} ${elem.projectTitle}")
          None
        } else {
          logger.info(s"${elem.id.getOrElse(0)} ${elem.projectTitle} has no project files attached")
          ValidationProblem.fromProjectEntry(elem, currentJob, Some("No project files attached to this entry"))
        }
      })
  }

  /**
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private implicit val db: JdbcBackend#DatabaseDef = dbConfigProvider.get[PostgresProfile].db
    private var asyncInProgress = false
    private var shouldComplete = false

    private val noProblemCb = createAsyncCallback[Unit](_=>{
      if(shouldComplete) {
        completeStage()
      } else {
        pull(in)
      }
    })

    private val problemFoundCb = createAsyncCallback[ValidationProblem](p=>{
      push(out,p)
      if(shouldComplete) completeStage()
    })

    private val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        asyncInProgress=true
        getRecordCount(elem.id.get).onComplete({
          case Success(fileCount)=>
            if(fileCount>0) {
              logger.info(s"No problem found with ${elem.id.getOrElse(0)} ${elem.projectTitle}")
              asyncInProgress=false
              noProblemCb.invoke( () )
            } else {
              logger.info(s"${elem.id.getOrElse(0)} ${elem.projectTitle} has no project files attached")
              asyncInProgress=false
              problemFoundCb.invoke(ValidationProblem.fromProjectEntry(elem, currentJob, Some("No project files attached to this entry")).get)
            }
          case Failure(err)=>
            logger.error(s"Could not obtain file count for ${elem.id.getOrElse(0)} ${elem.projectTitle}: ${err.getMessage}", err)
            errorCb.invoke(err)
        })
      }

      override def onUpstreamFinish(): Unit = {
        if(asyncInProgress) {
          shouldComplete = true
        } else {
          completeStage()
        }
      }

    })
  }
  */
}
