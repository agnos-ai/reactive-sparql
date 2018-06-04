package org.modelfabric.sparql.stream

import org.modelfabric.sparql.api.SparqlQuery
import org.scalatest.WordSpec
import akka.http.scaladsl.model.HttpMethods._

class SparqlQueryConstructionSpec extends WordSpec {

  "The SparqlQuery apply() method" must {
    implicit val pm = org.modelfabric.sparql.api.PrefixMapping.none

    "correctly set the default HTTP Method for small queries" in {
      val q = SparqlQuery("small query")
      assert(GET === q.queryHttpMethod)
    }

    "correctly set the default HTTP Method for huge queries" in {
      val q = SparqlQuery("small query" * 300) // this will make it cross the threshold
      assert(POST === q.queryHttpMethod)
    }

    "correctly apply the selected HTTP Method for small queries" in {
      val q = SparqlQuery("small query", POST)
      assert(POST === q.queryHttpMethod)
    }

    "override the selected HTTP Method for large queries" in {
      val q = SparqlQuery("small query" * 300, GET) // asking explicitly for GET but this will make it cross the threshold
      assert(POST === q.queryHttpMethod)
    }

  }

}
