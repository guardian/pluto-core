package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.{AbstractController, ControllerComponents}

class DataMigrationController @Inject()
  (cc:ControllerComponents, config:Configuration, dbProvider:DatabaseConfigProvider) extends AbstractController(cc) {


}
