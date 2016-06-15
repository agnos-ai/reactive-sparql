package com.modelfabric.client.sparql.stream

import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import com.modelfabric.test.HttpEndpointTests

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps


import org.scalatest._
import akka.testkit.TestKit

/**
  * This test runs as part of the [[HttpEndpointTests]] Suite.
  * @param _system
  */
@DoNotDiscover
class StreamSpec(val _system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  def this() = this(HttpEndpointTests.testSystem)
  implicit val testMaterializer = ActorMaterializer()

  "Akka Streams" must {

    val auth = Authorization(BasicHttpCredentials("admin", "admin"))

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection("localhost", HttpEndpointTests.bindPort)

    "1. Allow a request to a valid URL" in {
      val responseFuture: Future[HttpResponse] =
          Source.single(HttpRequest(uri = "/test", headers = auth :: Nil))
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
    }

    "2. Fail with a request to an invalid URL" in {
      val responseFuture: Future[HttpResponse] =
          Source.single(HttpRequest(uri = "/blah", headers = auth :: Nil))
        .via(connectionFlow)
        .runWith(Sink.head)

      val result = Await.result(responseFuture, 5 seconds)
      println(result)
      assert {
        result match {
          case HttpResponse(StatusCodes.NotFound, _, _, _) => true
          case x@_ => false
        }
      }
    }
  }

}