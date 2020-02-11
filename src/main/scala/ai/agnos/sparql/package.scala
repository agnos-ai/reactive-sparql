package ai.agnos

import scala.util.Try
import java.io.File
import java.net.{URI, URL}

import org.apache.commons.lang3.SystemUtils

package object sparql {

  def urlEncode(string: String) : String = java.net.URLEncoder.encode(string, "UTF-8")

  def urlDecode(string: String) : String = java.net.URLDecoder.decode(string, "UTF-8")

  val strippedRegex = """((^)|(\n))\s*\|"""r

  /**
    * Converts the given URL string to its corresponding {{File}}.
    *
    * Adapted do Scala from Java original example based on the following article:
    *   https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
    *
    * @param url The URL to convert.
    * @return A file path suitable for use with e.g. {{FileInputStream}}
    */
  def urlToFile(url: URL): Try[File] = {
    var path = url.toString;
    if (path.startsWith("jar:")) {
      // remove "jar:" prefix and "!/" suffix
      val index = path.indexOf("!/");
      path = path.substring(4, index);
    }
    Try {
      if (SystemUtils.IS_OS_WINDOWS && path.matches("file:[A-Za-z]:.*")) {
        path = "file:///" + path.substring(5);
      } else if (SystemUtils.IS_OS_WINDOWS && path.matches("file:/[A-Za-z]:.*")) {
        path = "file:///" + path.substring(6);
      }
      val uri = new URI(path)
      val file = new File(uri)
      file
    } orElse Try {
      if (path.startsWith("file:")) {
        // pass through the URL as-is, minus "file:" prefix
        path = path.substring(5);
        new File(path);
      } else {
        throw new IllegalArgumentException("Invalid URL: " + url);
      }
    }
  }
}
