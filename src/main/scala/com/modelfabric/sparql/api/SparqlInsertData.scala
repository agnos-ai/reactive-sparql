package com.modelfabric.sparql.api

abstract class SparqlInsertData()(implicit pm : PrefixMapping) extends SparqlStatement() {

  override def httpMethod = HttpMethod.POST
}
