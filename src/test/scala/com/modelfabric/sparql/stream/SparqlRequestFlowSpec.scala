package com.modelfabric.sparql.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, _}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.modelfabric.sparql.api.{PrefixMapping, SparqlStatement, SparqlQuery}
import com.modelfabric.sparql.spray.client.ResultSet
import com.modelfabric.test.HttpEndpointSuiteTestRunner
import com.modelfabric.sparql.stream.client.Builder
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
 *
  * @param _system
  */
@DoNotDiscover
class SparqlRequestFlowSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  def this() = this(HttpEndpointSuiteTestRunner.testSystem)
  implicit val testMaterializer = ActorMaterializer()

  implicit val prefixMapping = PrefixMapping.all

  import HttpEndpointSuiteTestRunner._

  "The Sparql request flow" must {

    "1. Allow a a simple Ping request through" in {

      val sparqlRequestFlow = Builder.sparqlRequestFlow(testServerEndpoint)

      val ( source, sink ) = TestSource.probe[SparqlQuery]
        .via(sparqlRequestFlow)
        .toMat(TestSink.probe[ResultSet])(Keep.both)
        .run()

      sink.request(1)
      source.sendNext(new SparqlQuery() { override val statement = "select * where { ?s ?p ?o}" })

      sink.expectNext() match {
        case x@ResultSet(_, _) =>
          assert(true, x)
        case x@_ =>
          assert(false, x)
      }

      sink.expectNoMsg(5 seconds)

    }

  }

}