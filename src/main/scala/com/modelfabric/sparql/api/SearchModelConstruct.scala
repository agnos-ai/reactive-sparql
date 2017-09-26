package com.modelfabric.sparql.api

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import org.eclipse.rdf4j.model.IRI

object SearchModelConstruct {
  def apply(searchString: String = "*", //default to search anything
            graphIRIs: Seq[IRI] = Nil,
            reasoningEnabled: Boolean = false)(
             implicit _paging: PagingParams = NoPaging
           ): SearchModelConstruct = {

    new SearchModelConstruct()(PrefixMapping.standard) {

      private def values(binding: String, iris: Seq[IRI]): String = {
        def mkIRIs(iris: Seq[IRI]): String = {
          iris.map(iri => s"<$iri>").mkString(" ")
        }
        iris match {
          case Nil => ""
          case _ => s"VALUES ?$binding { ${mkIRIs(iris)} }"
        }
      }

      private val pagingParams: String = {
        s"""
           |${_paging.offset.map(o => s"OFFSET $o").getOrElse("")}
           |${_paging.limit.map(o => s"LIMIT $o").getOrElse("")}
         """.stripMargin
      }

      private val searchParams: String = {
        _paging.limit.map(o => s"'${searchString}' $o").getOrElse(s"'${searchString}' 10")

      }

      private val whereClause = {
        s"""
           |WHERE {
           |  ${values("graphIri", graphIRIs)}
           |  GRAPH ?graphIri {
           |    ?resourceIri ?propertyIri ?value .
           |    (?value ?score) <tag:stardog:api:property:textMatch> (${searchParams}) .
           |  }
           |}
         """.stripMargin
      }

      private val graphConstruct = {
        s"""
           |CONSTRUCT {
           |  GRAPH ?graphIri {
           |    ?resourceIri ?propertyIri ?value .
           |  }
           |}
           |$whereClause
           |$pagingParams
        """.stripMargin.trim
      }

      private val graphConstructReified = {
        s"""
           |CONSTRUCT {
           |  _:g rdf:subject ?resourceIri ;
           |      rdf:predicate ?propertyIri ;
           |      rdf:object ?value ;
           |      rdf:graph ?graphIri .
           |}
           |$whereClause
           |$pagingParams
        """.stripMargin.trim
      }

      // this won't work when n-quads or n-triples are required to be returned via HTTP from the triple store.
      private val graphConstructViaSelect = {
        s"""
           |SELECT ?resourceIri ?propertyIri ?value ?graphIri
           |$whereClause
           |$pagingParams
        """.stripMargin.trim

      }
      override val statement: String = build(graphConstructReified)

      override val reasoning: Boolean = reasoningEnabled

    }
  }
  def unapply(construct: SearchModelConstruct): Option[(HttpMethod, String, Boolean)] = {
    Some((construct.httpMethod, construct.statement, construct.reasoning))
  }
}

abstract class SearchModelConstruct()(
  implicit pm: PrefixMapping) extends SparqlStatement()(pm) {

  override val httpMethod = HttpMethods.POST

  def reasoning = false

}

