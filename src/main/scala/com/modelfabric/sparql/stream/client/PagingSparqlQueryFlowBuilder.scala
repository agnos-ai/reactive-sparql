package com.modelfabric.sparql.stream.client

import java.util.stream.Collectors

import akka.{Done, actor}
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.impl.LinkedHashModelFactory

import scala.concurrent.{Future, Promise}
import scala.util.Success


trait PagingSparqlQueryFlowBuilder extends SparqlClientHelpers {

  import SparqlClientConstants._

  type Sparql = String

  def pagingSparqlQuery(limit: Int, sparql: Sparql, sink: Sink[Model, _]): Future[Boolean] = {
    import akka.pattern.ask
    import system.dispatcher

/*
    def sparqlWithOffsetAndLimit(sparql: Sparql, offset: Long, limit: Long): Sparql = {
      val query = s"$sparql OFFSET $offset LIMIT $limit"
      println(s"will run: $query")
      query
    }

    val promise = Promise[Boolean]()

    // an actor wrapper around the sink, so the same can be reused in each paging flow
    val sinkRef: ActorRef = Source
      .actorRef[Model](Runtime.getRuntime.availableProcessors(), OverflowStrategy.fail)
      .toMat(sink)(Keep.left).run()

    def next(offset: Int = 0): Unit = {
      // need to materialize a stream for every page, not that efficient
      val modelFlow = constructModelFlow
        .fold(new LinkedHashModelFactory().createEmptyModel()) { (model, m) =>
          val r = m.stream().collect(Collectors.toList())
          model.addAll(r)
          model
        }

      val faRef = futureWrappedStreamActor(modelFlow)(flowProbe.ref)

      ask(faRef, sparqlWithOffsetAndLimit(sparql, offset, limit)).mapTo[Model] onComplete {
        case Success(model) =>
          // send the model to the sink
          sinkRef ! model
          if (model.isEmpty) {
            info(s"nothing found at page with offset=$offset")
            promise.success(true)
          } else {
            val nextOffset = offset + limit
            info(s"requesting next page with offset=$nextOffset")
            next(nextOffset)
          }
        case x@_ =>
          val error = new IllegalStateException(s"unexpected result $x")
          promise.failure(error)
          throw error
      }

      // complete the stream, so elements belonging to the page can be folded
      faRef.tell(CompleteStream(Done), flowProbe.ref) // force completing the modelFlow stream
      // now testing if the stream completes!
      flowProbe.expectMsgPF(15 seconds) {
        case StreamCompleted(_) => info("Flow Stream completed as expected")
        case x@_ => fail(s"unexpected: $x")
      }
      context.stop(flowProbe.ref)
    }

    next()

    // complete the sink
    sinkRef ! actor.Status.Success(Done)

    promise.future
*/
    ???
  }


}
