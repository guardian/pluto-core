package helpers

import play.api.Logger

import scala.jdk.CollectionConverters._

class PostrunDataCache(entries:Map[String,String]) {
  private val logger = Logger(getClass)

  /**
    * appends a string->string map of entries to the data cache and returns a new cache instance with them in it
    * @param values values to append
    * @return new PostrunDataCache
    */
  def ++(values:Map[String,String]):PostrunDataCache = {
    new PostrunDataCache(entries ++ values)
  }

  def withString(key:String, value:String):PostrunDataCache = new PostrunDataCache(entries ++ Map(key->value))

  /**
    * Retrieve a value from the data cache, as a string. The internally held python object is converted back to a Scala
    * string in the process
    * @param key key to check
    * @return An Option with None if no value exists or Some(string) if it does
    */
  def get(key:String):Option[String] = entries.get(key)

  /**
    * Convert the contents back into a Scala map
    * @return a Map[String,String] of the cache contents
    */
  def asScala:Map[String,String] = entries
}

object PostrunDataCache {
  def apply():PostrunDataCache = {
    new PostrunDataCache(Map())
  }

  /**
    * filter out non-ascii chars because PyString complains about them.  You _can_ pass them over as a ByteArray but
    * that would require a ton of changes on the Python side; since I am now of the opinion that the Python stuff is
    * more trouble than it's worth it'll probably get removed sooner rather than later.
    * https://stackoverflow.com/questions/51566281/how-to-convert-string-in-utf-8-to-ascii-ignoring-errors-and-removing-non-ascii-c
    * @param from string to remove the characters from
    * @return string consisting of only the ASCII characters from the input string
    */
  private def removeNonAscii(from:String):String =
    from
      .filter(Character.UnicodeBlock.of(_) == Character.UnicodeBlock.BASIC_LATIN)


  def apply(entries: Map[String,String]): PostrunDataCache = new PostrunDataCache(entries)
}