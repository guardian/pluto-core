package services.migrationcomponents

import java.sql.Timestamp
import java.time.Instant

import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import models.{EntryStatus, PlutoCommission, PlutoWorkingGroup, ProductionOffice}
import org.slf4j.LoggerFactory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

class LinkVSCommissiontoPL (commissioners:VSGlobalMetadataGroup, defaultWorkingGroup:Int) (implicit dbConfig:DatabaseConfigProvider) extends GraphStage[FlowShape[VSCommissionEntity, PlutoCommission]] {
  private final val in:Inlet[VSCommissionEntity] = Inlet.create("LinkVSCommissiontoPL.in")
  private final val out:Outlet[PlutoCommission] = Outlet.create("LinkVSCommissiontoPL.out")
  private final val vsExtractor = "^(\\w{2})-(\\d+)$".r

  override def shape: FlowShape[VSCommissionEntity, PlutoCommission] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private implicit val db = dbConfig.get[PostgresProfile].db

    private val completedCb = createAsyncCallback[PlutoCommission](comm=>push(out, comm))
    private val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

    def createNewCommission(elem:VSCommissionEntity, vsId:String):Future[PlutoCommission] = { //guard for exceptons when manipulating data
      logger.info(s"Commission does not exist for $vsId")
      val maybeWgFut = elem.workingGroupId match {
        case Some(wgId)=>PlutoWorkingGroup.entryForUuid(wgId)
        case None=>Future(None)
      }
      val maybeCommissioner = elem.commissionerId.flatMap(commissioners.valueFor)

      maybeWgFut.map(maybeWg=>{
          vsId match {
            case vsExtractor(siteId, collectionId) =>
              PlutoCommission(
                id = None,
                collectionId = Some(collectionId.toInt),
                siteId = Some(siteId),
                created = elem.created.getOrElse(Timestamp.from(Instant.now())),
                updated = elem.updated.getOrElse(Timestamp.from(Instant.now())),
                title = elem.title.getOrElse("untitled"),
                status = elem.status.getOrElse(EntryStatus.Completed),
                description = elem.description,
                workingGroup = maybeWg.flatMap(_.id).getOrElse(defaultWorkingGroup),
                originalCommissionerName = maybeCommissioner.flatMap(_.entries.get("gnm_subgroup_displayname")),
                scheduledCompletion = elem.scheduledCompletion.getOrElse(Timestamp.from(Instant.now())),
                owner = elem.ownerId.getOrElse("unknown"),
                notes = elem.notes,
                productionOffice = elem.productionOffice.getOrElse(ProductionOffice.UK),
                originalTitle = None
              )
          }
        })
    }

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        elem.collectionId match {
          case None=>
            logger.warn(s"Got a collection with no id! Title: ${elem.title}")
            pull(in)
          case Some(vsId)=>
            PlutoCommission.entryForVsid(vsId).map({
              case Some(existingCommission)=>
                logger.info(s"Commission already exists for $vsId (${elem.title}")
                Success(completedCb.invoke(existingCommission))
              case None=>

            })
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
