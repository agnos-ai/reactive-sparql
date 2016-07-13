package com.modelfabric.sparql.api

// JC: not used class?
abstract class SparqlInsertData()(implicit pm : PrefixMapping) extends SparqlStatement() {

  override def httpMethod = HttpMethod.POST
}
