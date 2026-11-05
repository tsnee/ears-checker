# Proposal

## Why

0.1.0's Scala.js library didn't link in an application that depended on nothing else: the
classifier referred to `java.util.Locale`, which Scala.js has only through scala-java-locales.
CI didn't notice, because the Scala.js tests link against the test classpath, and zio-test
brings that library in. The npm package's release link, added in `add-npm-package`, guards only
what `classify` reaches; `Requirement.render`, for one, is outside it, and its source was one of
the three that used `Locale.ROOT`. Nothing checks the library the way a user gets it.

## What Changes

- **A link check for the published Scala.js library**, which needs no hand-written program:
  1. Mill publishes `ears.js` to the local Ivy repository, exactly as a release would (jar and
     POM).
  2. A small generator, `ears.linkCheck`, reads the SemanticDB the build already produces for
     the library and writes a Scala program that references every public operation as a
     function value. The Scala.js linker checks the body of every method that's reachable, so
     referencing an operation is enough; nothing has to call it with real arguments.
  3. `scala-cli` links that program in release mode, with the published artifact as its only
     dependency, and runs it on Node.

  A new script, `scripts/check-scalajs-link`, runs the three steps locally and in CI.
- **Coverage by construction:** a new public operation is in the next generated program with no
  other edit, so no guard or hand-kept list is needed. An operation the generator can't
  reference yet (an overload, say) fails the check and names it.
- **Answers stay where they are:** `ears.js.test` already checks the library's results on
  Node. This change adds only what that misses: the user's classpath.
- **CI** runs the check in the `check` job, with `scala-cli` installed by its published setup
  action, and measures the generator's coverage like the other modules. Release runs get the
  check too, since `release.yml` runs the whole CI workflow.
- **Folded in, from the 0.1.1 release:** the README's install lines and its "on Maven Central"
  line move to 0.1.1; `docs/roadmap.md` loses its "Release 0.1.1" item, lists the planned order
  for Markdown on every platform, and gains a Later item: extract the generator as a Mill plugin
  once it has proved itself here.
- **Not in this change:** checking the artifact on Maven Central after a release (the owner's
  Publish click comes first, so it can't run unattended), and binary compatibility between
  versions (MiMa), a different problem.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `continuous-integration`: CI fails when a Scala.js program that depends only on the
  published library, and reaches every public operation, fails to link for release, or when the
  generator can't reference an operation (it names it).
- `test-coverage`: the generator, `ears.linkCheck`, is measured like the other modules.

## Impact

- **New files:** `scripts/check-scalajs-link`; the generator, `ears/linkCheck/src/…`, and its
  tests, `ears/linkCheck/test/src/…`. The generated program is written to a temporary
  directory, never to the source tree.
- **`build.mill`:** a JVM module `ears.linkCheck` (not published), with scalameta's
  `semanticdb-shared` and ZIO, and coverage minimums. A fixture module,
  `ears.linkCheck.fixture`, has one operation of every shape the generator references; the
  script compiles its program.
- **`.github/workflows/ci.yml`:** the `scala-cli` setup action, pinned by commit; a "Check the
  Scala.js library" step in `check`; and `ears.linkCheck` in the coverage report and check.
- **Docs:** `README.md` (0.1.1, and the new script under Building), `CLAUDE.md` (the script and
  `ears.linkCheck`), `docs/roadmap.md`, and `docs/worklog.md`.
- **Library, CLI, and npm package code:** no changes.
