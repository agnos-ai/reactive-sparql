package com.modelfabric.sparql.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, _}
import akka.testkit.TestKit
import com.modelfabric.sparql.api.SparqlQuery
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

  import HttpEndpointSuiteTestRunner._

  "The Sparql request flow" must {

    "1. Allow a a simple Ping request through" in {
/*
      val auth = Authorization(BasicHttpCredentials("admin", "admin"))

      val sparqlSource: Source[SparqlQuery, NotUsed] = Source.single(new SparqlQuery() { override val statement = "select * where { ?s ?p ?o}" })

      val resultSink: Sink[ResultSet] = Sink.head

      val sparlqRequestFlow = Builder.sparqlRequestFlow(testServerEndpoint, sparqlSource.to, resultsInlet)

      val responseFuture: Future[HttpResponse] =
        Source.single(new SparqlQuery() { override val statement = "select * where { ?s ?p ?o}" })
          .via(connectionFlow)
          .runWith(Sink.head)

      val result = Await.result(responseFuture, 5 seconds)
      println(result)
      assert {
        result match {
          case HttpResponse(StatusCodes.OK, _, _, _) => true
          case x@_ => false
        }
      }
*/
    }

  }

}