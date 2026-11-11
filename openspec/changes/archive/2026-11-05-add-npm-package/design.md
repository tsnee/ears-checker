# Design

## Context

See proposal.md for why. What's in place:

- `ears.js`, in `build.mill`, compiles the `ears` sources and tests with Scala.js 1.22.0, and its tests run on Node. It's a `Published` module, so its Scala.js jar goes to Maven Central with every release. It exports nothing to JavaScript.
- The library's API is `Requirement.classify(profile, input)`. It returns `Either[NonEmptyList[Diagnostic], Requirement]`.
  - A `Requirement` has optional feature, precondition, and trigger clauses (`Trigger.Event` or `Trigger.Unwanted`), then the system and the response. Each is a `Located[String]` with a `SourceSpan`.
  - `Template.of` names the template.
  - A `Diagnostic` has a kind, a span, a message, and an optional `Fix`. `Fix.applyTo` applies the fix's edits to the input.
  - Spans count UTF-16 code units, which is how JavaScript strings index.
- The CLI's JSON output (`Checker.findingJson`) names kinds in kebab case (`Checker.kindName`, derived from the enum case name), gives `span: {start, end}`, and uses `null` for an absent value. Its `fix` is only the corrected sentence.
- `Published.publishVersion` is the tag's version in a release and `0.1.0-SNAPSHOT` otherwise. `0.1.0` is already released, and under semver ordering `0.1.0-SNAPSHOT` sorts before it.
- `ears/test`'s `Generators.damagedSentence` generates sentences that do or don't match, and is compiled for Node by `ears.js.test`.
- In CI, `check` sets up Node 26, and `check-jdk17` (the oldest-JDK job) also sets up Node 26 for the OpenSpec CLI. Both install the OpenSpec CLI, pinned, with `npm install -g`. OpenSpec 1.16.0 needs Node 22.12 or newer.
- Coverage is measured with Mill's `ScoverageModule`, on the JVM, for `ears` and `ears.cli` only.
- Locally, the sandbox lets Claude only read `~/.npm`. npm commands need a `--cache` directory under a temporary or scratch directory.

## Goals / Non-Goals

**Goals:**

- JavaScript and TypeScript callers get the classifier's full results: template, clauses, diagnostics, and fixes.
- The API follows npm norms closely enough that it can grow without breaking callers.
- The results use the CLI's JSON conventions for kind names, spans, and absent values.
- The package that `npm pack` builds is the thing CI tests, from a real install. CI type-checks it under Node and bundler module resolution, runs it on the oldest and newest Node versions it supports, and lints it with publint and Are the Types Wrong.
- The Maven Central artifacts don't change, apart from the snapshot version.

**Non-Goals:**

- Publishing to npm. That's a later change; see Decision 9 for its plan.
- A Node build of the CLI. Markdown input uses commonmark-java, which is JVM-only.
- A CommonJS build or a browser bundle. ES modules load in Node and in modern bundlers.
- Exporting rendering, `Template.of` on its own, or any other library function besides `classify`.

## Decisions

### 1. A measured core, `ears.npm`, and a Scala.js export, `ears.npm.js`; neither on Maven Central

The layout mirrors `ears` and `ears.js`:

- **`ears.npm`** is a JVM module (`Base with Covered`, `pureWarts`, `moduleDeps = Seq(ears)`). Its sources, in `ears/npm/src/io/github/tsnee/ears/npm/`, are the pure core, `Api`: every decision behind `classify`, producing a small JSON-like ADT, `Value`. Its tests run on the JVM, and scoverage measures them with a minimum, like `ears` and `ears.cli`.
- **`ears.npm.js`** is a Scala.js module (`moduleDeps = Seq(ears.js)`). It compiles the core's sources plus `ears/npm/js/src/…/Facade.scala`, the export, and its tests run on Node.
- Both use the package `io.github.tsnee.ears.npm`. Neither mixes in `Published`, so `./mill __.publishArtifacts` and the release don't touch them.

_Why two modules:_ Mill's `ScoverageModule` instruments a JVM copy of a module, so it can't measure Scala.js-only code (tried in apply: the instrumented copy compiles to JVM classes, which can't run the Node tests). Keeping the decisions in a module that also builds for the JVM gets them measured; the facade left on Scala.js only reads arguments and writes objects.

_Why not export from `ears.js` itself:_ `@JSExportTopLevel` in a published Scala.js library puts those exports into every Scala.js application that depends on it. The export has to live outside the library jar.

### 2. One exported function with an options object

```scala
enum Value:
  case Null; case Bool(value: Boolean); case Num(value: Int); case Str(value: String)
  case Arr(items: List[Value]); case Obj(fields: List[(String, Value)])

object Api:
  def classify(profile: Profile, sentence: String): Value.Obj
  def profile(argument: ProfileArgument): Either[String, Profile]

object Facade:
  @JSExportTopLevel("classify") def classify(sentence: Matchable, options: Matchable): js.Dynamic
```

The arguments:

- `sentence` is the requirement's text.
- `options` is optional, and today has one field, `profile`. `"classic"` expects a lowercase `shall`; `"openspec"` expects `SHALL`. It defaults to `"classic"`, as the CLI does for text and Markdown.
- An options object, not a positional string, lets later options (a language, a rule set) be added without breaking callers. That's the npm norm for optional settings.
- Both are typed `Matchable` and checked with type patterns, because a plain JavaScript caller can pass anything. Decision 3 covers the errors.
- The export is one method with two parameters. As with any JavaScript function, a missing `options` arrives as `undefined` and extra arguments are ignored, with no Scala default argument (which the DefaultArguments wart bans). The first version had two overloads, one per argument count; the review found that a third argument then threw Scala.js's unhelpful `No matching overload`.
- Any non-null value whose `typeof` is `"object"` counts as options, including one with no prototype (`Object.create(null)`, which Node's `util.parseArgs` returns). The first version's `js.Object` type pattern compiled to `instanceof Object`, which refuses those. Only the options' own enumerable properties count, as with an object spread. The facade reads `profile` with `Object.entries` into a `ProfileArgument` (absent, a name, or another value), and `Api.profile` decides.

`Api.classify` builds the result as a `Value`; the facade turns each `Value` into the JavaScript value it names (`Null` into `null`, `Obj` into a `js.Dynamic.literal`, and so on). It's a discriminated union on `ok`, declared in `index.d.ts` (Decision 6) as:

```ts
export type Profile = "classic" | "openspec";
export interface Options { readonly profile?: Profile }
export interface Span { readonly start: number; readonly end: number }
export interface Clause { readonly text: string; readonly span: Span }
export interface Trigger { readonly kind: "event" | "unwanted"; readonly clause: Clause }
export interface Requirement {
  readonly feature: Clause | null; readonly precondition: Clause | null;
  readonly trigger: Trigger | null; readonly system: Clause; readonly response: Clause;
}
export interface TextEdit { readonly span: Span; readonly replacement: string }
export interface Fix { readonly edits: readonly TextEdit[]; readonly fixed: string }
export interface Diagnostic {
  readonly kind: DiagnosticKind; readonly message: string; readonly span: Span;
  readonly fix: Fix | null;
}
export type Result =
  | { readonly ok: true; readonly template: Template; readonly requirement: Requirement }
  | { readonly ok: false; readonly diagnostics: readonly Diagnostic[] };
export function classify(sentence: string, options?: Options): Result;
```

The shapes:

- `Template` and `DiagnosticKind` are unions of kebab-case string literals, spelled with explicit, exhaustive matches, not derived from `toString`, so renaming a Scala enum case can't silently change any output. The kind names are the library's `DiagnosticKind.kebabName`, which the CLI uses too (the review found the CLI deriving them from `toString` while `Api` had its own table); the `ears-classifier` delta states the requirement, and a library test checks each name is its case name in kebab case. Template names are `Api.templateName`, with the same test in `ApiSuite`.
- `null`, never `undefined`, marks an absent value, as in the CLI's JSON.
- `fix` is richer than the CLI's: the edits as well as the corrected sentence, so an editor can apply them in place.
- Every field and array is `readonly`, because a result is output.
- Every shape has an exported name, so callers can annotate their own code with it.

_Alternative considered:_ exporting rendering too. Only `classify` is asked for. Exporting less keeps the 0.x API small.

### 3. Argument errors throw `TypeError`

There are three argument errors:

- **A sentence that isn't a string.**
- **A second argument that is neither `undefined` nor an object.** That includes a profile passed as a plain string.
- **An unknown profile.** The message names `classic` and `openspec`.

All three throw with `js.special.throw(js.TypeError(...))`. That's how JavaScript reports a caller's programming error; Node's own `ERR_INVALID_ARG_TYPE` and `ERR_INVALID_ARG_VALUE` are `TypeError`s. TypeScript callers can't reach the errors at all, because the declared types already rule them out. It isn't exceptions for control flow: a classification problem is always an `ok: false` result. `js.special.throw` is a method call, not Scala's `throw`, so the Throw wart doesn't fire. A comment at the call site says why the facade throws.

_Alternative considered:_ an `{ ok: false, error }` result. That mixes a programmer's mistake into the classification results, and every caller would have to check for it.

### 4. Versions: `0.2.0-SNAPSHOT` for Maven and `0.2.0-dev` for npm outside releases

- `Published.publishVersion`'s non-release value becomes `0.2.0-SNAPSHOT`, the next minor version, so it sorts after the released `0.1.0`. The `release-publishing` delta changes the requirement to match. After each release, the change that starts the next version bumps it.
- The npm package's version is `publishVersion` with `-SNAPSHOT` replaced by `-dev`. `-SNAPSHOT` is a Maven idiom; `-dev` is a plain semver pre-release. A release version has no suffix, so it's the same on both.
- README and CLAUDE.md mention `0.1.0-SNAPSHOT`; both change.

### 5. ES module output and a Mill task that assembles the package

- `ears.npm.js` sets `moduleKind = ModuleKind.ESModule`, and the package uses its `fullLinkJS`. Scala.js's default split style produces one `main.js` and its `main.js.map`.
- **The output isn't minified.** Found in apply: Mill 1.3.1 does set `withMinify(true)` for release links, but Scala.js minifies release output only through the Closure Compiler, which it doesn't use for ES modules. A small `scala-cli` test linked the same program to 14 kB as a plain script and 64 kB, indented and with full names, as an ES module. The owner chose to ship the unminified ES module: `main.js` is 1.57 MB, 243 kB gzipped. Bundlers minify their own output, and Node doesn't need it. `docs/roadmap.md` lists revisiting it.
  - _Alternatives considered:_ linking as CommonJS so Closure minifies it, behind a hand-written ES module entry file (hand-written JavaScript, and a riskier lint setup); and minifying with terser from npm in the package task (an npm tool in the build itself).
- `ears.npm.npmPackage`, a `Task { … }`, writes the package directory under its `Task.dest`:
  - `main.js`, from `fullLinkJS`.
  - `main.js.map`, with `sourcesContent` for every source from this repository. Found in apply: 240 of the map's 246 sources are library sources (Scala, Scala 3, Scala.js, cats, cats-parse), named by public GitHub URLs at their release tags, and some (Scala.js's internal `RuntimeLong`) may be in no published sources jar. The owner chose to embed only this repository's sources and keep the library URLs, which debuggers fetch on demand.
    - A repository source is a path relative to the repository root, or in a release its tagged URL under `ears.releaseSourceUrl`. Mill lets a task read only declared sources, so the task reads them through `ears.js`'s and `ears.npm.js`'s source roots, and fails on one outside them rather than ship a map with a gap.
    - The link also showed `ears.npm.js`'s own sources named by absolute `file:///…` paths. `ears.js`'s `-scalajs-mapSourceURI` option moved into a `SourceUris` trait that both Scala.js modules mix in, so no path from the build machine reaches the package.
  - `index.d.ts` and `README.md`, copied from `ears/npm/package/`.
  - `LICENSE`, copied from the repository root.
  - `package.json`, generated with `ujson`:
    - `name: "@tsnee/ears-checker"`;
    - `version`, from Decision 4;
    - `description: ears.pomDescription`;
    - `license: "CC0-1.0"`;
    - `type: "module"`;
    - `exports: { ".": { types: "./index.d.ts", default: "./main.js" }, "./package.json": "./package.json" }`;
    - `types: "./index.d.ts"`;
    - `files`;
    - `sideEffects: false`;
    - `engines: { node: ">=22" }`, the oldest version CI tests (Decision 8);
    - `repository: { type: "git", url: "git+https://github.com/tsnee/ears-checker.git" }`, the exact form npm's trusted publishing compares against the GitHub repository;
    - `homepage`, `bugs`, and `keywords`;
    - no `dependencies` or `peerDependencies`.
- The task returns a `PathRef` to the directory.

_Why generate `package.json`:_ the version and description then come from the same build values as the Maven artifacts.

### 6. Hand-written declarations, checked by a TypeScript consumer and two linters

`ears/npm/package/index.d.ts` declares `classify` and the types in Decision 2. Scala.js 1 has no maintained generator for `.d.ts` files, and a package with no declarations gives TypeScript users `any`. To keep the hand-written file honest, the smoke check (Decision 8) type-checks a TypeScript consumer against the installed package under both Node and bundler resolution, and lints the package with publint and Are the Types Wrong (attw). The file is declarations only; the package has no hand-written JavaScript.

### 7. Tests: the core on the JVM, the export on Node

`ApiSuite`, in `ears.npm.test` (`CoveredTests with ZioSpecs`, with `ears.test` for `Generators`), reads `Api`'s values back as the library's types:

- **The spec scenarios, one test each:**
  - a match with each kind of trigger and with none, and with a feature and a precondition;
  - a missing modal;
  - wrong case with its fix and `fixed` sentence;
  - a diagnostic with no fix;
  - a span that slices the sentence to its clause;
  - each `ProfileArgument`.
- **A table test** over every `DiagnosticKind` and `Template` case. It checks each JavaScript name against the kebab-case of the case name.
- **A property test** over `Generators.damagedSentence` and arbitrary strings, with both profiles. It checks that the result, read back, equals the library's `Requirement.classify`, in kinds, messages, spans, fix edits, `fixed`, and order.

The core's minimums are 100% statements and branches, which it reaches today.

`FacadeSuite`, in `ears.npm.js.test` (`ScalaJSTests with ZioSpecs`, with `ears.js.test`), calls the export as JavaScript sees it:

- **A property test:** `JSON.stringify` of the export's result equals the JSON that the core's `Value` names, for the same sentences and profiles. That checks the conversion to JavaScript objects as a whole, `null` included.
- **The options:** `{ profile: "openspec" }`, no options, `undefined`, `{}`, and an undefined `profile`.
- **The four `TypeError`s:** an unknown profile, a profile that isn't a string, options that aren't an object, and a sentence that isn't a string.

`./mill __.test` runs both with the rest.

### 8. Smoke check: `scripts/check-npm-package`

It's a bash script like `scripts/check-openspec`, and runs locally and in CI:

1. `./mill --ticker false show ears.npm.npmPackage` gets the directory.
2. `npm pack` into a temporary directory makes the tarball. Every npm command gets `--cache` under the temporary directory, so the check never writes to `~/.npm`. That keeps it hermetic in CI and works in the sandbox locally.
3. No file in the tarball may contain the repository's path or `$HOME`.
4. `publint` and `attw --profile esm-only` lint the tarball. The `esm-only` profile stops attw from reporting the CommonJS resolution failures an ESM-only package has by design.
5. It creates an empty project: a `package.json` with `"type": "module"`, and `npm install` of the tarball.
6. `npm ls --all --parseable` must list exactly one installed package, at any depth: `node_modules/@tsnee/ears-checker`. `jq` on the installed `package.json` also checks it declares no `dependencies`, `peerDependencies`, `optionalDependencies`, or bundled dependencies. (The first version read only `npm ls --json`'s top level, which can't fail.)
7. It copies `ears/npm/smoke/consumer.ts` in and type-checks it twice with `tsc --noEmit --strict --verbatimModuleSyntax --erasableSyntaxOnly --exactOptionalPropertyTypes --noUncheckedIndexedAccess --target es2022`:
   - with `--module nodenext --moduleResolution nodenext`;
   - with `--module esnext --moduleResolution bundler`.

   `--verbatimModuleSyntax` and `--erasableSyntaxOnly` make `tsc` reject what Node's type stripping can't run, such as a type imported without `import type`.

8. `node consumer.ts` runs it. An `EXIT` trap removes the temporary directory, and `INT` and `TERM` traps exit so that it runs when the check is interrupted too.

`consumer.ts` uses only the package and the standard library, so it needs no `@types/node`. It imports the named types with `import type`, narrows the results on `ok`, checks one match, one fix, and one `TypeError`, and throws an `Error` when something is wrong. An uncaught error exits Node with a non-zero status. Lines under `// @ts-expect-error` assign well-typed values to a match's `ok` and `template` and call `pop()` on `diagnostics` and a fix's `edits`, so the type-check fails if any of them stops being read-only. It also calls `classify` as untyped JavaScript, with one argument and with three, the cases Scala-side tests can't reach.

In CI:

- The pinned `npm install -g` adds `typescript@7.0.2`, `publint@0.3.25`, and `@arethetypeswrong/cli@0.18.5`, today's `latest` of each.
- `check` runs the script, on Node 26, as a step named "Check the npm package", after "Test".
- `check-jdk17` becomes the job for the oldest supported runtimes. Its Node changes from 26 to 22, which OpenSpec 1.16.0 supports, and it runs the script too. Its job id stays the same, so branch protection that names it keeps working. Its comment says what it's for.

Locally, the developer installs the three tools globally, the same way the OpenSpec CLI is installed.

### 9. Publishing, recorded for the later change and not built here

These choices are for the follow-up change:

- **Trusted publishing.** It uses OIDC, so no npm token is stored. npm generates provenance automatically for trusted publishing, so `--provenance` isn't needed. It needs npm 11.5.1 or newer and Node 22.14 or newer. The first publish uses `--access public`, as scoped packages default to private.
- **A separate `npm` job and environment.** The owner is the required reviewer. The owner approves it after clicking Publish on the Central Portal, so a version dropped there never reaches npm.
- **`id-token: write` on that job only.** The `release-publishing` requirement "Read-only token in releases" will need a delta for it.
- **First publish.** npm may only let a trusted publisher be configured on a package that already exists. If so, the owner publishes the first version by hand. Check npm's current rules then.

### 10. Case mapping per platform, so the library links on Scala.js by itself

Found in apply: the first release link of `ears.npm.js` failed. The classifier called `toLowerCase(Locale.ROOT)` and `toUpperCase(Locale.ROOT)`, and Scala.js has no `java.util.Locale` without the scala-java-locales library. The Scala.js tests passed only because zio-test brings that library in (through scala-java-time). So the released 0.1.0 Scala.js artifact doesn't link in an application without it.

The fix:

- **A `private[ears] object LetterCase`** with `lowerCase` and `upperCase` extensions, in two versions. `ears/src-jvm` keeps `Locale.ROOT`, because the JVM's no-argument methods follow the default locale (Turkish maps "I" to a dotless "ı"). `ears/js/src` uses JavaScript's no-argument methods, which ignore the locale already. Behavior doesn't change on either platform.
- **The build:** `ears` gets `sharedSources` (`ears/src`) and `jvmSources` (`ears/src-jvm`). `ears.js` compiles `sharedSources` plus its own `ears/js/src`.
- **The guard:** linking the npm package for release uses no test libraries, so CI's npm package check fails if the library ever needs one again. The `ears-classifier` delta states the requirement.
- **After merging:** the owner tags `v0.1.1`, so Scala.js users get a library that links. The snapshot stays `0.2.0-SNAPSHOT`.

_Alternatives considered:_ `Character.toLowerCase` per character (one source, but it differs from `String.toLowerCase` for a few Unicode special cases), and a dependency on scala-java-locales (every Scala.js user and the npm package would carry locale data they don't need).

## Risks / Trade-offs

- **[The facade itself isn't measured]** It's about 40 lines that read arguments and write objects, with no decisions of its own. `FacadeSuite` reaches each of its branches, and its property test checks the whole conversion against the measured core.
- **[The declarations drift from the facade]** The smoke check's type-checks and run catch a shape that changes on one side only. A field added only to the facade wouldn't fail the check, but TypeScript users wouldn't see it either.
- **[A second CI job runs the smoke check]** `check-jdk17` already builds the CLI with Mill, so adding the npm package there costs one more link and the npm installs, about a minute.
- **[Library sources aren't embedded]** A bundler that reads source maps from disk may warn about the 240 library sources it can't read. Browsers' dev tools fetch them by URL.
- **[Package size]** `main.js` is 1.57 MB unminified (243 kB gzipped), mostly the Scala standard library and cats-parse; the map is 1.4 MB before sources are embedded. Apply reports the packed size.
