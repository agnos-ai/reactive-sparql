package com.modelfabric.sparql

import java.net.URI

import com.modelfabric.sparql.api.HttpMethod.POST
import com.modelfabric.sparql.api._
import com.modelfabric.sparql.mapper.SolutionMapper

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
  lazy val query2Post = SparqlQuery(select2, method = HttpMethod.POST)

  lazy val query2Result: List[ResultSet] = ResultSet(
    ResultSetVars(List("g", "b", "c")),
    ResultSetResults(List(QuerySolution(Map(
      "q"-> QuerySolutionValue("uri",None,"urn:test:mfab:data"),
      "b" -> QuerySolutionValue("uri",None,"http://xmlns.com/foaf/0.1/givenName"),
      "c" -> QuerySolutionValue("literal",None,"William")))))) :: Nil

  lazy val emptyResult: List[ResultSet] = ResultSet(
    ResultSetVars(List("g", "b", "c")),
    ResultSetResults(Nil)) :: Nil

  // Mapped queries
  /**
    * Person Object + Case Class defines the domain object and the mapping
    * from the Sparql ResultSet (QuerySolution) via providing the implementation for
    * the ResultMapper and SolutionMapper.
    */
  object Person extends ResultMapper[Person] with SolutionMapper[Person] {
    override def map(qs: QuerySolution): Person = {
      val uri = qs.uri("g")
      val name = qs.string("c")
      Person(uri.get, name.get)
    }
  }
  case class Person(id: URI, name: String) extends SparqlResult

  lazy val mappingQuery2Get = SparqlQuery( select2, mapping = Person)
  lazy val mappingQuery2Post = SparqlQuery( select2, method = POST, mapping = Person)

  lazy val mappedQuery2Result = Person(uri("urn:test:mfab:data"), "William")

  /**
    * TODO: Move this to the string extensions in modelfabric/scala-utils project.
    *
    * @param value
    * @return
    */
  private def uri(value: String) = URI.create(value)
}
