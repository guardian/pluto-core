package drivers

import com.om.mxs.client.japi.MatrixStore
import drivers.objectmatrix.MXSConnectionBuilder
import org.slf4j.LoggerFactory

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

/**
  * The conection manager is a global singleton that maintains a cache of initialised MXS connections.
  * A storage driver instance obtains a reference to this singleton via runtime DI, and requests a connection
  * via the `getConnection` public method.  If none has been made already, then a connection is established and cached
  * The MXS object is the re-used for any subsequent connections to the same access key id.
  * At shutdown, all memorised connections are terminated.
  */
@Singleton
class MXSConnectionManager @Inject() () {
  private val logger = LoggerFactory.getLogger(getClass)
  private var connectionCache:Map[String,MatrixStore] = Map()

  sys.addShutdownHook(()=>{
    logger.info(s"Shutting down, terminating ${connectionCache.count(_=>true)} cached MXS connections")
    connectionCache.foreach({
      case (_, mxs)=>
        Try { mxs.dispose() } match {
          case Success(_)=>
            logger.info("MXS connection successfully disposed")
          case Failure(exception)=>
            logger.error(s"Could not shut down MXS connection: ${exception.getMessage}", exception)
        }
    })
  })

  private def newConnection(hosts:String,accessKeyId:String,accessKeySecret:String) = {
    val splitter = "\\s*,\\s*".r

    for {
      builder <- Try { MXSConnectionBuilder(splitter.split(hosts), accessKeyId, accessKeySecret) }
      mxs <- builder.build()
      _ <- Try { this.synchronized { connectionCache = connectionCache + (accessKeyId -> mxs)} }
    } yield mxs
  }

  def getConnection(hosts:String,accessKeyId:String,accessKeySecret:String) = {
    val existingConnection = this.synchronized {
      connectionCache.get(accessKeyId)
    }

    existingConnection match {
      case Some(mxs)=>
        logger.debug(s"Serving connection to key $accessKeyId from cache...")
        Success(mxs)
      case None=>
        logger.debug(s"Initialising new connection to key $accessKeyId")
        newConnection(hosts, accessKeyId, accessKeySecret)
    }
  }
}
