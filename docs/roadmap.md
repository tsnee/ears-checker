# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order, grouped by theme. Each theme is one branch and one pull request, merged when the
theme is done.

### Release 0.2.0 (branch `batch-release-0.2.0`, PR #17)

Already in it: the CLI on Scala Native, release notes and GitHub Releases, the JSON output
reference, the README for users, faster Markdown on Node, and loading cmark safely.

1. **The CLI as a Coursier app**: an app description, with a channel file in this repository,
   so `cs install` sets up `ears-check`.
2. **Release 0.2.0**, after a security review of the whole repository (CLAUDE.md's Releasing
   steps). It's a minor release: inline code changes some of the classifier's verdicts. The
   pull request merges just before the tag, since a release is tagged on `main`.

### npm

3. **Publish the npm packages**: the library `@tsnee/ears-checker` and the command line
   `@tsnee/ears-checker-cli`. The owner (npm account `tsnee`, created 2026-11-07) publishes
   0.2.0 by hand, since npm wants a package to exist before a trusted publisher can be set up.
   Later versions are published from the release workflow with npm's trusted publishing, in
   their own job behind their own approval, after the Central Portal deployment is published.
   The plan is in the archived `add-npm-package` change's design (Decision 9). The
   repository README's quick start then drops "it isn't on npm yet" and uses `npx` and `npm
   install` (always the scoped names, such as `npx @tsnee/ears-checker-cli`: an unscoped
   `npx ears-check` fetches whatever package claims that name), and the npm badge joins the
   others.

### A general Scala.js link checker

4. **A general Scala.js link checker**, extracted from `ears.linkCheck`. It writes a program
   that references every public operation of a Scala.js library from its SemanticDB, and
   `scripts/check-scalajs-link` links it against only the published artifact; it caught
   0.1.0's `java.util.Locale` bug, which the tests hid (zio-test brings in scala-java-locales).
   Searches on 2026-11-07 found no existing tool that does this. To be general it needs: the
   operation shapes it now refuses by name (overloads, `using` parameters, varargs, givens,
   extension methods, bounded type parameters, constructors that take arguments) or a way to
   exclude one; a build integration others use (a Mill plugin, and an sbt plugin: GitHub's code search on
   2026-11-08 found about 38,800 `build.sbt` files enabling `ScalaJSPlugin`, against about 300 Mill
   build files using `ScalaJSModule`); a check on Scala 2 libraries' SemanticDB; and
   its own repository, versions, and documentation. It's already a separate module with no
   dependency on the classifier, so it can be extracted whenever it's proved itself here.

## Later

- **Skip Markdown tables.** No parser here recognizes GitHub-style tables, so a table is read as
  one paragraph and a table with `shall` in it gives misleading findings (seen on the jar and the
  native binary on 2026-11-07, and on Node with commonmark.js on 2026-11-08). Skip them on every platform, with a conformance fixture; or read
  each cell as text.
- **Coverage on Codecov, with a badge.** CI would upload each module's scoverage XML, in a job
  apart from those that build release files (the uploader is outside code), with a
  `continuous-integration` requirement. On 2026-11-07, Codecov's API knew the owner's account
  and listed `ears-checker`, never activated and with no coverage; check its current terms for
  public repositories (token or tokenless upload) when planning. Coverage minimums are already
  enforced per module in CI.
- **`--help` anywhere on the command line.** It works only as the first argument: on
  2026-11-07, `ears-check --output json --help` printed "Unexpected option: --help" and exited 2,
  where other programs accept it anywhere (the README documents it as the first argument). A
  fix in option parsing, with a test per position.
- **Lone surrogates in JSON output.** An OpenSpec requirement with an escaped lone surrogate
  (`"\ud83d"`) is written as `?` by the jar and the native binary, and as U+FFFD (`ef bf bd`) by
  Node (seen 2026-11-08 in the raw output bytes), so the platforms' JSON differs. Escape it as `\uXXXX` on every
  platform, with a case in `scripts/cli-cases`. OpenSpec's own output can't produce one.
- **Directories as input** (`ears-check docs/`), and **a CI snippet or GitHub Action** for
  checking requirements in other projects.
- **A size limit on each input**, for untrusted input (SECURITY.md counts it as in scope, with
  this limit known). Its memory grows with
  the input, mostly in the Markdown parser (commonmark-java took about 1 GB for a million
  nested emphasis markers), so a limit on nesting wouldn't help; a limit on bytes would.
- **Suppressing findings**, for example `<!-- ears-check: ignore -->` before a sentence, or a
  file of known exceptions, so a document can waive a finding without rewording.
- **A signed macOS binary**, signed and notarized so macOS opens it however it's downloaded;
  it needs a paid Apple developer account. And **more native platforms**: Windows, and macOS on
  Intel, which GitHub offers only on its `-intel` and larger runners.
- **Building the Linux binaries in a container** (such as `debian:bookworm`, glibc 2.36) once
  GitHub retires `ubuntu-22.04`, the runner that sets their oldest glibc today.
