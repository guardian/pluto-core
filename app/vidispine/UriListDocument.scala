package vidispine

case class UriListDocument (uri:Seq[String])

/**
 * if there are no thumnbnails we get an empty _object_ rather than an empty _sequence_. This is modelled here.
 * @param uri
 */
case class OptionalUriListDocument (uri:Option[Seq[String]]) {
  def toUriListDocument = UriListDocument(uri.getOrElse(Seq()))
  def toOption = uri.map(UriListDocument.apply)
}