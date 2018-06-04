package com.modelfabric.sparql.stream

import java.io.File
import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.stream.testkit.TestSubscriber.Probe
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.{GraphStoreRequestFlowBuilder, HttpEndpointFlow, SparqlRequestFlowBuilder}
import com.modelfabric.sparql.util.{HttpEndpoint, RdfModelTestUtils}
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.eclipse.rdf4j.model.util.ModelBuilder
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.rio.RDFFormat
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, WordSpecLike}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

@DoNotDiscover
class GraphStoreProtocolBuilderSpec extends TestKit(ActorSystem("GraphStoreProtocolBuilderSpec"))
  with WordSpecLike
  with BeforeAndAfterAll
  with GraphStoreRequestFlowBuilder
  with SparqlRequestFlowBuilder
  with SparqlQueries
  with RdfModelTestUtils {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  import HttpEndpointSuiteTestRunner._

  val timeout: FiniteDuration = 30 seconds

  var binding: Option[ServerBinding] = None

  override def beforeAll(): Unit = {
    clearTestData()
  }

  override def afterAll(): Unit = {
    shutdownFileServer()
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

  private val flowUnderTest = graphStoreRequestFlow(HttpEndpointFlow(testServerEndpoint, defaultHttpClientFlow[GraphStoreRequest]))
  private val sparqlRequests = sparqlRequestFlow(HttpEndpointFlow(testServerEndpoint, defaultHttpClientFlow[SparqlRequest]))

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
    checkAllGood(sink)

    // clear the model graph
    sink.request(1)
    source.sendNext(DropGraph(Some(modelGraphIri)))
    checkAllGood(sink)

    // clear the default graph
    sink.request(1)
    source.sendNext(DropGraph(None))
    checkAllGood(sink)
  }

  /**
    * Looks at the Sink and makes sure all is as expected after each test
    * @param probe the Sink probe to check
    * @param expectedStatus if set, check the status of the response, if None checking is skipped
    * @param expectedModelSize if set check the size of the returned model, if None checking is skipped
    * @param timeout set the timeout, defaults to the one set for this spec.
    * @tparam T
    * @return
    */
  def checkAllGood[T]
  (
    probe: Probe[T],
    expectedStatus: Option[Boolean] = None,
    expectedModelSize: Option[Int] = None,
    timeout: FiniteDuration = timeout
  ): T = {

    val response = probe.expectNext(timeout)
    info(s"Got Response: $response")
    response match {
      case GraphStoreResponse(request, success, statusCode, statusText, modelOpt) =>
        info(s"response status for $request ===>>> $success / $statusCode / $statusText\n$modelOpt")
        modelOpt.foreach(dumpModel(_))
        expectedStatus foreach (s => assert(s === success, s"expecting the response status to have the correct value, was: $s"))
        for {
          testSize <- expectedModelSize
          model <- modelOpt
          modelSize = model.size()
        } yield {
          assert(testSize === modelSize, s"expecting model to be of certain size, was: ($modelSize)\n$model")
        }
      case SparqlResponse(request, success, _, result, error) =>
        info(s"response status for $request ===>>> $success / ${result.size} items/ error: $error")

    }
    response
  }

  import scala.collection.JavaConversions._

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
      checkAllGood(sink)

      // check with regular Sparql if the file is there
      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query1Get))
      checkAllGood(sparqlSink) match {
        case SparqlResponse (_, true, _, result, None) =>
          assert(result === query1Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(None))
      checkAllGood(sink, Some(true), Some(1))

    }

    "2. Add a triple to the named graph" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model1default, Some(graphIri)))
      checkAllGood(sink)

      // check with regular Sparql if the file is there
      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query2Get))
      checkAllGood(sparqlSink) match {
        case SparqlResponse (_, true, _, result, None) =>
          assert(result === query2Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      checkAllGood(sink, Some(true), Some(1))

    }

    "3. Add a triple to the named graph" in {
      assert(model1named.contexts().contains(graphIri), "checking the model")

      sink.request(1)
      source.sendNext(InsertGraphFromModel(model1named, Some(graphIri)))
      checkAllGood(sink, Some(true))

      sparqlSink.request(1)
      sparqlSource.sendNext(SparqlRequest(query2Get))
      checkAllGood(sparqlSink) match {
        case SparqlResponse (_, true, _, result, None) =>
          assert(result === query2Result)
      }

      // now check with GetGraph (graph-store protocol variant)
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      checkAllGood(sink, Some(true), Some(1))
    }

    "4. Add a second triple to the named graph and see if it is merged" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model2named, Some(graphIri), mergeGraphs = true))
      checkAllGood(sink, Some(true))

      // now there should be 2 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      checkAllGood(sink, Some(true), Some(2))
    }

    "5. Add a third triple to the named graph with merging off, and check it is the only one left" in {
      sink.request(1)
      source.sendNext(InsertGraphFromModel(model3named, Some(graphIri)))
      checkAllGood(sink, Some(true))

      // now there should be 2 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(graphIri)))
      checkAllGood(sink, Some(true), Some(1))
    }

    "6. Load N-TRIPLES file from the local filesystem into a named graph" in {
      val ntFilePath = new File("src/test/resources/labels.nt").getAbsoluteFile.toPath

      sink.request(1)
      source.sendNext(InsertGraphFromPath(ntFilePath, RDFFormat.NTRIPLES, Some(modelGraphIri)))
      checkAllGood(sink, Some(true))

      // now there should be 40 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(modelGraphIri)))
      val res = checkAllGood(sink, Some(true), Some(40))
      res.model.get.predicates.containsAll(Set(RDFS.LABEL, RDFS.COMMENT))
      dumpModel(res.model.get, RDFFormat.TURTLE)
      dumpModel(res.model.get, RDFFormat.JSONLD)
    }

    "7. Load TURTLE file from the local filesystem into a named graph" in {
      val ntFilePath = new File("src/test/resources/labels.ttl").getAbsoluteFile.toPath

      sink.request(1)
      source.sendNext(InsertGraphFromPath(ntFilePath, RDFFormat.TURTLE, Some(modelGraphIri)))
      checkAllGood(sink, Some(true))

      // now there should be 40 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(modelGraphIri)))
      val res = checkAllGood(sink, Some(true), Some(40))
      res.model.get.predicates.containsAll(Set(RDFS.LABEL, RDFS.COMMENT))
    }

    "8. Load JSON-LD file from the local filesystem into a named graph" in {
      val ntFilePath = new File("src/test/resources/labels.json").getAbsoluteFile.toPath

      sink.request(1)
      source.sendNext(InsertGraphFromPath(ntFilePath, RDFFormat.JSONLD, Some(modelGraphIri)))
      checkAllGood(sink, Some(true))

      // now there should be 40 triples in the graph, checking with GetGraph
      sink.request(1)
      source.sendNext(GetGraph(Some(modelGraphIri)))
      val res = checkAllGood(sink, Some(true), Some(40))
      res.model.get.predicates.containsAll(Set(RDFS.LABEL, RDFS.COMMENT))
    }

    "9. Load TURTLE file from an http server using a URL into a named graph" in {

      def serveFileViaHttp(serverEndpoint: HttpEndpoint, rootFolder: String): Future[ServerBinding] = {
        import com.modelfabric.sparql.stream.client.SparqlClientConstants._
        val route =
          get {
            path(serverEndpoint.path.replaceFirst("/", "") / RemainingPath) {
              case path if path.toString == "labels.ttl" =>
                val fileSource = FileIO.fromPath(new File(s"$rootFolder/$path").toPath)
                complete(HttpResponse(entity = HttpEntity(`text/turtle`, fileSource)))
            }
          }

        Http().bindAndHandle(route, serverEndpoint.host, serverEndpoint.port, log = system.log)
      }

      val endpoint = HttpEndpoint.localhostWithAutomaticPort("/resources")
      binding = Some(Await.result(serveFileViaHttp(endpoint, "src/test/resources"), 5 seconds))
      val endpointUrlString = s"${endpoint.url}/labels.ttl"
      val rdfUrl = new URL(endpointUrlString)
      info(s"made the resource URL: $rdfUrl")

      sink.request(1)
      source.sendNext(InsertGraphFromURL(rdfUrl, RDFFormat.TURTLE, Some(modelGraphIri)))
      checkAllGood(sink, Some(true), None, 10 seconds)

      // checking with GetGraph whether it worked
      sink.request(1)
      source.sendNext(GetGraph(Some(modelGraphIri)))
      val res = checkAllGood(sink, Some(true), Some(40))
      res.model.get.predicates.containsAll(Set(RDFS.LABEL, RDFS.COMMENT))

      // should the test never get this far to stop the server, the afterAll() hook will attempt it again
      shutdownFileServer()
    }

    "10. Streams must complete gracefully" in {

      source.sendComplete()
      sparqlSource.sendComplete()

      sink.expectComplete()
      sparqlSink.expectComplete()

    }

  }

  def shutdownFileServer(): Unit = {
    binding.foreach(b => Await.result(b.unbind(), timeout))
    binding = None
  }

}
