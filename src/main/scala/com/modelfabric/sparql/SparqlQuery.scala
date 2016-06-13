package com.modelfabric.sparql

import com.modelfabric.extension.StringExtensions._

/**
 * SparqlQuery is the interface representing all SPARQL queries. Create a
 * subclass of SparqlQuery for each and every (SELECT) query that you send to the
 * SparqlClient.
 */
abstract class SparqlQuery()(implicit pm_ : PrefixMapping) extends SparqlStatement()(pm_) {

  def query = statement

}
