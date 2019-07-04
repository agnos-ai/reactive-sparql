package ai.agnos.sparql.api

import org.eclipse.rdf4j.model.IRI
import org.scalatest.WordSpec
import ai.agnos.sparql.stream.client.SparqlClientConstants.{valueFactory => vf}

class SparqlQueryStringBuilderSpec extends WordSpec {

  implicit val pm: PrefixMapping = PrefixMapping.none

  def iri(resource: String): IRI = vf.createIRI(s"http://agnos.ai/resource/$resource")

  private def urlDecode(string: String) : String = java.net.URLDecoder.decode(string, "UTF-8")

  import ai.agnos.sparql.util.SparqlQueryStringConverter._

  "The SparqlQuery" should {

    "know how to build a KG simple query string" in {
      val query = SparqlQuery(
        "select * from <urn:uuid:1234> where { ?s ?p ?o }"
      )
      info(query)
      info(s"DECODED:\n${urlDecode(toQueryString(query))}")
      assert(toQueryString(query) === "query=select+*+from+%3Curn%3Auuid%3A1234%3E+where+%7B+%3Fs+%3Fp+%3Fo+%7D")
    }

    "know how to build a KG a complex query string" in {
      val query = SparqlQuery(
        "select * from <urn:uuid:1234> where { ?s ?p ?o",
        bindings = Map("s" -> iri("resource1")),
        defaultGraphs = List(iri("graph1"), iri("graph2")),
        namedGraphs = List(iri("graph3"), iri("graph4")),
        limit = Some(100),
        offset = Some(1000000),
        reasoning = Some(true),
        timeout = Some(5000)
      )
      info(query)
      info(s"DECODED:\n${urlDecode(toQueryString(query))}")
      assert(toQueryString(query) === "query=select+*+from+%3Curn%3Auuid%3A1234%3E+where+%7B+%3Fs+%3Fp+%3Fo&$s=%3Chttp%3A%2F%2Fagnos.ai%2Fresource%2Fresource1%3E&default-graph-uri=%3Chttp%3A%2F%2Fagnos.ai%2Fresource%2Fgraph1%3E&default-graph-uri=%3Chttp%3A%2F%2Fagnos.ai%2Fresource%2Fgraph2%3E&named-graph-uri=%3Chttp%3A%2F%2Fagnos.ai%2Fresource%2Fgraph3%3E&named-graph-uri=%3Chttp%3A%2F%2Fagnos.ai%2Fresource%2Fgraph4%3E&limit=100&offset=1000000&reasoning=true&timeout=5000")
    }

    "fail with invalid binding names" in {
      val query = SparqlQuery(
        "select",
        bindings = Map("naught spaces" -> vf.createLiteral("blah"))
      )

      intercept[IllegalArgumentException] {
        toQueryString(query)
      }
    }

    "bind to a literal" in {

      val query = SparqlQuery(
        "select",
        bindings = Map(
          "int" -> vf.createLiteral(1),
          "string" -> vf.createLiteral("string"),
          "double" -> vf.createLiteral(1.0D / 3D),
          "boolean" -> vf.createLiteral(true)
        )
      )
      val queryString = toQueryString(query)
      info(s"raw: ${queryString}")
      val qs = queryString.split(("&"))
      val sortedParams = qs.sorted.mkString("&")
      info(s"sorted: $sortedParams")
      val decodedParams = urlDecode(sortedParams)
      info(s"decoded: ${decodedParams}")
      assert(decodedParams === """$boolean="true"^^<http://www.w3.org/2001/XMLSchema#boolean>&$double="0.3333333333333333"^^<http://www.w3.org/2001/XMLSchema#double>&$int="1"^^<http://www.w3.org/2001/XMLSchema#int>&$string="string"^^<http://www.w3.org/2001/XMLSchema#string>&query=select""")
    }

  }

}