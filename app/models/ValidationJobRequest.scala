package models

case class ValidationJobRequest(validationType:ValidationJobType.Value, nullField:Option[String])

