package matrixstore

case class MatrixStoreConfig(hosts: Array[String], clusterId:String, accessKeyId: String, accessKeySecret:String, nearlineVaultId:String, internalArchiveVaultId:Option[String])
