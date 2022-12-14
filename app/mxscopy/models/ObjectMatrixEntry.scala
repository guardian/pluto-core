package mxscopy.models

import java.time.{Instant, ZoneId, ZonedDateTime}
import akka.stream.Materializer
import com.om.mxs.client.japi.{MXFSFileAttributes, Vault}
import mxscopy.helpers.MetadataHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

case class ObjectMatrixEntry(oid:String, attributes:Option[MxsMetadata], fileAttribues:Option[FileAttributes]) {
  def getMxsObject(vault:Vault) = vault.getObject(oid)

  def getMetadata(vault:Vault)(implicit mat:Materializer, ec:ExecutionContext) = MetadataHelper
    .getAttributeMetadata(getMxsObject(vault))
    .map(mxsMeta=>
      this.copy(oid, Some(mxsMeta), Some(FileAttributes(MetadataHelper.getMxfsMetadata(getMxsObject(vault)))))
    )

  /**
   * pull filesystem metadata from the appliance
   * @param vault vault to query
   * @return
   */
  def getMxfsMetadata(vault:Vault) = Try {
    this.copy(oid, fileAttribues = Some(FileAttributes(MetadataHelper.getMxfsMetadata(getMxsObject(vault)))))
  }

  def stringAttribute(key:String) = attributes.flatMap(_.stringValues.get(key))
  def intAttribute(key:String) = attributes.flatMap(_.intValues.get(key))
  def longAttribute(key:String) = attributes.flatMap(_.longValues.get(key))
  def timeAttribute(key:String, zoneId:ZoneId=ZoneId.systemDefault()) = attributes
    .flatMap(_.longValues.get(key))
    .map(v=>ZonedDateTime.ofInstant(Instant.ofEpochMilli(v),zoneId))

  def maybeGetPath() = stringAttribute("MXFS_PATH")
  def maybeGetFilename() = stringAttribute("MXFS_FILENAME")

  def maybeGetSize() = {
  (longAttribute("__mxs__length"), stringAttribute("__mxs__length"), longAttribute("DPSP_SIZE"), stringAttribute("DPSP_SIZE"), fileAttribues) match {
    case (Some(size),_,_,_,_)=>
      Some(size)
    case (_, Some(sizeString), _, _, _)=>
      sizeString.toLongOption
    case (_,_,Some(size), _, _)=>
      Some(size)
    case (_,_,_,Some(sizeString), _)=>
      sizeString.toLongOption
    case (_,_,_,_, attrs)=>
      attrs.map(_.size)
  }
}

  def pathOrFilename = maybeGetPath() match {
    case Some(p)=>Some(p)
    case None=>maybeGetFilename()
  }
}

object ObjectMatrixEntry {
  /**
   * Returns an ObjectMatrixEntry pre-populated with the full metadata from the given object.
   * @param oid id of the object matrix blob to interrogate
   * @param vault open vault ID to look it up on. `withVault` can get you this value.
   */
  def fromOID(oid: String, vault: Vault)(implicit mat:Materializer, ec:ExecutionContext) = for {
      withMXFS <- Future.fromTry(new ObjectMatrixEntry(oid, None, None).getMxfsMetadata(vault))
      withMeta <- withMXFS.getMetadata(vault)
    } yield withMeta

  /**
   * Returns an ObjectMatrixEntry that _only_ contains the given OID and NO METADATA YET.
   * This should only be used internally; `ObjectMatrixEntry.fromOID` is more likely to do what you want
   * @param oid id of the object matrix blob to interrogate
   * @return the ObjectMatrixEntry record
   */
  def apply(oid:String) = new ObjectMatrixEntry(oid, None, None)
}
