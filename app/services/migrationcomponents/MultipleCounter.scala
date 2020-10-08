package services.migrationcomponents

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import akka.stream.{Attributes, Inlet, Outlet, SinkShape, UniformFanInShape}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic, GraphStageWithMaterializedValue}

import scala.collection.IterableOnce.iterableOnceExtensionMethods
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class MultipleCounter[T](inputCount:Int) extends GraphStageWithMaterializedValue[UniformFanInShape[T,T], Future[Seq[Int]]] {
  val ins:Seq[Inlet[T]] = (1 to inputCount).map(n=>Inlet.create(s"MultipleCounter.in$n"))
  val out:Outlet[T] = Outlet.create("MultipleCounter.out")

  override def shape = UniformFanInShape(out, ins:_*)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Seq[Int]]) = {
    val resultValue = Promise[Seq[Int]]

    def logic = new GraphStageLogic(shape) {
      def makeInitialEntries = {
        val initialEntries = (1 to inputCount).map(_=>0)
        mutable.IndexedSeq(initialEntries:_*)
      }

      private var counter:mutable.IndexedSeq[Int] = makeInitialEntries
      private var completions:Int =0

      setHandler(out, new AbstractOutHandler {
        override def onPull(): Unit = (1 to inputCount).foreach(n=>if(!hasBeenPulled(ins(n-1))) pull(ins(n-1)))

      })

      (1 to inputCount).foreach(n=>{
        setHandler(ins(n-1), new AbstractInHandler {
          override def onPush(): Unit = {
            counter(n-1) = counter(n-1) + 1
          }

          override def onUpstreamFinish(): Unit = {
            completions+=1
            if(completions==inputCount) resultValue.complete(Success(counter.toSeq))
          }

          override def onUpstreamFailure(ex: Throwable): Unit = resultValue.complete(Failure(ex))
        })
      })

    }
    (logic, resultValue.future)
  }
}
