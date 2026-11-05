package io.github.tsnee.ears

/** Locale-independent case mapping on Scala.js, where `toLowerCase` and `toUpperCase` are
  * JavaScript's, which ignore the locale. `ears/src-jvm` has the JVM version, which passes
  * `Locale.ROOT`; using `java.util.Locale` here would need scala-java-locales to link.
  */
private[ears] object LetterCase:

  extension (text: String)
    def lowerCase: String = text.toLowerCase()
    def upperCase: String = text.toUpperCase()
