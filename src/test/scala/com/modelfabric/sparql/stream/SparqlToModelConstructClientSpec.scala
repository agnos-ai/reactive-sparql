package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.SparqlRequestFlowBuilder
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
//@DoNotDiscover
class SparqlToModelConstructClientSpec extends TestKit(ActorSystem("SparqlToModelConstructClientSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with SparqlQueries with SparqlRequestFlowBuilder with RdfModelTestUtils {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val receiveTimeout: FiniteDuration = 30 seconds

  import HttpEndpointSuiteTestRunner._

  "The ModelConstructFlow Builder" must {
    val sparqlRequestFlowUnderTest = sparqlRequestFlow(testServerEndpoint)

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .log("constructModelRequestFlow")(log = testSystem.log)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    val fakeSparqlRequestFlowUnderTest = sparqlRequestFlow(fakeServerEndpoint)

    val ( source1, sink1 ) = TestSource.probe[SparqlRequest]
      .via(fakeSparqlRequestFlowUnderTest)
      .log("constructModelRequestFlow")(log = testSystem.log)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {

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

    "2.0 Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insertModelGraphData))
      assertSuccessResponse(sink.expectNext(receiveTimeout))
    }

    "2.1 Fail one insert" in {
      info("Using fake sparql endpoint and on purpose to fail it")
      sink1.request(1)
      source1.sendNext(SparqlRequest(insertModelGraphData))
      assertErrorResponse(Try(sink1.expectNext(receiveTimeout)))

    }

    "3. Get the filtered graph just inserted via a model construct query" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct(graphIRIs = modelAlternateGraphIri :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 10)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }
    "3.1 Get the filtered graph just inserted via a model construct query 1" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct(graphIRIs = modelGraphIri :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 30)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }
    "4. Get the full graph just inserted via a model construct query with pagenation" in {

      //TODO - need to use pagenation, otherwise the test will be failed with "Unexpected end of file" error
      val _paging = QueryPaging(Some(0), Some(30))

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct()(_paging)
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
          SparqlModelConstruct(
            resourceIRIs = modelResourceIri("0") :: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, Seq(SparqlModelResult(modelResult)), None) =>
          dumpModel(modelResult)
          assert(modelResult.size() === 3)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "6. Get the filtered predicates across graphs" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct(
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

    "7. Stream must complete gracefully" in {

      source.sendComplete()
      sink.expectComplete()
    }

  }

  private def assertSuccessResponse(response: SparqlResponse): Unit = response match {
    case SparqlResponse(_, true, _, _, _) => assert(true)
    case x@SparqlResponse(_, _, _, _, _) => fail(s"unexpected: $x")
  }

  private def assertResponse(response: Try[SparqlResponse]): Unit = response match {
    case Success(SparqlResponse(_, true, _, _, _)) => assert(true)
    case Success(x@SparqlResponse(_, _, _, _, _)) => fail(s"unexpected: $x")
    case Failure(x) => fail(s"unexpected: $x")
  }

  private def assertErrorResponse(response: Try[SparqlResponse]): Unit = response match {
    case Success(SparqlResponse(_, true, _, _, _)) => assert(false)
    case Success(x@SparqlResponse(_, _, _, _, _)) => assert(true)
    case Failure(x) => assert(true)
  }


  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

}
