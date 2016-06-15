package com.modelfabric.client.sparql.spray

import com.modelfabric.client.sparql.spray.client.QuerySolution
import com.modelfabric.client.sparql.spray.mapper.{SolutionMapper, DefaultSolutionMapper}
import spray.http.HttpMethods
import com.modelfabric.extension.StringExtensions._
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * SparqlStatement is the interface representing all SPARQL statements. Create a
 * subclass of SparqlStatement for each and every statement that you send to the
 * SparqlClient.
 *
 * By the way, this also goes for some non SPARQL statements such as some Graph API statements
 */
abstract class SparqlStatement()(implicit val pm : PrefixMapping) {

  /**
   * @return the SPARQL statement in executable form
   */
  def statement : String

  /**
   * @return the HTTP Method to be used to transport this statement to the endpoint, default is GET
   */
  def httpMethod = HttpMethods.GET

  /**
   * @return a solution mapper that is aware of how to map query solutions to objects of a specific type.
   *         Objects may be maps of fields and values or case class instances, for example.
   */
  def solutionMapper : SolutionMapper[_] = DefaultSolutionMapper

  protected def build(statement_ : String) : String = s"""
    |${pm.sparql}
    ${statement_}
  """.stripped

  override def toString = super.toString + ":\n" + statement

  /**
   * @return the receive timeout for the sparql statement, subclasses should
   *         override the default value which is set here to 3 seconds.
   */
  def receiveTimeout = 3 seconds

  /**
   * @return the idle timeout for the sparql statement, subclasses should
   *         override the default value which is set here to 2 seconds.
   */
  def idleTimeout = 5 seconds

  /**
   *
   * @return the overall execution timeout of this particular statement
   *         this will be used by the Future while obtaining a connection
   *         and also executing the statement and handling the responses
   */
  def executionTimeout = 10 seconds
}
