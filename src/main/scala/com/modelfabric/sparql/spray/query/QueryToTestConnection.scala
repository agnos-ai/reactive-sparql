package com.modelfabric.sparql.spray.query

import com.modelfabric.sparql.api.{SparqlQuery, PrefixMapping}

/**
 * This query is executed periodically by the SparqlConnectionTester Actor to test whether the given SPARQL endpoint
 * is still available.
 */
case class QueryToTestConnection() extends SparqlQuery()(PrefixMapping.none) {

  /**
   * @return the SPARQL statement in executable form
   */
  def statement = build(s"""
    |SELECT * WHERE { ?a ?b ?c } LIMIT 1
  """)
}