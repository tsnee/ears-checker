package io.github.tsnee.ears

import zio.test.Gen

/** Generated requirement sentences, intact or damaged in known ways. Words avoid commas, clause
  * keywords, "the", "then", and modal verbs, which the classifier treats as structure.
  */
object Generators:

  val phrase: Gen[Any, String] = Gen
    .listOfBounded(1, 4)(
      Gen.elements(
        "order",
        "cart",
        "customer",
        "door",
        "alarm",
        "user",
        "session",
        "page",
        "item",
        "stock",
        "sign-in",
        "total",
        "empty",
        "open",
        "is",
        "a",
        "of",
        "to",
        "for",
        "list",
        "show",
        "log"
      )
    )
    .map(_.mkString(" "))

  /** How a generated sentence departs from canonical form. */
  final case class Damage(
      modal: String,
      reversedClauses: Boolean,
      missingComma: Boolean,
      missingThen: Boolean,
      missingArticle: Boolean,
      missingPeriod: Boolean
  )

  val damage: Gen[Any, Damage] =
    for
      modal <- Gen.elements("SHALL", "shall", "should", "MUST")
      reversed <- Gen.boolean
      comma <- Gen.boolean
      thenWord <- Gen.boolean
      article <- Gen.boolean
      period <- Gen.boolean
    yield Damage(modal, reversed, comma, thenWord, article, period)

  private def capitalize(text: String): String = text.take(1).toUpperCase + text.drop(1)

  /** Sentences that are valid except for how the system name is introduced (a replaceable
    * determiner, an acronym, a name with inner capitals, or a bare word, at the start of the
    * sentence or after a When clause), paired with the sentence the missing-article fix should
    * produce.
    */
  /** A system-name phrase that doesn't itself start with a determiner ("a" is the only one in the
    * vocabulary), so the expected fix is unambiguous.
    */
  private val systemPhrase: Gen[Any, String] =
    phrase.filter(words => words.takeWhile(_ != ' ') != "a")

  val articleOnlyDamage: Gen[Any, (String, String)] =
    for
      leadIn <- Gen.option(phrase.map(text => s"When $text, "))
      introduced <- Gen.oneOf(
        Gen.elements("a", "an", "this", "that", "our", "its", "their").zip(systemPhrase).map {
          (determiner, system) => (s"$determiner $system", system)
        },
        systemPhrase.map(system => (s"API $system", s"API $system")),
        systemPhrase.map(system => (s"OpenSpec $system", s"OpenSpec $system")),
        systemPhrase.map(system => (system, system))
      )
      response <- phrase
    yield
      val (written, systemName) = introduced
      val atStart = leadIn.isEmpty
      val subject = if atStart then capitalize(written) else written
      val article = if atStart then "The" else "the"
      val prefix = leadIn.getOrElse("")
      (
        s"$prefix$subject SHALL $response.",
        s"$prefix$article $systemName SHALL $response."
      )

  // A valid requirement's words avoid commas, clause keywords, "the", "then", and modal verbs,
  // which the classifier treats as structure (see design.md, "Two-stage classification").
  private val validWord: Gen[Any, String] = Gen.elements(
    "order",
    "cart",
    "customer",
    "door",
    "alarm",
    "user",
    "session",
    "page",
    "item",
    "stock",
    "sign-in",
    "total",
    "empty",
    "open",
    "closed",
    "is",
    "are",
    "a",
    "an",
    "of",
    "to",
    "for",
    "with",
    "list",
    "show",
    "send",
    "log"
  )

  private val validPhrase: Gen[Any, Located[String]] =
    Gen.listOfBounded(1, 5)(validWord).map(words => Located(words.mkString(" "), SourceSpan(0, 0)))

  private val validTrigger: Gen[Any, Trigger] =
    Gen.oneOf(validPhrase.map(Trigger.Event(_)), validPhrase.map(Trigger.Unwanted(_)))

  /** A requirement of any template, with every span empty, and its canonical text under `profile`,
    * which classifies back to the same requirement.
    */
  def validRequirement(profile: Profile): Gen[Any, (Requirement, String)] =
    for
      feature <- Gen.option(validPhrase)
      precondition <- Gen.option(validPhrase)
      trigger <- Gen.option(validTrigger)
      system <- validPhrase
      response <- validPhrase
    yield
      val requirement = Requirement(feature, precondition, trigger, system, response)
      (requirement, requirement.render(profile))

  /** A sentence built from generated parts with the given damage applied. */
  val damagedSentence: Gen[Any, String] =
    for
      feature <- Gen.option(phrase)
      precondition <- Gen.option(phrase)
      trigger <- Gen.option(Gen.elements("when", "if"))
      triggerText <- phrase
      system <- phrase
      response <- phrase
      harm <- damage
    yield
      val canonical = List(
        feature.map(("where", _)),
        precondition.map(("while", _)),
        trigger.map((_, triggerText))
      ).flatten
      val clauses = if harm.reversedClauses then canonical.reverse else canonical
      val leading = clauses.zipWithIndex.map { case ((keyword, text), i) =>
        val written = if i == 0 then capitalize(keyword) else keyword
        val separator = if harm.missingComma && i == clauses.size - 1 then " " else ", "
        s"$written $text$separator"
      }
      val thenWord = if trigger.contains("if") && !harm.missingThen then "then " else ""
      val atStart = leading.isEmpty && thenWord.isEmpty
      val main =
        if harm.missingArticle then s"${if atStart then capitalize(system) else system}"
        else s"${if atStart then "The" else "the"} $system"
      val period = if harm.missingPeriod then "" else "."
      s"${leading.mkString}$thenWord$main ${harm.modal} $response$period"

  /** Generated requirement sentences, and arbitrary text as well. */
  val damagedSentenceOrText: Gen[Any, String] = Gen.oneOf(damagedSentence, Gen.string)
