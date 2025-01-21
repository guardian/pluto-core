package vidispine

case class VidispineConfig (baseUri:String, username:String, password:String)

object VidispineConfig extends ((String,String,String)=>VidispineConfig) {
  def fromEnvironment = {
    (sys.env.get("VIDISPINE_BASEURI"), sys.env.get("VIDISPINE_USER"), sys.env.get("VIDISPINE_PASSWORD")) match {
      case (Some(base), Some(u), Some(p))=>Right(new VidispineConfig(base, u, p))
      case (_,_,_)=>Left("You must specify VIDISPINE_BASEURI, VIDISPINE_USER and VIDISPINE_PASSWORD")
    }
  }
}
