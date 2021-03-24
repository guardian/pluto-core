package services

import io.circe.Decoder.decodeEnumeration
import io.circe.Encoder.encodeEnumeration
object ServiceEventAction extends Enumeration {
  type ServiceEventAction = Value
  val PerformAction, CancelAction = Value
}

object ServiceEventCodec {
  implicit val serviceActionEventDecoder = decodeEnumeration(ServiceEventAction)
  implicit val serviceEventActionEncoder = encodeEnumeration(ServiceEventAction)
}

sealed trait ServiceEvents {
  val action:ServiceEventAction.Value
}

case class ScanEvent(override val action: ServiceEventAction.Value) extends ServiceEvents