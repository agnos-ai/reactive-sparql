package com.modelfabric.stream

import akka.actor.{ActorLogging, Props, ActorSystem}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http

import scala.concurrent.{Await, Future}

import com.modelfabric.test.Helpers

import scala.language.postfixOps

import org.scalatest._
import akka.testkit.{TestProbe, TestKit}

import com.typesafe.config.ConfigFactory

import Helpers._

import scala.concurrent.duration._

object StreamSpec {

  // This may have to be scaled back in the future
  val dbTimeout = 2 seconds

  val config = {
    ConfigFactory.parseString(
      s"""
         |akka.loggers = ["akka.testkit.TestEventListener"]
         |akka.loglevel = INFO
         |akka.remote {
         |  netty.tcp {
         |    hostname = ""
         |    port = 0
         |  }
         |}
         |akka.cluster {
         |  seed-nodes = []
         |}
    """.stripMargin).withFallback(ConfigFactory.load())
  }
  implicit val testSystem = ActorSystem("testsystem", config)
  implicit val testMaterializer = ActorMaterializer()
}

class StreamSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll {

  import StreamSpec._

  def this() = this(StreamSpec.testSystem)

  override def beforeAll() {
  }

  override def afterAll() {
    shutdownSystem
  }

  "Akka Streams" must {

    val auth = Authorization(BasicHttpCredentials("admin", "admin"))

    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection("localhost", 5820)

    "Allow a request to a valid URL" in {
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

    "Fail with a request to an invalid URL" in {
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