# ears-checker

Checks requirements against the EARS templates: a Scala 3 library for the JVM and Scala.js,
and the `ears-check` command line for plain text, Markdown, and OpenSpec JSON.

**0.1.0** is on Maven Central. The API is not yet stable and may change in any 0.x release.

## EARS

EARS, the Easy Approach to Requirements Syntax (Alistair Mavin and colleagues, 2009), writes
each requirement as one sentence in one of six templates:

| Template | Form |
|---|---|
| Ubiquitous | The \<system\> shall \<response\>. |
| Event-driven | When \<trigger\>, the \<system\> shall \<response\>. |
| State-driven | While \<precondition\>, the \<system\> shall \<response\>. |
| Unwanted behavior | If \<trigger\>, then the \<system\> shall \<response\>. |
| Optional feature | Where \<feature is included\>, the \<system\> shall \<response\>. |
| Complex | While \<precondition\>, when \<trigger\>, the \<system\> shall \<response\>. |

A complex requirement combines clauses in the order Where, While, then When or If.

## Command line

`ears-check [--format <string>] [--profile <string>] [--output <string>] [--verbose] [<file>...]`

It runs on JDK 17 or newer. Run it from Maven Central with [Coursier](https://get-coursier.io):

```sh
alias ears-check="cs launch --java-opt -XX:+IgnoreUnrecognizedVMOptions --java-opt --sun-misc-unsafe-memory-access=allow io.github.tsnee:ears-checker-cli_3:0.1.0 --"
```

Or build it from this repository and define `ears-check` as an alias for the jar. On JDK 23 and
newer,
`--sun-misc-unsafe-memory-access=allow` stops the JVM's warnings about the Scala runtime's use of
`sun.misc.Unsafe`; older JDKs don't know that flag, and `-XX:+IgnoreUnrecognizedVMOptions` makes
them skip it, so the same alias works on any of them.

```sh
./mill ears.cli.assembly
alias ears-check="java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar $PWD/out/ears/cli/assembly.dest/out.jar"
```

It reads each file, or standard input when none is given, and reports every requirement that
doesn't fit a template, with a suggested fix where one exists:

```text
$ cat reqs.txt
When a customer submits an order the checkout service shall send a confirmation email.
The scheduler should list upcoming events.
While the cart is empty, the checkout service shall disable the pay button.
$ ears-check reqs.txt
reqs.txt:1: missing-comma: Put a comma after the When clause. Fix: "When a customer submits an order, the checkout service shall send a confirmation email."
reqs.txt:2: weak-modal: Use shall instead of "should". Fix: "The scheduler shall list upcoming events."
$ echo $?
1
```

- `--format`: `text` (one requirement per line), `markdown`, or `openspec` (JSON from
  `openspec show --json`). By default `.md` files are Markdown, `.json` files are OpenSpec,
  and everything else is text.
- `--profile`: `classic` (lowercase `shall`) or `openspec` (uppercase `SHALL`, which
  OpenSpec's validator requires). OpenSpec input defaults to `openspec`, other input to
  `classic`.
- `--output`: `text` (one line per finding) or `json` (one document).
- `--verbose`: log every step to standard error (the arguments, each input read, and the
  outcome), not only warnings and errors.
- Standard output carries only the findings or the usage text. Standard error carries error
  messages and log entries, one line each: a timestamp, the level, the message, and details as
  `key=value` pairs. Without `--verbose`, only failures are logged, each with its input, the
  error's type and message, and the exit status.
- Exit status: 0 when every requirement passes or `--help` is given, 1 when any finding is
  reported, and 2 when the arguments are invalid or an input can't be read or parsed.

## Library

`io.github.tsnee:ears-checker_3` (and `ears-checker_sjs1_3` for Scala.js) classifies one
sentence. It has no IO and no effect system. It needs Scala 3.10 or newer. Add it with:

```scala
// Mill (the second :: picks the Scala.js build in a Scala.js module)
mvn"io.github.tsnee::ears-checker::0.1.0"
// sbt (%%% instead of %% for Scala.js)
libraryDependencies += "io.github.tsnee" %% "ears-checker" % "0.1.0"
```

```xml
<!-- Maven -->
<dependency>
  <groupId>io.github.tsnee</groupId>
  <artifactId>ears-checker_3</artifactId>
  <version>0.1.0</version>
</dependency>
```

0.x versions may change the API in any minor release.

```scala
import cats.data.NonEmptyList
import io.github.tsnee.ears.*

val input = "The scheduler should list upcoming events."

val template: Either[NonEmptyList[Diagnostic], Template] =
  Requirement.classify(Profile.Classic, input).map(Template.of)

val corrected: Option[String] =
  template.left.toOption.flatMap(_.head.fix).map(_.applyTo(input))
// Some("The scheduler shall list upcoming events.")
```

A `Requirement` is the syntax tree, with a source span on every part. A `Diagnostic` has a
kind, a span, a message, and usually a `Fix` whose `applyTo(input)` gives the corrected
sentence.

## JavaScript and TypeScript

The npm package `@tsnee/ears-checker` is the same classifier compiled to JavaScript, as an ES
module with TypeScript declarations and no dependencies. It isn't on npm yet; build it with
`./mill ears.npm.npmPackage`, which prints the package directory, and install that directory
or the tarball `npm pack` makes from it.

```ts
import { classify } from "@tsnee/ears-checker";

const result = classify("The scheduler SHALL list upcoming events.", { profile: "openspec" });
if (result.ok) console.log(result.template); // "ubiquitous"
else console.log(result.diagnostics.map((d) => d.fix?.fixed ?? d.message));
```

The package's [README](ears/npm/package/README.md) describes the results and options.

## Building

`./mill __.test` runs every test on the JVM and, for the library and the npm package, on Node,
and measures test coverage on the JVM; `./mill ears.scoverage.htmlReport` (or `ears.cli`, or
`ears.npm`) writes a report under `out/`.
`./mill __.publishLocal` publishes the next version as a snapshot (`0.2.0-SNAPSHOT`) to
`~/.ivy2/local`.
`scripts/check-npm-package` builds the npm package, lints it with publint and Are the Types
Wrong, installs it into an empty project, and type-checks and runs a TypeScript program that uses
it; it needs `tsc`, `publint`, and `attw` installed from npm.
GitHub Actions checks formatting, compiles, runs every test, checks each module's coverage
against its minimum in `build.mill`, checks the npm package on the oldest and newest Node it
supports, and checks the OpenSpec specs on each push to `main` and each pull request.

## License

[CC0 1.0 Universal](LICENSE): dedicated to the public domain.
