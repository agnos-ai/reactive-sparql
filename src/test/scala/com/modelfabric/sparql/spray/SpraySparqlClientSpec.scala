package com.modelfabric.sparql.spray

import com.modelfabric.sparql.SparqlQueries
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.spray.client.{SparqlHttpSprayClient, MessageSparqlClientQueryEnd}
import com.modelfabric.test.HttpEndpointSuiteTestRunner

import scala.language.postfixOps

import akka.actor.{Props, ActorSystem}

import spray.http.{StatusCodes, HttpResponse}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest._

import com.modelfabric.sparql.spray.client._

import scala.concurrent.duration._

object SpraySparqlClientSpec {
  // This may have to be scaled back in the future
  val dbTimeout = 5 seconds
}

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
  *
  * @param _system
  */
@DoNotDiscover
class SpraySparqlClientSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike with MustMatchers with BeforeAndAfterAll
  with ImplicitSender with SparqlQueries {

  def this() = this(HttpEndpointSuiteTestRunner.testSystem)

  def config = SparqlClientConfig(_system)

  lazy val client = system.actorOf(Props(classOf[SparqlHttpSprayClient], config))

  import SpraySparqlClientSpec._

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

  "The Spray Sparql Client" must {

    "1. Clear the data" in {

      client ! delete

      fishForMessage(dbTimeout, "a. wait for MessageSparqlClientUpdateSuccessful") {
        case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
    }

    "2. Allow one insert" in {

      client ! insert1

      fishForMessage(dbTimeout, "a. wait for MessageSparqlClientUpdateSuccessful") {
        case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
        case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
        case msg @ _ => handleUnknownMessage(msg)
      }
    }

    "3. Allow for an update" in {

      client ! update

      fishForMessage(dbTimeout, "a. response from sparql client") {
         case MessageSparqlClientUpdateSuccessful(_) => handleSparqlUpdateSuccessful
         case msg @ MessageSparqlStatementFailed(_, _, _) => handleSparqlClientError(msg.statement, msg.response)
         case msg @ _ => handleUnknownMessage(msg)
       }
    }

    "4. Get the results just inserted via HTTP GET" in {

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

    "5. Get the results just inserted via HTTP POST" in {

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

