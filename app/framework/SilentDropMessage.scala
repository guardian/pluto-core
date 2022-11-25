package framework

/**
 * If a processor fails on this exception, then the message is silently dropped and not routed to DLQ.
 * e.g. if(thisIsSilly) Future.failed(SilentDropMessage()) else .....
 * @param msg Optional reason
 */
case class SilentDropMessage(msg:Option[String]=None) extends Throwable {
  override def fillInStackTrace(): Throwable = this
  override def getMessage: String = msg.getOrElse("This message is expected to be invalid")
}
