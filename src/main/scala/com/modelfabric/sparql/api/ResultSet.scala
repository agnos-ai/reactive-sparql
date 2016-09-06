/*
 * Supports http://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
 */
package com.modelfabric.sparql.api

import java.net.URI
import javax.xml.bind.DatatypeConverter

import com.modelfabric.sparql.mapper.SolutionMapper

trait ResultMapper[T] extends SolutionMapper[T] {
  def map(result: ResultSet): T = result.results.bindings.map { b =>
    map(b)
  }
}

object ResultSetMapper extends ResultMapper[ResultSet] {
  override def map(result: ResultSet): List[ResultSet] = result :: Nil
  override def map(querySolution: QuerySolution): ResultSet = null //this is ugly
}

case class ResultSet(head : ResultSetVars, results : ResultSetResults)

case class ResultSetVars(vars : List[String])

case class ResultSetResults(bindings : List[QuerySolution])

case class QuerySolutionValue(`type` : String, datatype : Option[String] = None, value : String = null) {

  def asString : String = DatatypeConverter.parseString(value)

  def asShort : Short = DatatypeConverter.parseShort(value)

  def asInteger : Int = DatatypeConverter.parseInt(value)

  def asBigInteger : BigInt = DatatypeConverter.parseInteger(value)

  def asBoolean : Boolean = DatatypeConverter.parseBoolean(value)

  def asUri : URI = URI.create(value)

  def asLocalName : String = {
    val lastIndex = math.max(value.lastIndexOf('/'), value.lastIndexOf('#'))
    value.substring(lastIndex + 1)
  }

  def prettyPrint = {

    val sb = new StringBuilder

    sb ++= "\n\ttype: "
    sb ++= `type`

    if (datatype.isDefined) {
      sb ++= "\n\tdatatype: "
      sb ++= datatype.get
    }
    sb ++= "\n\tvalue: "
    sb ++= value

    sb.toString()
  }
}

/**
  * The default query solution, used to unmarshal the 'application/sparql-results+json'
  * server response.
  *
  * @param values
  */
case class QuerySolution(values : Map[String, QuerySolutionValue]) {

  def uri(var_ : String) : Option[URI] = values get var_ map (_.asUri)

  def localName(var_ : String) : Option[String] = values get var_ map (_.asLocalName)

  def string(var_ : String) : Option[String] = values get var_ map (_.asString)

  def short(var_ : String) : Option[Short] = values get var_ map (_.asShort)

  def integer(var_ : String) : Option[Int] = values get var_ map (_.asInteger)

  def bool(var_ : String) : Option[Boolean] = values get var_ map (_.asBoolean)

  def hasValue(var_ : String) = values.get(var_).isDefined

  def asValueMap = values

  def prettyPrint = {

    val sb = new StringBuilder

    for ((key, value) <- values) {
      sb ++= "\n?"
      sb ++= key
      sb ++= ":"
      sb ++= value.prettyPrint
    }

    sb.toString()
  }
}


