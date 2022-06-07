package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsError, JsPath, Json, Reads}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class ProjectRequest(filename:String,destinationStorageId:Int,title:String, projectTemplateId:Int,
                          user:String, workingGroupId: Option[Int], commissionId: Option[Int],
                          deletable: Boolean, deep_archive: Boolean, sensitive: Boolean, productionOffice: ProductionOffice.Value, obitProject:Option[String]) {
  /* looks up the ids of destination storage and project template, and returns a new object with references to them or None */
  def hydrate(implicit db:slick.jdbc.PostgresProfile#Backend#Database):Future[Option[ProjectRequestFull]] = {
    val storageFuture = StorageEntryHelper.entryFor(this.destinationStorageId)
    val projectTemplateFuture = ProjectTemplate.entryFor(this.projectTemplateId)

    Future.sequence(Seq(storageFuture, projectTemplateFuture)).map(resultSeq=>{
      val successfulResults = resultSeq.flatten
      if(successfulResults.length==2){
        Some(ProjectRequestFull(this.filename,
          successfulResults.head.asInstanceOf[StorageEntry],
          this.title,
          successfulResults(1).asInstanceOf[ProjectTemplate],
          user, workingGroupId, commissionId, existingVidispineId = None, shouldNotify = true, deletable, deep_archive, sensitive, productionOffice, obitProject))
      } else None
    })
  }
}

case class ProjectRequestFull(filename:String,destinationStorage:StorageEntry,title:String,projectTemplate:ProjectTemplate,
                              user:String, workingGroupId: Option[Int], commissionId:Option[Int], existingVidispineId: Option[String],
                              shouldNotify: Boolean, deletable: Boolean, deep_archive: Boolean, sensitive: Boolean,
                              productionOffice: ProductionOffice.Value, obitProject:Option[String]) {

}

trait ProjectRequestSerializer {
  import ProductionOfficeMapper._
  implicit val projectRequestReads:Reads[ProjectRequest] = (
    (JsPath \ "filename").read[String] and
      (JsPath \ "destinationStorageId").read[Int] and
      (JsPath \ "title").read[String] and
      (JsPath \ "projectTemplateId").read[Int] and
      (JsPath \ "user").read[String] and
      (JsPath \ "workingGroupId").readNullable[Int] and
      (JsPath \ "commissionId").readNullable[Int] and
      (JsPath \ "deletable").read[Boolean] and
      (JsPath \ "deepArchive").read[Boolean] and
      (JsPath \ "sensitive").read[Boolean] and
      (JsPath \ "productionOffice").read[ProductionOffice.Value] and
      (JsPath \ "obitProject").readNullable[String]
  )(ProjectRequest.apply _)
}
