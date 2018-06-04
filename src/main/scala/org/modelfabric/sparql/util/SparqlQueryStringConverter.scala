package org.modelfabric.sparql.util

import org.modelfabric.sparql._
import org.modelfabric.sparql.api.SparqlQuery
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Value}

object SparqlQueryStringConverter {

  implicit def toQueryString(sparql: SparqlQuery): String = {

    def mkQuery(statement: String): Iterable[String] = {
      List(s"query=${urlEncode(statement)}")
    }
    def mkBindings(bindings: Map[String, Value]): Iterable[String] = {
      bindings map {
        case (k,v) if urlEncode(k) == k => s"$$$k=${valueToString(v)}"
        case (k,_) => throw new IllegalArgumentException(s"invalid binding name [$k]")
      }
    }
    def valueToString(value: Value): String = {
      value match {
        case l: Literal =>
          val v = urlEncode(l.stringValue())
          val t = l.getDatatype()
          if (t != XMLSchema.STRING) {
            s""""$v"^^$t"""
          } else {
            v
          }
        case i: IRI => urlEncode(s"<${i.toString}>")
        case _: BNode => throw new IllegalArgumentException("BNode bindings are not allowed")
      }
    }
    def mkColBindings[T](bindVar: String, value: Iterable[T]): Iterable[String] = {
      value map {
        case v: Value =>
          s"$bindVar=${valueToString(v)}"
        case o =>
          s"$bindVar=${o}"

      }
    }

    List(
      mkQuery(sparql.statement),
      mkBindings(sparql.bindings),
      mkColBindings("default-graph-uri", sparql.defaultGraphs),
      mkColBindings("named-graph-uri", sparql.namedGraphs),
      mkColBindings("limit", sparql.limit),
      mkColBindings("offset", sparql.offset),
      mkColBindings("reasoning", sparql.reasoning),
      mkColBindings("timeout", sparql.timeout)
    ).flatten mkString "&"
  }

}
