# ears-checker

[![CI](https://github.com/tsnee/ears-checker/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tsnee/ears-checker/actions/workflows/ci.yml?query=branch%3Amain) [![Maven Central](https://img.shields.io/maven-central/v/io.github.tsnee/ears-checker_3)](https://central.sonatype.com/artifact/io.github.tsnee/ears-checker_3) [![License: CC0-1.0](https://img.shields.io/badge/license-CC0--1.0-blue)](LICENSE)

Checks that requirements follow EARS, the Easy Approach to Requirements Syntax, and suggests a fix when one doesn't. `ears-check` reads plain text, Markdown, and OpenSpec specs and changes (as `openspec show --json` prints them); the same checker is a Scala 3 library for the JVM and Scala.js, and an npm package.

```text
$ ears-check reqs.txt
reqs.txt:1: missing-comma: Put a comma after the When clause. Fix: "When a customer submits an order, the checkout service shall send a confirmation email."
reqs.txt:2: weak-modal: Use shall instead of "should". Fix: "The scheduler shall list upcoming events."
```

The current release is **0.2.0**. Until 1.0, any minor release can change the API and the command line.

## Quick start

### The command line

Pick one way to run it; each gives the same output and exit status.

**A native binary** (Linux on x64 or Arm with glibc 2.35 or later, such as Ubuntu 22.04 or Debian 12; macOS 15 or later on Apple silicon), from the [latest release](https://github.com/tsnee/ears-checker/releases/latest). It starts instantly and needs no JVM or Node:

```sh
curl -fL -O https://github.com/tsnee/ears-checker/releases/download/v0.2.0/ears-check-0.2.0-linux-x64 \
  -O https://github.com/tsnee/ears-checker/releases/download/v0.2.0/SHA256SUMS
grep ' ears-check-0.2.0-linux-x64$' SHA256SUMS | sha256sum -c
chmod +x ears-check-0.2.0-linux-x64
mv ears-check-0.2.0-linux-x64 ears-check
./ears-check reqs.txt
```

Use `linux-arm64` or `macos-arm64` in the file names for those platforms, and `shasum -a 256 -c` where there's no `sha256sum`. The checksum shows the download is intact; [docs/release-files.md](docs/release-files.md) also shows how to check who built it. To read Markdown the binary needs [cmark](https://github.com/commonmark/cmark) 0.30 or later: `brew install cmark` on macOS, `apt install cmark` on Debian and Ubuntu, or your system's cmark package. The macOS binary isn't signed: download it with `curl` as above, since macOS won't open an unsigned program a browser downloaded.

**The jar**, on JDK 17 or later, from the same release:

```sh
curl -fLO https://github.com/tsnee/ears-checker/releases/download/v0.2.0/ears-check-0.2.0.jar
java -XX:+IgnoreUnrecognizedVMOptions --sun-misc-unsafe-memory-access=allow -jar ears-check-0.2.0.jar reqs.txt
```

The two flags stop JDK 23 and later from warning about the Scala runtime's use of `sun.misc.Unsafe`; older JDKs skip them.

**[Coursier](https://get-coursier.io)**, as an app from this repository's channel. It installs the latest release's native binary where there is one (Linux on x64 or Arm, macOS on Apple silicon), and runs on the JVM (JDK 17 or later) everywhere else:

```sh
cs install --channel https://raw.githubusercontent.com/tsnee/ears-checker/main/coursier/apps.json ears-check
ears-check reqs.txt
```

`cs update ears-check` moves it to a newer release. The native binary needs cmark to read Markdown, as above. Coursier keeps it next to the `ears-check` it installs, as `.ears-check.aux` (by default in `~/.local/share/coursier/bin` on Linux and `~/Library/Application Support/Coursier/bin` on macOS); [docs/release-files.md](docs/release-files.md) shows how to check who built it.

**Node** 22 or later, as the npm package `@tsnee/ears-checker-cli`. It isn't on npm yet; until it is, install the tarball from the release, which installs its one dependency too:

```sh
curl -fLO https://github.com/tsnee/ears-checker/releases/download/v0.2.0/tsnee-ears-checker-cli-0.2.0.tgz
npm install -g ./tsnee-ears-checker-cli-0.2.0.tgz
ears-check reqs.txt
```

### The library

Scala 3.10 or later, on the JVM or Scala.js:

```scala
// Mill (the second :: picks the Scala.js build in a Scala.js module)
mvn"io.github.tsnee::ears-checker::0.2.0"
// sbt (%%% instead of %% for Scala.js)
libraryDependencies += "io.github.tsnee" %% "ears-checker" % "0.2.0"
```

```xml
<!-- Maven -->
<dependency>
  <groupId>io.github.tsnee</groupId>
  <artifactId>ears-checker_3</artifactId>
  <version>0.2.0</version>
</dependency>
```

For JavaScript and TypeScript, see [below](#javascript-and-typescript).

## What it checks

EARS (Alistair Mavin and colleagues, 2009) writes each requirement as one sentence in one of six templates:

| Template | Form |
| --- | --- |
| Ubiquitous | The \<system\> shall \<response\>. |
| Event-driven | When \<trigger\>, the \<system\> shall \<response\>. |
| State-driven | While \<precondition\>, the \<system\> shall \<response\>. |
| Unwanted behavior | If \<trigger\>, then the \<system\> shall \<response\>. |
| Optional feature | Where \<feature is included\>, the \<system\> shall \<response\>. |
| Complex | While \<precondition\>, when \<trigger\>, the \<system\> shall \<response\>. |

A complex requirement combines clauses in the order Where, While, then When or If.

For each requirement that doesn't fit, the checker reports what's wrong, and a corrected sentence where it can: the modal keyword (`shall` missing, in the wrong case, or replaced by `should`, `must`, `will`, and the like), nothing after it, more than one response or sentence, clauses out of order, repeated, or conflicting, a missing comma, `then`, period, or article, and sentences that fit no template. [docs/json-output.md](docs/json-output.md#kinds) lists all 14 kinds.

### Limits

- **It checks form, not content.** It doesn't judge vague words ("quickly", "user-friendly"), whether a requirement can be tested, or whether it's the right requirement. It reads English only.
- **In Markdown, only sentences containing the word `shall`**, in any case and outside inline code, are checked, in paragraphs, list items, and block quotes. A sentence with `should` and no `shall` isn't reported in Markdown (in plain text it's a `weak-modal` finding). Headings, code blocks, and HTML aren't read.
- **Markdown tables aren't recognized.** A GitHub-style table is read as one paragraph of text, so a table with `shall` in it gives misleading findings. Keep requirements out of tables, or check them as plain text.
- **In plain text, every nonblank line is one requirement.**
- **In OpenSpec JSON, requirement text only:** each added and modified requirement of a change, or every requirement of a spec. Scenarios aren't checked. A `spec.md` file given directly is read as Markdown, which also checks scenario lines that quote a requirement: check OpenSpec through its JSON, as [below](#openspec).

## Command line

`ears-check [--format <string>] [--profile <string>] [--output <string>] [--verbose] [<file>...]`

It reads each file, or standard input when none is given, and reports every requirement that doesn't fit a template, with a suggested fix where one exists:

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

- `--format`: `text` (one requirement per line), `markdown`, or `openspec` (JSON from `openspec show --json`). By default `.md` files are Markdown, `.json` files are OpenSpec, and everything else is text.
- `--profile`: `classic` (lowercase `shall`) or `openspec` (uppercase `SHALL`, which OpenSpec's validator requires). OpenSpec input defaults to `openspec`, other input to `classic`.
- `--output`: `text` (one line per finding) or `json` (one document; see [JSON output](#json-output)).
- `--verbose`: log every step to standard error (the arguments, each input read, and the outcome), not only warnings and errors.
- `--help`: print the usage and these options. For now it works only as the first argument; elsewhere it's an unexpected option (exit status 2).
- Exit status: 0 when every requirement passes, or for `--help` as the first argument; 1 when any finding is reported; 2 when the arguments are invalid or an input can't be read or parsed.
- Standard output carries only the findings or the usage text. Standard error carries error messages and log entries, one line each: a timestamp, the level, the message, and details as `key=value` pairs. Without `--verbose`, only failures are logged, each with its input, the error's type and message, and the exit status.
- Inline code: in every format, text between backticks is quoted, as in Markdown (a run of backticks up to the next run of the same length; `` \` `` is a plain backtick and `\\` a plain backslash). Its periods, commas, and words don't count, so `` `Cannot read notes.txt. Check the path.` `` is one quoted word, and a requirement whose only `shall` is quoted still lacks one.
- Encoding: input is read as UTF-8 whatever the locale, and an input that isn't valid UTF-8 is an error giving the line and column of the first bad byte; a leading UTF-8 byte-order mark is ignored, and UTF-16 or UTF-32 input is named by its byte-order mark. Output is written as UTF-8. On Linux, the JVM decodes file names on the command line with the locale's charset, so under the C locale (common in Docker images) it can't open a file whose name isn't ASCII; run it under a UTF-8 locale, such as `LC_ALL=C.UTF-8`. The Node build and the native binary open such a file under any locale.
- The native binary and cmark: it finds cmark when it runs, whatever release you have (0.30 or later), in the absolute directories `LD_LIBRARY_PATH` (`DYLD_LIBRARY_PATH` on macOS) names, Homebrew's directory (`$HOMEBREW_PREFIX/lib` or the usual ones), MacPorts', or the system's. It never loads cmark from the current directory. On macOS, while `DYLD_LIBRARY_PATH` has a relative entry such as `.`, it reads no Markdown and says to remove the entry, since macOS would look there first. (On Linux, such an entry in `LD_LIBRARY_PATH` exposes every program's startup libraries, so leave relative and empty entries out.) Without cmark, plain text and OpenSpec JSON still work, and a Markdown file gets a message saying what to install.

### OpenSpec

Check an OpenSpec change or spec through the JSON the OpenSpec CLI prints; OpenSpec input uses the `openspec` profile (uppercase `SHALL`) unless `--profile` says otherwise. `--type` and `--json` below are options of `openspec show`; `ears-check` needs only the file, or `--format openspec` for standard input:

```sh
openspec show add-reminders --type change --json > add-reminders.json
ears-check add-reminders.json
openspec show reminders --type spec --json | ears-check --format openspec
```

A file name ending in `.json` is read as OpenSpec; standard input needs `--format openspec`.

### JSON output

`--output json` writes one document for the whole run, for programs to read. It names the ears-checker release that wrote it, and has a format version that changes when a field is removed, renamed, or changes type or meaning, or a new location type appears. With the `reqs.txt` above:

```text
$ ears-check --output json reqs.txt | jq -c '.version, .checked, (.findings[] | [.location.line, .kind, .fix])'
1
3
[1,"missing-comma","When a customer submits an order, the checkout service shall send a confirmation email."]
[2,"weak-modal","The scheduler shall list upcoming events."]
```

[docs/json-output.md](docs/json-output.md) describes every field, the location types for Markdown and OpenSpec input, what spans count, what can change within a version, and when there's no document.

## Scala library

`io.github.tsnee:ears-checker_3` (and `ears-checker_sjs1_3` for Scala.js) classifies one sentence, reading inline code between backticks as one quoted word, as the command line does. It has no IO and no effect system. Scala.js users need 0.1.1 or newer: 0.1.0's Scala.js build doesn't link in an application without scala-java-locales.

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

A `Requirement` is the syntax tree, with a source span on every part. A `Diagnostic` has a kind, a span, a message, and usually a `Fix` whose `applyTo(input)` gives the corrected sentence.

## JavaScript and TypeScript

The npm package `@tsnee/ears-checker` is the same classifier compiled to JavaScript, as an ES module with TypeScript declarations and no dependencies, for Node 22 or later and bundlers. It isn't on npm yet; until it is, install the tarball from the [latest release](https://github.com/tsnee/ears-checker/releases/latest):

```sh
curl -fLO https://github.com/tsnee/ears-checker/releases/download/v0.2.0/tsnee-ears-checker-0.2.0.tgz
npm install ./tsnee-ears-checker-0.2.0.tgz
```

```ts
import { classify } from "@tsnee/ears-checker";

const result = classify("The scheduler SHALL list upcoming events.", { profile: "openspec" });
if (result.ok) console.log(result.template); // "ubiquitous"
else console.log(result.diagnostics.map((d) => d.fix?.fixed ?? d.message));
```

The package's [README](ears/npm/package/README.md) describes the results and options.

## Releases

What changed in each version is in [CHANGELOG.md](CHANGELOG.md). From 0.2.0, each version's [GitHub release](https://github.com/tsnee/ears-checker/releases) has those notes and the files its release run built and checked: the CLI jar, both npm packages' tarballs, the native binaries, a `SHA256SUMS` file, and a build-provenance attestation for each file, showing this repository's release workflow built it for that version's tag:

```sh
gh attestation verify <file> --repo tsnee/ears-checker \
  --signer-workflow tsnee/ears-checker/.github/workflows/release.yml \
  --source-ref refs/tags/v<version>
```

The notes end with what each file needs and how to check it ([docs/release-files.md](docs/release-files.md)). Check a file before running it.

The macOS binary isn't signed, so macOS won't open it after a browser downloads it ("Apple could not verify…"). Download it with `curl -fLO <its URL>`, or, once it's checked, clear the browser's quarantine mark with `xattr -d com.apple.quarantine <file>`.

## Contributing

Building, testing, and how changes are made are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

Report a vulnerability privately, as [SECURITY.md](SECURITY.md) says, not in an issue.

## License

[CC0 1.0 Universal](LICENSE): dedicated to the public domain.
