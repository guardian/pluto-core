package controllers

import javax.inject.Inject
import play.api.libs.json._
import auth.{BearerTokenAuth, Security}
import play.api.{Configuration, Logger}
import play.api.mvc._
import play.api.cache.SyncCacheApi

class Buckets @Inject() (override val controllerComponents:ControllerComponents, override val bearerTokenAuth:BearerTokenAuth,
                        configuration: Configuration, cacheImpl:SyncCacheApi)
  extends AbstractController(controllerComponents) with Security {

  implicit val cache:SyncCacheApi = cacheImpl
  implicit val config:Configuration = configuration

  def list = IsAuthenticated {uid=>{request=>
    Ok(Json.obj(
      "status"->"ok",
      "buckets"->config.getOptional[Seq[String]]("s3_buckets"),
    ))
  }}
}
