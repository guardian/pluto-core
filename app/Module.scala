import com.google.inject.AbstractModule
import com.newmotion.akka.rabbitmq.ConnectionActor
import helpers.JythonRunner
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import services.actors.{MessageProcessorActor, ProjectCreationActor}
import services._

class Module extends AbstractModule with AkkaGuiceSupport {
  private val logger = Logger(getClass)

  override def configure(): Unit = {
    JythonRunner.initialise

    bind(classOf[TestModeWarning]).asEagerSingleton()

    if(!sys.env.contains("CI")) {
      bind(classOf[AppStartup]).asEagerSingleton()
    }
    //this makes the actor instance accessible via injection
    bindActor[MessageProcessorActor]("message-processor-actor")
    bindActor[ProjectCreationActor]("project-creation-actor")
    bindActor[PostrunActionScanner]("postrun-action-scanner")
    bindActor[StorageScanner]("storage-scanner")
    bindActor[ValidateProject]("validate-project-actor")
    bindActor[CommissionStatusPropagator]("commission-status-propagator")
    bindActor[RabbitMqPropagator]("rabbitmq-propagator")
  }
}
