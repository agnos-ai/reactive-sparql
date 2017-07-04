package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.stream.client.{HttpClientFlowBuilder, HttpEndpointFlow, SparqlRequestFlowBuilder}
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import org.scalatest._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
  */
@DoNotDiscover
class SparqlRequestClientSpec extends TestKit(ActorSystem("SparqlRequestClientSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with SparqlQueries with SparqlRequestFlowBuilder with HttpClientFlowBuilder {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val receiveTimeout: FiniteDuration = 30 seconds

  import HttpEndpointSuiteTestRunner._

  "The Akka-Streams Sparql Client" must {
    val sparqlRequestFlowUnderTest = sparqlRequestFlow(HttpEndpointFlow(testServerEndpoint, pooledClientFlow[SparqlRequest]))

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {
      val numUpdates = 5
      sink.request(numUpdates)
      // all we need is a single delete, however we are trying
      // to saturate the stream, so it back-pressures in case
      // the responses don't get processed properly - this was a known bug
      for ( _ <- 1 to numUpdates) {
        source.sendNext(SparqlRequest(dropGraph))
      }

      for ( _ <- 1 to numUpdates) {
        assertSuccessResponse(sink.expectNext(receiveTimeout))
      }

      sink.request(1)
      source.sendNext(SparqlRequest(query1))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result == emptyResult)
        case x@_ => fail(s"Unexpected: $x")
      }

    }

    "2. Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insert1))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

    }

    "3. Allow for an update" in {

      sink.request(1)
      source.sendNext(SparqlRequest(update))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

    }

    "4. Get the results just inserted via HTTP GET" in {

      sink.request(1)
      source.sendNext(SparqlRequest(query2Get))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result === query2Result)
      }
    }

    "5. Get the results just inserted via HTTP POST" in {

      sink.request(1)
      source.sendNext(SparqlRequest(query2Post))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result === query2Result)
      }

    }

    "6. Stream must accept a 'heavy' load" in {

      val numRequests = 8

      for( i <- 0 until numRequests) {
        sink.request(1)
        println(s"sending request $i")
        source.sendNext(SparqlRequest(query2Get))
      }

      val x = for (
        _ <- 0 until numRequests;
        response = sink.expectNext(receiveTimeout)
      ) yield {
        response match {
          case SparqlResponse(_, true, _, _, _) => true
          case _ => false
        }
      }

      assert(x.count(b => b) === numRequests)

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

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

}
