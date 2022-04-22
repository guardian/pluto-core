package streamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, UniformFanOutShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{FileEntryDAO, ProjectEntry, ProjectEntryRow, ValidationJob, ValidationProblem}
import org.slf4j.{LoggerFactory, MDC}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.jdbc.PostgresProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * checks if the given ProjectEntry exists in the expected location(s) or not.
  * If it validates then grabs the next input from the stream, if it does not validate then outputs a ValidationProblem
  */
class ProjectValidationComponent(dbConfigProvider:DatabaseConfigProvider, currentJob:ValidationJob)(implicit mat:Materializer, injector:Injector, fileEntryDAO:FileEntryDAO) extends GeneralValidationComponent[ProjectEntryRow](dbConfigProvider){
  override protected val in:Inlet[ProjectEntry] = Inlet.create("ValidateProjectSwitch.in")
  override protected  val out:Outlet[ValidationProblem] = Outlet.create("ValidateProject.out")

  override def shape = FlowShape.of(in, out)

  override def handleRecord(elem: ProjectEntry): Future[Option[ValidationProblem]] = Future.failed(new RuntimeException("not needed here"))

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private implicit val dbConfig = dbConfigProvider.get[PostgresProfile]
    private implicit val db = dbConfig.db

    setHandler(in, new AbstractInHandler {
      val yesCb = createAsyncCallback[ProjectEntry](_=>pull(in))
      val noCb = createAsyncCallback[ValidationProblem](entry=>push(out, entry))
      val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

      override def onPush(): Unit = {
        val elem = grab(in)

        elem.associatedFiles(true).map(entries=>{
          Future.sequence(entries.map(_.validatePathExists)).map(lookups=>{
            val failures = lookups.collect({case Left(err)=>err})
            if(failures.nonEmpty){
              logger.error(s"Received ${failures.length} errors looking up associated files for ${elem.id} (${elem.projectTitle}): ")
              MDC.put("errors",failures.toString())
              failures.foreach(err=>logger.error(s"\t$err"))
              errorCb.invoke(new RuntimeException(s"Received ${failures.length} errors looking up associated files for ${elem.id} (${elem.projectTitle}), please consult log"))
            } else {
              val notexist = lookups.collect({case Right(result)=>result}).filter(_==false)
              if(notexist.nonEmpty){
                val errorMsg = s"Project ${elem.id} (${elem.projectTitle}) is missing ${notexist.length} files out of ${lookups.length}!"
                ValidationProblem.fromProjectEntry(elem, currentJob, Some(errorMsg)) match {
                  case Some(problem)=>noCb.invoke(problem)
                  case None=>errorCb.invoke(new RuntimeException(s"Project entry $elem is invalid, I can't generate a problem report from it"))
                }
              } else {
                yesCb.invoke(elem)
              }
            }
          })
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = if(!hasBeenPulled(in)) pull(in)
    })
  }
}
