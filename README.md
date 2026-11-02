# ears-checker

Checks requirements against the EARS templates: a Scala 3 library for the JVM and Scala.js,
and the `ears-check` command line for plain text, Markdown, and OpenSpec JSON.

**Unreleased.** 0.1.0 will be the first release, and its API is not yet stable.

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

`ears-check [--format <string>] [--profile <string>] [--output <string>] [<file>...]`

The checker isn't packaged yet. Build it from this repository and define `ears-check` as an
alias for the jar. The `--sun-misc-unsafe-memory-access` flag needs JDK 23 or newer; on an
older JDK, leave it out.

```sh
./mill ears.cli.assembly
alias ears-check="java --sun-misc-unsafe-memory-access=allow -jar $PWD/out/ears/cli/assembly.dest/out.jar"
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
- Exit status: 0 when every requirement passes or `--help` is given, 1 when any finding is
  reported, and 2 when the arguments are invalid or an input can't be read or parsed.

## Library

`io.github.tsnee:ears-checker_3` (and `ears-checker_sjs1_3` for Scala.js) classifies one
sentence. It has no IO and no effect system.

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

## Building

`./mill __.test` runs every test on the JVM and, for the library, on Node.
`./mill __.publishLocal` publishes 0.1.0-SNAPSHOT to `~/.ivy2/local`.

## License

[CC0 1.0 Universal](LICENSE): dedicated to the public domain.
