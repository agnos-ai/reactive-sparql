package com.briskware.sparql

import java.net.ServerSocket

import com.briskware.sparql.client.{SparqlHttpSprayClient, SparqlClientConfig, QuerySolution, MessageSparqlClientQueryEnd}
import com.briskware.test.functional.{FusekiManager, Helpers}

import scala.language.postfixOps

import akka.actor.{Props, ActorSystem}

import spray.http.{HttpMethods, StatusCodes, HttpResponse}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import com.briskware.sparql.client._
import Helpers._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object SparqlClientSpec {

  // This may have to be scaled back in the future
  val dbTimeout = 2 seconds

  val endpointKey = "SPARQL_ENDPOINT"

  val useFuseki: Boolean = ! sys.env.contains(endpointKey)

  val endpoint: String = sys.env.getOrElse(endpointKey, s"http://localhost:${port}/test")

  lazy val port: Int = {
    val socket = new ServerSocket(0)
    val p = socket.getLocalPort
    socket.close()
    p
  }

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
        |  endpoint = "${endpoint}"
        |  userId = "admin"
        |  password = "admin"
        |}
    """.stripMargin).withFallback(ConfigFactory.load())
    }
  implicit val testSystem = ActorSystem("testsystem", config)
}

class SparqlClientSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with ImplicitSender {

  import SparqlClientSpec._
  import FusekiManager._

  def this() = this(SparqlClientSpec.testSystem)

  lazy val fusekiManager = system.actorOf(Props(classOf[FusekiManager], port), "fuseki-manager")

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

  def config = SparqlClientConfig(_system)

  lazy val client = system.actorOf(Props(classOf[SparqlHttpSprayClient], config))

  implicit val pm = PrefixMapping.extended

  lazy val query1 = new SparqlQuery {
    override def statement = build(s"""
    |SELECT ?a ?b ?c
    |FROM
    |  <urn:test:bware:data>
    |WHERE {
    | ?a ?b ?c .
    |}
    |LIMIT 1
    |""")
  }

  lazy val insert1x = new SparqlUpdate {
    override def statement = build(s"""
    |WITH <urn:test:bware:data>
    |DELETE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme bware:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader bware:StoryType-Story
    |}
    |INSERT {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme bware:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader bware:StoryType-Story
    |}
    |WHERE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme bware:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader bware:StoryType-Story
    |}""")
  }

  lazy val insert1 = new SparqlUpdate {
    override def statement = build(s"""
    |INSERT DATA {
    |  GRAPH <urn:test:bware:data> {
    |    <urn:test:whatever> foaf:givenName "Bill"
    |  }
    |}""")
  }

  lazy val update = new SparqlUpdate {
    override def statement = build(s"""
    |WITH <urn:test:bware:data>
    |DELETE {
    |  ?person foaf:givenName "Bill"
    |}
    |INSERT {
    |  ?person foaf:givenName "William"
    |}
    |WHERE {
    |  ?person foaf:givenName "Bill"
    |}""")
  }

  lazy val sparql2 = s"""
      |SELECT ?g ?b ?c
      |FROM NAMED <urn:test:bware:data>
      |WHERE {
      |  GRAPH ?g {
      |    <urn:test:whatever> ?b ?c
      |  }
      |}"""

  lazy val query2Get = new SparqlQuery {
    override def statement = build(sparql2)
  }

  lazy val query2Post = new SparqlQuery {
    override def statement = build(sparql2)
    override def httpMethod = HttpMethods.POST
  }

  import SparqlClientSpec._

  def handleSparqlQuerySolution(qs_ : QuerySolution) = {
    system.log.info("Received MessageSparqlClientQuerySolution {}", qs_)
    true
  }

  def handleSparqlQueryEnd = {
    system.log.info("Received MessageSparqlClientQueryEnd")
    true
  }

  def handleSparqlQueryNoResults = {
    system.log.error("Received MessageSparqlClientQueryEnd before any MessageSparqlClientQuerySolution messages")
    false
  }

  def handleSparqlUpdateSuccessful = {
    system.log.info("Received MessageSparqlClientUpdateSuccessful")
    true
  }

  def handleSparqlClientError(statement_ : SparqlStatement, response_ : Option[HttpResponse]) = {
    system.log.error("Error '{}' occurred for SPARQL statement: {}",
                     response_.getOrElse(HttpResponse(status = StatusCodes.RequestTimeout)).status.defaultMessage,
                     statement_.statement)
    false
  }

  def handleUnknownMessage(msg : Any) = {
    system.log.error("Received unknown message: {}", msg)
    false
  }

  "The SparqlClient" must {

    "1. Allow one insert" in {

      client ! insert1

      fishForMessage(dbTimeout, "a. wait for MessageSparqlClientUpdateSuccessful") {
        case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
    }

    "2. Allow for an update" in {

      client ! update

      fishForMessage(dbTimeout, "a. response from sparql client") {
         case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
         case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
         case msg @ _ => handleUnknownMessage(msg)
       }
    }

    "3. Get the results just inserted via HTTP GET" in {

      client ! query2Get

      fishForMessage(dbTimeout, "a. wait for MessageSparqlClientQuerySolution") {
        case MessageSparqlClientQuerySolution(_, qs) => handleSparqlQuerySolution(qs)
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryNoResults
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
      fishForMessage(dbTimeout, "b. wait for MessageSparqlClientQueryEnd") {
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryEnd
        case msg @ _ => handleUnknownMessage(msg)
      }
  }

    "4. Get the results just inserted via HTTP POST" in {

      client ! query2Post

      fishForMessage(dbTimeout, "a. wait for MessageSparqlClientQuerySolution") {
        case MessageSparqlClientQuerySolution(_, qs) => handleSparqlQuerySolution(qs)
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryNoResults
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
      fishForMessage(dbTimeout, "b. wait for MessageSparqlClientQueryEnd") {
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryEnd
        case msg @ _ => handleUnknownMessage(msg)
      }
    }
  }
}

