package com.modelfabric.sparql.stream

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
class StreamSparqlClientSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with SparqlQueries {

  def this() = this(HttpEndpointSuiteTestRunner.testSystem)
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

    "1. Allow one insert" in {

      sink.request(1)
      source.sendNext(SparqlRequest(insert1))

      assertSuccessResponse(sink.expectNext())

    }

    "2. Allow for an update" in {

      sink.request(1)
      source.sendNext(SparqlRequest(update))

      assertSuccessResponse(sink.expectNext())

    }

    "3. Get the results just inserted via HTTP GET" in {

      sink.request(1)
      source.sendNext(SparqlRequest(query2Get))

      sink.expectNext() match {
        case SparqlResponse (_, true, Some (query2Result), None) => assert(true)
      }
    }

    // JC: it's not clear it's inserted in test 1 or 2.
    "4. Get the results just inserted via HTTP POST" in {

      sink.request(1)
      source.sendNext(SparqlRequest(query2Post))

      sink.expectNext() match {
        case SparqlResponse (_, true, Some (query2Result), None) => assert(true)
      }

    }

    "5. Stream must accept a 'heavy' load" in {

      val numRequests = 20

      for( i <- 0 until numRequests) {
        sink.request(1)
        println(s"sending request $i")
        source.sendNext(SparqlRequest(query2Get))
      }

      val x = for (
        i <- 0 until numRequests;
        response = sink.expectNext(3 seconds)
      ) yield {
        response match {
          case SparqlResponse(_, true, _, _) => true
          case _ => false
        }
      }

      assert(x.count(b => b) === numRequests)

    }

    //FIXME: the stream does not want to complete with Fuseki!!!
    "6. Stream must complete gracefully" in {

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
