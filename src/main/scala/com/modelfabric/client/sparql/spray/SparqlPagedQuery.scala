package com.modelfabric.client.sparql.spray

import com.modelfabric.extension.StringExtensions._

/**
 * @param limit (optional) The maximum amount of results to return.
 *    Defaults to all entries if omitted.
 * @param offset (optional) Skip the first {offset} amount of results when returning the results.
 *    Defaults to zero if omitted.
 * @param orderBy (optional) The property of the story to order the stories by.
 *    Can be one of "title", "storyType", "author", "status". Defaults to natural ordering
 *    if omitted.
 * @param orderDesc (optional) By default all results are returned in ascending order when
 *    orderBy is given a value. Passing in true for this argument will cause the results to be
 *    returned in descending order.
 */
case class SparqlPagingParams private (
  limit : Option[Int] = None,
  offset : Option[Int] = None,
  orderBy : Option[String] = None,
  orderDesc : Boolean = false)

object SparqlPagingParams {

  val Defaults = new SparqlPagingParams()

  def apply(query_ : spray.http.Uri.Query) : SparqlPagingParams = {

    val limit : Option[Int] = query_.get("limit") match {
      case Some(l) => l.asInstanceOf[String].toPositiveIntegerSafe
      case None    => Defaults.limit
    }
    val page : Option[Int] = query_.get("page") flatMap { _.toPositiveIntegerSafe } flatMap { page =>
      if (page > 0) Some(page) else None
    }

    val offset : Option[Int] = limit flatMap { limit =>
      page map { page =>
        (page - 1) * limit
      }
    }

    val orderBy : Option[String] = query_.get("orderBy")
    val orderDesc : Boolean = query_.getOrElse("orderDesc", "false").toBooleanSafe

    SparqlPagingParams(limit, offset, orderBy, orderDesc)
  }
}

abstract class SparqlPagedQuery(
    val itemsToOrderBy : List[String] = List())(implicit pm : PrefixMapping, pagingParams : SparqlPagingParams) extends SparqlQuery()(pm) {

  val limit = pagingParams.limit
  val offset = pagingParams.offset
  val orderBy : Option[String] = pagingParams.orderBy orElse { itemsToOrderBy.headOption }
  val orderDesc = pagingParams.orderDesc

  def hasOrderBy(orderBy : Option[String]) : Boolean =
    orderBy map { orderBy =>
      itemsToOrderBy.isEmpty || itemsToOrderBy.contains(orderBy)
    } getOrElse { false }

  private def descClause = if (pagingParams.orderDesc) "DESC" else ""

  protected val orderByStr = if (hasOrderBy(orderBy)) {
    s"""ORDER BY $descClause (?${orderBy.get})"""
  }
  else ""

  protected val limitStr = limit map { limit => s"""LIMIT $limit""" } getOrElse ""

  protected val offsetStr = offset map { offset => s"""OFFSET $offset""" } getOrElse ""

  protected override def build(statement_ : String) : String = {
    val sb = new StringBuilder(super.build(statement_))
    sb ++= "\n"
    sb ++= orderByStr
    sb ++= "\n"
    sb ++= limitStr
    sb ++= "\n"
    sb ++= offsetStr
    sb.toString()
  }
}
