package services.migrationcomponents

import java.sql.Timestamp
import java.time.Instant

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import models._
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * this flow takes in a VSProjectEntity and converts it into a ProjectEntry.
  * - if there is a project entry in the database with a matching VSID, then the new (non-Projectlocker) fields and
  * title are updated only and the rest of the entry is passed on to the output
  * - if there is no matching project entry, then a new entry is created
  * @param createProjectTypeId there is no easy way to map the project type in Pluto/VS to the project type in pluto-core.
  *                            So a "migrated" type must be created, and you put the ID of that here
  * @param vsUserCache initialised VSUserCache for looking up VS users
  * @param dbConfigProvider implicitly provided database configuration
  */
class LinkVStoPL (createProjectTypeId:Int, vsUserCache: VSUserCache)(implicit dbConfigProvider:DatabaseConfigProvider)
  extends GraphStage[FlowShape[VSProjectEntity, ProjectEntry]] {

  private final val logger = LoggerFactory.getLogger(getClass)
  private final val in:Inlet[VSProjectEntity] = Inlet.create("LinkVStoPL.in")
  private final val out:Outlet[ProjectEntry] = Outlet.create("LinkVStoPL.out")

  override def shape: FlowShape[VSProjectEntity, ProjectEntry] = FlowShape.of(in, out)

  def getVSProjectStatus(vsProject:VSProjectEntity) = Try {
    vsProject.getSingle("gnm_project_status").map(statusString=>{
      if(statusString=="In production") { //some entities have the incorrect version with lowercase p which won't match
        EntryStatus.InProduction
      } else {
        EntryStatus.withName(statusString)
      }
    })
  }.toOption.flatten

  def getVSProductionOffice(vsProject:VSProjectEntity) = Try {
    vsProject.getSingle("gnm_project_production_office").map(ProductionOffice.withName)
  }.toOption.flatten

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private implicit val db = dbConfigProvider.get[PostgresProfile].db

    val successCb = createAsyncCallback[ProjectEntry](ent=>
      push(out, ent)
    )
    val errCb     = createAsyncCallback[Throwable](err=>failStage(err))

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val vsProject = grab(in)

        val maybeProjectStatus = getVSProjectStatus(vsProject)

        val maybeProductionOffice = getVSProductionOffice(vsProject)

        //see if we have an existing project with that id
        val projectLookupFut = vsProject.getMetaOptional("collectionId").flatMap(_.headOption) match {
          case Some(vsid)=>
            ProjectEntry.lookupByVidispineId(vsid).map({
              case Failure(err)=>throw err
              case Success(resultList)=>resultList.headOption //should only be 1 or 0 results on this query
            })
          case None=>
            Future.failed(new RuntimeException("Incoming VS project had no collection ID!"))
        }

        //build the new project out of the old one. if we have an existing project, then only update the "new" fields;
        //otherwise, create a whole new record
        val updatedFut = projectLookupFut.flatMap({
          case None=>
            logger.debug(s"No pre-existing project for ${vsProject.getSingle("collectionId")}, creating new")
            Future.sequence(Seq(
              ProjectHelper.findWorkingGroup(vsProject),
              ProjectHelper.findCommission(vsProject)
            )).map(results=> {
              val maybeWg = results.head.asInstanceOf[Option[PlutoWorkingGroup]]
              val maybeCommission = results(1).asInstanceOf[Option[PlutoCommission]]

              ProjectEntry(
                None,
                createProjectTypeId,
                vsProject.getSingle("collectionId"),
                projectTitle = vsProject.title.getOrElse("(no title)"),
                created = vsProject.created.getOrElse(Timestamp.from(Instant.now)),
                updated = vsProject.updated.getOrElse(Timestamp.from(Instant.now)),
                user = vsProject.owner_id_list.map(vsUserCache.lookup).collect({case Some(user)=>user}).mkString("|"),
                workingGroupId = maybeWg.flatMap(_.id),
                commissionId = maybeCommission.flatMap(_.id),
                deletable = Some(vsProject.isDeletable),
                deep_archive = Some(vsProject.isDeepArchive),
                sensitive = Some(vsProject.isSensitive),
                status = maybeProjectStatus.getOrElse(EntryStatus.Completed),
                productionOffice = maybeProductionOffice.getOrElse(ProductionOffice.UK),
                None,
                Some(false)
              )
            })
          case Some(existingProject)=>
            logger.debug(s"Updating existing project ${existingProject.id} (${existingProject.vidispineProjectId}")
            /*
            if we have an entry already in the database, that's from Projectlocker.
            in that case, we must apply the fields that did not exist in Projectlocker, and update the title
            because an updated title in Pluto would not reflect in PL
             */
            Future(existingProject.copy(
              projectTitle = vsProject.title.getOrElse(existingProject.projectTitle),
              updated = vsProject.updated.getOrElse(Timestamp.from(Instant.now())),
              status  = maybeProjectStatus.getOrElse(EntryStatus.Completed),
              productionOffice = maybeProductionOffice.getOrElse(ProductionOffice.UK)
            ))
        })

        updatedFut.onComplete({
          case Success(newProjectEntry)=>
            logger.info(s"Successfully update project ${vsProject.getSingle("collectionId")} to ${newProjectEntry}")
            successCb.invoke(newProjectEntry)
          case Failure(err)=>
            logger.error(s"Could not perform project update on ${vsProject.getSingle("collectionId")}: ", err)
            errCb.invoke(err)
        })
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
