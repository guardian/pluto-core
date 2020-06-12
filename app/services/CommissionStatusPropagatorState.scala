package services

import java.util.UUID

case class CommissionStatusPropagatorState(events:Map[UUID, CommissionStatusPropagator.CommissionStatusEvent]) {
  def updated(evt:CommissionStatusPropagator.CommissionStatusEvent) = copy(events ++ Map(evt.uuid->evt))
  def removed(evt:CommissionStatusPropagator.CommissionStatusEvent) = copy(events.filter(_._1!=evt.uuid))
  def removed(eventId:UUID) = copy(events.filter(_._1!=eventId))
  def size:Int = events.size

  def foreach(block: ((UUID, CommissionStatusPropagator.CommissionStatusEvent))=>Unit):Unit = events.foreach(block)

  override def toString: String = events.toString()
}
