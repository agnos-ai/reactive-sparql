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
            reasoningEnabled: Boolean = false)(implicit _pm: PrefixMapping = PrefixMapping.none, _paging: PagingParams = NoPaging): SparqlModelConstruct = {
    new SparqlModelConstruct() {
      private def mkIRIs(iris: Seq[URI]): String = {
        iris.map(iri => s"<$iri>").mkString(", ")
      }
      override val statement: String =
        s"""
           |CONSTRUCT {
           |  _:g rdf:subject ?resourceIri ;
           |      rdf:predicate ?propertyIri ;
           |      rdf:object ?value ;
           |      rdf:graph ?graphIri .
           |}
           |WHERE {
           |  VALUES ?graphIri { ${mkIRIs(graphIRIs)} }
           |  GRAPH ?graphIri {
           |    VALUES ?resourceIri { ${mkIRIs(resourceIRIs)} }
           |    VALUES ?propertyIri { ${mkIRIs(propertyIRIs)} }
           |    ?resourceIri ?propertyIri ?value .
           |  }
           |}
           |${_paging.offset.map(o => s"OFFSET $o")}
           |${_paging. limit.map(o =>  s"LIMIT $o")}
       """.stripMargin.trim

      override val reasoning: Boolean = reasoningEnabled
    }
  }

  def unapply(construct: SparqlModelConstruct): Option[(HttpMethod, String)] = {
    Some((construct.httpMethod, construct.statement))
  }

}

abstract class SparqlModelConstruct()(
  implicit pm: PrefixMapping) extends SparqlStatement()(pm) {

  override val httpMethod = HttpMethod.POST

  def reasoning = false
}
