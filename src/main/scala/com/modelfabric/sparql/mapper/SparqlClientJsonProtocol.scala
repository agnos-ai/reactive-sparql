package com.modelfabric.sparql.mapper

import akka.http.scaladsl.unmarshalling._
import com.modelfabric.sparql.api._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsObject, JsValue, RootJsonFormat}

/**
  * Json protocol which relies on spray's Json library.
  */
object SparqlClientJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with PredefinedFromEntityUnmarshallers with PredefinedFromStringUnmarshallers {

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

  // JC: add some comments when these formats are used?
  implicit val format2 = jsonFormat(ResultSetResults, "bindings")
  implicit val format1 = jsonFormat(ResultSetVars, "vars")
  implicit val format3 = jsonFormat2(ResultSet)


  /**
    * Boolean entity unmarshaller for (text/boolean) media type.
    * @return
    */
  implicit def booleanEntityUnmarshaller: FromEntityUnmarshaller[Boolean] =
    byteStringUnmarshaller mapWithInput { (entity, bytes) ⇒
      if (entity.isKnownEmpty) false
      else bytes.decodeString(Unmarshaller.bestUnmarshallingCharsetFor(entity).nioCharset.name).toLowerCase.equals("true")
    }

}
