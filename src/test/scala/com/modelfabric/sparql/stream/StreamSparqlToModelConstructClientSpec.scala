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
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
 *
  * @param _system the actor system
  */
@DoNotDiscover
class StreamSparqlToModelConstructClientSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with SparqlQueries with SparqlRequestFlowBuilder {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val receiveTimeout: FiniteDuration = 3 seconds

  import HttpEndpointSuiteTestRunner._

  "The Akka-Streams Sparql Client" must {
    val sparqlRequestFlowUnderTest = sparqlRequestFlow(testServerEndpoint)

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .log("constructModelRequestFlow")(log = testSystem.log)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {

      sink.request(1)
      source.sendNext(SparqlRequest(deleteModelGraph))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

      sink.request(1)
      source.sendNext(SparqlRequest(queryModelGraph))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, result, None) => assert(result == emptyResult)
      }

    }

    "2. Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insertModelGraphData))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

    }

    "4. Get the filtered graph just inserted via a model construct query" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct(graphIRIs = modelAlternateGraphIri :: Nil)
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, Seq(SparqlModelResult(modelResult)), None) =>
          assert(modelResult.size() === 10)
          info(modelResult.toString)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "3. Get the full graph just inserted via a model construct query" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct()
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, Seq(SparqlModelResult(modelResult)), None) =>
          assert(modelResult.size() === 40)
          info(modelResult.toString)
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
        case SparqlResponse (_, true, Seq(SparqlModelResult(modelResult)), None) =>
          assert(modelResult.size() === 3)
          info(modelResult.toString)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "6. Get the filtered predicates across graphs" in {

      sink.request(1)
      source.sendNext(
        SparqlRequest(
          SparqlModelConstruct(
            propertyIRIs = uri(PrefixMapping.standard.getNsPrefixURI("rdfs") + "label") :: Nil
          )
        )
      )

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, Seq(SparqlModelResult(modelResult)), None) =>
          assert(modelResult.size() === 25)
          info(modelResult.toString)
        case x@_ => fail(s"failing due to unexpected message received: $x")
      }
    }

    "7. Stream must complete gracefully" in {

      source.sendComplete()
      sink.expectComplete()
    }

  }

  private def assertSuccessResponse(response: SparqlResponse): Unit = response match {
    case SparqlResponse(_, true, _, _) => assert(true)
    case x@SparqlResponse(_, _, _, _) => assert(false, x)
  }

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
  }
}
