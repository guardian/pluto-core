package streamcomponents

import akka.stream.{FlowShape, Materializer}
import akka.stream.stage.GraphStage
import models.{ProjectEntry, ValidationJob, ValidationProblem}
import slick.lifted.AbstractTable

/**
  * Represents an Akka stage that can be used as a validation component.
  *
  * Any implentor must take in a data record from the given table, and if a problem is detected it should output
  * a ValidationProblem record.
  *
  * The data type of the incoming element is the record type, e.g. for GeneralValidationComponent[ProjectEntryRow] then
  * the incoming element is of type `ProjectEntry`, NOT `ProjectEntryRow`.
  *
  * If no problem is detected then it should simply re-pull the input and output nothing.
  * @tparam E the row type of the table that is being queried, e.g. ProjectEntryRow
  */
trait GeneralValidationComponent[E<:AbstractTable[_]] extends GraphStage[FlowShape[E#TableElementType,ValidationProblem]]
