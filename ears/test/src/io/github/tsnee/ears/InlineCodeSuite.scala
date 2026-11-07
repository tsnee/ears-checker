package io.github.tsnee.ears

import io.github.tsnee.ears.DiagnosticKind.*
import zio.test.*

/** A code span, as in CommonMark, is one quoted word: its content takes no part in classification.
  */
object InlineCodeSuite extends ZIOSpecDefault:

  private def classified(input: String): Option[Requirement] =
    Requirement.classify(Profile.OpenSpec, input).toOption

  private def kinds(input: String): List[DiagnosticKind] =
    Requirement.classify(Profile.OpenSpec, input).left.toOption.toList.flatMap(_.toList).map(_.kind)

  private def fixed(input: String, kind: DiagnosticKind): Option[String] =
    Requirement
      .classify(Profile.OpenSpec, input)
      .left
      .toOption
      .toList
      .flatMap(_.toList)
      .find(_.kind == kind)
      .flatMap(_.fix)
      .map(_.applyTo(input))

  /** Code-span content: any text without a backtick. */
  private val content: Gen[Any, String] =
    Gen.string1(Gen.printableChar.filter(_ != '`')) <*> Gen.elements("", " ", ". ", ", ") map
      ((text, end) => text + end)

  def spec = suite("Inline code")(
    test("periods inside code end no sentence"):
      val input =
        "The checker SHALL write `Cannot read notes.txt. Check the path.` to standard error."
      assertTrue(
        classified(input).exists(
          _.response.value.contains("`Cannot read notes.txt. Check the path.`")
        )
      )
    ,
    test("a modal word inside code is not a second response"):
      val input =
        "The scheduler SHALL reject an event titled `The calendar shall sync` with a warning."
      assertTrue(
        classified(input).map(Template.of).contains(Template.Ubiquitous),
        !kinds(input).contains(MultipleResponses)
      )
    ,
    test("the modal keyword only inside code is missing"):
      assertTrue(kinds("The linter writes `SHALL` in uppercase.").contains(MissingModal))
    ,
    test("double backticks quote a backtick"):
      val input = "The formatter SHALL escape ``a ` b`` in output."
      assertTrue(classified(input).exists(_.response.value.contains("``a ` b``")))
    ,
    test("an unmatched backtick quotes nothing"):
      assertTrue(
        kinds("The shell SHALL reject a lone ` in a name. Then it SHALL stop.")
          .contains(MultipleSentences)
      )
    ,
    test("escaped backticks quote nothing"):
      assertTrue(
        kinds("The shell SHALL print \\`now. Then\\` on start.").contains(MultipleSentences)
      )
    ,
    test("an escaped backslash leaves the next backtick free to open code"):
      val input = "The exporter SHALL write to C:\\\\`out. Then` daily."
      assertTrue(classified(input).exists(_.response.value.contains("`out. Then`")))
    ,
    test("an escaped backslash, then an escaped backtick, quotes nothing"):
      assertTrue(
        kinds("The shell SHALL print \\\\\\`now. Then\\` on start.").contains(MultipleSentences)
      )
    ,
    test("a system name in code gets the article before the code"):
      assertTrue(
        fixed("`job-runner` SHALL retry failed jobs.", MissingArticle)
          .contains("The `job-runner` SHALL retry failed jobs.")
      )
    ,
    test("a system name in code with a space gets the article before the code"):
      assertTrue(
        fixed("`job runner` SHALL retry failed jobs.", MissingArticle)
          .contains("The `job runner` SHALL retry failed jobs.")
      )
    ,
    test("any code span in the response leaves a valid sentence valid"):
      check(content) { quoted =>
        val input = s"The scheduler SHALL list `$quoted` events."
        assertTrue(classified(input).exists(_.response.value.contains(s"`$quoted`")))
      }
  )
