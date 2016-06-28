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
    new SparqlQuery() { override val statement = sparql }
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
