package com.modelfabric.sparql.api


import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import org.eclipse.rdf4j.model.IRI

object SparqlConstruct extends SparqlConstructFactory {
  def apply(resourceIRIs: Seq[IRI] = Nil,
            propertyIRIs: Seq[IRI] = Nil,
            graphIRIs: Seq[IRI] = Nil,
            reasoningEnabled: Boolean = false)(
    implicit _paging: PagingParams = NoPaging
  ): SparqlConstruct = {
    new SparqlConstruct()(PrefixMapping.standard) {

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

//      private val graphConstruct = {
//        s"""
//           |CONSTRUCT {
//           |  GRAPH ?graphIri {
//           |    ?resourceIri ?propertyIri ?value .
//           |  }
//           |}
//           |$whereClause
//           |${pagingParams(_paging)}
//        """.stripMargin.trim
//      }

      // this won't work when n-quads or n-triples are required to be returned via HTTP from the triple store.
//      private val graphConstructViaSelect = {
//        s"""
//           |SELECT ?resourceIri ?propertyIri ?value ?graphIri
//           |$whereClause
//           |${pagingParams(_paging)}
//        """.stripMargin.trim
//
//      }

      override val statement: String = build(graphConstructReified(whereClause, _paging))

      override val reasoning: Boolean = reasoningEnabled
    }
  }

}


/**
  * Creates a construct which will match given resource IRI(s) with property value.
  * This construct is to find 'inbound' properties of an resource, where the resource appears
  * on the object side of a triple
  */
object SparqlConstructResourceAsObject extends SparqlConstructFactory {

  def apply(resourceIRIs: Seq[IRI] = Nil,
            graphIRIs: Seq[IRI] = Nil,
            reasoningEnabled: Boolean = false)(
             implicit _paging: PagingParams = NoPaging
           ): SparqlConstruct = {
    new SparqlConstruct()(PrefixMapping.standard) {

      private val whereClause: String = {
        s"""
           |WHERE {
           |  ${values("graphIri", graphIRIs)}
           |  GRAPH ?graphIri {
           |    ${values("value", resourceIRIs)}
           |    ?resourceIri ?propertyIri ?value .
           |  }
           |}
         """.stripMargin
      }

      override val statement: String = build(graphConstructReified(whereClause, _paging))

      override val reasoning: Boolean = reasoningEnabled
    }
  }
}

trait SparqlConstructFactory {

  protected def graphConstructReified (
    whereClause: String,
    _paging: PagingParams
  ): String = {

    val pagingParams: String = {
      s"""
         |${_paging.offset.map(o => s"OFFSET $o").getOrElse("")}
         |${_paging.limit.map(o => s"LIMIT $o").getOrElse("")}
         """.stripMargin
    }

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

  protected def values(binding: String, iris: Seq[IRI]): String = {
    def mkIRIs(iris: Seq[IRI]): String = {
      iris.map(iri => s"<$iri>").mkString(" ")
    }
    iris match {
      case Nil => ""
      case _ => s"VALUES ?$binding { ${mkIRIs(iris)} }"
    }
  }

  def unapply(construct: SparqlConstruct): Option[(HttpMethod, String, Boolean)] = {
    Some((construct.httpMethod, construct.statement, construct.reasoning))
  }
}


abstract class SparqlConstruct()(
  implicit pm: PrefixMapping) extends SparqlStatement()(pm) {

  override val httpMethod = HttpMethods.POST

  def reasoning = false
}
