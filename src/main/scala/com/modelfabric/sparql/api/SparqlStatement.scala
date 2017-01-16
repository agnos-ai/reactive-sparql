package com.modelfabric.sparql.api

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
abstract class SparqlStatement()(implicit val pm : PrefixMapping) extends ClientHttpRequest {

  /**
   * @return the SPARQL statement in executable form
   */
  def statement : String

  override def httpMethod: HttpMethod = HttpMethod.GET

  protected def build(statement_ : String) : String = s"""
    |${pm.sparql}
    ${statement_}
  """.trim.stripped

  override def toString: String = super.toString + ":\n" + statement

  /**
   * @return the receive timeout for the sparql statement, subclasses should
   *         override the default value which is set here to 3 seconds.
   */
  def receiveTimeout: FiniteDuration = 3 seconds

  /**
   * @return the idle timeout for the sparql statement, subclasses should
   *         override the default value which is set here to 2 seconds.
   */
  def idleTimeout: FiniteDuration = 5 seconds

  /**
   *
   * @return the overall execution timeout of this particular statement
   *         this will be used by the Future while obtaining a connection
   *         and also executing the statement and handling the responses
   */
  def executionTimeout: FiniteDuration = 10 seconds

}
