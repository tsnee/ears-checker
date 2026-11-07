package io.github.tsnee.ears.cli

import io.github.tsnee.ears.Profile
import io.github.tsnee.ears.Requirement
import io.github.tsnee.ears.markdown.Markdown
import zio.test.*

/** The Markdown reader and the classifier agree on where inline code is: Markdown written from
  * generated pieces, read by the reader, classifies as a sentence whose only structure is outside
  * the code. Here because only the CLI has both; it runs on the JVM and on Node.
  */
object InlineCodeRoundTripSuite extends ZIOSpecDefault:

  /** Text that holds nothing structural: letters, backticks, and backslashes. */
  private val text: Gen[Any, String] = Gen.string1(Gen.elements('a', 'b', '`', '\\'))

  /** Code that holds everything structural: periods, spaces, commas, the modal, backticks. */
  private val code: Gen[Any, String] =
    Gen
      .listOfBounded(1, 6)(Gen.elements("a", "`", "``", " ", ". ", ", ", "\\", "shall"))
      .map(_.mkString)

  /** Markdown for text: each backtick and backslash escaped, which CommonMark reads as itself. */
  private def writeText(literal: String): String =
    literal.flatMap {
      case '`'   => "\\`"
      case '\\'  => "\\\\"
      case other => other.toString
    }

  /** Markdown for code: a fence longer than any run inside, padded where a backtick touches it. */
  private def writeCode(literal: String): String =
    val fence = "`" * ("`+".r.findAllIn(literal).map(_.length).maxOption.getOrElse(0) + 1)
    val padding = if literal.startsWith("`") || literal.endsWith("`") then " " else ""
    s"$fence$padding$literal$padding$fence"

  def spec = suite("Inline code, from Markdown to the classifier")(
    test("code with periods, commas, and modals, between text with backticks and backslashes"):
      check(Gen.listOfBounded(1, 3)(text <*> code), text) { (pairs, last) =>
        val middle = pairs.map((t, c) => writeText(t) + writeCode(c)).mkString + writeText(last)
        val sentences = Markdown.sentences(s"The scheduler shall print x$middle now.\n")
        assertTrue(
          sentences.map(_.size) == Right(1),
          sentences.exists(
            _.forall(sentence => Requirement.classify(Profile.Classic, sentence.text).isRight)
          )
        )
      }
  )
