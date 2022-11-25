package vidispine

/*
data models for constructing metadata updates that go to Vidispine.
These "write" variants contain only the fields you need to _push_ data, not the extra fields that come back
when you read it
 */
case class MetadataValuesWrite(value:String)
case class MetadataFieldWrite(name:String, value:Seq[MetadataValuesWrite])
case class MetadataFieldGroupWrite(name:String, field:Seq[MetadataFieldWrite])
case class Timespan(field:Seq[MetadataFieldWrite],  group:Seq[MetadataFieldGroupWrite], start:String="-INF", end:String="+INF")
case class MetadataWrite(timespan:Seq[Timespan])

object MetadataWrite {
  /**
   * convenience method that builds a serializable object to write a key/value pair to item metadata
   * @param field field name to set
   * @param value value to set
   * @return a MetadataWrite document, that can be serialized with `.asJson.noSpaces`
   */
  def simpleKeyValue(field:String, value:String):MetadataWrite = MetadataWrite(
    Seq(
      Timespan(
        field = Seq(
          MetadataFieldWrite(field, value=Seq(MetadataValuesWrite(value)))
        ),
        group = Seq()
      )
    )
  )

  def groupedKeyValue(groupName:String, field:String, value:String):MetadataWrite = MetadataWrite(
    Seq(
      Timespan(
        group = Seq(
          MetadataFieldGroupWrite(groupName, field=Seq(
            MetadataFieldWrite(field, value=Seq(MetadataValuesWrite(value)))
          ))
        ),
        field = Seq()
      )
    )
  )

  /**
   * convenience method that builds a serializable object to write a  number of values to the same field in item metadata
   * @param field field name to set
   * @param values values to set
   * @return a MetadataWrite document, that can be serialized with `.asJson.noSpaces`
   */
  def keyMultipleValue(field: String, values:Seq[String]):MetadataWrite = MetadataWrite(
    Seq(
      Timespan(
        field = Seq(
          MetadataFieldWrite(field, value=values.map(MetadataValuesWrite.apply))
        ),
        group = Seq()
      )
    )
  )
}

case class ItemMetadataSimplified(revision:String, timespan:Seq[Timespan])
case class ItemResponseContentSimplified(metadata:ItemMetadataSimplified)

case class ItemResponseSimplified(item:Seq[ItemResponseContentSimplified]) {

  /**
   * Returns the metadata values, as a string, for the given field. By default only "root" level fields are searched
   * but you can look inside a group instead by setting `maybeGroupname`
   * @param fieldName field to look for
   * @param maybeGroupname group name to search within
   * @return a sequence of `MetadataValuesWrite`
   */
  def valuesForField(fieldName:String, maybeGroupname:Option[String]=None) = {
    val timespans = item.flatMap(_.metadata.timespan.filter(t=>t.start=="-INF" && t.end=="+INF"))

    val fieldsToSearch = maybeGroupname match {
      case None=>timespans.flatMap(_.field)
      case Some(groupName)=>timespans.flatMap(_.group.filter(_.name==groupName).flatMap(_.field))
    }

    fieldsToSearch.filter(_.name==fieldName).flatMap(_.value)
  }
}