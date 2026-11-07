# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order:

1. **Inline code is opaque to the classifier.** Everything between backticks should count as one
   quoted word, whatever periods, commas, spaces, or modal keywords it holds. Today the
   classifier has no notion of code: `` `Cannot read notes.txt. Check the path.` `` ends a
   sentence inside the quote, `` `The calendar shall sync` `` counts as a second modal, and
   `` `Done.` `` passes only because a period before a backtick isn't punctuation. To settle: an
   unmatched backtick, double-backtick spans, fixes that would edit inside code, and whether
   the Markdown facade's sentences keep their backticks.

   In the same batch, two small encoding fixes: plain-text input drops a leading UTF-8
   byte-order mark, as Markdown input already does (today it stays at the start of the first
   line, and a `missing-article` fix keeps it); and UTF-16 input is reported as UTF-16, not as
   invalid UTF-8 at line 1, column 1.
2. **The CLI on Scala Native**, with the cmark parser `ears.markdown.native` already has; the
   binary links cmark, so its packaging has to say how users get it.
3. **Publish the npm packages**: the library `@tsnee/ears-checker` and the command line
   `@tsnee/ears-checker-cli`. Publish them from the release workflow with npm's trusted
   publishing, in their own job behind its own approval, after the Central Portal deployment is
   published. The plan is in the archived `add-npm-package` change's design (Decision 9). npm
   wants a package to exist before a trusted publisher can be set up, so the owner (npm account
   `tsnee`, created 2026-11-07) publishes the first version of each package by hand.

## Later

- **The CLI as a Coursier app**, and **GraalVM native binaries** of it.
- **A smaller npm package.** Scala.js doesn't minify ES modules (only Closure minifies, and it
  isn't used for them), so the library's `main.js` ships unminified at 1.57 MB, 243 kB gzipped,
  and the CLI's at 5.04 MB, 748 kB gzipped. Revisit when Scala.js or Mill can minify an ES
  module, or weigh a separate minifier.
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
