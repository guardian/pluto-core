import com.google.inject.{AbstractModule, Provides}
import com.newmotion.akka.rabbitmq.ConnectionActor
import drivers.MXSConnectionManager
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import services.actors.{Auditor, ProjectCreationActor}
import services._

class Module extends AbstractModule with AkkaGuiceSupport {
  private val logger = Logger(getClass)

  override def configure(): Unit = {
    bind(classOf[TestModeWarning]).asEagerSingleton()

    //this makes the actor instance accessible via injection
    bindActor[StorageScanner]("storage-scanner")
    bindActor[ValidateProject]("validate-project-actor")
    bindActor[ProjectCreationActor]("project-creation-actor")
    bindActor[PostrunActionScanner]("postrun-action-scanner")
    bindActor[CommissionStatusPropagator]("commission-status-propagator")
    bindActor[RabbitMqPropagator]("rabbitmq-propagator")
    bindActor[Auditor]("auditor")
    bind(classOf[PeriodicScanReceiver]).asEagerSingleton()

    bind(classOf[MXSConnectionManager]).asEagerSingleton()
  }

}
