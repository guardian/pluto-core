package drivers

import java.time.ZonedDateTime

/**
  * This is a base class that the GetMetadata methods should return.  A superclass can define _more_ methods than this
  * but should always return these ones
  */
trait StorageMetadata {
  def size:Long
  def lastModified:ZonedDateTime
  def toString:String
}
