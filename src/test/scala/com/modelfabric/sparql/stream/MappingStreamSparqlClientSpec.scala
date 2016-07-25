package com.modelfabric.sparql.stream

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.Builder
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
 *
  * @param _system the actor system
  */
@DoNotDiscover
class MappingStreamSparqlClientSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with SparqlQueries {

  implicit val testMaterializer = ActorMaterializer()
  implicit val executionContext = _system.dispatcher
  implicit val prefixMapping = PrefixMapping.none

  import HttpEndpointSuiteTestRunner._

  "The Akka-Streams Sparql Client" must {
    val sparqlRequestFlowUnderTest = Builder.sparqlRequestFlow(testServerEndpoint)

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {

      sink.request(1)
      source.sendNext(SparqlRequest(delete))

      assertSuccessResponse(sink.expectNext())

      sink.request(1)
      source.sendNext(SparqlRequest(query1))

      sink.expectNext() match {
        case SparqlResponse (_, true, emptyResult, None) => assert(true)
      }

    }

    "2. Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insert1))

      assertSuccessResponse(sink.expectNext())

    }

    "3. Get the MAPPED results just inserted via HTTP GET" in {

      sink.request(1)
      source.sendNext(SparqlRequest(mappingQuery2Get))

      sink.expectNext() match {
        case SparqlResponse (_, true, mappedQuery2Result, None) => assert(true)
        case r@SparqlResponse(_, _, _, _) => assert(false, r)
      }
    }

    "4. Get the MAPPED results just inserted via HTTP POST" in {

      sink.request(1)
      source.sendNext(SparqlRequest(mappingQuery2Post))

      sink.expectNext() match {
        case SparqlResponse (_, true, mappedQuery2Result, None) => assert(true)
        case r@SparqlResponse(_, _, _, _) => assert(false, r)
      }

    }

    "5. Stream must complete gracefully" in {

      source.sendComplete()
      sink.expectComplete()
      sink.expectNoMsg(1 second)
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
