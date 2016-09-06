package com.modelfabric.sparql.api

import com.modelfabric.sparql.api.HttpMethod.GET


object SparqlQuery {

  /**
    * Construct a SparqlQuery from the passed string and implicit prefix mappings.
    *
    * @param sparql the query string
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(sparql: String)(implicit _pm : PrefixMapping): SparqlQuery = {
    // JC: why not just create two constructors in SparqlQuery, and make it concrete
    new SparqlQuery() { override val statement = build(sparql) }
  }

  /**
    * Construct a SparqlQuery from the passed string and implicit prefix mappings.
    *
    * @param sparql the query string
    * @param method the HTTP method to use
    * @param mapping the Result Set mapping to use, defaults to ResultSets
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(
    sparql: String,
    method: HttpMethod = GET,
    mapping: ResultMapper[_] = ResultSetMapper
  )(implicit _pm : PrefixMapping): SparqlQuery = {

    new SparqlQuery() {
      override val httpMethod = method
      override val statement = build(sparql)
      override val resultMapper = mapping
    }
  }

  /**
    *
    * @param query
    * @return
    */
  def unapply(query: SparqlQuery): Option[(HttpMethod, String, ResultMapper[_])] = {
    Some((query.httpMethod, query.statement, query.resultMapper))
  }

}

/**
 * SparqlQuery is the interface representing all SPARQL queries. Create a
 * subclass of SparqlQuery for each and every (SELECT) query that you send to the
 * SparqlClient.
 */
abstract class SparqlQuery()(implicit _pm : PrefixMapping) extends SparqlStatement()(_pm) {

  def query = statement

  /**
    * @return a result mapper that is aware of how to map a result set to objects of a specific type.
    *         Objects may be maps of fields and values or case class instances, for example.
    */
  def resultMapper : ResultMapper[_] = ResultSetMapper



}
