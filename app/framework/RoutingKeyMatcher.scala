package framework

object RoutingKeyMatcher {
  /**
   * performs matching against a routing key spec, that potentially contains wildcards, as per a topic exchange
   * in RabbitMQ.
   *
   * That is, the key is broken down into an array on the '.' character and every part is compared to its equivalent.
   * '*' always matches that part only, and '#' will bypass matching and return true for all subsequent parts
   * @param keySpec the key to match against
   * @param routingKeyParts the actual key presented, broken down on the '.' character via the `split()` method
   * @return a Boolean, True if they match
   */
  def checkMatch(keySpec:String, routingKeyParts:Seq[String]): Boolean = {
    val keySpecParts = keySpec.split("\\.")

    def matchPiece(keySpecPiece:Option[String], remainingKeySpec:Seq[String], routingKeyPiece:Option[String], remainingRoutingKey:Seq[String]):Boolean = {
      if(keySpecPiece.isEmpty && routingKeyPiece.isEmpty) { //we got to the end of the iteration and both match
        true
      } else if(keySpecPiece.contains("#")) {
        true
      } else if(keySpecPiece.contains("*") && remainingRoutingKey.nonEmpty) {
        matchPiece(remainingKeySpec.headOption, remainingKeySpec.tail, remainingRoutingKey.headOption, remainingRoutingKey.tail)
      } else if(keySpecPiece==routingKeyPiece) {
        if(remainingKeySpec.isEmpty && remainingRoutingKey.isEmpty) { //both end on the next piece - we have 100% match
          true
        } else if(remainingKeySpec.isEmpty || remainingRoutingKey.isEmpty) {  //one ends but the other doesn't - then there is a mismatch
          false
        } else {  //keep going on to the next piece
          matchPiece(remainingKeySpec.headOption, remainingKeySpec.tail, remainingRoutingKey.headOption, remainingRoutingKey.tail)
        }
      } else {  //if they don't match literally or on wildcard (or one is empty) then we have a mismatch
        false
      }
    }

    matchPiece(keySpecParts.headOption, keySpecParts.tail, routingKeyParts.headOption, routingKeyParts.tail)
  }

  /**
   * determines which item in the sequence of source keys matches the actual routing key that was passed.
   * @param sourceKeys sequence of strings representing the source keys that the processor is subscribing to
   * @param actualRoutingKey string representing the routing key of the actual message being processed
   * @return None if none of the source keys matched, otherwise the index of the matching one.
   */
  def findMatchingIndex(sourceKeys:Seq[String], actualRoutingKey:String):Option[Int] = {
    val routingKeyParts = actualRoutingKey.split("\\.")
    findMatchingIndex(sourceKeys, routingKeyParts)
  }

  private def findMatchingIndex(sourceKeys:Seq[String], routingKeyParts:Seq[String], i:Int=0):Option[Int] = {
    if(checkMatch(sourceKeys.head, routingKeyParts)) {
      Some(i)
    } else if(sourceKeys.length<=1) {
      None
    } else {
      findMatchingIndex(sourceKeys.tail, routingKeyParts, i+1)
    }
  }
}