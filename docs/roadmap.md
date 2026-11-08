# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order:

1. **Document the JSON output.** A `docs/json-output.md` reference (fields, an example per
   location type, what's stable), a short README section, requirements in the `ears-cli` spec,
   and a format version in the document (`"version": 1`) so consumers can detect changes.
2. **A README for users.** A quick start (`npx`, Coursier, Maven and sbt, npm), what it checks
   and its limits, the command line, the output formats, and the library; building and
   contributing move to `CONTRIBUTING.md`; a `SECURITY.md`; and a few badges (CI, Maven Central,
   license, and npm once published).
3. **Speed up large Markdown files on Node.** The Node CLI's time grows faster than its input
   does: one Markdown file of 0.7 MB takes 0.55 s, 1.4 MB 1.1 s, 2.8 MB 2.9 s, and 7 MB 12.6 s,
   where the JVM takes about 1 s for any of them. Probably in the Markdown facade's Node path
   (mdast, or the facade's line mapping); not yet investigated, so this starts with a timeboxed
   look at the cause.
4. **The CLI as a Coursier app**: an app description, with a channel file in this repository,
   so `cs install` sets up `ears-check`.
5. **Release 0.2.0**, after a security review of the whole repository (CLAUDE.md's Releasing
   steps). It's a minor release: inline code changes some of the classifier's verdicts.
6. **Publish the npm packages**: the library `@tsnee/ears-checker` and the command line
   `@tsnee/ears-checker-cli`. The owner (npm account `tsnee`, created 2026-11-07) publishes
   0.2.0 by hand, since npm wants a package to exist before a trusted publisher can be set up.
   Later versions are published from the release workflow with npm's trusted publishing, in
   their own job behind their own approval, after the Central Portal deployment is published.
   The plan is in the archived `add-npm-package` change's design (Decision 9).
7. **A general Scala.js link checker**, extracted from `ears.linkCheck`. It writes a program
   that references every public operation of a Scala.js library from its SemanticDB, and
   `scripts/check-scalajs-link` links it against only the published artifact; it caught
   0.1.0's `java.util.Locale` bug, which the tests hid (zio-test brings in scala-java-locales).
   Searches on 2026-11-07 found no existing tool that does this. To be general it needs: the
   operation shapes it now refuses by name (overloads, `using` parameters, varargs, givens,
   extension methods, bounded type parameters, constructors that take arguments) or a way to
   exclude one; a build integration others use (a Mill plugin, and probably an sbt plugin,
   since most Scala.js libraries build with sbt); a check on Scala 2 libraries' SemanticDB; and
   its own repository, versions, and documentation. It's already a separate module with no
   dependency on the classifier, so it can be extracted whenever it's proved itself here.

## Later

- **Directories as input** (`ears-check docs/`), and **a CI snippet or GitHub Action** for
  checking requirements in other projects.
- **A size limit on each input**, if the CLI ever checks untrusted input. Its memory grows with
  the input, mostly in the Markdown parser (commonmark-java took about 1 GB for a million
  nested emphasis markers), so a limit on nesting wouldn't help; a limit on bytes would.
- **Suppressing findings**, for example `<!-- ears-check: ignore -->` before a sentence, or a
  file of known exceptions, so a document can waive a finding without rewording.
- **A signed macOS binary**, signed and notarized so macOS opens it however it's downloaded;
  it needs a paid Apple developer account. And **more native platforms**: Windows, and macOS on
  Intel, which GitHub offers only on its `-intel` and larger runners.
- **Building the Linux binaries in a container** (such as `debian:bookworm`, glibc 2.36) once
  GitHub retires `ubuntu-22.04`, the runner that sets their oldest glibc today.
