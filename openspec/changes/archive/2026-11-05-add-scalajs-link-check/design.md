# Design

## Context

See proposal.md for why. What's in place:

- `ears.js` (`build.mill`) is the Scala.js build of the library: `Published`, with
  `artifactName` `ears-checker`, so `publishLocal` writes `ears-checker_sjs1_3` at
  `publishVersion` (`0.2.0-SNAPSHOT` outside releases) to `~/.ivy2/local`, which the sandbox
  lets Claude write. Its only dependency is cats-parse. `ears.js.test` runs the library's suites
  on Node, so the library's answers are already checked on Scala.js; what it misses is the
  user's classpath.
- In apply for 0.1.1, a hand-run `scala-cli run --js-mode release` with only
  `ears-checker::0.1.0` failed to link (`Referring to non-existent method static
  java.util.Locale.ROOT()`), and with `0.1.1` linked and ran. Local `scala-cli` is 1.18.0, the
  latest; `VirtusLab/scala-cli-setup` v1.18.0 (2026-10-07) is commit
  `3f5bf11a33bb88343238780c18257c8fa43789e1`.
- The Scala.js linker reports a linking error for any method whose body is *reachable* and
  refers to something missing. A method referenced as a function value in reachable code is
  reachable, whether or not it's called.
- Mill already produces SemanticDB for every Scala module (`ears.semanticDbData`, the data
  Metals uses): one `.semanticdb` file per source, with each symbol's kind, properties, access,
  and signature, and every occurrence of a symbol, marked as a definition or a reference.
  scalameta's `semanticdb-shared` 4.17.4 (published for Scala 2.13, usable from Scala 3) reads
  it; `metap` is installed locally for spot checks. `ears.js.semanticDbData` covers what's
  published for Scala.js: `ears/src` and `ears/js/src`. (The first version read the JVM
  build's, which would miss an operation that exists only on Scala.js.)
- What a dump of the library's SemanticDB showed (2026-11-05, with a throwaway `scala-cli`
  script over `semanticdb-shared`):
  - Members the compiler generates for case classes and enums (`copy`, `copy$default$N`, `_N`,
    `apply`, `unapply`, `toString`, `values`, `valueOf`, `fromOrdinal`, `ordinal`,
    `derived$CanEqual`) are recorded as public with no property that sets them apart, but none
    has a definition occurrence. Every member written in the source has one.
  - Members of `private[ears]` objects (`Classifier.classify`, `Rendering.render`,
    `LetterCase.lowerCase`, `SkeletonParser.parse`) are recorded as public: the restriction is
    on the object, not on the member.
  - With both points applied, the public members written in the source are 7 operations, all
    in objects: `Requirement.classify`, the `Requirement` extensions `render` and `stripSpans`,
    `Template.of`, `Trigger`'s extension `text`, `Fix`'s extension `applyTo`, and
    `DiagnosticKind`'s extension `kebabName`. The rest is data: case-class fields, enum cases,
    and `Profile#keyword`, all constructor parameters or enum cases.
- `OwnSpecsSuite` (in `ears.cli.test`) shows the pattern for a test reading files outside its
  own sources: the build passes a path through `forkEnv`, and the forked test reads it.
- CI's `check` job has Java, Node, and coursier's cache, and already runs two scripts
  (`check-npm-package`, `check-openspec`).

## Goals / Non-Goals

**Goals:**
- CI links the Scala.js library as a user gets it: the published jar and POM, no test
  libraries, release mode.
- The linked program reaches every public operation without anyone writing or updating it.
- An operation the generator can't reference fails the check by name, never silently.
- Existing tools only: Mill's `publishLocal` and SemanticDB, `scala-cli`, and scalameta's
  SemanticDB reader.

**Non-Goals:**
- Checking answers on Scala.js (`ears.js.test` does).
- Checking the artifact on Maven Central after a release, or binary compatibility (MiMa).
- The same check for the JVM library or the CLI: on the JVM, the test classpath can't hide a
  missing JDK class, and `check-jdk17` already runs the CLI jar on the oldest JDK.
- A general tool for other libraries. The roadmap's Later list gets "extract it as a Mill
  plugin" once it has proved itself here.

## Decisions

### 1. The generator: a measured JVM module, pure core and thin shell

`object linkCheck extends Base with Covered` inside `object ears`: package
`io.github.tsnee.ears.linkcheck`, sources in `ears/linkCheck/src`, dependencies
`semanticdb-shared` and ZIO, not `Published`, with coverage minimums like the other modules.

- **`LinkCheck`, pure:** `operations(documents: List[TextDocument])` returns either the
  operations, each with its symbol and the Scala expression that references it, or the
  operations it can't reference, each named. `program(operations)` returns the program's text.
- **`Main`, the shell:** `ears.linkCheck.run <semanticdb directory> <output file>` reads every
  `.semanticdb` file under the directory, writes the program, and prints how many operations it
  references. On a `Left` it prints each named operation to standard error and exits 1. It's a
  ZIO app whose only logger writes to standard error, like the CLI's, and logs each step's
  outcome (files read, operations found, file written, or the failure).

### 2. Which symbols are operations

A symbol in the library's SemanticDB is an operation when it:

1. has a definition occurrence (it's written in the source, not generated);
2. is a method or an inline method (SemanticDB kinds `METHOD` and `MACRO`: an inline method's
   body is compiled into the user's code, so it can break the user's link too);
3. isn't data: not an enum case (property `ENUM`), and not a field that is a constructor
   parameter (its owner's `<init>` has a parameter of the same name);
4. is public, and so is every enclosing definition up to the package, found by walking the
   symbol's owners (`scala.meta.internal.semanticdb.Scala`'s symbol operations).

Data is left out because reading a field or naming an enum case runs no code that could refer
to a missing class.

### 3. How an operation is referenced

Each operation becomes one fully qualified expression, ascribed on its own, in a `List[Any]`.
The form comes from the operation's signature (revised after the review, which compiled each
form and found the first version's bare references failing for several shapes):

- **A method of an object with parameters** (extension methods included):
  `(_root_.….Owner.member: AnyRef)`. Scala 3 turns a method with parameters into a function
  value without calling it; ascribing each element on its own keeps one element's type (a
  by-name parameter, say) from breaking the inference of the whole list.
- **A method whose first parameter list is empty:** `(() => _root_.….Owner.member())`. A bare
  `Owner.member` doesn't compile for these.
- **A `val`, or a `def` with no parameter list:** `(_root_.….Owner.member: Any)`, since it's a
  value, possibly a primitive one.
- **A member of a class:** `((x: _root_.….Owner) => x.member)`, with `x.member()` for an empty
  parameter list and `?` for each of the class's type parameters. A class nested in a class is
  written `Outer#Inner`.
- **Not referenced, so a named failure** (Decision 1): an overloaded method (its symbol has a
  disambiguator such as `(+1)`); a method with `using` or `implicit` parameters; and a method
  with a repeated parameter (`xs: Int*`). Each is a case to support when the library first
  needs it.

`ears.linkCheck.fixture`, a small unpublished module, has one public operation of every shape
above that's referenced; the script compiles the program generated from it (Decision 5).

The generated program:

```scala
@main def linkCheck(): Unit =
  val reached: List[Any] = List(
    (_root_.io.github.tsnee.ears.Requirement.classify: AnyRef),
    (_root_.io.github.tsnee.ears.Requirement.render: AnyRef),
    …
  )
  println(s"link-check: ${reached.size} operations reachable")
```

The list is built in `main`, so every function value in it is reachable, and the linker checks
each referenced method's body and everything that body reaches. (The review confirmed that the
Scala.js analyzer computes reachability before optimizing.)

### 4. Tests

`ears.linkCheck.test` (`CoveredTests with ZioSpecs`):

- **The rules, on small `TextDocument` values built in the test:** a generated member, an
  enum case, a constructor field, members of `private[ears]`, `private`, and `protected`
  definitions, a member of a nested public object, an inline method, each reference form in
  Decision 3, and each named failure. Each lands where Decisions 2 and 3 say.
- **The program text** for a few operations.
- **The fixture:** the operations and references from `ears.linkCheck.fixture`'s real
  SemanticDB, one per shape. Unlike the library's, this list is test data: the fixture exists
  to have exactly these shapes.
- **The real library:** through `forkEnv`, the test reads `ears.js.semanticDbData()` (the
  shared and the Scala.js-only sources, which is what's published for Scala.js) and checks
  invariants of `operations`, not a list of names, so adding an operation needs no test edit:
  it succeeds; it includes `Requirement.classify` and the extension `Requirement.render` (one
  plain method and one extension, so both reference forms are exercised); nothing in it is
  owned by a `private[ears]` object; and nothing in it is a compiler-generated name (`copy`,
  `apply`, `unapply`, `_1`, `values`, …). Apply also compares its result once with the 7 in
  Context.
- **`Main`** on a temporary directory: a good run writes the file, and a failing run exits 1
  with the names.

### 5. `publishLocal`, generate, link: `scripts/check-scalajs-link`

A POSIX `sh` script in the style of `check-npm-package`:

1. Reads `ears.publishVersion`, `ears.js.scalaVersion`, `ears.js.scalaJSVersion`,
   `ears.js.semanticDbData`, and `ears.linkCheck.fixture.semanticDbData` in one
   `./mill --ticker false show '{…}'`, which prints a JSON object keyed by task, so nothing is
   pinned twice. Each value must be present, and each directory must exist, or the script
   stops.
2. Generates the fixture's program and compiles it with the fixture's sources
   (`scala-cli compile`, on the JVM): every reference form has to compile.
3. `./mill --ticker false ears.js.publishLocal` publishes the jar and POM to `~/.ivy2/local`.
4. `./mill --ticker false ears.linkCheck.run <semanticdb> <tmp>/LinkCheck.scala` generates the
   library's program in a temporary directory, removed on exit, interrupt, or termination.
5. `scala-cli run <tmp>/LinkCheck.scala --js --js-mode release --scala <version>
   --js-version <version> --repository ivy2Local
   --dependency io.github.tsnee::ears-checker::<version> --server=false` links it (Closure, full
   optimization) and runs it on Node.
   - `--server=false` runs without Bloop's background server, so nothing is left running.
   - A snapshot in `ivy2Local` is a local file repository, which coursier reads without caching,
     so a second run sees the new jar. Apply confirms this, and adds `--ttl 0` if it doesn't.
6. Requires the program's last line to match the generator's. That shows only that the program
   the generator wrote is the one that linked and ran: the count is the length of the list the
   generator wrote. Which operations it references is the generator's job, and its tests'.

*Alternatives considered:* a hand-written program with a SemanticDB guard checking it covers
the API (the previous version of this plan: the generated program can't miss an operation, so
it needs no guard); a Mill Scala.js module depending on `ears.js` (it would use the build's
classpath, not the published POM); linking the npm package only (it reaches only `classify`).

### 6. CI

- In `check`, after "Check the npm package": `VirtusLab/scala-cli-setup` at
  `3f5bf11a33bb88343238780c18257c8fa43789e1` (v1.18.0), with `scala-cli-version: 1.18.0` and
  its JVM input unset (it uses the runner's Java from `setup-java`); then a step "Check the
  Scala.js library" running `scripts/check-scalajs-link`. Dependabot already updates action
  pins.
- `ears.linkCheck` joins the "Report coverage" loop and the "Check coverage" step.
- `check-jdk17` doesn't run the link check: it depends on the Scala.js linker, not the runtime
  JDK.

### 7. Docs folded in from 0.1.1

- **README:** `0.1.0` becomes `0.1.1` in the "on Maven Central" line, the Coursier alias, and
  the Mill, sbt, and Maven install lines, with a note that Scala.js users need 0.1.1 or newer.
  Building lists `scripts/check-scalajs-link`.
- **`docs/roadmap.md`:** drop "Release 0.1.1". Under Next, in the order discussed on
  2026-11-05: a Markdown facade with one parser per platform (commonmark-java on the JVM,
  `mdast-util-from-markdown` on Node, cmark on Scala Native) and one conformance suite run on
  all three; the CLI on Node; Scala Native; then publishing the npm package, with a CLI as well
  as the library. Under Later: extract the link-check generator as a Mill plugin.
- **CLAUDE.md:** Build and test gets the script; Layout gets `ears/linkCheck`; the coverage
  commands include `ears.linkCheck`.

## Risks / Trade-offs

- **[A rule drops a real operation]** Then the program doesn't reach it, and nothing fails. The
  real-library test (Decision 4) catches a rule that drops everything, or `classify` or
  `render`, or lets private or generated members in; a subtler drop isn't caught. Apply
  compares the result with the 7 in Context and spot-checks the SemanticDB with `metap`.
- **[SemanticDB changes with the compiler]** A Scala upgrade could record generated members
  differently, so they'd count as operations (the program then references `copy` and the like,
  harmless) or real ones would drop out (the real-library test fails).
- **[An operation's shape isn't supported yet]** It fails the check by name, so the gap is
  visible; supporting it is a small change to Decision 3, and a new member of the fixture.
- **[A shape is referenced wrongly]** The fixture's program is compiled on every run, so a
  form that doesn't compile fails there, for a shape the fixture has. A shape it lacks shows up
  only when the library gains one; adding it to the fixture is part of supporting it.
- **[scala-cli and Mill disagree on the Scala.js version]** The script passes Mill's versions,
  so they can't drift. `scala-cli` itself is pinned, and Dependabot moves the action.
- **[Run time]** `publishLocal` builds the docs jar, and `scala-cli` links with Closure:
  perhaps a minute in CI. Apply reports it.
