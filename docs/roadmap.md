# Roadmap

Planned changes, each its own OpenSpec change. Order and scope can change; the specs under
`openspec/specs/` are what's built.

## Next

- **Release 0.1.1.** The owner tags `v0.1.1` on `main` once `add-npm-package` is merged: 0.1.0's
  Scala.js artifact doesn't link without scala-java-locales. Then the README's install lines
  and its "0.1.0 is on Maven Central" line move to 0.1.1.
- **Publish the npm package.** Publish `@tsnee/ears-checker` from the release workflow with
  npm's trusted publishing, in its own job behind its own approval, after the Central Portal
  deployment is published. The plan is in the archived `add-npm-package` change's design
  (Decision 9).

## Later

- **The CLI as a Coursier app**, and **GraalVM native binaries** of it.
- **Scala Native**, once Markdown input no longer needs commonmark-java.
- **A smaller npm package.** Scala.js doesn't minify ES modules (only Closure minifies, and it
  isn't used for them), so `main.js` ships unminified at 1.57 MB, 243 kB gzipped. Revisit when
  Scala.js or Mill can minify an ES module, or weigh a separate minifier.
