package com.modelfabric.sparql

import com.modelfabric.sparql.api._
import org.eclipse.rdf4j.model.IRI

import akka.http.scaladsl.model.HttpMethods._

import com.modelfabric.sparql.stream.client.SparqlClientConstants.{valueFactory => svf}

trait SparqlQueries {
  implicit val pm = PrefixMapping.extended

  implicit def stringToIri(iri: String): IRI = {
    svf.createIRI(iri.toString)
  }

  lazy val dropGraph = SparqlUpdate { s"""
    |DROP SILENT GRAPH <$graphIri>
    |"""
  }

  lazy val query1 = SparqlQuery { s"""
    |SELECT ?g ?a ?b ?c
    |WHERE {
    | VALUES ?g { <$graphIri> }
    | GRAPH ?g {
    |  ?a ?b ?c .
    | }
    |}
    |LIMIT 1
    |"""
  }

  lazy val insert1x = SparqlUpdate { s"""
    |WITH <$graphIri>
    |DELETE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}
    |INSERT {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}
    |WHERE {
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:inScheme mfab:ConceptScheme-StoryTypeTaxonomy.
    |    <urn:uuid:te-36adbd34-2d84-4cd2-b061-6c8550c7d648> skos:broader mfab:StoryType-Story
    |}"""
  }

  lazy val graphIri   : IRI = "urn:test:mfab:data"
  lazy val whateverIri: IRI = "urn:test:whatever"
  lazy val propertyIri: IRI = "http://xmlns.com/foaf/0.1/givenName"


  lazy val insert1 = SparqlUpdate { s"""
    |INSERT DATA {
    |  GRAPH <$graphIri> {
    |    <$whateverIri> <$propertyIri> "Bill"
    |  }
    |}"""
  }

  lazy val mappedQuery1Result = Person(whateverIri, "Bill") :: Nil

  lazy val update = SparqlUpdate { s"""
    |WITH <$graphIri>
    |DELETE {
    |  ?person <$propertyIri> "Bill"
    |}
    |INSERT {
    |  ?person <$propertyIri> "William"
    |}
    |WHERE {
    |  ?person <$propertyIri> "Bill"
    |}"""
  }

  lazy val select1 = {s"""
    |SELECT ?a ?b ?c
    |WHERE {
    |   VALUES ?a { <$whateverIri> }
    |  ?a ?b ?c
    |}"""
  }

  lazy val select2 = { s"""
    |SELECT ?g ?a ?b ?c
    |FROM NAMED <$graphIri>
    |WHERE {
    |  GRAPH ?g {
    |    VALUES ?a { <$whateverIri> }
    |    ?a ?b ?c
    |  }
    |}"""
  }

  lazy val query1Get = SparqlQuery(select1)

  lazy val query2Get = SparqlQuery(select2)
  lazy val query2Post = SparqlQuery(select2, httpMethod = POST)

  lazy val query1Result: List[ResultSet] = ResultSet(
    ResultSetVars(List("a", "b", "c")),
    ResultSetResults(List(QuerySolution(Map(
      "a"-> QuerySolutionValue("uri",None,s"$whateverIri"),
      "b" -> QuerySolutionValue("uri",None,s"$propertyIri"),
      "c" -> QuerySolutionValue("literal",None,"William")))))) :: Nil

  lazy val query2Result: List[ResultSet] = ResultSet(
    ResultSetVars(List("g", "a", "b", "c")),
    ResultSetResults(List(QuerySolution(Map(
      "g"-> QuerySolutionValue("uri",None,s"$graphIri"),
      "a"-> QuerySolutionValue("uri",None,s"$whateverIri"),
      "b" -> QuerySolutionValue("uri",None,s"$propertyIri"),
      "c" -> QuerySolutionValue("literal",None,"William")))))) :: Nil

  lazy val emptyResult: List[ResultSet] = ResultSet(
    ResultSetVars(List("g", "a", "b", "c")),
    ResultSetResults(Nil)) :: Nil

  // Mapped queries
  /**
    * Person Object + Case Class defines the domain object and the mapping
    * from the Sparql ResultSet (QuerySolution) via providing the implementation for
    * the ResultMapper and SolutionMapper.
    */
  object Person extends ResultMapper[Person] {
    override def map(qs: QuerySolution): Person = {
      Person(qs.iri("a").get, qs.string("c").get)
    }
  }
  case class Person(id: IRI, name: String) extends SparqlResult

  lazy val mappingQuery2Get   = SparqlQuery( select2, queryType = ClientMappedQuery(Person))
  lazy val mappingQuery2Post  = SparqlQuery( select2, httpMethod = POST, queryType = ClientMappedQuery(Person))

  lazy val mappedQuery2Result = Person(whateverIri, "William") :: Nil


  lazy val modelGraphIri          : IRI = "urn:test:mfab:model"
  lazy val modelAlternateGraphIri : IRI = "urn:test:mfab:modelalt"
  lazy val deleteDefaultGraphTriples = {
    SparqlUpdate(s"""
       |DELETE { ?s ?p ?o }
       |WHERE  { ?s ?p ?o }
     """)
  }

  lazy val deleteModelGraph = {
    SparqlUpdate(s"""
       |DROP SILENT GRAPH <$modelGraphIri>
     """)
  }

  lazy val deleteAlternateModelGraph = {
    SparqlUpdate(s"""
       |DROP SILENT GRAPH <$modelAlternateGraphIri>
     """)
  }

  def modelResourceIri(suffix: String): IRI = s"urn:test:mfab:res:$suffix"


  lazy val queryModelGraph = {
    SparqlQuery(s"""
       |SELECT ?g ?a ?b ?c
       |FROM NAMED <$modelGraphIri>
       |WHERE {
       |  GRAPH ?g {
       |    ?a ?b ?c
       |  }
       |}
     """)
    }

  lazy val insertModelGraphData = {
    SparqlUpdate(s"""
       |INSERT DATA {
       |  GRAPH <$modelGraphIri> {
       |    <urn:test:mfab:res:0> rdfs:label "Label 0" .
       |    <urn:test:mfab:res:1> rdfs:label "Label 1" .
       |    <urn:test:mfab:res:2> rdfs:label "Label 2" .
       |    <urn:test:mfab:res:3> rdfs:label "Label 3" .
       |    <urn:test:mfab:res:4> rdfs:label "Label 4" .
       |    <urn:test:mfab:res:5> rdfs:label "Label 5" .
       |    <urn:test:mfab:res:6> rdfs:label "Label 6" .
       |    <urn:test:mfab:res:7> rdfs:label "Label 7" .
       |    <urn:test:mfab:res:8> rdfs:label "Label 8" .
       |    <urn:test:mfab:res:9> rdfs:label "Label 9" .
       |    <urn:test:mfab:res:10> rdfs:label "Label 10" .
       |    <urn:test:mfab:res:11> rdfs:label "Label 11" .
       |    <urn:test:mfab:res:12> rdfs:label "Label 12" .
       |    <urn:test:mfab:res:13> rdfs:label "Label 13" .
       |    <urn:test:mfab:res:14> rdfs:label "Label 14" .
       |    <urn:test:mfab:res:0> rdfs:comment "Comment 0" .
       |    <urn:test:mfab:res:1> rdfs:comment "Comment 1" .
       |    <urn:test:mfab:res:2> rdfs:comment "Comment 2" .
       |    <urn:test:mfab:res:3> rdfs:comment "Comment 3" .
       |    <urn:test:mfab:res:4> rdfs:comment "Comment 4" .
       |    <urn:test:mfab:res:5> rdfs:comment "Comment 5" .
       |    <urn:test:mfab:res:6> rdfs:comment "Comment 6" .
       |    <urn:test:mfab:res:7> rdfs:comment "Comment 7" .
       |    <urn:test:mfab:res:8> rdfs:comment "Comment 8" .
       |    <urn:test:mfab:res:9> rdfs:comment "Comment 9" .
       |    <urn:test:mfab:res:10> rdfs:comment "Comment 10" .
       |    <urn:test:mfab:res:11> rdfs:comment "Comment 11" .
       |    <urn:test:mfab:res:12> rdfs:comment "Comment 12" .
       |    <urn:test:mfab:res:13> rdfs:comment "Comment 13" .
       |    <urn:test:mfab:res:14> rdfs:comment "Comment 14" .
       |  }
       |  GRAPH <$modelAlternateGraphIri> {
       |    <urn:test:mfab:res:0> rdfs:label "Alt Label 0" .
       |    <urn:test:mfab:res:1> rdfs:label "Alt Label 1" .
       |    <urn:test:mfab:res:2> rdfs:label "Alt Label 2" .
       |    <urn:test:mfab:res:3> rdfs:label "Alt Label 3" .
       |    <urn:test:mfab:res:4> rdfs:label "Alt Label 4" .
       |    <urn:test:mfab:res:5> rdfs:label "Alt Label 5" .
       |    <urn:test:mfab:res:6> rdfs:label "Alt Label 6" .
       |    <urn:test:mfab:res:7> rdfs:label "Alt Label 7" .
       |    <urn:test:mfab:res:8> rdfs:label "Alt Label 8" .
       |    <urn:test:mfab:res:9> rdfs:label "Alt Label 9" .
       |  }
       |}
     """)
  }


  lazy val insertModelGraphDataWithObjResource = {
    SparqlUpdate(s"""
                    |INSERT DATA {
                    |  GRAPH <$modelGraphIri> {
                    |    <urn:test:mfab:res:0> rdf:type <urn:test:mfab:type:0> .
                    |    <urn:test:mfab:res:1> rdfs:label "Label 1" .
                    |    <urn:test:mfab:res:2> rdfs:label "Label 2" .
                    |    <urn:test:mfab:res:3> rdfs:label "Label 3" .
                    |  }
                    |  GRAPH <$modelAlternateGraphIri> {
                    |    <urn:test:mfab:res:0> rdf:type <urn:test:mfab:type:2> .
                    |    <urn:test:mfab:res:1> rdfs:label "Alt Label 1" .
                    |    <urn:test:mfab:res:2> rdfs:label "Alt Label 2" .
                    |    <urn:test:mfab:res:3> rdfs:label "Alt Label 3" .
                    |  }
                    |}
     """)
  }
}
