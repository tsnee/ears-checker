# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order:

1. **The CLI on Scala Native**, with the cmark parser `ears.markdown.native` already has; the
   binary links cmark, so its packaging has to say how users get it.
2. **Publish the npm packages**: the library `@tsnee/ears-checker` and the command line
   `@tsnee/ears-checker-cli`. Publish them from the release workflow with npm's trusted
   publishing, in their own job behind its own approval, after the Central Portal deployment is
   published. The plan is in the archived `add-npm-package` change's design (Decision 9). npm
   wants a package to exist before a trusted publisher can be set up, so the owner (npm account
   `tsnee`, created 2026-11-07) publishes the first version of each package by hand.

## Later

- **The CLI as a Coursier app**, and **GraalVM native binaries** of it.
- **Speed up large Markdown files on Node.** The Node CLI's time grows faster than its input
  does: one Markdown file of 0.7 MB takes 0.55 s, 1.4 MB 1.1 s, 2.8 MB 2.9 s, and 7 MB 12.6 s,
  where the JVM takes about 1 s for any of them. Probably in the Markdown facade's Node path
  (mdast, or the facade's line mapping); not yet investigated.
- **A size limit on each input**, if the CLI ever checks untrusted input. Its memory grows with
  the input, mostly in the Markdown parser (commonmark-java took about 1 GB for a million
  nested emphasis markers), so a limit on nesting wouldn't help; a limit on bytes would.
- **The link-check generator as a Mill plugin.** `ears.linkCheck` writes a program that
  references every public operation of a Scala.js library from its SemanticDB, and
  `scripts/check-scalajs-link` links it against the published artifact. Any Scala.js library
  whose tests bring in libraries its users don't have could use it. Extract it once it has
  proved itself here.
