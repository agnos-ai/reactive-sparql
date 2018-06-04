package org.modelfabric.sparql.api

import java.text.SimpleDateFormat

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}

object SparqlUpdate {

  /**
    * Construct a SparqlUpdate from the passed string and implicit prefix mappings.
    *
    * @param sparql the query string
    * @param _pm prefix mappings from the current scope
    * @return
    */
  def apply(sparql: String)(
    implicit _pm : PrefixMapping): SparqlUpdate = {
    new SparqlUpdate() {
      override val statement: String = build(sparql)
    }
  }

  def unapply(update: SparqlUpdate): Option[(HttpMethod, String)] = {
    Some((update.httpMethod, update.statement))
  }

}

abstract class SparqlUpdate()(
  implicit _pm: PrefixMapping) extends SparqlStatement()(_pm) {

  lazy val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

  override val httpMethod = HttpMethods.POST

  protected def formatDate(date : java.util.Date) = {
    formatter.format(date)
  }
}
