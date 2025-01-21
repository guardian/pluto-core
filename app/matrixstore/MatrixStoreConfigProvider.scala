package matrixstore

trait MatrixStoreConfigProvider {
  def get():Either[String, MatrixStoreConfig]
}
