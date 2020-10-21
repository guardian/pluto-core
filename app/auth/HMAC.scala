package auth

import play.api.Logger
import play.api.mvc.RequestHeader
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security._
import java.util.Base64

import scala.jdk.CollectionConverters._

object HMAC {
  val logger: Logger = Logger(this.getClass)
  private val checksumXtractor = "^([\\w\\d\\-]+)=(.*)$".r

  /**
    * generate the HMAC digest of a string, given a shared secret to encrypt it with
    * @param sharedSecret passphrase to encrypt with
    * @param preHashString content to digest
    * @return Base64 encoded string of the hmac digest
    */
  def generateHMAC(sharedSecret: String, preHashString: String): String = {
    val secret = new SecretKeySpec(sharedSecret.getBytes, "HmacSHA384")   //Crypto Funs : 'SHA256' , 'HmacSHA1'
    val mac = Mac.getInstance("HmacSHA384")
    mac.init(secret)
    val hashString: Array[Byte] = mac.doFinal(preHashString.getBytes)
    Base64.getEncoder.encodeToString(hashString) //new String(hashString.map(_.toChar))
  }

  def extract_checksum(digestHeader:String) = {
    val matches = checksumXtractor.findAllIn(digestHeader)
    if(matches.nonEmpty) {
      if(matches.group(1)!="SHA-384") {
        logger.error(s"Only SHA-384 digest is accepted, got ${matches.group(1)}")
        None
      } else {
        Some(matches.group(2))
      }
    } else {
      None
    }
  }
  /**
    * Take the relevant request headers and calculate what the digest should be
    * @param request Play request, must contain the headers Date, Content-Length, X-Sha384-Checksum
    * @param sharedSecret passphrase to encrypt with
    * @return Option containing the hmac digest, or None if any headers were missing
    */
  def calculateHmac(request: RequestHeader, sharedSecret: String):Option[String] = try {
    request.headers.get("Digest").map(extract_checksum).map(checksum=> {
      val string_to_sign = s"${request.uri}\n${request.headers.get("Date").get}\n${request.headers.get("Content-Type").getOrElse("")}\n$checksum\n${request.method}\n"
      logger.debug(s"Incoming request, string to sign: $string_to_sign")
      val hmac = generateHMAC(sharedSecret, string_to_sign)
      logger.debug(s"HMAC generated: $hmac")
      hmac
    })
  } catch {
      case e:java.util.NoSuchElementException=>
        logger.debug(e.toString)
        None
  }

  /**
    * Returns information about the available crypto algorithms on this platform
    * @return
    */
  def getAlgos:Array[(String,String,String)] = {
    for {
      provider <- Security.getProviders
      key <- provider.stringPropertyNames.asScala
    } yield Tuple3(provider.getName, key, provider.getProperty(key))

  }
}
