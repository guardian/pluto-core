package streamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{ProjectEntry, ProjectType, ValidationJob, ValidationProblem}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import slick.lifted.AbstractTable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Represents an Akka stage that can be used as a validation component.
  *
  * Any implentor must take in a data record from the given table, and if a problem is detected it should output
  * a ValidationProblem record.
  *
  * The data type of the incoming element is the record type, e.g. for GeneralValidationComponent[ProjectEntryRow] then
  * the incoming element is of type `ProjectEntry`, NOT `ProjectEntryRow`.
  *
  * If no problem is detected then it should simply re-pull the input and output nothing.
  * @tparam E the row type of the table that is being queried, e.g. ProjectEntryRow
  */
abstract class GeneralValidationComponent[E<:AbstractTable[_]](dbConfigProvider:DatabaseConfigProvider)(implicit ec:ExecutionContext) extends GraphStage[FlowShape[E#TableElementType,ValidationProblem]] {
  protected val in:Inlet[E#TableElementType]
  protected val out:Outlet[ValidationProblem]

  /**
    * an implementor must supply this function.
    * It takes a stream record as a parameter and returns None if no problem was detected or a ValidationProblem
    * if one was detected
    * @param elem the incoming stream element
    * @return a Future containing either a ValidationProblem or None. This future should fail on error.
    */
  def handleRecord(elem:E#TableElementType):Future[Option[ValidationProblem]]

  /**
    * callback that allows an implementor to emit a custom error message if an exception is detected
    * @param elem the stream element on which the error occurred
    * @param err the error that happened
    */
  def logError(elem:E#TableElementType, err:Throwable):Unit = {}

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private implicit val db = dbConfigProvider.get[PostgresProfile].db

    protected var asyncInProgress = false
    protected var shouldComplete = false

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

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    setHandler(in, new AbstractInHandler {


      override def onPush(): Unit = {
        val elem = grab(in)

        asyncInProgress = true

        handleRecord(elem).onComplete({
          case Success(Some(problem))=>
            asyncInProgress = false
            problemDetectedCb.invoke(problem)
          case Success(None)=>
            asyncInProgress = false
            noProblemCb.invoke()
          case Failure(exception)=>
            asyncInProgress = false

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
