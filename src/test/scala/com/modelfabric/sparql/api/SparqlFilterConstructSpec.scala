package com.modelfabric.sparql.api

import org.scalatest.WordSpec

import com.modelfabric.sparql.stream.client.SparqlClientConstants.{valueFactory => vf}

class SparqlFilterConstructSpec extends WordSpec {

  "The SparqlFilterConstruct" should {

    "Build correct construct for filter of string value" in {

      val expectedResult = s"""PREFIX dc: <http://purl.org/dc/elements/1.1/>
                             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                             |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                             |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                             |
                             |    CONSTRUCT {
                             |  _:g rdf:subject ?resourceIri ;
                             |      rdf:predicate ?propertyIri ;
                             |      rdf:object ?value ;
                             |      rdf:graph ?graphIri .
                             |}
                             |
                             |WHERE {
                             |\u0020\u0020
                             |  GRAPH ?graphIri {
                             |    ?resourceIri rdfs:label 'SomeName'^^xsd:string .
                             |    VALUES ?propertyIri { rdfs:label }
                             |    ?resourceIri ?propertyIri ?value .
                             |  }
                             |}""".stripMargin.trim

      val c = SparqlFilterConstruct(
        filters = Map(vf.createIRI(s"rdfs:label") -> vf.createLiteral("SomeName") ),
        propertyIRIs = Seq(vf.createIRI("rdfs:label"))
      )

      assert(c.statement === expectedResult)
    }

    "Build correct construct for filter of IRI value" in {
      val expectedResult = s"""PREFIX dc: <http://purl.org/dc/elements/1.1/>
                              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                              |
                              |    CONSTRUCT {
                              |  _:g rdf:subject ?resourceIri ;
                              |      rdf:predicate ?propertyIri ;
                              |      rdf:object ?value ;
                              |      rdf:graph ?graphIri .
                              |}
                              |
                              |WHERE {
                              |\u0020\u0020
                              |  GRAPH ?graphIri {
                              |    ?resourceIri rdf:type <https://test.domain/owl/test-topology#Something> .
                              |    VALUES ?propertyIri { rdfs:label }
                              |    ?resourceIri ?propertyIri ?value .
                              |  }
                              |}""".stripMargin.trim

      val c = SparqlFilterConstruct(
        filters = Map(vf.createIRI(s"rdf:type") -> vf.createIRI("https://test.domain/owl/test-topology#Something") ),
        propertyIRIs = Seq(vf.createIRI("rdfs:label"))
      )

      assert(c.statement === expectedResult)
    }

    "Build correct construct for multiple filters" in {
      val expectedResult = s"""PREFIX dc: <http://purl.org/dc/elements/1.1/>
                              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                              |
                              |    CONSTRUCT {
                              |  _:g rdf:subject ?resourceIri ;
                              |      rdf:predicate ?propertyIri ;
                              |      rdf:object ?value ;
                              |      rdf:graph ?graphIri .
                              |}
                              |
                              |WHERE {
                              |\u0020\u0020
                              |  GRAPH ?graphIri {
                              |    ?resourceIri rdf:type <https://test.domain/owl/test-topology#Something> .
                              |    ?resourceIri rdfs:label 'SomeName'^^xsd:string .
                              |    VALUES ?propertyIri { rdfs:label }
                              |    ?resourceIri ?propertyIri ?value .
                              |  }
                              |}""".stripMargin.trim

      val c = SparqlFilterConstruct(
        filters = Map(
          vf.createIRI(s"rdf:type") -> vf.createIRI("https://test.domain/owl/test-topology#Something"),
          vf.createIRI(s"rdfs:label") -> vf.createLiteral("SomeName")
        ),
        propertyIRIs = Seq(vf.createIRI("rdfs:label"))
      )

      assert(c.statement === expectedResult)
    }
  }

}
