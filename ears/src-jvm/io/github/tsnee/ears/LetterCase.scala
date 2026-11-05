package io.github.tsnee.ears

import java.util.Locale

/** Locale-independent case mapping on the JVM, whose no-argument `toLowerCase` and `toUpperCase`
  * follow the default locale (Turkish maps "I" to a dotless "ı"). `ears/js/src` has the Scala.js
  * version: Scala.js has no `java.util.Locale` without an extra library, and JavaScript's own case
  * mapping is locale-independent already.
  */
private[ears] object LetterCase:

  extension (text: String)
    def lowerCase: String = text.toLowerCase(Locale.ROOT)
    def upperCase: String = text.toUpperCase(Locale.ROOT)
