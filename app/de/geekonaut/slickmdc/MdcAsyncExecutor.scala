package de.geekonaut.slickmdc

/*
From https://github.com/AVGP/slickmdc/blob/master/src/main/scala/de/geekonaut/slickmdc/MdcAsyncExecutor.scala,
there is no maven build for 2.13
 */
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent._
import slick.util.AsyncExecutor

/** Taken from the original Slick AsyncExecutor and simplified
 * @see https://github.com/slick/slick/blob/3.1/slick/src/main/scala/slick/util/AsyncExecutor.scala
 */

object MdcAsyncExecutor {
  /** Create an [[MdcAsyncExecutor]] with a fixed-size thread pool.
   *
   * @param name The name for the thread pool.
   * @param numThreads The number of threads in the pool.
   */

  def apply(name:String, numThreads: Int): AsyncExecutor = {
    new AsyncExecutor {
      val tf = new DaemonThreadFactory(name + "-")

      lazy val executionContext = {
        new MdcExecutionContext(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(numThreads, tf)))
      }

      def close(): Unit = {}
    }
  }

  def default(name: String = "AsyncExecutor.default"): AsyncExecutor = apply(name, 20)

  private class DaemonThreadFactory(namePrefix: String) extends ThreadFactory {
    private[this] val group = Option(System.getSecurityManager).fold(Thread.currentThread.getThreadGroup)(_.getThreadGroup)
    private[this] val threadNumber = new AtomicInteger(1)

    def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement, 0)
      if(!t.isDaemon) t.setDaemon(true)
      if(t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      t
    }
  }
}