package ai.agnos.test

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.testkit.{ImplicitSender, TestKit}
import ai.agnos.sparql.stream._
import ai.agnos.sparql.util.{BasicAuthentication, HttpEndpoint}
import ai.agnos.test.FusekiManager._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._

import scala.concurrent.Await
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


  val config: Config = {
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
    new SparqlConstructClientSpec(),
    new SparqlHttpSpec(),
    new SparqlRequestClientSpec(),
    new MappingStreamSparqlClientSpec(),
    new GraphStoreProtocolBuilderSpec()
  )

  val _log = akka.event.Logging(this.system, testActor)

  private lazy val fusekiManager = system.actorOf(Props(classOf[FusekiManager], testServerEndpoint), "fuseki-manager")

  val startTimeout = 100 seconds
  val stopTimeout = 20 seconds

  override def beforeAll() {
    if (useFuseki) {
      fusekiManager ! Start
      expectMsg(startTimeout, StartOk)
    }
  }

  override def afterAll() {
    if (useFuseki) {
      fusekiManager ! Shutdown
      expectMsg(stopTimeout, "Allowing Fuseki Server to shut down", ShutdownOk)
    }
    shutdownSystem
  }

  /**
    * Shutdown the system if it hasn't been done so already.
    *
    * @param system The ActorSystem currently in use.
    */
  def shutdownSystem(implicit system: ActorSystem) {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

}
