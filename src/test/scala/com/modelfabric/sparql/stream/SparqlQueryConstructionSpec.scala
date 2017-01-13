package com.modelfabric.sparql.stream

import com.modelfabric.sparql.api.HttpMethod.{GET, POST}
import com.modelfabric.sparql.api.SparqlQuery
import org.scalatest.WordSpec


class SparqlQueryConstructionSpec extends WordSpec {

  "The SparqlQuery apply() method" must {
    implicit val pm = com.modelfabric.sparql.api.PrefixMapping.none

    "correctly set the default HTTP Method for small queries" in {
      val q = SparqlQuery("small query")
      assert(GET === q.httpMethod)
    }

    "correctly set the default HTTP Method for huge queries" in {
      val q = SparqlQuery("small query" * 100) // this will make it cross the threshold
      assert(POST === q.httpMethod)
    }

    "correctly apply the selected HTTP Method for small queries" in {
      val q = SparqlQuery("small query", POST)
      assert(POST === q.httpMethod)
    }

    "override the selected HTTP Method for large queries" in {
      val q = SparqlQuery("small query" * 100, GET) // asking explicitly for GET but this will make it cross the threshold
      assert(POST === q.httpMethod)
    }

  }

}
