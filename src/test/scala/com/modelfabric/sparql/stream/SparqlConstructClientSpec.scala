package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.{HttpClientFlowBuilder, HttpEndpointFlow, SparqlRequestFlowBuilder}
import com.modelfabric.sparql.util.RdfModelTestUtils
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
  */
@DoNotDiscover
class SparqlConstructClientSpec
  extends TestKit(ActorSystem("SparqlToModelConstructClientSpec"))
  with SparqlConstructSpecBase {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none


  "The ModelConstructFlow Builder" must {

    "1. Clear the data" in {
      clearData()
    }

    "2 Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insertModelGraphData))
      assertSuccessResponse(sink.expectNext(receiveTimeout))
    }


    "3. Get the filtered graph just inserted via a model construct query" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(graphIRIs = modelAlternateGraphIri :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 11)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }
    "3.1 Get the filtered graph just inserted via a model construct query 1" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(graphIRIs = modelGraphIri :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 31)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "3.2 return empty model for invalid IRI" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(graphIRIs = "urn:test:mfab:not-exist-iri" :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 0)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "4. Get the full graph just inserted via a model construct query with pagenation" in {

      //TODO - need to use pagenation, otherwise the test will be failed with "Unexpected end of file" error
      val _paging = QueryPaging(Some(0), Some(30))

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct()(_paging)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 30)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "5. Get the filtered resources across graphs" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(
            resourceIRIs = modelResourceIri("0") :: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 5)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "6. Get the filtered predicates across graphs" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(
            propertyIRIs = PrefixMapping.standard.getNsPrefixURI("rdfs") + "label":: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 25)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "7. Get resource as value" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlConstruct(
            valueIRIs = "urn:test:mfab:type:0" :: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 2)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "8. Stream must complete gracefully" in {

      source.sendComplete()
      sink.expectComplete()
    }

  }

}

trait SparqlConstructSpecBase
  extends SparqlRequestFlowBuilder
  with HttpClientFlowBuilder
  with SparqlQueries
  with RdfModelTestUtils
  with WordSpecLike
  with BeforeAndAfterAll { this: TestKit =>

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

  import HttpEndpointSuiteTestRunner._

  implicit val receiveTimeout: FiniteDuration = 30 seconds

  private lazy val sparqlRequestFlowUnderTest =
    sparqlRequestFlow(
      HttpEndpointFlow(
        testServerEndpoint,
        pooledClientFlow[SparqlRequest]
      )
    )

  lazy val (source, sink): (TestPublisher.Probe[SparqlRequest], TestSubscriber.Probe[SparqlResponse]) =
    TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .log("constructModelRequestFlow")(log = testSystem.log)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()


  protected def clearData(): Unit = {
    sink.request(1)
    source.sendNext(SparqlRequest(deleteDefaultGraphTriples))
    assertSuccessResponse(sink.expectNext(receiveTimeout))

    sink.request(1)
    source.sendNext(SparqlRequest(deleteModelGraph))
    assertSuccessResponse(sink.expectNext(receiveTimeout))

    sink.request(1)
    source.sendNext(SparqlRequest(deleteAlternateModelGraph))
    assertSuccessResponse(sink.expectNext(receiveTimeout))

    sink.request(1)
    source.sendNext(SparqlRequest(queryModelGraph))
    sink.expectNext(receiveTimeout) match {
      case SparqlResponse (_, true, _, result, None) => assert(result == emptyResult)
    }
  }


  protected def assertSuccessResponse(response: SparqlResponse): Unit = response match {
    case SparqlResponse(_, true, _, _, _) => assert(true)
    case x@SparqlResponse(_, _, _, _, _) => fail(s"unexpected: $x")
  }

  protected def assertResponse(response: Try[SparqlResponse]): Unit = response match {
    case Success(SparqlResponse(_, true, _, _, _)) => assert(true)
    case Success(x@SparqlResponse(_, _, _, _, _)) => fail(s"unexpected: $x")
    case Failure(x) => fail(s"unexpected: $x")
  }

  protected def assertErrorResponse(response: Try[SparqlResponse]): Unit = response match {
    case Success(SparqlResponse(_, true, _, _, _)) => assert(false)
    case Success(x@SparqlResponse(_, _, _, _, _)) => assert(true)
    case Failure(x) => assert(true)
  }

}
