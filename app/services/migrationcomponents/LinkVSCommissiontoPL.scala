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

/**
  * Flow stage that takes in a VSCommissionEntity from Vidispine and outputs a PlutoCommission object, which is either
  * a pre-existing one with a matching vidispine ID or a newly created one.
  * A newly created record is NOT yet saved to the database, and has a "None" record ID as a result.
  * @param commissioners
  * @param defaultWorkingGroup
  * @param dbConfig
  */
class LinkVSCommissiontoPL (commissioners:VSGlobalMetadataGroup, vsUserCache: VSUserCache, defaultWorkingGroup:Int) (implicit dbConfig:DatabaseConfigProvider) extends GraphStage[FlowShape[VSCommissionEntity, PlutoCommission]] {
  private final val in:Inlet[VSCommissionEntity] = Inlet.create("LinkVSCommissiontoPL.in")
  private final val out:Outlet[PlutoCommission] = Outlet.create("LinkVSCommissiontoPL.out")
  private final val vsExtractor = "^(\\w{2})-(\\d+)$".r

  override def shape: FlowShape[VSCommissionEntity, PlutoCommission] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)

    private implicit val db = dbConfig.get[PostgresProfile].db

    private val completedCb = createAsyncCallback[PlutoCommission](comm=>push(out, comm))
    private val errorCb = createAsyncCallback[Throwable](err=>failStage(err))

    def createNewCommission(elem:VSCommissionEntity, vsId:String):Future[PlutoCommission] = {
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
                owner = elem.ownerId
                  .map(_.map(uidString=>Try {
                    vsUserCache.lookup(uidString.toInt)
                  }.toOption.flatten))
                  .map(_.collect({case Some(ownerName)=>ownerName}))
                  .map(_.mkString(","))
                  .getOrElse("unknown"),
                notes = elem.notes,
                productionOffice = elem.productionOffice.getOrElse(ProductionOffice.UK),
                originalTitle = None,
                googleFolder = None
              )
            case _=>
              throw new RuntimeException(s"Invalid vidispine ID $vsId")
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
            PlutoCommission.entryForVsid(vsId).flatMap({
              case Some(existingCommission)=>
                logger.info(s"Commission already exists for $vsId (${elem.title}")
                Future(existingCommission)
              case None=>
                logger.info(s"No commission exists for $vsId (${elem.title}")
                createNewCommission(elem, vsId).map(newCommission=>{
                  logger.info(s"created new commission record: $newCommission")
                  newCommission
                })
            })
            .map(completedCb.invoke)
            .recover({
              case err:Throwable=>
                logger.error(s"Could not determine PlutoCommission for ${elem.collectionId}: ", err)
                errorCb.invoke(err)
            })
        }
      }
    })

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
