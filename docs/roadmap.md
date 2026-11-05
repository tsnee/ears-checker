# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

In this order:

1. **The CLI on Node** (`npx ears-check`): Markdown already reads the same there
   (`ears.markdown.js`), and decline, ZIO, and zio-json build for Scala.js; reading files needs a
   small Node `fs` binding, and the npm package gains `mdast-util-from-markdown` as a dependency.
2. **The CLI on Scala Native**, with the cmark parser `ears.markdown.native` already has; the
   binary links cmark, so its packaging has to say how users get it.
3. **Publish the npm package**, with the CLI as well as the library. Publish
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
