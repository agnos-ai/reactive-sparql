package com.modelfabric.sparql.api

import java.net.URI

object SparqlModelConstruct {
  def apply(resourceIRIs: Seq[URI] = Nil,
            propertyIRIs: Seq[URI] = Nil,
            graphIRIs: Seq[URI] = Nil,
            reasoningEnabled: Boolean = false)(
    implicit _paging: PagingParams = NoPaging
  ): SparqlModelConstruct = {
    new SparqlModelConstruct()(PrefixMapping.standard) {

      private def values(binding: String, iris: Seq[URI]): String = {
        def mkIRIs(iris: Seq[URI]): String = {
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

      private val whereClause = {
        s"""
           |WHERE {
           |  ${values("graphIri", graphIRIs)}
           |  GRAPH ?graphIri {
           |    ${values("resourceIri", resourceIRIs)}
           |    ${values("propertyIri", propertyIRIs)}
           |    ?resourceIri ?propertyIri ?value .
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

  def unapply(construct: SparqlModelConstruct): Option[(HttpMethod, String, Boolean)] = {
    Some((construct.httpMethod, construct.statement, construct.reasoning))
  }

}

abstract class SparqlModelConstruct()(
  implicit pm: PrefixMapping) extends SparqlStatement()(pm) {

  override val httpMethod = HttpMethod.POST

  def reasoning = false
}