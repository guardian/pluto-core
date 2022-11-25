package framework

import com.rabbitmq.client.ConnectionFactory

/**
 * This trait is a simple wrapper to get a ConnectionFactory for RabbitMQ.
 * It's implemented like this so that a mock implementation of the trait can be injected into
 * classes for testing with a fully mocked RabbitMQ API so we don't need a broker available for unit testing
 */
trait ConnectionFactoryProvider {
  def get():ConnectionFactory
}

/**
 * Implementation of ConnectionFactoryProvider that returns the real RabbitMQ API
 */
object ConnectionFactoryProviderReal extends ConnectionFactoryProvider {
  override def get(): ConnectionFactory = new ConnectionFactory()
}