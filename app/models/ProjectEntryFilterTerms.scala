package models
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, _}
import slick.lifted.Query
import slick.jdbc.PostgresProfile.api._


case class ProjectEntryFilterTerms(title:Option[String],
                                   vidispineProjectId:Option[String],
                                   filename:Option[String],
                                   user:Option[String],
                                   group:Option[String],
                                   commissionId:Option[Int],
                                   showKilled:Option[Boolean],
                                   status:Option[String],
                                   sensitive:Option[Boolean],

                                   wildcard:FilterTypeWildcard.Value)
extends GeneralFilterEntryTerms[ProjectEntryRow, ProjectEntry] {

  /**
    * adds the relevant filter terms to the end of a Slick query
    * @param f a lambda function which is passed nothing and should return the base query to which the filter terms should
    *          be appended
    * @return slick query with the relevant filter terms added
    */
  override def addFilterTerms(f: =>Query[ProjectEntryRow, ProjectEntry, Seq]):Query[ProjectEntryRow, ProjectEntry, Seq] = {
    import EntryStatusMapper._
    var action = f
    if(filename.isDefined){
      /* see http://slick.lightbend.com/doc/3.0.0/queries.html#joining-and-zipping */
      action = for {
        (assoc, matchingFiles) <- TableQuery[FileAssociationRow] join TableQuery[FileEntryRow] on (_.fileEntry===_.id) if matchingFiles.filepath like makeWildcard(filename.get)
        projectEntryRow <- action.filter(_.id===assoc.projectEntry)
      } yield projectEntryRow
    }
    if(title.isDefined) action = action.filter(_.projectTitle.toLowerCase like makeWildcard(title.get).toLowerCase)
    if(vidispineProjectId.isDefined) action = action.filter(_.vidispineProjectId like makeWildcard(vidispineProjectId.get))
    if(user.isDefined && user.get!="Everyone") action = action.filter(_.user like makeWildcard(user.get))
    if(group.isDefined && group.get!="All") action = action.filter(_.workingGroup===group.get.toInt)
    if(showKilled.contains(false)) action = action.filter(_.status=!=EntryStatus.Killed)
    if(commissionId.isDefined ) action = action.filter(_.commission===commissionId.get)
    if(status.isDefined) action = action.filter(_.status===EntryStatus.withName(status.get))
    if(sensitive.isDefined) action = action.filter(_.sensitive === sensitive.get)
    action
  }
}

trait ProjectEntryFilterTermsSerializer {
  implicit val wildcardSerializer:Reads[FilterTypeWildcard.Value] = Reads.enumNameReads(FilterTypeWildcard)

  implicit val projectEntryFilterTermsReads:Reads[ProjectEntryFilterTerms] = (
    (JsPath \ "title").readNullable[String] and
      (JsPath \ "vidispineId").readNullable[String] and
      (JsPath \ "filename").readNullable[String] and
      (JsPath \ "user").readNullable[String] and
      (JsPath \ "group").readNullable[String] and
      (JsPath \ "commissionId").readNullable[Int] and
      (JsPath \ "showKilled").readNullable[Boolean] and
      (JsPath \ "status").readNullable[String] and
      (JsPath \ "sensitive").readNullable[Boolean] and
      (JsPath \ "match").read[FilterTypeWildcard.Value]
  )(ProjectEntryFilterTerms.apply _)
}