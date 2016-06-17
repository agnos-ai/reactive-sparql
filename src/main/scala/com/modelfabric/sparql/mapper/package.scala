package com.modelfabric.sparql.spray

package object mapper {

  /**
    * A helper type used to map fields and values.
    *
    * For the moment both spray and akka-http rely on spray-json for JSON parsing purposes
    * so the package dependency on Spray can't be removed.
    *
    */
  type OutputMap = Map[String, String]
}
