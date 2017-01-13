package com.modelfabric.sparql.api

import com.modelfabric.sparql.api.HttpMethod.{GET, POST}


object SparqlQuery {

  /**
    * The threshold at which the SPARQL client will force to use HTTP [[POST]] method on the
    * Sparql endpoint. This is due to HTTP limiting the size of the query string.
    *
    * Defaults to 512, but may be override by setting the SPARQL_CLIENT_MAX_HTTP_QUERY_URI_LENGTH
    *
    * @see [[https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.2.1]]
    */
  val sparqlQueryPostThreshold: Int = sys.env.get("SPARQL_CLIENT_MAX_HTTP_QUERY_URI_LENGTH").map(_.toInt).getOrElse(512)

  /**
    * Decides which method to use for the query. Will return the desired method, unless the
    * query is longer that the POST threshold configured by [[sparqlQueryPostThreshold]], in which case
    * [[POST]] is returned
    * @param sparql the sparql statement
    * @param desiredMethod the desired HTTP method to use, defaults to [[GET]]
    * @return the decided HTTP method to be used
    */
  def decideQueryHttpMethod(sparql: String, desiredMethod: HttpMethod): HttpMethod = {
    (sparql, desiredMethod) match {
      case (_, POST) =>
        POST
      case (s, m) if s.length > sparqlQueryPostThreshold =>
        if (m == GET) {
          // no log is configured so we just dump this to stderr
          System.err.println(s"unable to set desired method GET due to query length = ${s.length}, using POST instead!!!")
        }
        POST
      case (_, m) => m
    }
  }

  /**
    * Construct a SparqlQuery from the passed string and implicit prefix mappings with reasoning disabled by default.
    *
    * @param sparql the query string
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(sparql: String)(implicit _pm : PrefixMapping): SparqlQuery = {
    // JC: why not just create two constructors in SparqlQuery, and make it concrete
    new SparqlQuery() { override val statement: String = build(sparql) }
  }

  /**
    * Construct a SparqlQuery from the passed string and implicit prefix mappings.
    *
    * @param sparql the query string
    * @param method the HTTP method to use
    * @param mapping the Result Set mapping to use, defaults to ResultSets
    * @param reasoningEnabled set to true to enable reasoning
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(
    sparql: String,
    method: HttpMethod = GET,
    mapping: ResultMapper[_] = ResultSetMapper,
    reasoningEnabled: Boolean = false
  )(implicit _pm : PrefixMapping = PrefixMapping.none, _paging: PagingParams = PagingParams.Defaults): SparqlQuery = {

    new SparqlQuery() {
      override val httpMethod: HttpMethod = decideQueryHttpMethod(sparql, method)
      override val statement: String = build(sparql)
      override val resultMapper: ResultMapper[_] = mapping
      override val reasoning: Boolean = reasoningEnabled
      //override val paging = _paging
    }
  }

  /**
    *
    * @param query
    * @return
    */
  def unapply(query: SparqlQuery): Option[(HttpMethod, String, ResultMapper[_], Boolean, PagingParams)] = {
    Some((query.httpMethod, query.statement, query.resultMapper.asInstanceOf[ResultMapper[_ <: SparqlResult]], query.reasoning, query.paging))
  }

}

/**
 * SparqlQuery is the interface representing all SPARQL queries. Create a
 * subclass of SparqlQuery for each and every (SELECT) query that you send to the
 * SparqlClient.
 */
abstract class SparqlQuery()(implicit _pm : PrefixMapping) extends SparqlStatement()(_pm) {

  import SparqlQuery._

  def query: String = statement

  override def httpMethod: HttpMethod = decideQueryHttpMethod(statement, GET)

  /**
    * @return a result mapper that is aware of how to map a result set to objects of a specific type.
    *         Objects may be maps of fields and values or case class instances, for example.
    */
  def resultMapper : ResultMapper[_] = ResultSetMapper

  def reasoning = false

  def paging = NoPaging

}
