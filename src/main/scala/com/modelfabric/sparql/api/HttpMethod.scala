package com.modelfabric.sparql.api

/**
  * A framework agnostic way to pass around the desired HTTP Method
  */
sealed trait HttpMethod
case object HttpMethod {
  case object GET extends HttpMethod
  case object POST extends HttpMethod
  case object PUT extends HttpMethod
  case object DELETE extends HttpMethod
}
