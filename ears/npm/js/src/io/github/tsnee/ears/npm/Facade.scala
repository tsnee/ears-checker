package io.github.tsnee.ears.npm

import scala.compiletime.asMatchable
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

import io.github.tsnee.ears.Profile

/** The npm package's export, `classify(sentence, options?)`. It only reads JavaScript arguments and
  * writes JavaScript objects; `Api` makes every decision.
  */
object Facade:

  /** `classify(sentence, options?)`. One export, not an overload per argument count: as with any
    * JavaScript function, a missing `options` arrives as `undefined`, and extra arguments are
    * ignored.
    */
  @JSExportTopLevel("classify")
  def classify(sentence: Matchable, options: Matchable): js.Dynamic =
    sentence match
      case text: String => toObject(Api.classify(profileOf(options), text))
      case _            => argumentError(Api.sentenceError)

  /** Undefined (Scala.js's `Unit`) means no options. Any other non-null object counts, including
    * one with no prototype (`Object.create(null)`, as Node's `util.parseArgs` returns), which an
    * `instanceof Object` test would refuse. Like an object spread, only the options' own enumerable
    * properties count.
    */
  private def profileOf(options: Matchable): Profile = options match
    case _: Unit => Profile.Classic
    case _ if js.typeOf(options) == "object" && !js.special.strictEquals(options, absent) =>
      val field =
        js.Object.entries(js.Object(options)).find(_._1 == "profile").map(_._2.asMatchable)
      val argument = field match
        case None | Some(_: Unit) => ProfileArgument.Absent
        case Some(name: String)   => ProfileArgument.Name(name)
        case Some(_)              => ProfileArgument.Other
      Api.profile(argument).fold(argumentError, identity)
    case _ => argumentError(Api.optionsError)

  /** A caller's programming error, reported the way JavaScript reports one (Node's own argument
    * checks throw `TypeError` too). A sentence that doesn't fit EARS is never one: that's an
    * `ok: false` result. TypeScript callers can't get here, since the declared types rule it out.
    */
  private def argumentError(message: String): Nothing =
    js.special.`throw`(js.TypeError(message))

  private def toObject(value: Value.Obj): js.Dynamic =
    js.Dynamic.literal(value.fields.map((name, field) => name -> toJavaScript(field))*)

  private def toJavaScript(value: Value): js.Any = value match
    case Value.Null        => absent
    case Value.Bool(flag)  => flag
    case Value.Num(number) => number
    case Value.Str(text)   => text
    case Value.Arr(items)  => js.Array(items.map(toJavaScript)*)
    case fields: Value.Obj => toObject(fields)

  /** JavaScript's `null`, which marks an absent value, as in the command line's JSON. Explicit
    * nulls keep `null` out of `js.Any`, so this one cast puts it back; nothing else here uses null.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.AsInstanceOf"))
  private val absent: js.Any = null.asInstanceOf[js.Any]
