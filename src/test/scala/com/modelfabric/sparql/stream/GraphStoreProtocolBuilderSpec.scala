package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.{GraphStoreRequestFlowBuilder, SparqlRequestFlowBuilder}
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.scalatest.{DoNotDiscover, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


@DoNotDiscover
class GraphStoreProtocolBuilderSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike
  with GraphStoreRequestFlowBuilder
  with SparqlRequestFlowBuilder
  with SparqlQueries {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val receiveTimeout: FiniteDuration = 5 seconds

  import HttpEndpointSuiteTestRunner._

  "The Akka-Streams Graph Store Protocol Client" must {
    val flowUnderTest = graphStoreRequestFlow(testServerEndpoint)
    val sparqlRequests = sparqlRequestFlow(testServerEndpoint)

    val (source, sink) = TestSource.probe[GraphStoreRequest]
      .via(flowUnderTest)
      .toMat(TestSink.probe[GraphStoreResponse])(Keep.both)
      .run()

    val (sparqlSource, sparqlSink) = TestSource.probe[SparqlRequest]
      .via(sparqlRequests)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {

      // clear the test graph
      sink.request(1)
      source.sendNext(DropGraph(Some(graphIri)))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

      // clear the default graph
      sink.request(1)
      source.sendNext(DropGraph(None))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

    }

    "2. Add a triples to the default graph" in {
      val b = new ModelBuilder()
      b.add(whateverIri, propertyIri, "Hello")
      val model = b.build
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model, merge = false))
      assertSuccessResponse(sink.expectNext(receiveTimeout))

      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(SparqlQuery(select1)))
      sparqlSink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, List(_), None) =>
          assert(true)
      }

    }

    "X. Clear the data" in {

      // clear the test graph
      sink.request(1)
      source.sendNext(DropGraph(Some(graphIri)))

      //assertSuccessResponse(sink.expectNext(receiveTimeout))
      sink.expectNext(receiveTimeout) match {
        case GraphStoreResponse(_, status) =>
          info(s"named graph $graphIri deletion status: $status")
        case x@_ => assert(false, s"unexpected: $x")
      }

      // clear the default graph
      sink.request(1)
      source.sendNext(DropGraph(None))
      assertSuccessResponse(sink.expectNext(receiveTimeout))

      // making sure the data is gone now
      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(SparqlQuery(select1)))
      sparqlSink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, ResultSet(_, results) :: Nil, None) if results.bindings.isEmpty =>
          assert(true)
        case x@_ => assert(false, s"unexpected: $x")
      }
    }

  }

  private def assertSuccessResponse(response: GraphStoreResponse): Boolean = {
    response.success
  }
}
