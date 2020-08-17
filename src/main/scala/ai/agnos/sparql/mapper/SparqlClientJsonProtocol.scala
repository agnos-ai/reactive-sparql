package ai.agnos.sparql.mapper

import akka.http.scaladsl.unmarshalling._
import ai.agnos.sparql.api._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Json protocol which relies on spray's Json library.
  */
object SparqlClientJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with PredefinedFromEntityUnmarshallers with PredefinedFromStringUnmarshallers {

  /**
    * A set if JSON parsers form "query results" format compliant
    * with https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
    */
  implicit val format4 = jsonFormat3(QuerySolutionValue)
  implicit object format5 extends RootJsonFormat[QuerySolution] {
    def write(c : QuerySolution) = {
      JsObject(c.values.map(e => e._1 -> e._2.toJson))
    }
    def read(row : JsValue) = read(row.asInstanceOf[JsObject])
    def read(row : JsObject) = {
      QuerySolution((row.fields mapValues {
        (value : JsValue) => value.convertTo[QuerySolutionValue]
      }).toMap)
    }
  }
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
