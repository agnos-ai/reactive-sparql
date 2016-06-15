package com.modelfabric.test

import java.net.ServerSocket

import _root_.akka.actor.{ActorSystem, Props}
import _root_.akka.testkit.{ImplicitSender, TestKit}
import com.modelfabric.client.sparql.spray.SparqlClientSpec
import com.modelfabric.client.sparql.stream.StreamSpec
import com.modelfabric.test.FusekiManager._
import com.modelfabric.test.Helpers._
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.concurrent.duration._
import scala.languageFeature.postfixOps

object HttpEndpointTests {

  val endpointKey = "SPARQL_ENDPOINT"

  val useFuseki: Boolean = ! sys.env.contains(endpointKey)

  val bindHost = "localhost"

  lazy val bindPort: Int = {
    val socket = new ServerSocket(0)
    val p = socket.getLocalPort
    socket.close()
    p
  }

  val endpoint: String = sys.env.getOrElse(endpointKey, s"http://$bindHost:$bindPort/test")

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
         |sparql.client {
         |  type = "HttpSpray"
         |  endpoint = "$endpoint"
         |  userId = "admin"
         |  password = "admin"
         |}
    """.stripMargin).withFallback(ConfigFactory.load())
  }

  val testSystem = ActorSystem("testsystem", config)
}


/**
  * A wrapper Suite that enables any nested tests to use a Fuseki server instance. The Suite will launch a fuseki-server
  * and bind it to the next available port. After the nested Suites have completed, the endpoint is shut down.
  *
  * In situations or test environments where there already is a stand-alone triple-store present, you may use the
  * ${SPARQL_ENDPOINT} environment variable to point to that endpoint instead. In this case the local
  * Fuseki server won't be started.
  *
  * @param _system
  */
class HttpEndpointTests(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with ImplicitSender with BeforeAndAfterAll {

  import HttpEndpointTests._

  def this() = this(HttpEndpointTests.testSystem)

  lazy val fusekiManager = testSystem.actorOf(Props(classOf[FusekiManager], bindHost,  bindPort), "fuseki-manager")

  override def beforeAll() {
    if (useFuseki) {
      fusekiManager ! Start
      fishForMessage(20 seconds, "Allowing Fuseki Server to start up") {
        case StartOk =>
          true
        case StartError =>
          false
      }
    }
  }

  override def afterAll() {
    if (useFuseki) {
      fusekiManager ! Shutdown
      fishForMessage(20 seconds, "Allowing Fuseki Server to shut down") {
        case ShutdownOk =>
          true
        case ShutdownError =>
          false
      }
    }
    shutdownSystem
  }

  /**
    * Add your Suites to be run here.
    *
    * The added Suites should be annotated with the @org.scalatest.DoNotDiscover to make them
    * not eligible for auto-discovery. Otherwise these will run individually as well without having the HttpEndpoint up.
    *
    * @return
    */
  override def nestedSuites = Vector(
    new SparqlClientSpec(HttpEndpointTests.testSystem),
    new StreamSpec(HttpEndpointTests.testSystem)
  )
}
