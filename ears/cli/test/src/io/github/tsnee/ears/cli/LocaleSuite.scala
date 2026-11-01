package io.github.tsnee.ears.cli

import java.util.Locale

import io.github.tsnee.ears.*
import zio.*
import zio.test.*

/** Classification must not depend on the JVM's default locale. Turkish is the classic trap: its
  * lowercase of "I" is a dotless "ı". This lives in the JVM-only module because it changes the
  * process-wide default locale, which Scala.js doesn't model.
  */
object LocaleSuite extends ZIOSpecDefault:

  private val turkish = Locale.forLanguageTag("tr-TR")

  private val underTurkish: ZIO[Scope, Nothing, Unit] =
    ZIO
      .acquireRelease(
        ZIO.succeed(Locale.getDefault).tap(_ => ZIO.succeed(Locale.setDefault(turkish)))
      )(original => ZIO.succeed(Locale.setDefault(original)))
      .unit

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

  /** Computes `value` while the Turkish locale is in effect. `assertTrue` evaluates lazily, after
    * the locale is restored, so everything locale-sensitive must happen in here.
    */
  private def inTurkish[A](value: => A): UIO[A] =
    ZIO.scoped(underTurkish *> ZIO.succeed(value))

  def spec = suite("Default locale independence")(
    test("an If clause is recognized under a Turkish default locale"):
      inTurkish(
        Requirement
          .classify(Profile.OpenSpec, "If the door opens, then the alarm SHALL sound.")
          .isRight
      ).map(recognized => assertTrue(recognized))
    ,
    test("an uppercase clause keyword is recognized under a Turkish default locale"):
      inTurkish(
        Requirement
          .classify(
            Profile.OpenSpec,
            "WHILE the cart is empty, the checkout service SHALL disable the pay button."
          )
          .isRight
      ).map(recognized => assertTrue(recognized))
    ,
    test("an uppercase weak modal is recognized under a Turkish default locale"):
      inTurkish(kinds("The scheduler MIGHT list events."))
        .map(found => assertTrue(found.contains(DiagnosticKind.WeakModal)))
    ,
    test("rendering capitalizes If as If under a Turkish default locale"):
      inTurkish(
        Requirement
          .classify(Profile.OpenSpec, "If the door opens, then the alarm SHALL sound.")
          .toOption
          .map(_.render(Profile.OpenSpec))
      ).map(rendered => assertTrue(rendered.exists(_.startsWith("If "))))
    ,
    test("the missing-article fix lowercases I as i under a Turkish default locale"):
      inTurkish(fixed("Inventory service SHALL list stock.", DiagnosticKind.MissingArticle))
        .map(result => assertTrue(result.contains("The inventory service SHALL list stock.")))
  ) @@ TestAspect.sequential
