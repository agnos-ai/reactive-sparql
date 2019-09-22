package ai.agnos.sparql.api

import com.sun.org.apache.xerces.internal.util.XMLChar

import scala.util.control.Breaks._

/**
  * Defines the most generic namespaces as constants
  */
object NamespaceConstants {

  val PREFIX_RDFS = "rdfs"
  val PREFIX_RDF = "rdf"
  val PREFIX_DC = "dc"
  val PREFIX_OWL = "owl"
  val PREFIX_XSD = "xsd"
  val PREFIX_SKOS = "skos"
  val PREFIX_TIME = "time"
  val PREFIX_FOAF = "foaf"
  val PREFIX_TERMS = "terms"
  val PREFIX_AGNOS_DATA = "agnos"

  val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val RDFS = "http://www.w3.org/2000/01/rdf-schema#"
  val RDFSyntax = "http://www.w3.org/TR/rdf-syntax-grammar#"
  val OWL = "http://www.w3.org/2002/07/owl#"
  val DC_11 = "http://purl.org/dc/elements/1.1/"
  val TERMS = "http://purl.org/dc/terms/"
  val XSD = "http://www.w3.org/2001/XMLSchema#"
  val SKOS = "http://www.w3.org/2004/02/skos/core#"
  val TIME = "http://www.w3.org/2006/time#"
  val FOAF = "http://xmlns.com/foaf/0.1/"
  val AGNOS_DATA = "https://data.agnos.ai/resource/"
}

object PrefixMapping {

  import NamespaceConstants._

  class IllegalPrefixException(prefix : String) extends IllegalArgumentException

  def none = new PrefixMapping

  /**
   * A PrefixMapping that contains the "standard" prefixes we know about,
   * viz rdf, rdfs, dc, rss, vcard, and owl.
   */
  def standard = {
    val pm = new PrefixMapping
    pm.setNsPrefix(PREFIX_RDFS, RDFS)
    pm.setNsPrefix(PREFIX_RDF, RDF)
    pm.setNsPrefix(PREFIX_DC, DC_11)
    pm.setNsPrefix(PREFIX_OWL, OWL)
    pm.setNsPrefix(PREFIX_XSD, XSD)
    pm
  }

  def extended = {
    val pm = standard
    pm.setNsPrefix(PREFIX_SKOS, SKOS)
    pm.setNsPrefix(PREFIX_FOAF, FOAF)
    pm.setNsPrefix(PREFIX_TIME, TIME)
    pm.setNsPrefix(PREFIX_TERMS, TERMS)
    pm.setNsPrefix(PREFIX_AGNOS_DATA, AGNOS_DATA)
    pm
  }

  /**
   * Only use this when printing a ResultSet, not for all SPARQL queries as
   * that would take way too much space/bandwidth.
   */
  def all = {
    val pm = extended
    pm.setNsPrefix("uuid",          "urn:uuid:")
    pm.setNsPrefix("agnos",         "https://data.agnos.ai/resource/")
    pm
  }

  /**
   * Given an absolute URI, determine the split point between the namespace part and the localname part.
   * If there is no valid localname part then the length of the string is returned.
   *
   * The algorithm tries to find the longest NCName at the end of the uri, not immediately preceded
   * by the first colon in the string.
   *
   * @param uri
   * @return the index of the first character of the localname
   */
  def splitNamespace(uri : String) : Int = {

    // XML Namespaces 1.0:
    // A qname name is NCName ':' NCName
    // NCName             ::=      NCNameStartChar NCNameChar*
    // NCNameChar         ::=      NameChar - ':'
    // NCNameStartChar    ::=      Letter | '_'
    //
    // XML 1.0
    // NameStartChar      ::= ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] |
    //                        [#xD8-#xF6] | [#xF8-#x2FF] |
    //                        [#x370-#x37D] | [#x37F-#x1FFF] |
    //                        [#x200C-#x200D] | [#x2070-#x218F] |
    //                        [#x2C00-#x2FEF] | [#x3001-#xD7FF] |
    //                        [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]
    // NameChar           ::= NameStartChar | "-" | "." | [0-9] | #xB7 |
    //                        [#x0300-#x036F] | [#x203F-#x2040]
    // Name               ::= NameStartChar (NameChar)*

    val lg = uri.length

    if (lg == 0) return 0

    var i = lg - 1
    var ch = ' '

    while (i >= 1) {
      i -= 1
      ch = uri.charAt(i)
      if (notNameChar(ch)) break
    }

    var j = i + 1

    if (j >= lg)
      return lg

    //
    // Check we haven't split up a %-encoding.
    //
    if (j >= 2 && uri.charAt(j - 2) == '%') j += 1

    if (j >= 1 && uri.charAt(j - 1) == '%') j += 2

    //
    // Have found the leftmost NCNameChar from the
    // end of the URI string.
    // Now scan forward for an NCNameStartChar
    // The split must start with NCNameStart.
    //
    while (j < lg) {

      j += 1
      ch = uri.charAt(j)

      //
      // if (XMLChar.isNCNameStart(ch))
      //   break ;
      //
      if (XMLChar.isNCNameStart(ch)) {
        //
        // "mailto:" is special.
        // Keep part after mailto: at least one charcater.
        // Do a quick test before calling .startsWith
        // OLD: if ( uri.charAt(j - 1) == ':' && uri.lastIndexOf(':', j - 2) == -1)
        //
        if (!(j == 7 && uri.startsWith("mailto:"))) break
      }
    }
    j
  }

  /**
   * answer true iff this is not a legal NCName character, ie, is a possible split-point start.
   */
  def notNameChar(ch : Char) : Boolean = !XMLChar.isNCName(ch)
}

/**
 * Inspired by Jena's PrefixMappingImpl class, this class does more or les the same.
 *
 * See http://svn.apache.org/repos/asf/jena/trunk/jena-core/src/main/java/com/hp/hpl/jena/shared/impl/PrefixMappingImpl.java
 */
class PrefixMapping {

  import PrefixMapping._

  protected var prefixToURI : Map[String, String] = Map.empty
  protected var URItoPrefix : Map[String, String] = Map.empty

  protected def set(prefix : String, uri : String) {
    prefixToURI += prefix -> uri
    URItoPrefix += uri -> prefix
  }

  def get(prefix : String) : String = prefixToURI.getOrElse(prefix, null)

  def setNsPrefix(prefix : String, uri : String) : PrefixMapping = {
    checkLegal(prefix)
    require(uri != null, "null URIs are prohibited as arguments to setNsPrefix")
    set(prefix, uri)
    this
  }

  def removeNsPrefix(prefix : String) : PrefixMapping = {

    prefixToURI -= prefix

    regenerateReverseMapping()
    this
  }

  protected def regenerateReverseMapping() {
    URItoPrefix = prefixToURI.map(_.swap)
  }

  /**
   * Answer this PrefixMapping after updating it with the <code>(p, u)</code>
   * mappings in <code>other</code> where neither <code>p</code> nor
   * <code>u</code> appear in this mapping.
   */
  def withDefaultMappings(other : PrefixMapping) : PrefixMapping = {

    for ((prefix, uri) ← other.prefixToURI) {
      if (getNsPrefixURI(prefix) == null && getNsURIPrefix(uri) == null) {
        setNsPrefix(prefix, uri)
      }
    }

    this
  }

  /**
   * Add the bindings in the prefixToURI to our own. This will fail with a ClassCastException
   * if any key or value is not a String; we make no guarantees about order or
   * completeness if this happens. It will fail with an IllegalPrefixException if
   * any prefix is illegal; similar provisos apply.
   *
   * @param other the Map whose bindings we are to add to this.
   */
  def setNsPrefixes(other : Map[String, String]) : PrefixMapping = {

    for ((prefix, uri) ← other) {
      setNsPrefix(prefix, uri)
    }

    this
  }

  /**
   * Add the bindings of other to our own. We defer to the general case because we have to ensure the URIs are checked.
   *
   * @param other the PrefixMapping whose bindings we are to add to this.
   */
  def setNsPrefixes(other : PrefixMapping) : PrefixMapping = setNsPrefixes(other.prefixToURI)

  /**
   * Checks that a prefix is "legal" - it must be a valid XML NCName.
   */
  private def checkLegal(prefix : String) {
    if (prefix.length > 0 && !XMLChar.isValidNCName(prefix))
      throw new PrefixMapping.IllegalPrefixException(prefix)
  }

  def getNsPrefixURI(prefix : String) : String = get(prefix)

  def getNsPrefixMap : Map[String, String] = prefixToURI

  def getNsURIPrefix(uri : String) : String = URItoPrefix(uri)

  /**
   * Expand a prefixed URI. There's an assumption that any URI of the form
   * Head:Tail is subject to mapping if Head is in the prefix mapping. So, if
   * someone takes it into their heads to define eg "http" or "ftp" we have problems.
   */
  // JC: not used?
  def expandPrefix(prefixed : String) : String = {

    val colon = prefixed.indexOf(':')

    if (colon < 0) {
      prefixed
    }
    else {
      val uri = get(prefixed.substring(0, colon))

      if (uri == null) {
        prefixed
      }
      else {
        uri + prefixed.substring(colon + 1)
      }
    }
  }

  def isPrefixed(uri : String): Boolean = {

    val colon = uri.indexOf(':')

    if (colon < 0) {
      false
    }
    else {
      val prefix = get(uri.substring(0, colon))

      if (prefix == null) {
        false
      }
      else {
        true
      }
    }
  }

  /**
   * Answer a readable (we hope) representation of this prefix mapping.
   */
  override def toString : String = "pm:" + prefixToURI

  /**
   * Answer the qname for <code>uri</code> which uses a prefix from this mapping, or null if there isn't one.
   * <p>
   *   Relies on <code>splitNamespace</code> to carve uri into namespace and
   *   localname components; this ensures that the localname is legal and we just
   *   have to (reverse-)lookup the namespace in the prefix table.
   * </p>
   * @see com.hp.hpl.jena.shared.PrefixMapping#qnameFor(java.lang.String)
   */
  // JC: not used?
  def qnameFor(uri : String) : String = {

    val split = splitNamespace(uri)

    def ns = uri.substring(0, split)
    def local = uri.substring(split)

    if (local.equals("")) return null

    val prefix = URItoPrefix.get(ns)

    if (prefix == null) {
      null
    }
    else {
      prefix + ":" + local
    }
  }

  /**
   * Compress the URI using the prefix mapping. This version of the code looks through all the maplets and checks each
   * candidate prefix URI for being a leading substring of the argument URI. There's probably a much more efficient
   * algorithm available, pre-processing the prefix strings into some kind of search table, but for the moment we don't
   * need it.
   */
  def shortForm(uri : String) : String = {
    val (prefix, otherUri) = findMapping(uri, true)
    if (prefix == null) {
      uri
    }
    else {
      s"${prefix}:${uri.substring(otherUri.length)}"
    }
  }

  def samePrefixMappingAs(other : PrefixMapping) : Boolean = prefixToURI == other.prefixToURI

  /**
   * Answer a prefixToURI entry in which the value is an initial substring of <code>uri</code>.
   * If <code>partial</code> is false, then the value must equal <code>uri</code>.
   *
   * Does a linear search of the entire prefixToURI, so not terribly efficient for large maps.
   *
   * @param uri the value to search for
   * @param partial true if the match can be any leading substring, false for exact match
   * @return some entry (k, v) such that uri starts with v [equal for partial=false]
   */
  private def findMapping(uri : String, partial : Boolean) : (String, String) = {
    for ((prefix, otherUri) ← prefixToURI) {
      if (uri.startsWith(otherUri) && (partial || otherUri.length == uri.length)) {
        return (prefix, otherUri)
      }
    }
    (null, null)
  }

  private def pairs = prefixToURI.toList sortBy { _._1 }

  def sparql : String = {
    pairs map {
      case (key, value) ⇒ s"PREFIX ${key}: <${value}>"
    } mkString ("\n", "\n", "\n")
  }
}
