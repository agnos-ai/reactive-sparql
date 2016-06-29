package com.modelfabric.sparql.mapper

import com.modelfabric.sparql.api._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsObject, JsValue, RootJsonFormat}

/**
  * Json protocol which relies on spray's Json library.
  */
object SparqlClientJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val format4 = jsonFormat3(QuerySolutionValue)

  implicit object format5 extends RootJsonFormat[QuerySolution] {
    def write(c : QuerySolution) = JsObject()
    def read(row : JsValue) = read(row.asInstanceOf[JsObject])
    def read(row : JsObject) = {
      QuerySolution(row.fields mapValues {
        (value : JsValue) => value.convertTo[QuerySolutionValue]
      })
    }
  }

  implicit val format2 = jsonFormat(ResultSetResults, "bindings")
  implicit val format1 = jsonFormat(ResultSetVars, "vars")
  implicit val format3 = jsonFormat2(ResultSet)

}
