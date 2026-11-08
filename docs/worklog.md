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
- 2026-11-07: add-cli-on-native's CI run 42414891269 passed (check 739 s, check-jdk17 141 s;
  the native CLI check 154 s). The first run (42409556968) showed `libcmark0.30.0` doesn't exist
  on Ubuntu 26.04 (it's `libcmark0.30.2`), so the message now says `apt install cmark`. On
  Linux under `LC_ALL=C`, the native binary opens a non-ASCII file name.
- 2026-11-07: add-release-notes applied locally: CHANGELOG.md (0.1.0, 0.1.1, Unreleased), the
  ears.releaseNotes tool, `--out`/`--binary`/`--jar` on the check scripts, native.yml (oldest and
  newest runner per platform), release-* artifacts on tags, and release.yml's draft-release job.
  The macOS binary now declares `minos 15.0` (was the build machine's 27.2). Every local check
  passed; coverage (statement/branch %): ears 95.91/92.04, ears.cli 96.87/98.67, ears.npm
  100/100, ears.linkCheck 97.28/100, ears.markdown 100/100, ears.releaseNotes 96.60/100. A local
  rehearsal of draft-release's SHA256SUMS step on kept snapshot files passed `sha256sum -c` and
  failed as it should with a file missing or extra.
- 2026-11-07: the v0.1.0 and v0.1.1 GitHub Releases were backfilled (notes only; v0.1.1 latest).
  Review fixes: CI's npm tools come from `.github/tools/package-lock.json` with
  `npm ci --ignore-scripts`; native.yml fails unless the binary needs glibc 2.35 at most and
  declares `minos 15.0`, runs the Markdown conformance suite against Ubuntu 22.04's cmark 0.30,
  and keeps its artifacts 7 days; `check-native-cli --out` keeps the jar too; the docs give the
  full attestation check.
- 2026-11-07: add-release-notes' CI run 42428531488 passed on ebcb225, 1,636 job-seconds:
  check 440 s, check-jdk17 159 s; native builds linux-x64 238 s, linux-arm64 322 s,
  macos-arm64 273 s; rechecks 64 s, 69 s, 71 s. Both Linux binaries need glibc 2.34; the macOS
  binary declares `minos 15.0`. The conformance suite passed on Ubuntu 22.04's cmark 0.30.2
  (Ubuntu 26.04 ships 0.30.2 too; macos-15's Homebrew has 0.31.2). No release-* artifacts.
- 2026-11-07: document-json-output applied locally: `"version": 1` first in the JSON document,
  docs/json-output.md (every example compared with the jar's output), and whole-document tests.
  Every local check passed; coverage (statement/branch %): ears 95.91/92.04, ears.cli
  97.26/100 (was 96.87/98.67), ears.npm 100/100, ears.linkCheck 97.28/100, ears.markdown
  100/100, ears.releaseNotes 96.60/100. `--help` after another option fails with status 2; it's
  on the roadmap.
- 2026-11-07: document-json-output's review fixes, and a `"checker"` field (the release that
  wrote the document, from a generated `CheckerBuild.version`; `BuildInfo` clashed with
  zio-json's under `import zio.json.*` and gave `1.1.1`). Version 1 covers the document's shape;
  how text is derived and what spans cover can change within it (the owner's choice, as SARIF
  and ESLint do). Every local check passed; ears.cli coverage 97.08/100.
- 2026-11-07: document-json-output's CI run 42432940033 passed on 7ecd44b, 1,906 job-seconds:
  check 472 s, check-jdk17 154 s; native builds linux-x64 391 s, linux-arm64 323 s,
  macos-arm64 367 s; rechecks 62 s, 65 s, 72 s.
- 2026-11-07: readme-for-users applied: the README rewritten for users (quick start per route,
  limits, OpenSpec through its JSON), CONTRIBUTING.md and SECURITY.md (private vulnerability
  reporting, turned on by the owner), and package READMEs that describe the published packages
  with scoped names only. The review found that Markdown tables are read as paragraphs and that
  an empty or relative `LD_LIBRARY_PATH`/`DYLD_LIBRARY_PATH` entry lets the loader search the
  current directory: the docs say so, and the loader fix is on the roadmap before 0.2.0. Format,
  OpenSpec, and both npm checks passed.
- 2026-11-07: readme-for-users' CI run 42437105542 passed on 8e20e65, 1,864 job-seconds: check
  586 s, check-jdk17 147 s; native builds linux-x64 305 s, linux-arm64 306 s, macos-arm64 316 s;
  rechecks 70 s, 65 s, 69 s. The archive adds a `--help` bullet to both command-line READMEs and
  says `--type` belongs to `openspec show`.
- 2026-11-07: speed-up-node-markdown applied locally. The classifier rebuilt a 65,536-character
  set per inline code span (`Skeleton.scala`); building it once took the native binary from 88 s
  to 2.7 s and the JVM from 4.2 s to 1.5 s on 7 MB of Markdown. Node reads Markdown with
  commonmark.js instead of mdast (micromark grew faster than its input): 42.9 s to 1.9 s. Lines
  come from `SourceLines`; `check-npm-cli` caught escaped table rows placed early, fixed with
  escape-aware matching and breaks moving the search. `LineEndings` removed (owner approved).
  Runs in docs/performance.md. Every local check passed; coverage (statement/branch %): ears
  95.91/92.04, ears.cli 97.08/100, ears.npm 100/100, ears.linkCheck 97.28/100, ears.markdown
  100/100, ears.releaseNotes 96.60/100.
- 2026-11-07: speed-up-node-markdown's review fixes: `SourceLines` searches only to the end of
  the next line (an entity on each of 8,000 lines took 78 s on Node, now 1.07 s), joins only true
  sibling text (not across inline HTML), never moves past the paragraph's last line, and reads
  `\\` in text as an escape; two conformance fixtures added. `time-cli` validates JSON output,
  dates runs locally, and marks dirty trees. One full `./mill __.test` run exited 1 with no failed
  test (its log was deleted unread); five later runs passed.
- 2026-11-07: speed-up-node-markdown's CI run 42443487482 passed on 6d6c4cc, 1,696 job-seconds:
  check 470 s, check-jdk17 98 s; native builds linux-x64 341 s, linux-arm64 288 s, macos-arm64
  348 s; rechecks 59 s, 49 s, 43 s.
- 2026-11-08: load-cmark-safely applied locally. Probes: on macOS, `DYLD_LIBRARY_PATH=.` made the
  native binary run a library planted in the current directory, though it asked for an absolute
  path (empty entries and an empty value are ignored); on Linux (Ubuntu 22.04 and 24.04 in colima,
  run by the owner), the bare name followed empty and relative `LD_LIBRARY_PATH` entries, absolute
  paths never did. The binary now skips the bare name on Linux and declines cmark on macOS for
  such entries; CI plants a library to check. Every local check passed. Local coverage once read
  ears.cli 100% from 12 statements: Mill's incremental compile of the instrumented module left
  scoverage's statement list with only the recompiled file; after cleaning the instrumented
  builds (statement/branch %): ears 95.91/92.04, ears.cli 97.27/100, ears.npm 100/100,
  ears.linkCheck 97.28/100, ears.markdown 100/100, ears.releaseNotes 96.60/100.
- 2026-11-08: load-cmark-safely's reviews: glibc also splits `LD_LIBRARY_PATH` at `;` (confirmed
  by the owner's second colima probe: `/opt/lib;`, `/opt/lib;.`, `;/opt/lib`, and `;` loaded a
  planted library by bare name), now handled on Linux; the CI step gains a preload control, a
  check for versioned cmark files, a `readelf -d` check for RPATH/RUNPATH, and the semicolon
  cases; the docs claim only that cmark is never loaded from the current directory.
- 2026-11-08: load-cmark-safely's CI run 42482560093 passed on 1cf081e, 1,666 job-seconds: check
  491 s, check-jdk17 87 s; native builds linux-x64 353 s, linux-arm64 291 s, macos-arm64 263 s;
  rechecks 69 s, 54 s, 58 s. The planted-library step: the preload control ran the marker on all
  three; no unsafe value ran it; on Linux every unsafe value (semicolons included) loaded
  `/usr/lib/<arch>/libcmark.so` by absolute path; on macOS `.`, `lib`, and `/opt/homebrew/lib:.`
  were declined with status 2; neither Linux binary has RPATH or RUNPATH.
- 2026-11-08: measure-coverage-fully: an override making the instrumented compile
  non-incremental was tried (it counted every statement, about 1 s a module) and reviewed. By the
  review's reading of the compiler, Scala 3.10 merges each compile's statements into the previous
  list, so the 12-statement report needs that list to have been missing or damaged, and a missing
  list with unchanged sources still reports 100% of 0. Dropped for the owner's simpler rule:
  measure from a clean slate, `./mill clean __.scoverage` before `./mill __.test` (10 s to
  rebuild; ears.cli then counts 512), now in CLAUDE.md and CONTRIBUTING.md. The test-coverage
  spec now names `ears.releaseNotes` and starts its scenario with the clean step. The documented
  gate, clean step included, passed: ears 684 statements, 95.91% / 92.04%; ears.cli 512, 97.27% /
  100%; ears.npm 56, 100% / 100%; ears.linkCheck 294, 97.28% / 100%; ears.markdown 558, 100% /
  100%; ears.releaseNotes 235, 96.60% / 100%; all five scripts passed.
