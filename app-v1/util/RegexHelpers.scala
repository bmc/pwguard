package util

import scala.util.matching.Regex

/** Enrichments for Scala Regex class.
  */
object RegexHelpers {

  object Implicits {

    implicit class RichRegex(r: Regex) {

      /** Determine whether the regular expression matches a string, without
        * returning any match information.
        *
        * @param s  the string
        *
        * @return true or false
        */
      def matches(s: String): Boolean = {
        r.findFirstMatchIn(s).map {_ => true}.getOrElse(false)
      }
    }
  }


}
