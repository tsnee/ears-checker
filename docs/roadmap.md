# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order:

1. **A Markdown facade with one parser per platform.** A small interface for what the CLI
   reads from Markdown (paragraphs, list items included, with source spans; code skipped), with
   an implementation per platform: commonmark-java on the JVM, `mdast-util-from-markdown` on
   Node, and cmark (CommonMark's reference C implementation) on Scala Native. One conformance
   suite, the same fixtures and expected candidates, runs on every platform, so the parsers
   can't drift apart unseen.
2. **The CLI on Node** (`npx ears-check`), once Markdown works there: decline, ZIO, and zio-json
   already build for Scala.js; reading files needs a small Node `fs` binding.
3. **Scala Native**, with cmark.
4. **Publish the npm package**, with the CLI as well as the library. Publish
   `@tsnee/ears-checker` from the release workflow with npm's trusted publishing, in its own job
   behind its own approval, after the Central Portal deployment is published. The plan is in the
   archived `add-npm-package` change's design (Decision 9); npm wants the package to exist
   before a trusted publisher can be set up, so the owner publishes the first version by hand.

## Later

- **The CLI as a Coursier app**, and **GraalVM native binaries** of it.
- **A smaller npm package.** Scala.js doesn't minify ES modules (only Closure minifies, and it
  isn't used for them), so `main.js` ships unminified at 1.57 MB, 243 kB gzipped. Revisit when
  Scala.js or Mill can minify an ES module, or weigh a separate minifier.
- **The link-check generator as a Mill plugin.** `ears.linkCheck` writes a program that
  references every public operation of a Scala.js library from its SemanticDB, and
  `scripts/check-scalajs-link` links it against the published artifact. Any Scala.js library
  whose tests bring in libraries its users don't have could use it. Extract it once it has
  proved itself here.
