package io.github.tsnee.ears.linkcheck

import java.nio.file.Paths
import scala.meta.internal.semanticdb.*

import zio.*
import zio.test.*

/** The rules that pick out the library's public operations and write their references: on small
  * SemanticDB documents built here, on the fixture's SemanticDB (whose generated program
  * `scripts/check-scalajs-link` compiles), and on the library's own.
  */
object LinkCheckSuite extends ZIOSpecDefault:

  private val pkg = "p/"

  private def defined(symbol: String): SymbolOccurrence =
    SymbolOccurrence(
      range = Some(Range(0, 0, 0, 1)),
      symbol = symbol,
      role = SymbolOccurrence.Role.DEFINITION
    )

  private def obj(symbol: String): SymbolInformation =
    SymbolInformation(
      symbol = symbol,
      kind = SymbolInformation.Kind.OBJECT,
      access = PublicAccess()
    )

  private def cls(symbol: String): SymbolInformation =
    SymbolInformation(
      symbol = symbol,
      kind = SymbolInformation.Kind.CLASS,
      access = PublicAccess()
    )

  private def scope(symlinks: List[String]): scala.meta.internal.semanticdb.Scope =
    scala.meta.internal.semanticdb.Scope(symlinks = symlinks)

  /** A public method with these parameter lists, each a list of parameter symbols. */
  private def withLists(symbol: String, lists: List[List[String]]): SymbolInformation =
    SymbolInformation(
      symbol = symbol,
      kind = SymbolInformation.Kind.METHOD,
      access = PublicAccess(),
      signature = MethodSignature(parameterLists = lists.map(scope))
    )

  /** A public method with one parameter, `x`. */
  private def method(symbol: String): SymbolInformation =
    withLists(symbol, List(List(s"$symbol(x)")))

  /** A public `val`, or a `def` with no parameter list: no method signature. */
  private def value(symbol: String): SymbolInformation =
    SymbolInformation(
      symbol = symbol,
      kind = SymbolInformation.Kind.METHOD,
      access = PublicAccess()
    )

  private def parameter(symbol: String, properties: Int): SymbolInformation =
    SymbolInformation(
      symbol = symbol,
      kind = SymbolInformation.Kind.PARAMETER,
      properties = properties
    )

  /** One document with these symbols, each defined in the source unless listed in `generated`.
    */
  private def document(
      symbols: List[SymbolInformation],
      generated: Set[String]
  ): TextDocument =
    TextDocument(
      uri = "p/Model.scala",
      symbols = symbols,
      occurrences = symbols.map(_.symbol).filterNot(generated.contains).map(defined)
    )

  private def operationsOf(symbols: SymbolInformation*) =
    LinkCheck.operations(List(document(symbols.toList, Set.empty)))

  private def symbolsOf(found: Either[?, List[Operation]]): List[String] =
    found.toOption.toList.flatten.map(_.symbol)

  private def problemsOf(found: Either[NonEmptyChunk[Problem], ?]): List[Problem] =
    found.left.toOption.toList.flatMap(_.toList)

  private val owner = obj(s"${pkg}Owner.")
  private val apply = method(s"${pkg}Owner.applyTo().")

  def spec = suite("link-check program")(
    suite("which symbols are operations")(
      test("a public method of a public object, written in the source"):
        assertTrue(
          operationsOf(owner, apply) ==
            Right(List(Operation(apply.symbol, "(_root_.p.Owner.applyTo: AnyRef)")))
        )
      ,
      test("an inline method (SemanticDB kind MACRO) is one: its body goes into the user's code"):
        val inlined = method(s"${pkg}Owner.inlined().").copy(kind = SymbolInformation.Kind.MACRO)
        assertTrue(symbolsOf(operationsOf(owner, inlined)) == List(inlined.symbol))
      ,
      test("a member the compiler generated has no definition occurrence, so it isn't one"):
        val copy = method(s"${pkg}Owner.copy().")
        val found =
          LinkCheck.operations(List(document(List(owner, apply, copy), Set(copy.symbol))))
        assertTrue(symbolsOf(found) == List(apply.symbol))
      ,
      test("an enum case isn't one"):
        val enumCase = value(s"${pkg}Owner.Complex.").copy(
          properties = SymbolInformation.Property.ENUM.value | SymbolInformation.Property.CASE.value
        )
        assertTrue(symbolsOf(operationsOf(owner, apply, enumCase)) == List(apply.symbol))
      ,
      test("a field that is a constructor parameter isn't one"):
        val span = cls(s"${pkg}Span#")
        val field = value(s"${pkg}Span#start.")
        val param =
          parameter(s"${pkg}Span#`<init>`().(start)", SymbolInformation.Property.VAL.value)
        assertTrue(symbolsOf(operationsOf(owner, apply, span, field, param)) == List(apply.symbol))
      ,
      test("a public member of a private[p] object isn't one"):
        val hidden = obj(s"${pkg}Hidden.").copy(access = PrivateWithinAccess(pkg))
        val member = method(s"${pkg}Hidden.run().")
        assertTrue(symbolsOf(operationsOf(owner, apply, hidden, member)) == List(apply.symbol))
      ,
      test("a public member of a private object isn't one"):
        val hidden = obj(s"${pkg}Owner.Hidden.").copy(access = PrivateAccess())
        val member = method(s"${pkg}Owner.Hidden.run().")
        assertTrue(symbolsOf(operationsOf(owner, apply, hidden, member)) == List(apply.symbol))
      ,
      test("a protected member isn't one: users can't call it"):
        val guarded = method(s"${pkg}Owner.guarded().").copy(access = ProtectedAccess())
        assertTrue(symbolsOf(operationsOf(owner, apply, guarded)) == List(apply.symbol))
      ,
      test("a member of a public object nested in a public object is one"):
        val inner = obj(s"${pkg}Owner.Inner.")
        val member = method(s"${pkg}Owner.Inner.run().")
        assertTrue(
          operationsOf(owner, inner, member) ==
            Right(List(Operation(member.symbol, "(_root_.p.Owner.Inner.run: AnyRef)")))
        )
      ,
      test("operations come out sorted by symbol, from every document"):
        val other = obj(s"${pkg}Another.")
        val run = method(s"${pkg}Another.run().")
        val found = LinkCheck.operations(
          List(document(List(owner, apply), Set.empty), document(List(other, run), Set.empty))
        )
        assertTrue(symbolsOf(found) == List(run.symbol, apply.symbol))
    ),
    suite("how each shape is referenced")(
      test("a val, or a def with no parameter list, as a value"):
        val limit = value(s"${pkg}Owner.limit.")
        assertTrue(
          operationsOf(owner, limit) ==
            Right(List(Operation(limit.symbol, "(_root_.p.Owner.limit: Any)")))
        )
      ,
      test("a method whose first parameter list is empty, by a function that calls it"):
        val empty = withLists(s"${pkg}Owner.empty().", List(Nil))
        assertTrue(
          operationsOf(owner, empty) ==
            Right(List(Operation(empty.symbol, "(() => _root_.p.Owner.empty())")))
        )
      ,
      test("a method of a class, through a parameter, with ? for type parameters"):
        val pair = cls(s"${pkg}Pair#").copy(signature =
          ClassSignature(typeParameters = Some(scope(List(s"${pkg}Pair#[A]", s"${pkg}Pair#[B]"))))
        )
        val swap = value(s"${pkg}Pair#swap().")
        assertTrue(
          operationsOf(pair, swap) ==
            Right(List(Operation(swap.symbol, "((x: _root_.p.Pair[?, ?]) => x.swap)")))
        )
      ,
      test("a method of a class with no type parameters, and one with an empty parameter list"):
        val box = cls(s"${pkg}Box#")
        val grow = method(s"${pkg}Box#grow().")
        val reset = withLists(s"${pkg}Box#reset().", List(Nil))
        assertTrue(
          operationsOf(box, grow, reset) == Right(
            List(
              Operation(grow.symbol, "((x: _root_.p.Box) => x.grow)"),
              Operation(reset.symbol, "((x: _root_.p.Box) => x.reset())")
            )
          )
        )
      ,
      test("a method of a class nested in a class, through Outer#Inner"):
        val box = cls(s"${pkg}Box#")
        val lid = cls(s"${pkg}Box#Lid#")
        val open = value(s"${pkg}Box#Lid#open().")
        assertTrue(
          operationsOf(box, lid, open) ==
            Right(List(Operation(open.symbol, "((x: _root_.p.Box#Lid) => x.open)")))
        )
      ,
      test("a name that isn't a plain identifier, in backticks"):
        val plus = method(s"${pkg}Owner.`+`().")
        assertTrue(
          operationsOf(owner, plus) ==
            Right(List(Operation(plus.symbol, "(_root_.p.Owner.`+`: AnyRef)")))
        )
    ),
    suite("operations it can't reference")(
      test("an overloaded method"):
        val second = method(s"${pkg}Owner.applyTo(+1).")
        assertTrue(
          problemsOf(operationsOf(owner, apply, second)) ==
            List(Problem.Overloaded("Owner.applyTo"))
        )
      ,
      test("a method with a using parameter"):
        val ordered = method(s"${pkg}Owner.sorted().")
        val usingParameter =
          parameter(s"${pkg}Owner.sorted().(ordering)", SymbolInformation.Property.GIVEN.value)
        assertTrue(
          problemsOf(operationsOf(owner, ordered, usingParameter)) ==
            List(Problem.ContextParameters("Owner.sorted"))
        )
      ,
      test("a method with an implicit parameter"):
        val ordered = method(s"${pkg}Owner.sorted().")
        val implicitParameter =
          parameter(s"${pkg}Owner.sorted().(ordering)", SymbolInformation.Property.IMPLICIT.value)
        assertTrue(
          problemsOf(operationsOf(owner, ordered, implicitParameter)) ==
            List(Problem.ContextParameters("Owner.sorted"))
        )
      ,
      test("a method with a variable number of arguments"):
        val joined = method(s"${pkg}Owner.joined().")
        val parts = parameter(s"${pkg}Owner.joined().(x)", 0)
          .copy(signature = ValueSignature(RepeatedType(TypeRef(symbol = "scala/Predef.String#"))))
        assertTrue(
          problemsOf(operationsOf(owner, joined, parts)) == List(Problem.Varargs("Owner.joined"))
        )
      ,
      test("a parameter of an ordinary type isn't a problem"):
        val joined = method(s"${pkg}Owner.joined().")
        val part = parameter(s"${pkg}Owner.joined().(x)", 0)
          .copy(signature = ValueSignature(TypeRef(symbol = "scala/Predef.String#")))
        assertTrue(symbolsOf(operationsOf(owner, joined, part)) == List(joined.symbol))
      ,
      test("no operations at all"):
        assertTrue(problemsOf(operationsOf(owner)) == List(Problem.NoOperations))
      ,
      test("each problem's message names what went wrong"):
        assertTrue(
          Problem.Overloaded("Owner.applyTo").message.contains("Owner.applyTo"),
          Problem.Overloaded("Owner.applyTo").message.contains("overloaded"),
          Problem.ContextParameters("Owner.sorted").message.contains("Owner.sorted"),
          Problem.Varargs("Owner.joined").message.contains("Owner.joined"),
          Problem.NoOperations.message.nonEmpty
        )
    ),
    suite("the program")(
      test("references every operation from main and prints how many references it holds"):
        val text = LinkCheck.program(
          List(
            Operation("p/Owner.applyTo().", "(_root_.p.Owner.applyTo: AnyRef)"),
            Operation("p/Owner.limit.", "(_root_.p.Owner.limit: Any)")
          )
        )
        assertTrue(
          text.contains("@main def linkCheck(): Unit ="),
          text.contains("val reached: List[Any] = List("),
          text.contains(
            "    (_root_.p.Owner.applyTo: AnyRef),\n    (_root_.p.Owner.limit: Any)\n"
          ),
          text.contains("""println(s"link-check: ${reached.size} operations reachable")""")
        )
      ,
      test("the line the generator prints matches the one the program will print"):
        assertTrue(LinkCheck.summary(7) == "link-check: 7 operations reachable")
    ),
    suite("real compiler output")(
      test("the fixture: every shape, each with the reference its program compiles with"):
        val fixture = "_root_.io.github.tsnee.ears.linkcheck.fixture"
        for found <- read("FIXTURE_SEMANTICDB").map(LinkCheck.operations)
        yield assertTrue(
          found.map(_.map(_.reference)) == Right(
            List(
              s"((x: $fixture.Box#Lid) => x.open)",
              s"((x: $fixture.Box) => x.area)",
              s"((x: $fixture.Box) => x.grow)",
              s"((x: $fixture.Box) => x.reset())",
              s"((x: $fixture.Pair[?, ?]) => x.swap)",
              s"($fixture.Shapes.Nested.inside: AnyRef)",
              s"($fixture.Shapes.`+++`: AnyRef)",
              s"($fixture.Shapes.byName: AnyRef)",
              s"($fixture.Shapes.curried: AnyRef)",
              s"($fixture.Shapes.doubled: AnyRef)",
              s"(() => $fixture.Shapes.empty())",
              s"(() => $fixture.Shapes.emptyThenMore())",
              s"($fixture.Shapes.generic: AnyRef)",
              s"($fixture.Shapes.inlined: AnyRef)",
              s"($fixture.Shapes.lazyLimit: Any)",
              s"($fixture.Shapes.limit: Any)",
              s"($fixture.Shapes.old: AnyRef)",
              s"($fixture.Shapes.ordering: Any)",
              s"($fixture.Shapes.parameterless: Any)",
              s"($fixture.Shapes.plain: AnyRef)"
            )
          )
        )
      ,
      test("the library: a plain method and an extension, and nothing private or generated"):
        for
          found <- read("EARS_SEMANTICDB").map(LinkCheck.operations)
          symbols = symbolsOf(found)
          // The definitions and member each symbol names, without its package: `Owner.member`.
          names = symbols.map(
            _.stripPrefix("io/github/tsnee/ears/").replaceAll("[.#]?$|\\(\\)\\.$", "")
          )
        yield assertTrue(
          found.isRight,
          symbols.contains("io/github/tsnee/ears/Requirement.classify()."),
          symbols.contains("io/github/tsnee/ears/Requirement.render()."),
          !names.exists(name =>
            List("Classifier.", "Rendering.", "LetterCase.", "SkeletonParser.", "Skeleton.")
              .exists(name.startsWith)
          ),
          !names.exists(name =>
            List("copy", "apply", "unapply", "_1", "values", "valueOf", "fromOrdinal", "toString")
              .exists(generated => name.endsWith(s".$generated") || name.endsWith(s"#$generated"))
          )
        )
    )
  )

  /** Every document under the SemanticDB directory the build passes in `variable`. */
  private def read(variable: String): Task[List[TextDocument]] =
    // The real environment: zio-test's TestSystem starts with none.
    Live
      .live(System.env(variable))
      .someOrFail(new IllegalStateException(s"$variable is not set"))
      .flatMap(root => Main.readDocuments(Paths.get(root)))
