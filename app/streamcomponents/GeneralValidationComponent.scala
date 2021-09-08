package streamcomponents

import akka.stream.{FlowShape, Materializer}
import akka.stream.stage.GraphStage
import models.{ProjectEntry, ValidationJob, ValidationProblem}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.Injector
import slick.lifted.AbstractTable

trait GeneralValidationComponent[E<:AbstractTable[_]] extends GraphStage[FlowShape[E#TableElementType,ValidationProblem]] {

}
