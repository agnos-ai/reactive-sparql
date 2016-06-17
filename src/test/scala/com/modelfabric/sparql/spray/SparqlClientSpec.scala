package com.modelfabric.sparql.spray

import com.modelfabric.sparql.api._
import com.modelfabric.sparql.spray.client.{SparqlHttpSprayClient, QuerySolution, MessageSparqlClientQueryEnd}
import com.modelfabric.test.HttpEndpointTests

import scala.language.postfixOps

import akka.actor.{Props, ActorSystem}

import spray.http.{StatusCodes, HttpResponse}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import com.modelfabric.sparql.spray.client._

import scala.concurrent.duration._

object SparqlClientSpec {
  // This may have to be scaled back in the future
  val dbTimeout = 2 seconds
}

/**
  * This test runs as part of the [[HttpEndpointTests]] Suite.
  *
  * @param _system
  */
@DoNotDiscover
class SparqlClientSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with ImplicitSender {

  def this() = this(HttpEndpointTests.testSystem)

  def config = SparqlClientConfig(_system)

  lazy val client = system.actorOf(Props(classOf[SparqlHttpSprayClient], config))

  implicit val pm = PrefixMapping.extended

  lazy val query1 = new SparqlQuery {
    override def statement = build(s"""
    |SELECT ?a ?b ?c
    |FROM
    |  <urn:test:mfab:data>
    |WHERE {
    | ?a ?b ?c .
    |}
    |LIMIT 1
    |""")
  }

  lazy val insert1x = new SparqlUpdate {
    override def statement = build(s"""
    |WITH <urn:test:mfab:data>
    |DELETE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}
    |INSERT {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}
    |WHERE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}""")
  }

  lazy val insert1 = new SparqlUpdate {
    override def statement = build(s"""
    |INSERT DATA {
    |  GRAPH <urn:test:mfab:data> {
    |    <urn:test:whatever> foaf:givenName "Bill"
    |  }
    |}""")
  }

  lazy val update = new SparqlUpdate {
    override def statement = build(s"""
    |WITH <urn:test:mfab:data>
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
      |FROM NAMED <urn:test:mfab:data>
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
    override def httpMethod = HttpMethod.POST
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

