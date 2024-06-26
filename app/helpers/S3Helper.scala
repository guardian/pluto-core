package helpers

import org.slf4j.LoggerFactory
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

import software.amazon.awssdk.services.s3.model.{ListObjectVersionsRequest, ListObjectsV2Request, S3Object, DeleteObjectRequest, ObjectVersion, DeleteObjectResponse}
import software.amazon.awssdk.transfer.s3.{S3ClientConfiguration, S3TransferManager}
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.jdk.CollectionConverters._
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.{StartQueryExecutionRequest, StartQueryExecutionResponse, GetQueryResultsRequest, QueryExecutionContext, ResultConfiguration}

class S3Helper(transferManager: S3TransferManager, client: S3Client,athenaClient: AthenaClient, var bucketName: String)(implicit ec:ExecutionContext) {
  private val logger = LoggerFactory.getLogger(getClass)
  import S3Helper._

  def listBucketObjects(prefix: String): List[S3Object] = {
    val listObjects = ListObjectsV2Request.builder.bucket(bucketName).prefix(prefix).build
    val res = client.listObjectsV2(listObjects)
    val objects = res.contents
    return objects.asScala.toList
  }

  def listObjectsVersions(s3object: S3Object): List[ObjectVersion] = {
    val objectsVersions = ListObjectVersionsRequest.builder.bucket(bucketName).prefix(s3object.key).build
    val res = client.listObjectVersions(objectsVersions)
    res.versions().asScala.toList
  }

  def deleteObject(s3object: S3Object, version: String): DeleteObjectResponse = {
    val deleteObject = DeleteObjectRequest.builder.bypassGovernanceRetention(true).bucket(bucketName).key(s3object.key).versionId(version).build
    val res = client.deleteObject(deleteObject)
    res
  }

  def deleteObjectByKey(key: String): Boolean = {
    val deleteObject = DeleteObjectRequest.builder.bucket(bucketName).key(key).build
    val res = client.deleteObject(deleteObject)
    res.deleteMarker()
  }
  def executeAthenaQuery(query: String, database: String): List[String] = {
    val queryExecutionContext = QueryExecutionContext.builder().database(database).build()
    val resultConfiguration = ResultConfiguration.builder().outputLocation(s"s3://$bucketName/results/").build()

    val startQueryExecutionRequest = StartQueryExecutionRequest.builder()
      .queryString(query)
      .queryExecutionContext(queryExecutionContext)
      .resultConfiguration(resultConfiguration)
      .build()

    val startQueryExecutionResponse: StartQueryExecutionResponse = athenaClient.startQueryExecution(startQueryExecutionRequest)
    val queryExecutionId: String = startQueryExecutionResponse.queryExecutionId()

    // Wait for query to complete
    Thread.sleep(5000)  // This is a simple approach, consider implementing a proper wait mechanism

    val getQueryResultsRequest = GetQueryResultsRequest.builder().queryExecutionId(queryExecutionId).build()
    val getQueryResultsResponse = athenaClient.getQueryResults(getQueryResultsRequest)

    val results = getQueryResultsResponse.resultSet().rows().asScala.map(_.data().asScala.map(_.varCharValue()).mkString(",")).toList
    results
  }
}

object S3Helper {
  private def s3ClientConfig = {
    val b = S3ClientConfiguration.builder()
    val withRegion = sys.env.get("AWS_REGION") match {
      case Some(rgn)=>b.region(Region.of(rgn))
      case None=>b
    }
    withRegion.build()
  }
  private def initTransferManager = wrapJavaMethod(()=>
    S3TransferManager.builder()
      .s3ClientConfiguration(s3ClientConfig)
      .build()
  )

  private def initS3Client = wrapJavaMethod(()=>{
    val b = S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder())

    val withRegion = sys.env.get("AWS_REGION") match {
      case Some(rgn)=>b.region(Region.of(rgn))
      case None=>b
    }
    withRegion.build()
  })

  private def wrapJavaMethod[A](blk: ()=>A) = Try { blk() }.toEither.left.map(_.getMessage)

  def createFromBucketName(bucketName:String)(implicit ec:ExecutionContext):Either[String, S3Helper] =
    for {
      transferManager <- initTransferManager
      s3Client <- initS3Client
      athenaClient <- initAthenaClient
      result <- Right(new S3Helper(transferManager, s3Client, athenaClient, bucketName))
    } yield result


  private def initAthenaClient = wrapJavaMethod(() => {
    val b = AthenaClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder())

    val withRegion = sys.env.get("AWS_REGION") match {
      case Some(rgn) => b.region(Region.of(rgn))
      case None => b
    }
    withRegion.build()
  })
}
