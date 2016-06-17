package com.modelfabric.sparql.api

import java.text.SimpleDateFormat

private object SparqlUpdate {
  val format = "yyyy-MM-dd'T'HH:mm:ssZ"
}

abstract class SparqlUpdate()(
    implicit pm : PrefixMapping) extends SparqlStatement()(pm) {

  override def httpMethod = HttpMethod.POST

  protected def formatDate(date : java.util.Date) = {
    val formatter = new SimpleDateFormat(SparqlUpdate.format)
    formatter.format(date)
  }
}
