package com.modelfabric.sparql.stream

import java.io.StringWriter

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
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


@DoNotDiscover
class GraphStoreProtocolBuilderSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike
  with BeforeAndAfterAll
  with GraphStoreRequestFlowBuilder
  with SparqlRequestFlowBuilder
  with SparqlQueries {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val receiveTimeout: FiniteDuration = 5 seconds

  import HttpEndpointSuiteTestRunner._

  override def beforeAll(): Unit = {
    clearTestData()
  }

  override def afterAll(): Unit = {
    //clearTestData()
  }

  private val flowUnderTest = graphStoreRequestFlow(testServerEndpoint)
  private val sparqlRequests = sparqlRequestFlow(testServerEndpoint)

  private val (source, sink) = TestSource.probe[GraphStoreRequest]
    .via(flowUnderTest)
    .log("graphStoreRequest")
    .toMat(TestSink.probe[GraphStoreResponse])(Keep.both)
    .run()

  private val (sparqlSource, sparqlSink) = TestSource.probe[SparqlRequest]
    .via(sparqlRequests)
    .log("sparqlRequest")
    .toMat(TestSink.probe[SparqlResponse])(Keep.both)
    .run()

  def clearTestData(): Unit = {
    // clear the test graph
    sink.request(1)
    source.sendNext(DropGraph(Some(graphIri)))
    sink.expectNextPF(processResponse())
    sink.request(1)
    source.sendNext(GetGraph(Some(graphIri)))
    sink.expectNextPF(processResponse(None, None))

    // clear the default graph
    sink.request(1)
    source.sendNext(DropGraph(None))
    sink.expectNextPF(processResponse())
    sink.request(1)
    source.sendNext(GetGraph(None))
    sink.expectNextPF(processResponse(None, None))
  }

  def processResponse
  (
    expectedStatus: Option[Boolean] = None,
    expectedModelSize: Option[Int] = None
  ): PartialFunction[Any, GraphStoreResponse] = {
    case x@GraphStoreResponse(request, success, statusCode, statusText, modelOpt) =>
      info(s"response status for $request ===>>> $success / $statusCode / $statusText")
      modelOpt.foreach(dumpModel)
      expectedStatus foreach (s => assert( s === success, s"expecting the response status to have the correct value, was: $s") )
      for {
        testSize  <- expectedModelSize
        model     <- modelOpt
        modelSize  = model.size()
      } yield {
        assert(testSize === modelSize, s"expecting model to be of certain size, was: ($modelSize)")
      }
      x
  }

  "The Akka-Streams Graph Store Protocol Client" must {

    val model1default: Model = {
      val b = new ModelBuilder()
        .defaultGraph()
        .add(whateverIri, propertyIri, "William")
      b.build
    }

    val model1named: Model = {
      val b = new ModelBuilder()
        .namedGraph(graphIri)
        .add(whateverIri, propertyIri, "William")
      b.build
    }

    val model2named: Model = {
      val b = new ModelBuilder()
        .namedGraph(graphIri)
        .add(whateverIri, propertyIri, "Bill")
      b.build
    }

    val model3named: Model = {
      val b = new ModelBuilder()
        .namedGraph(graphIri)
        .add(whateverIri, propertyIri, "Will")
      b.build
    }

    "1. Add a triple to the default graph" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model1default))
      assertSuccessResponse(sink.expectNext(receiveTimeout))

      // check with regular Sparql if the file is there
      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query1Get))
      sparqlSink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, result, None) =>
          assert(result === query1Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(None))
      sink.expectNextPF(processResponse(Some(true), Some(1)))

    }

    "1b. Add a triple to the named graph" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model1default, Some(graphIri)))
      assertSuccessResponse(sink.expectNext(receiveTimeout))

      // check with regular Sparql if the file is there
      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query2Get))
      sparqlSink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, result, None) =>
          assert(result === query2Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      sink.expectNextPF(processResponse(Some(true), Some(1)))

    }

    "2. Add a triple to the named graph" in {
      assert(model1named.contexts().contains(uriToIri(graphIri)), "checking the model")

      sink.request(1)
      source.sendNext(InsertGraphFromModel(model1named, Some(graphIri)))
      assertSuccessResponse(sink.expectNext(receiveTimeout))

      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query2Get))
      sparqlSink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, result, None) =>
          assert(result === query2Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      sink.expectNextPF(processResponse(Some(true), Some(1)))
    }

    "3. Add a second triple to the named graph and see if it is merged" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model2named, Some(graphIri), mergeGraphs = true))
      sink.expectNextPF(processResponse(Some(true), None))

      // now there should be 2 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      sink.expectNextPF(processResponse(Some(true), Some(2)))
    }

    "4. Add a third triple to the named graph with merging off, and check it is the only one left" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model3named, Some(graphIri)))
      sink.expectNextPF(processResponse(Some(true), None))

      // now there should be 2 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      sink.expectNextPF(processResponse(Some(true), Some(1)))
    }

  }

  private def assertSuccessResponse(response: GraphStoreResponse): Boolean = {
    info(s"Got response:\n:$response")
    response.success
  }

  private def dumpModel(model: Model): Unit = {
    info(">>> Model dump START")
    val writer = new StringWriter()
    Rio.write(model, writer, RDFFormat.NQUADS)
    info(writer.getBuffer.toString)
    info("<<< Model dump END")
  }

}
