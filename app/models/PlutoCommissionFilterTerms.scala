package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, _}
import slick.lifted.Query
import slick.jdbc.PostgresProfile.api._

//https://github.com/d6y/slick-ilike-example/blob/master/src/main/scala/main.scala
object ILike {
  implicit class IlikeOps(s: Rep[String]) {
    def ilike(p: Rep[String]): Rep[Boolean] = {
      val expr = SimpleExpression.binary[String,String,Boolean] { (s, p, qb) =>
        qb.expr(s)
        qb.sqlBuilder += " ILIKE "
        qb.expr(p)
      }
      expr.apply(s,p)
    }
  }
}

case class PlutoCommissionFilterTerms(title:Option[String],
                                      status:Option[EntryStatus.Value],
                                      siteId:Option[String],
                                      collectionId:Option[Int],
                                      user:Option[String],
                                      group:Option[String],
                                      description:Option[String],
                                      wildcard:FilterTypeWildcard.Value)
extends GeneralFilterEntryTerms[PlutoCommissionRow, PlutoCommission] {

  /**
    * adds the relevant filter terms to the end of a Slick query
    * @param f a lambda function which is passed nothing and should return the base query to which the filter terms should
    *          be appended
    * @return slick query with the relevant filter terms added
    */
  override def addFilterTerms(f: =>Query[PlutoCommissionRow, PlutoCommission, Seq]):Query[PlutoCommissionRow, PlutoCommission, Seq] = {
    import ILike._
    var action = f

    import EntryStatusMapper._

    if(title.isDefined) action = action.filter(_.title ilike makeWildcard(title.get))
    if(siteId.isDefined) action = action.filter(_.siteId ===siteId.get)
    if(status.isDefined) action = action.filter(_.status === status.get)
    if(collectionId.isDefined) action = action.filter(_.collectionId===collectionId.get)
    if(user.isDefined && user.get!="Everyone") action = action.filter(_.owner like makeWildcard(user.get))
    if(group.isDefined && group.get!="All") action = action.filter(_.workingGroup===group.get.toInt)
    if(description.isDefined) action = action.filter(_.description like makeWildcard(description.get))
    action
  }
}

trait PlutoCommissionFilterTermsSerializer {
  implicit val wildcardSerializer:Reads[FilterTypeWildcard.Value] = Reads.enumNameReads(FilterTypeWildcard)
  import EntryStatusMapper._

  implicit val plutoCommissionFilterTermsReads:Reads[PlutoCommissionFilterTerms] = (
    (JsPath \ "title").readNullable[String] and
      (JsPath \ "status").readNullable[EntryStatus.Value] and
      (JsPath \ "siteId").readNullable[String] and
      (JsPath \ "collectionId").readNullable[Int] and
      (JsPath \ "user").readNullable[String] and
      (JsPath \ "group").readNullable[String] and
      (JsPath \ "description").readNullable[String] and
      (JsPath \ "match").read[FilterTypeWildcard.Value]
  )(PlutoCommissionFilterTerms.apply _)
}