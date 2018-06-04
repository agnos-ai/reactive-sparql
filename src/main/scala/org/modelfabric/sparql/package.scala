package org.modelfabric

package object sparql {

  def urlEncode(string: String) : String = java.net.URLEncoder.encode(string, "UTF-8")

  def urlDecode(string: String) : String = java.net.URLDecoder.decode(string, "UTF-8")

  val strippedRegex = """((^)|(\n))\s*\|"""r


}
