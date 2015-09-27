package com.briskware.sparql.client

object SparqlServerType extends Enumeration {

  type SparqlClientType = Value

  val HttpJena = Value
  val HttpSpray = Value
  //val StardogJena = Value
}
