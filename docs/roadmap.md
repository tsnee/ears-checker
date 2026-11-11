# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under `openspec/specs/` are what's built.

## Next

In this order, grouped by theme. Each theme is one branch and one pull request, merged when the theme is done.

### npm (branch `batch-npm`)

1. **Publish the npm packages**: the library `@tsnee/ears-checker` and the command line `@tsnee/ears-checker-cli`. The owner published 0.2.0 of both by hand on 2026-11-11, from the release's attested tarballs. From 0.3.0 on, the release workflow stages them with npm's trusted publishing and the owner approves them after checking them against the release (`publish-npm-packages`); the READMEs install from npm by the scoped names. Its follow-ups come at the 0.3.0 release: set up the trusted publishers within the two days before tagging; then check the shape of `npm stage list --json` against `scripts/approve-npm-release`, and that npm shows provenance for both packages (if not, switch to publishing directly behind an environment approval). Once it does, `docs/release-files.md` and the READMEs can mention npm's provenance.

### After npm

In this order (set 2026-11-11); their themes are chosen when they're planned.

2. **Skip Markdown tables.** No parser here recognizes GitHub-style tables, so a table is read as one paragraph and a table with `shall` in it gives misleading findings (seen on the jar and the native binary on 2026-11-07, and on Node with commonmark.js on 2026-11-08). Skip them on every platform, with a conformance fixture; or read each cell as text.
3. **`--help` anywhere on the command line.** It works only as the first argument: on 2026-11-07, `ears-check --output json --help` printed "Unexpected option: --help" and exited 2, where other programs accept it anywhere (the README documents it as the first argument). A fix in option parsing, with a test per position.
4. **Control characters and lone surrogates in JSON output.** zio-json escapes only the C0 control characters: C1 controls (such as the 8-bit CSI, U+009B), DEL, and U+2028/U+2029 from the input reach `--output json` raw (security review, confirmed with the jar, 2026-11-11). Few terminals act on a UTF-8 C1 control and JSON output is for tools, but escaping them costs a parser nothing (a `\u` escape and the raw character are the same JSON string) and doesn't change the document's version. And an OpenSpec requirement with an escaped lone surrogate (`"\ud83d"`) is written as `?` by the jar and the native binary, and as U+FFFD (`ef bf bd`) by Node (seen 2026-11-08 in the raw output bytes), so the platforms' JSON differs. Escape it as `\uXXXX` on every platform, with a case in `scripts/cli-cases`. OpenSpec's own output can't produce one.
5. **Bidirectional overrides in text output.** escape-text-output escapes control characters and line separators from the input, but not Unicode's bidirectional formatting characters (U+202A to U+202E, U+2066 to U+2069), which aren't control characters: a requirement or file name carrying them can make a finding display in a different order than it's written ("Trojan Source"). Few terminals act on them; editors and web views of saved output do. Escape them in text output and messages as well, with a test and a case in `scripts/cli-cases` (a non-goal of escape-text-output, 2026-11-11).
6. **A size limit on each input**, for untrusted input (SECURITY.md counts it as in scope, with this limit known). Its memory grows with the input, mostly in the Markdown parser (commonmark-java took about 1 GB for a million nested emphasis markers), so a limit on nesting wouldn't help; a limit on bytes would.
7. **Directories as input** (`ears-check docs/`), and **a CI snippet or GitHub Action** for checking requirements in other projects.
8. **A signed macOS binary**, signed and notarized so macOS opens it however it's downloaded; it needs a paid Apple developer account. And **more native platforms**: Windows, and macOS on Intel, which GitHub offers only on its `-intel` and larger runners.
9. **List the Coursier app in Coursier's own channel.** A pull request to `coursier/apps` adding `ears-check` to its contrib channel would make `cs install --contrib ears-check` work without this repository's URL; worth it once people use the app.

### A general Scala.js link checker

10. **A general Scala.js link checker**, extracted from `ears.linkCheck`. It writes a program that references every public operation of a Scala.js library from its SemanticDB, and `scripts/check-scalajs-link` links it against only the published artifact; it caught 0.1.0's `java.util.Locale` bug, which the tests hid (zio-test brings in scala-java-locales). Searches on 2026-11-07 found no existing tool that does this. To be general it needs: the operation shapes it now refuses by name (overloads, `using` parameters, varargs, givens, extension methods, bounded type parameters, constructors that take arguments) or a way to exclude one; a build integration others use (a Mill plugin, and an sbt plugin: GitHub's code search on 2026-11-08 found about 38,800 `build.sbt` files enabling `ScalaJSPlugin`, against about 300 Mill build files using `ScalaJSModule`); a check on Scala 2 libraries' SemanticDB; and its own repository, versions, and documentation. It's already a separate module with no dependency on the classifier, so it can be extracted whenever it's proved itself here.

## Later

- **Compile Scala Native in parallel again.** CI, CLAUDE.md, and CONTRIBUTING.md compile the Scala Native modules one at a time because Scala Native 0.5.12's compiler plugin caches its definitions globally with no locking (scala-native#4950). Once a Scala Native release fixes it, raise Scala Native and drop the extra step and the rule.
- **Coverage on Codecov, with a badge.** CI would upload each module's scoverage XML, in a job apart from those that build release files (the uploader is outside code), with a `continuous-integration` requirement. On 2026-11-07, Codecov's API knew the owner's account and listed `ears-checker`, never activated and with no coverage; check its current terms for public repositories (token or tokenless upload) when planning. Coverage minimums are already enforced per module in CI.
- **One predicate for which characters are escaped.** `Visible.escape` (text output and messages) and `Logging.escape` (log lines) each spell out the same predicate: ISO control characters, U+2028, and U+2029. Share it, so the two can't drift apart; no change in behavior (a suggestion from escape-text-output's verification, 2026-11-11). The bidirectional overrides (item 5) would then join both at once.
- **Suppressing findings**, for example `<!-- ears-check: ignore -->` before a sentence, or a file of known exceptions, so a document can waive a finding without rewording.
- **Building the Linux binaries in a container** (such as `debian:bookworm`, glibc 2.36) once GitHub retires `ubuntu-22.04`, the runner that sets their oldest glibc today.
