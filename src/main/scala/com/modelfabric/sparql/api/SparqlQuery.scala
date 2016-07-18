package com.modelfabric.sparql.api


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
    * @param method the HTTP method to use
    * @param sparql the query string
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(method: HttpMethod, sparql: String)(implicit _pm : PrefixMapping): SparqlQuery = {
    new SparqlQuery() {
      override val httpMethod = method
      override val statement = build(sparql)
    }
  }

  /**
    *
    * @param query
    * @return
    */
  def unapply(query: SparqlQuery): Option[(HttpMethod, String)] = {
    Some((query.httpMethod, query.statement))
  }

}

/**
 * SparqlQuery is the interface representing all SPARQL queries. Create a
 * subclass of SparqlQuery for each and every (SELECT) query that you send to the
 * SparqlClient.
 */
abstract class SparqlQuery()(implicit _pm : PrefixMapping) extends SparqlStatement()(_pm) {

  def query = statement

}
