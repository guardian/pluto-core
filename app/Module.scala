import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.google.inject.{AbstractModule, Provides}
import com.newmotion.akka.rabbitmq.ConnectionActor
import helpers.JythonRunner
import javax.inject.{Named, Singleton}
import play.api.Logger
import play.api.inject.Injector
import play.api.libs.concurrent.AkkaGuiceSupport
import services.actors.ProjectCreationActor
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
    bindActor[StorageScanner]("storage-scanner")
    bindActor[ValidateProject]("validate-project-actor")
  }

  @Provides
  @Singleton
  @Named("project-creation-actor")
  def projectCreationActorFactory(system:ActorSystem, injector: Injector): ActorRef = {
    logger.info("projectCreationActor building...")
    ProjectCreationActor.startupSharding(system, injector)
  }

  //set up postrun action scanner as a cluster singleton
  @Provides
  @Singleton
  @Named("postrun-action-scanner")
  def postrunActionScannerFactory(system: ActorSystem, injector: Injector): ActorRef = {
    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(injector.instanceOf(classOf[PostrunActionScanner])),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)
    ))
  }

  @Provides
  @Singleton
  @Named("commission-status-propagator")
  def commissionStatusPropagatorFactory(system:ActorSystem, injector: Injector): ActorRef = {
    logger.info("commissionStatusPropagatorFactory building...")
    CommissionStatusPropagator.startupSharding(system, injector)
  }

  @Provides
  @Singleton
  @Named("rabbitmq-propagator")
  def rabbitMqPropagatorFactory(system:ActorSystem, injector: Injector): ActorRef = {
    logger.info("rabbitMQPropagatorFactory building...")
    RabbitMqPropagator.startupSharding(system, injector)
  }
}
