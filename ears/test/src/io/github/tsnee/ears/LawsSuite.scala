package io.github.tsnee.ears

import Generators.*
import zio.test.*

object LawsSuite extends ZIOSpecDefault:

  private def diagnostics(input: String): List[Diagnostic] =
    Requirement.classify(Profile.OpenSpec, input).left.toOption.map(_.toList).getOrElse(Nil)

  private def within(input: String)(span: SourceSpan): Boolean =
    0 <= span.start && span.start <= span.end && span.end <= input.length

  private def treeTexts(requirement: Requirement): List[Located[String]] =
    requirement.feature.toList ++ requirement.precondition.toList ++
      requirement.trigger.map(_.text).toList ++ List(requirement.system, requirement.response)

  private val anyInput = Gen.oneOf(Gen.string, damagedSentence)

  def spec = suite("Laws")(
    test("fix soundness: applying a fix removes that kind of diagnostic"):
      check(damagedSentence) { input =>
        val unresolved = diagnostics(input).collect {
          case Diagnostic(kind, _, _, Some(fix))
              if diagnostics(fix.applyTo(input)).exists(_.kind == kind) =>
            (kind, fix.applyTo(input))
        }
        assertTrue(unresolved.isEmpty)
      }
    ,
    test("missing-article fixes produce exactly the expected sentence, which is then clean"):
      check(articleOnlyDamage) { (input, expected) =>
        val fixedSentences = diagnostics(input)
          .filter(_.kind == DiagnosticKind.MissingArticle)
          .flatMap(_.fix.toList)
          .map(_.applyTo(input))
        assertTrue(fixedSentences == List(expected), diagnostics(expected).isEmpty)
      }
    ,
    test("span sanity: diagnostic and fix spans lie within the input"):
      check(anyInput) { input =>
        val spans = diagnostics(input).flatMap { diagnostic =>
          diagnostic.span :: diagnostic.fix.toList.flatMap(_.edits.map(_.span))
        }
        assertTrue(spans.forall(within(input)))
      }
    ,
    test("span sanity: each tree element's span covers exactly its text"):
      check(anyInput) { input =>
        val mismatched = Requirement
          .classify(Profile.OpenSpec, input)
          .toOption
          .toList
          .flatMap(treeTexts)
          .filterNot(located =>
            within(input)(located.span) &&
              input.substring(located.span.start, located.span.end) == located.value
          )
        assertTrue(mismatched.isEmpty)
      }
  )
