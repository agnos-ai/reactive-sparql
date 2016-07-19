package com.modelfabric.sparql

import com.modelfabric.sparql.api._

trait SparqlQueries {
  implicit val pm = PrefixMapping.extended

  lazy val delete = SparqlUpdate { """
    |WITH <urn:test:mfab:data>
    |DELETE { ?a ?b ?c . }
    |WHERE { ?a ?b ?c . }
    |"""
  }

  lazy val query1 = SparqlQuery { """
    |SELECT ?a ?b ?c
    |FROM
    |  <urn:test:mfab:data>
    |WHERE {
    | ?a ?b ?c .
    |}
    |LIMIT 1
    |"""
  }

  lazy val insert1x = SparqlUpdate { """
    |WITH <urn:test:mfab:data>
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


  lazy val insert1 = SparqlUpdate { """
    |INSERT DATA {
    |  GRAPH <urn:test:mfab:data> {
    |    <urn:test:whatever> foaf:givenName "Bill"
    |  }
    |}"""
  }

  lazy val update = SparqlUpdate { """
    |WITH <urn:test:mfab:data>
    |DELETE {
    |  ?person foaf:givenName "Bill"
    |}
    |INSERT {
    |  ?person foaf:givenName "William"
    |}
    |WHERE {
    |  ?person foaf:givenName "Bill"
    |}"""
  }

  lazy val select2 = { """
    |SELECT ?g ?b ?c
    |FROM NAMED <urn:test:mfab:data>
    |WHERE {
    |  GRAPH ?g {
    |    <urn:test:whatever> ?b ?c
    |  }
    |}"""
  }

  lazy val query2Get = SparqlQuery(select2)
  lazy val query2Post = SparqlQuery(HttpMethod.POST, select2)

  lazy val query2Result = ResultSet(
    ResultSetVars(List("g", "b", "c")),
    ResultSetResults(List(QuerySolution(Map(
      "q"-> QuerySolutionValue("uri",None,"urn:test:mfab:data"),
      "b" -> QuerySolutionValue("uri",None,"http://xmlns.com/foaf/0.1/givenName"),
      "c" -> QuerySolutionValue("literal",None,"William"))))))

  lazy val emptyResult = ResultSet(
    ResultSetVars(List("g", "b", "c")),
    ResultSetResults(Nil))

}
