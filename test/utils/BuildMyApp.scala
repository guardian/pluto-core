package utils

import akka.stream.Materializer
import akka.testkit.TestProbe
import play.api.cache.SyncCacheApi
import play.api.cache.ehcache.EhCacheModule
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.json.Json
import services.actors.ProjectCreationActor
import testHelpers.TestDatabase

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait BuildMyApp extends MockedCacheApi {
  def buildApp = new GuiceApplicationBuilder().disable(classOf[EhCacheModule])
    .overrides(bind[DatabaseConfigProvider].to[TestDatabase.testDbProvider])
    .overrides(bind[SyncCacheApi].toInstance(mockedSyncCacheApi))
    .configure("ldap.ldapProtocol"->"ldaps")
    .configure("akka.persistence.journal.plugin"->"akka.persistence.journal.inmem")
    .configure("akka.persistence.journal.auto-start-journals"->Seq())
    .configure("akka.persistence.snapshot-store.plugin"->"akka.persistence.snapshot-store.local")
    .configure("akka.persistence.snapshot-store.auto-start-snapshot-stores"->Seq())
    .configure("akka.cluster.seed-nodes"->Seq("akka://application@127.0.0.1:2551")) //disable bootstrap when we are testing as the system is not up long enough
    .build

  def buildAppWithMockedProjectHelper = new GuiceApplicationBuilder().disable(classOf[EhCacheModule])
    .overrides(bind[DatabaseConfigProvider].to[TestDatabase.testDbProvider])
    .overrides(bind[SyncCacheApi].toInstance(mockedSyncCacheApi))
    .configure("akka.persistence.journal.plugin"->"akka.persistence.journal.inmem")
    .configure("akka.persistence.journal.auto-start-journals"->Seq())
    .configure("akka.persistence.snapshot-store.plugin"->"akka.persistence.snapshot-store.local")
    .configure("akka.persistence.snapshot-store.auto-start-snapshot-stores"->Seq())
    .configure("akka.cluster.seed-nodes"->Seq("akka://application@127.0.0.1:2551"))
    .build

  def bodyAsJsonFuture(response:Future[play.api.mvc.Result])(implicit materializer:Materializer) = response.flatMap(result=>
    result.body.consumeData.map(contentBytes=> {
      Json.parse(contentBytes.decodeString("UTF-8"))
    }
    )
  )
}
