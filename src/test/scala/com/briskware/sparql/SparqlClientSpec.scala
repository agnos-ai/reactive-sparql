package com.briskware.sparql

import com.briskware.sparql.client.{SparqlHttpSprayClient, SparqlClientConfig, QuerySolution, MessageSparqlClientQueryEnd}
import com.briskware.test.functional.Helpers

import scala.language.postfixOps
import scala.concurrent.duration._

import akka.actor.{Props, ActorSystem}

import spray.http.{StatusCodes, HttpResponse}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import com.briskware.sparql.client._
import Helpers._
import spray.http.HttpResponse
import com.typesafe.config.ConfigFactory

object SparqlClientSpec {
  // This may have to be scaled back in the future
  val dbTimeout = 2 seconds

  val realConfig = ConfigFactory.load()

  val config = ConfigFactory.parseString("""

    akka.loggers = ["akka.testkit.TestEventListener"]

    akka.remote {
      netty.tcp {
        hostname = ""
        port = 0
      }
    }

    akka.cluster {
      seed-nodes = []
    }""").withFallback(realConfig)

  implicit val testSystem = ActorSystem("testsystem", config)
}

class SparqlClientSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with ImplicitSender {

  def this() = this(SparqlClientSpec.testSystem)

  override def beforeAll() {
    //startServer()
  }

  override def afterAll() {
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

  lazy val insert2 = new SparqlUpdate {
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

  lazy val query2 = new SparqlQuery {
    override def statement = build(s"""
    |SELECT ?g ?b ?c
    |FROM NAMED <urn:test:bware:data>
    |WHERE {
    |  GRAPH ?g {
    |    <urn:test:whatever> ?b ?c
    |  }
    |}""")
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
    "1. Return at least one query solution" in {

      client ! query1

      val result = fishForMessage(dbTimeout, "a. wait for MessageSparqlClientQuerySolution") {
        case MessageSparqlClientQuerySolution(_, qs) => handleSparqlQuerySolution(qs)
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryNoResults
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
      if (result == true) {
        fishForMessage(dbTimeout, "b. wait for MessageSparqlClientQueryEnd") {
          case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryEnd
          case msg @ _ => handleUnknownMessage(msg)
        }
      }
    }

    "2. Allow one insert" in {

      client ! insert1

      fishForMessage(dbTimeout, "Wait for MessageSparqlClientUpdateSuccessful") {
        case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
    }


    "3. Allow one insert" in {

      client ! insert2

      fishForMessage(dbTimeout, "3. response from sparql client") {
         case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
         case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
         case msg @ _ => handleUnknownMessage(msg)
       }
    }

    "4. Get the results just inserted" in {

      client ! query2

      val result = fishForMessage(dbTimeout, "a. wait for MessageSparqlClientQuerySolution") {
        case MessageSparqlClientQuerySolution(_, qs) => handleSparqlQuerySolution(qs)
        case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryNoResults
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
      if (result == true) {
        fishForMessage(dbTimeout, "b. wait for MessageSparqlClientQueryEnd") {
          case MessageSparqlClientQueryEnd(_, _) => handleSparqlQueryEnd
          case msg @ _ => handleUnknownMessage(msg)
        }
      }
    }
  }
}

