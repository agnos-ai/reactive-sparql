package org.modelfabric.sparql.stream

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._

import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import org.modelfabric.test.HttpEndpointSuiteTestRunner

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


import org.scalatest._
import akka.testkit.TestKit

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
  */
@DoNotDiscover
class SparqlHttpSpec extends TestKit(ActorSystem("SparqlHttpSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  implicit val testMaterializer = ActorMaterializer()

  import HttpEndpointSuiteTestRunner.testServerEndpoint._

  "Akka Streams" must {

    val auth = Authorization(BasicHttpCredentials("admin", "admin"))

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(host, port)

    "1. Allow a request to a valid URL" in {
      val responseFuture: Future[HttpResponse] =
        Source.single(HttpRequest(uri = "/test", headers = auth :: Nil))
          .via(connectionFlow)
          .runWith(Sink.head)

      val result = Await.result(responseFuture, 5 seconds)
      println(result)
      result match {
        case HttpResponse(StatusCodes.OK, _, _, _) => assert(true)
        case x@_ => fail(s"unexpected: $x")
      }
    }

    "2. Fail with a request to an invalid URL" in {
      val responseFuture: Future[HttpResponse] =
        Source.single(HttpRequest(uri = "/blah", headers = auth :: Nil))
          .via(connectionFlow)
          .runWith(Sink.head)

      val result = Await.result(responseFuture, 5 seconds)
      println(result)
      result match {
        case HttpResponse(StatusCodes.NotFound, _, _, _) => assert(true)
        case x@_ => fail(s"unexpected: $x")
      }
    }
  }

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

}