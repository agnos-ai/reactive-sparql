package com.modelfabric.sparql.stream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.api._
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import com.modelfabric.sparql.stream.client.Builder
import org.scalatest._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
 *
  * @param _system the actor system
  */
@DoNotDiscover
class SparqlRequestFlowSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  def this() = this(HttpEndpointSuiteTestRunner.testSystem)
  implicit val testMaterializer = ActorMaterializer()
  implicit val executionContext = _system.dispatcher
  implicit val prefixMapping = PrefixMapping.none

  import HttpEndpointSuiteTestRunner._

  "The Sparql request flow" must {

    val sparqlRequestFlowUnderTest = Builder.sparqlRequestFlow(testServerEndpoint)

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Allow a simple SPARQL Select via HTTP GET" in {

      sink.request(1)
      source.sendNext {
        SparqlRequest {
          SparqlQuery {
            s"""
               |SELECT DISTINCT ?g
               |WHERE {
               |  GRAPH ?g {
               |   ?s ?p ?o .
               |  }
               |  FILTER(?g = <urn:test:mfab:data>)
               |}
               |LIMIT 1""".stripMargin
          }
        }
      }


      sink.expectNext() match {
        case SparqlResponse(true, Some(ResultSet(_, results)), None) =>
          val result = results.bindings.head
          println(result.prettyPrint)
          assert(result.asValueMap.get("g") === Some(QuerySolutionValue("uri", None, "urn:test:mfab:data")))
        case x@_ =>
          assert(false, x)
      }

      sink.expectNoMsg(1 second)
    }

    "2. Allow a simple SPARQL Select via HTTP POST" in {

      sink.request(1)
      source.sendNext {
        SparqlRequest {
          SparqlQuery(HttpMethod.POST,
            s"""
             |SELECT DISTINCT ?g
             |WHERE {
             |  GRAPH ?g {
             |   ?s ?p ?o .
             |  }
             |  FILTER(?g = <urn:test:mfab:data>)
             |}
             |LIMIT 1""".stripMargin
          )
        }
      }

      sink.expectNext() match {
        case SparqlResponse(true, Some(ResultSet(_, results)), None) =>
          val result = results.bindings.head
          println(result.prettyPrint)
          assert(result.asValueMap.get("g") === Some(QuerySolutionValue("uri", None, "urn:test:mfab:data")))
        case x@_ =>
          assert(false, x)
      }

      sink.expectNoMsg(1 second)
    }
  }

}
