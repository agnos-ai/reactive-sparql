package com.modelfabric.test

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.modelfabric.sparql.spray.SpraySparqlClientSpec
import com.modelfabric.sparql.stream.{StreamSparqlClientSpec, SparqlRequestFlowSpec, StreamSpec}
import com.modelfabric.sparql.util.{BasicAuthentication, HttpEndpoint}
import com.modelfabric.test.FusekiManager._
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.concurrent.duration._
import scala.languageFeature.postfixOps

object HttpEndpointSuiteTestRunner {

  val sparqlServerEndpointKey = "SPARQL_ENDPOINT"
  val sparqlServerEndpointUserKey = "SPARQL_ENDPOINT_USER"
  val sparqlServerEndpointPasswordKey = "SPARQL_ENDPOINT_PASSWORD"

  val sparqlServerEndpoint: Option[String] = sys.env.get(sparqlServerEndpointKey)
  val sparqlServerEndpointUser: String = sys.env.getOrElse(sparqlServerEndpointUserKey, "admin")
  val sparqlServerEndpointPassword: String = sys.env.getOrElse(sparqlServerEndpointPasswordKey, "admin")

  lazy val sparqlServerEndpointAuthentication = BasicAuthentication(sparqlServerEndpointUser, sparqlServerEndpointPassword)

  val useFuseki: Boolean = sparqlServerEndpoint.isEmpty

  val testServerEndpoint = sparqlServerEndpoint match {
    case Some(end) => HttpEndpoint(end, Some(sparqlServerEndpointAuthentication))
    case _ => HttpEndpoint.localhostWithAutomaticPort("/test")
  }

  val config = {
    // JC: type = "HttpSpray" ?
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
         |  endpoint = "${testServerEndpoint.url}"
         |  userId = "admin"
         |  password = "admin"
         |}
         |
         |akka {
         |  http {
         |    server.parsing.illegal-header-warnings = off
         |    client.parsing.illegal-header-warnings = off
         |  }
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
  * @param _system the actor system
  */
class HttpEndpointSuiteTestRunner(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with Suite with BeforeAndAfterAll {

  import HttpEndpointSuiteTestRunner._

  def this() = this(HttpEndpointSuiteTestRunner.testSystem)

  /**
    * Add your Suites to be run here.
    *
    * The added Suites should be annotated with the @org.scalatest.DoNotDiscover to make them
    * not eligible for auto-discovery. Otherwise these will run individually as well without having the HttpEndpoint up.
    *
    * @return
    */
  override def nestedSuites = Vector(
    new SpraySparqlClientSpec(system),
    new StreamSpec(system),
    new SparqlRequestFlowSpec(system),
    new StreamSparqlClientSpec(system)
  )

  val _log = akka.event.Logging(this.system, testActor)

  lazy val fusekiManager = system.actorOf(Props(classOf[FusekiManager], testServerEndpoint), "fuseki-manager")

  override def beforeAll() {
    if (useFuseki) {
      fusekiManager ! Start
      expectMsg(20 seconds, StartOk)
    }
  }

  override def afterAll() {
    if (useFuseki) {
      fusekiManager ! Shutdown
      expectMsg(20 seconds, "Allowing Fuseki Server to shut down", ShutdownOk)
    }
    shutdownSystem
  }

  /**
    * Shutdown the system if it hasn't been done so already.
    *
    * @param system The ActorSystem currently in use.
    */
  def shutdownSystem(implicit system: ActorSystem) {
    system.terminate()
    // Give the OS some time to clean up the actor system. This sucks.
    Thread.sleep(250)
  }

}
