package ai.agnos.sparql.api


//TODO: refactor the SparqlPagingParams classes to work with these
object PagingParams {
  val Defaults = NoPaging
}

abstract class PagingParams() {
  val offset: Option[Long] = None
  val  limit: Option[Long] = None
}

case object NoPaging extends PagingParams
case class  QueryPaging(override val offset: Option[Long], override val limit: Option[Long]) extends PagingParams

