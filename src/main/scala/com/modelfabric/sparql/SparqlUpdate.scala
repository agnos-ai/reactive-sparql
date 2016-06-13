package com.modelfabric.sparql

import spray.http.HttpMethods
import java.text.SimpleDateFormat

private object SparqlUpdate {
  val format = "yyyy-MM-dd'T'HH:mm:ssZ"
}

abstract class SparqlUpdate()(
    implicit pm : PrefixMapping) extends SparqlStatement()(pm) {

  override def httpMethod = HttpMethods.POST

  protected def formatDate(date : java.util.Date) = {
    val formatter = new SimpleDateFormat(SparqlUpdate.format)
    formatter.format(date)
  }
}
