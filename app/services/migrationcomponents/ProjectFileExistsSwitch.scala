package services.migrationcomponents

import java.io.File

import akka.stream.{Attributes, Inlet, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.ProjectEntry
import org.slf4j.LoggerFactory

class ProjectFileExistsSwitch(inPath:String)  extends GraphStage[UniformFanOutShape[ProjectEntry, ProjectFileResult]] {
  private final val logger = LoggerFactory.getLogger(getClass)
  private final val in:Inlet[ProjectEntry] = Inlet.create("ProjectFileExistsSwitch.in")
  private final val yes:Outlet[ProjectFileResult] = Outlet.create("ProjectFileExistsSwitch.yes")
  private final val no:Outlet[ProjectFileResult] = Outlet.create("ProjectFileExistsSwitch.no")

  //yeah this is a bit of a cheat but we only really need this code once
  val possibleExtensions = Seq("prproj","plproj","cpr","ptr","aep")

  /*
  very simple check, does the file exist?
   */
  def doesFileExist(filePath:String) = new File(filePath).exists()

  override def shape = UniformFanOutShape(in, yes, no)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val defaultOutletHandler = new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    }

    setHandler(yes, defaultOutletHandler)
    setHandler(no, defaultOutletHandler)

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.vidispineProjectId match {
          case Some(vsProjectId)=>
            val possiblePaths = possibleExtensions.map(xtn=>s"$inPath/$vsProjectId.$xtn")
            logger.debug(s"Checking $possiblePaths")
            val existing = possiblePaths.filter(doesFileExist)
            logger.debug(s"Paths existing are $existing")
            if(existing.nonEmpty) {
              push(yes, ProjectFileResult(elem, existing))
            } else {
              push(no, ProjectFileResult(elem, Seq()))
            }
          case None=>
            logger.warn(s"Project ${elem.id} (${elem.projectTitle}) has no VS ID and therefore no legacy project")
            push(no, ProjectFileResult(elem, Seq()))
        }
      }
    })
  }
}
