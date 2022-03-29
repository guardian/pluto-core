package models

import org.slf4j.LoggerFactory
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.util.{Failure, Success, Try}

/**
  * DisplayedVersion represents a version number in the format {major}.{minor}.{patch}
  * @param major major version number
  * @param minor minor version number
  * @param patch patch version number.
  */
case class DisplayedVersion(major:Int, minor:Int, patch: Int) {
  override def toString: String = s"$major.$minor.$patch"

  /**
    * Returns 1 if _this_ DisplayedVersion is later than the other DisplayedVersion,
    * -1 if this DisplayedVersion is earlier than the other DisplayedVersion and 0
    * if they match
    * @param other another DisplayedVersion instance to compare against
    * @return an integer indicating if the versions match or one is earlier/later than the other
    */
  def compare(other:DisplayedVersion) = {
    if(major > other.major) {
      1
    } else if(major < other.major) {
      -1
    } else if(minor > other.minor) {
      1
    } else if(minor < other.minor) {
      -1
    } else if(patch > other.patch) {
      1
    } else if(patch < other.patch) {
      -1
    } else {
      0
    }
  }
}

object DisplayedVersion {
  private val logger = LoggerFactory.getLogger(getClass)
  private val matcher = "^(\\d+).(\\d+).([\\d.]+)".r

  /**
    * Parses the given string into a (major, minor, patch) triplet
    * @param stringRepresentation version string to parse
    * @return DisplayedVersion instance if the parse is successful. On failure, None is returned and the error logged out.
    */
  def apply(stringRepresentation:String) = {
    stringRepresentation match {
      case matcher(x,y,z)=>
        Try { new DisplayedVersion(x.toInt, y.toInt, z.toInt) } match {
          case Failure(err)=>
            logger.error(s"Could not parse $stringRepresentation into a version number: ${err.getClass.getCanonicalName} ${err.getMessage}")
            None
          case Success(value)=>
            Some(value)
        }
      case _=>
        logger.error(s"Could not parse $stringRepresentation into a version number because it does not match the x.y.z format")
        None
    }
  }
}

case class PremiereVersionTranslation(internalVersionNumber:Int, name:String, displayedVersion: DisplayedVersion)

/**
  * Defines custom column -> object mappings for PremiereVersionTranslation
  */
object PremiereVersionTranslationMappers {
  implicit val DisplayedVersionConverter = MappedColumnType.base[DisplayedVersion, String](_.toString, DisplayedVersion.apply(_).get)
}

class PremiereVersionTranslationRow(tag:Tag) extends Table[PremiereVersionTranslation](tag, "PremiereVersionTranslation") {
  import PremiereVersionTranslationMappers._

  def internalVersionNumber = column[Int]("i_internal", O.PrimaryKey)
  def name = column[String]("s_name")
  def displayedVersion = column[DisplayedVersion]("s_displayed_version")

  def * = (internalVersionNumber, name, displayedVersion) <> (PremiereVersionTranslation.tupled, PremiereVersionTranslation.unapply)
}