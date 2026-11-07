# Work log

- 2026-11-01: Extracted the EARS checker, with its history, from the repository it was built in,
  and removed what belonged to that repository from the files and the history.
- 2026-11-02: bootstrap-ears-checker: dropped the leftover module from the build, made the three
  modules publishable as 0.1.0-SNAPSHOT under CC0 1.0, rewrote the OpenSpec context, added the
  README, LICENSE, and this log, and published locally to `~/.ivy2/local`.
- 2026-11-02: restore-openspec-check-spec: restored the OpenSpec gate's six requirements from the
  archived changes, confirmed each against the script and hook in a scratch copy, and corrected
  one scenario whose "should" example strict validation rejects before EARS checking runs.
  Planned add-github-actions-ci.
- 2026-11-02: add-github-actions-ci: added the CI workflow (format, compile, test, and the OpenSpec
  check on Linux, with SHA-pinned published actions and a read-only token) and weekly grouped
  Dependabot updates for the pins; linted it with actionlint and checked each pin against its tag.
  Confirmed on GitHub: pull request and push runs, cache hits, and a format failure that stops
  the run; archived it and synced the continuous-integration spec.
- 2026-11-02: Reworded the files that named the repository this one was extracted from; the
  history is unchanged.
- 2026-11-02: Made the repository public, protected `main`, set up the `release` environment, and
  bumped the OpenSpec CLI to 1.16.0. Planned choose-jvm-flag-by-jdk.
- 2026-11-03: choose-jvm-flag-by-jdk: the OpenSpec check starts the checker with
  `-XX:+IgnoreUnrecognizedVMOptions` before the JDK 23 flag, so it runs on JDK 17 (confirmed
  failing before, passing after) and stays quiet on JDK 27; added the `check-jdk17` CI job and
  updated the README alias and CLAUDE.md.
- 2026-11-03: publish-to-maven-central (before release): version from the tag, Scala.js source
  references mapped to GitHub, the release workflow (verify, then an approval-gated upload for
  review), and the `v*` tag ruleset; inspected the 0.1.0 POMs and jars and rehearsed signing and
  bundling locally with a throwaway key.
- 2026-11-03: released 0.1.0 to Maven Central: a rehearsal upload dropped in the Portal, then the
  real upload published; checked from outside (fetch from Central with an empty cache, the CLI on
  JDK 17 and 27, a signature against the keyserver key, Scala.js source references at the tag);
  archived publish-to-maven-central and synced its `release-publishing` spec.
- 2026-11-03: the downstream project moved to 0.1.0 from Maven Central; dropped CLAUDE.md's
  `publishLocal` note and the README's "after 0.1.0 is published" wording.
- 2026-11-04: add-coverage-measurement (implementation): scoverage on `ears` and `ears.cli` with
  per-module minimums (95/90 and 96/95), `Main`'s console wiring behind a testable `cli` seam
  with tests for unreadable input, and CI's coverage summary and check; `ears.cli` went from
  92.98% / 88.00% to 96.80% / 95.92%.
- 2026-11-04: merged add-coverage-measurement (PR #10; the `check` job takes 2m2s, up from
  about 1m45s) and archived it, syncing `test-coverage`, `continuous-integration`, and `ears-cli`.
- 2026-11-04: add-cli-logging (implementation): `ears-check` logs to standard error through ZIO's
  logging with its own one-line format, warnings and errors by default and every level with
  `--verbose`; a pure `Logging` object chooses the level and formats entries.
- 2026-11-04: add-cli-logging reviewed (8 of 11 findings fixed; the logger is now built in
  `bootstrap` from the arguments and ZIO's Clock instead of through FiberRefs) and archived in
  the same PR (#12), syncing 8 requirements into `ears-cli`.
- 2026-11-05: add-npm-package implemented: `@tsnee/ears-checker` built by
  `ears.npm.npmPackage`, with a measured JVM core (`ears.npm`, 100%) and a thin Scala.js export
  (`ears.npm.js`), hand-written declarations, and `scripts/check-npm-package` (publint, attw,
  TypeScript under Node and bundler resolution, Node 22 and 26 in CI). Found and fixed a 0.1.0
  bug: the Scala.js library didn't link without scala-java-locales (`java.util.Locale`), hidden
  by zio-test's dependencies; v0.1.1 follows the merge. Snapshots are now 0.2.0-SNAPSHOT
  (npm 0.2.0-dev). The roadmap moved from CLAUDE.md to `docs/roadmap.md`.
- 2026-11-05: add-npm-package reviewed (all 8 findings fixed), passed CI on Node 22 and 26, and
  archived in the same PR (#13), creating the `npm-package` spec (26 requirements) and updating
  `continuous-integration`, `ears-classifier`, `release-publishing`, and `test-coverage`.
- 2026-11-05: released 0.1.1 (the Scala.js linking fix); `scala-cli` showed 0.1.0 failing to link
  on its own and 0.1.1 linking. add-scalajs-link-check implemented: `ears.linkCheck` generates a
  program referencing every public operation from the library's SemanticDB (7 today), and
  `scripts/check-scalajs-link` links it with `scala-cli` against the locally published artifact;
  a `Locale.ROOT` call in `Rendering` now fails CI. README moved to 0.1.1; roadmap reordered.
- 2026-11-05: add-scalajs-link-check reviewed (all 8 findings fixed, adding a fixture of every
  operation shape whose generated program the script compiles), passed CI, and archived in the
  same PR (#14), adding 3 requirements to `continuous-integration` and updating `test-coverage`.
- 2026-11-05: add-markdown-facade implemented: `ears/markdown` reads Markdown through one parser
  per platform (commonmark-java, mdast on Node, cmark on Scala Native) with shared extraction,
  and a 23-fixture conformance suite passes on all three. The CLI's output on this repository's
  88 Markdown files is byte-identical. Probes found the mdast and cmark differences (text runs
  across lines, code-span line endings, image alt text, inline HTML, CRLF, cmark needing
  `CMARK_OPT_SOURCEPOS`); `ZioSpecs` now adds to a test module's dependencies.
- 2026-11-05: add-markdown-facade reviewed (all 8 findings fixed: cmark's line errors, mdast's
  image references and decoded line feeds, the BOM, deep nesting; 38 fixtures), passed CI with
  cmark 0.30.2, and archived in the same PR (#15), creating `markdown-input` (5 requirements)
  and updating `continuous-integration` and `test-coverage`.
- 2026-11-07: add-cli-on-node implemented: the CLI's program is shared (`Cli` behind `Reading`),
  with `Main` on the JVM and `NodeMain` on Node; `@tsnee/ears-checker-cli` builds (1.72 MB
  packed, `main.js` 5.04 MB); `scripts/check-npm-cli` finds the two builds identical on 16 cases
  and runs in CI on Node 26 and 22. Review led to: no interpretation of platform read errors
  (`Cannot read <input>`, the reason in the log), strict UTF-8 input with the bad byte's line
  and column, UTF-8 output on the JVM whatever the locale, and empty file names rejected; the
  library's source-map helper now renames dependencies' build-machine `file:` URIs to
  `library/<package path>`, so no package carries a machine path. Node's slowness on large
  Markdown files went on the roadmap.
- 2026-11-07: shrink-npm-packages implemented (second change in batch-node-cli): esbuild 0.28.2,
  pinned in `ears/npm/tools` and installed by `ears.npm.tools` (`npm ci --ignore-scripts`, now
  shared with the Markdown facade through `NpmInstalled`), minifies both packages. The library's
  `main.js` went from 1.57 MB to 608 KB (packed 584 KB to 501 KB) with its source map carried
  through; the CLI's from 5.08 MB to 1.85 MB, without a map (packed 1.72 MB to 500 KB, installed
  9.4 MB to 1.86 MB). The package checks enforce size limits and the map; the `library/`
  source-path rewrite went, unused without the CLI's map.
- 2026-11-07: opaque-inline-code implemented (third change in batch-node-cli): the classifier
  reads a code span (CommonMark's rule; `` \` `` and `` \\ `` are escapes) as one word; the
  Markdown reader returns code spans with their backticks, escapes other backticks (doubling a
  backslash before one), joins adjacent code, and doesn't end sentences inside code; a leading
  UTF-8 byte-order mark is ignored and UTF-16/UTF-32 input named. The three parsers agreed on
  nine backtick edge cases, now conformance fixtures, and a round-trip property ties the reader
  to the classifier. The planned token-based article fix proved unnecessary; review found the
  backslash-before-code bug.
- 2026-11-07: add-cli-on-native implemented (first change in batch-release-0.2.0): the CLI and
  the classifier build with Scala Native (`ears.native`, `ears.cli.native`, reusing `src-jvm`);
  the release binary is 26.7 MB, links in about 23 s, and checks the README in 0.05 s. The
  native Markdown reader loads cmark (0.30 or later) when it runs instead of linking it, since
  cmark names its library by full release; `pkg-config` is gone from the build.
  `scripts/check-native-cli` compares the binary with the jar on the cases now shared in
  `scripts/cli-cases`. The roadmap was reordered (GraalVM dropped). A stale Mill daemon ran out
  of class space and had to be killed by hand (`kill -9`, outside the sandbox).
- 2026-11-07: add-cli-on-native reviewed. The security review found that on macOS a bare
  `dlopen("libcmark.dylib")` also searches the current directory, so a repository could plant a
  library that runs as the user; now every candidate is an absolute path (apart from Linux's
  bare `libcmark.so`, which glibc never resolves from there), `$HOMEBREW_PREFIX/lib` is
  searched, and a planted file is ignored. The code review found the missing-cmark CI step
  couldn't fail (`-e` skips tests inside `&&`) and expected a finding from a file with none; also
  fixed: each candidate is tried until one binds, and the log names the parser or every library
  tried. Coverage: ears 95.91/92.04, ears.cli 96.87/98.67, ears.npm 100/100, ears.linkCheck
  97.28/100, ears.markdown 100/100 (statement/branch %).
