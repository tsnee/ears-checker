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
