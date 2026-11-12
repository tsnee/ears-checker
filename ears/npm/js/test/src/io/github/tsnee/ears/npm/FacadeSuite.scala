package io.github.tsnee.ears.npm

import scala.compiletime.asMatchable
import scala.scalajs.js

import io.github.tsnee.ears.*
import zio.*
import zio.test.*

/** The export as a JavaScript caller sees it: arguments of any JavaScript type, and results that
  * are the core's values as JavaScript objects. `ApiSuite` checks what those values say.
  */
object FacadeSuite extends ZIOSpecDefault:

  /** `value` as JSON text, written the way `JSON.stringify` writes a JavaScript object. */
  private def json(value: Value): String = value match
    case Value.Null        => "null"
    case Value.Bool(flag)  => flag.toString
    case Value.Num(number) => number.toString
    case Value.Str(text)   => js.JSON.stringify(text)
    case Value.Arr(items)  => items.map(json).mkString("[", ",", "]")
    case Value.Obj(fields) =>
      fields
        .map((name, field) => s"${js.JSON.stringify(name)}:${json(field)}")
        .mkString("{", ",", "}")

  private def options(profile: String): js.Dynamic = js.Dynamic.literal(profile = profile)

  private def profileName(profile: Profile): String = profile match
    case Profile.Classic  => "classic"
    case Profile.OpenSpec => "openspec"

  private def ok(result: js.Dynamic): Option[Boolean] = (result.ok: Matchable) match
    case flag: Boolean => Some(flag)
    case _             => None

  /** The message of the `TypeError` that `call` throws, if it throws one. */
  private def typeError(call: => js.Dynamic): UIO[Option[String]] =
    ZIO
      .attempt(call)
      .fold(
        {
          case js.JavaScriptException(thrown) =>
            thrown.asMatchable match
              case error: js.TypeError => Some(error.message)
              case _                   => None
          case _ => None
        },
        _ => None
      )

  private val ubiquitous = "The scheduler shall list upcoming events."

  def spec = suite("JavaScript export")(
    test("every result is the core's value as a JavaScript object"):
      check(Generators.damagedSentenceOrText, Gen.elements(Profile.Classic, Profile.OpenSpec)) {
        (sentence, profile) =>
          val result = Facade.classify(sentence, options(profileName(profile)))
          assertTrue(js.JSON.stringify(result) == json(Api.classify(profile, sentence)))
      }
    ,
    suite("options")(
      test("the OpenSpec profile"):
        assertTrue(
          ok(Facade.classify("The scheduler SHALL list upcoming events.", options("openspec")))
            .contains(true)
        )
      ,
      test("options with no prototype, as Node's util.parseArgs returns"):
        val bare = js.Dynamic.global.Object.create(js.JSON.parse("null"))
        bare.updateDynamic("profile")("openspec")
        assertTrue(
          ok(Facade.classify("The scheduler SHALL list upcoming events.", bare)).contains(true)
        )
      ,
      // A call with no options reaches the export as undefined; scripts/check-npm-package's
      // consumer makes that call from JavaScript.
      test("undefined options: the classic profile"):
        assertTrue(ok(Facade.classify(ubiquitous, js.undefined)).contains(true))
      ,
      test("empty options: the classic profile"):
        assertTrue(ok(Facade.classify(ubiquitous, js.Dynamic.literal())).contains(true))
      ,
      test("an undefined profile: the classic profile"):
        assertTrue(
          ok(Facade.classify(ubiquitous, js.Dynamic.literal(profile = js.undefined)))
            .contains(true)
        )
    ),
    suite("argument errors")(
      test("an unknown profile: a TypeError naming both profiles"):
        typeError(Facade.classify(ubiquitous, options("open-spec"))).map(message =>
          assertTrue(message.exists(m => m.contains("classic") && m.contains("openspec")))
        )
      ,
      test("a profile that isn't a string: a TypeError"):
        typeError(Facade.classify(ubiquitous, js.Dynamic.literal(profile = 1))).map(message =>
          assertTrue(message == Some(Api.profile(ProfileArgument.Other).left.getOrElse("")))
        )
      ,
      test("options that aren't an object: a TypeError"):
        typeError(Facade.classify(ubiquitous, "openspec")).map(message =>
          assertTrue(message == Some(Api.optionsError))
        )
      ,
      test("null options: a TypeError"):
        typeError(Facade.classify(ubiquitous, js.JSON.parse("null"))).map(message =>
          assertTrue(message == Some(Api.optionsError))
        )
      ,
      test("a sentence that isn't a string: a TypeError"):
        typeError(Facade.classify(42, js.undefined)).map(message =>
          assertTrue(message == Some(Api.sentenceError))
        )
    )
  )
