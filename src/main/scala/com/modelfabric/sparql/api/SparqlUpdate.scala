package com.modelfabric.sparql.api

import java.text.SimpleDateFormat

object SparqlUpdate {

  /**
    * Construct a SparqlUpdate from the passed string and implicit prefix mappings.
    *
    * @param sparql the query string
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(sparql: String)(implicit _pm : PrefixMapping): SparqlUpdate = {
    new SparqlUpdate() {
      override val statement = build(sparql)
    }
  }

  def unapply(update: SparqlUpdate): Option[(HttpMethod, String)] = {
    Some((update.httpMethod, update.statement))
  }

}

abstract class SparqlUpdate()(
    implicit pm : PrefixMapping) extends SparqlStatement()(pm) {

  lazy val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

  override def httpMethod = HttpMethod.POST

  protected def formatDate(date : java.util.Date) = {
    formatter.format(date)
  }
}
