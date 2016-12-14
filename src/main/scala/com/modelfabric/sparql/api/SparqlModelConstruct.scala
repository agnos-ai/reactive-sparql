package com.modelfabric.sparql.api

import java.net.URI

//TODO: refactor the SparqlPagingParams classes to work with these
abstract class PagingParams() {
  val offset: Option[Long] = None
  val limit: Option[Long] = None
}
case object NoPaging extends PagingParams
case class ConstructPaging(override val offset: Option[Long], override val limit: Option[Long]) extends PagingParams

object SparqlModelConstruct {
  def apply(resourceIRIs: Seq[URI] = Nil,
            propertyIRIs: Seq[URI] = Nil,
            graphIRIs: Seq[URI] = Nil,
            reasoningEnabled: Boolean = false)(
    implicit _pm: PrefixMapping = PrefixMapping.none, _paging: PagingParams = NoPaging
  ): SparqlModelConstruct = {
    new SparqlModelConstruct()(_pm) {

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
