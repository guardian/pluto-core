package services.guice

import com.google.inject.AbstractModule
import drivers.MXSConnectionManager
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import services._
import services.actors.{Auditor, ProjectCreationActor}

class BackupLauncherInjectionConfig extends AbstractModule with AkkaGuiceSupport {
  private val logger = Logger(getClass)

  override def configure(): Unit = {
    logger.warn(s"Running guice module ${getClass.getCanonicalName}")

    //this makes the actor instance accessible via injection
    bindActor[StorageScanner]("storage-scanner")
    bindActor[ValidateProject]("validate-project-actor")
    bindActor[ProjectCreationActor]("project-creation-actor")
    bindActor[PostrunActionScanner]("postrun-action-scanner")
    bindActor[RabbitMqPropagator]("rabbitmq-propagator")
    bindActor[CommissionStatusPropagator]("commission-status-propagator")
    bindActor[RabbitMqSend]("rabbitmq-send")
    bindActor[RabbitMqDeliverable]("rabbitmq-deliverable")
    bindActor[RabbitMqSAN]("rabbitmq-san")
    bindActor[RabbitMqMatrix]("rabbitmq-matrix")
    bindActor[Auditor]("auditor")

    bind(classOf[MXSConnectionManager]).asEagerSingleton()
  }

}
