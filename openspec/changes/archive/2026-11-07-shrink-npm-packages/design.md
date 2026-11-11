# Design

## Context

See proposal.md for why. What's in place, and what probes found (2026-11-07, esbuild 0.27.2 installed locally; the change pins the current release, 0.28.2):

- **Scala.js already minifies, partly.** Mill's `scalaJSMinify` is `true` by default for `fullLinkJS`, and the linker's "Name compressor" runs, but the output stays pretty-printed (the CLI's `main.js` is 148,000 lines) with many long names. Scala.js's documentation says this minifier is meant to be followed by a general-purpose JavaScript minifier.
- **esbuild on the linked output:**

  |                   | before                           | after `--minify`   |
  | ----------------- | -------------------------------- | ------------------ |
  | library `main.js` | 1,569,511 bytes (239 KB gzipped) | 608,053 (159 KB)   |
  | CLI `main.js`     | 5,077,436 (742 KB)               | 1,846,490 (494 KB) |

  The minified library still exports `classify` and classifies correctly. The minified CLI gives the same finding and exit status on a probe.

- **Source maps through esbuild:** given `--sourcemap`, esbuild reads Scala.js's map (named by the `sourceMappingURL` comment) and maps the minified code to the original sources: libraries' `https://` URLs are kept, and this repository's sources are named relative to the output, for example `../in/ears/npm/js/src/…/Facade.scala` when the input was in `in/` and the output in `out/`. Node's built-in `SourceMap` finds the start of `classify`'s function in `Facade.scala`. esbuild writes no `sourcesContent` when the input map has none, and without `--sourcemap` it drops the input's `sourceMappingURL` comment.
- **The CLI package's sizes** (packed, unpacked): 1.72 MB and 9.4 MB today; without the map 0.75 MB and 5.1 MB; minified and without the map, about 0.5 MB and 1.9 MB.
- **esbuild on npm:** the `esbuild` package has an install script, and ships its executable as one optional package per platform. A lockfile made with `--package-lock-only` on macOS lists them all (`@esbuild/linux-x64`, `@esbuild/darwin-arm64`, and 24 more), so `npm ci` on CI's Linux installs its own.
- **The Markdown facade's Node tests** already install an npm package from a lockfile with a Mill task (`ears.markdown.js.npmDependencies`: `npm ci` with the cache in the task's directory).

## Goals / Non-Goals

**Goals:**

- Both packages minified, the CLI without a map, the library with a map that still leads to the Scala sources (specs: `npm-package`, `cli-npm-package`).
- Size limits that fail the package checks on a regression.

**Non-Goals:**

- Minifying anything but the published packages: the tests keep running unminified (`fastLinkJS`), which keeps their failures readable.
- Shrinking the library's map, or the dependencies themselves.

## Decisions

### 1. esbuild

- **Why esbuild:** a single native executable, fast, minifies ES modules as they are (no bundling needed), and reads input source maps, so the library's map survives.
- **Alternatives:**
  - terser: also reads input maps and minifies well, but it's a JavaScript program with its own dependencies, where esbuild is one pinned executable; not measured here.
  - Closure Compiler through Scala.js: the strongest, but Scala.js doesn't support it for ES modules, which both packages are.
- **Pinned** at 0.28.2 in `ears/npm/tools/package.json` and its lockfile.

### 2. Installing it: `ears.npm.tools`

- A Mill module with no Scala, `ears.npm.tools`, whose `npmDependencies` task installs `ears/npm/tools`'s lockfile with `npm ci`, as the Markdown facade's does.
- The install logic moves into a trait both modules share (`NpmInstalled`: the manifest sources and `npmDependencies`), so it's written once.
- `npm ci` runs with `--ignore-scripts`, for both: no downloaded code runs at install time. esbuild's install script only checks and links the platform executable, which its launcher also finds without it; apply confirms that before relying on it. `mdast-util-from-markdown` has no install scripts.
- The task's cache stays in its own directory, never `~/.npm`.

### 3. The minify step

A build helper beside `embedProjectSources`, used by both package tasks:

1. Copy the linked `main.js` (and, for the library, `main.js.map`) into `in/` under the task's directory.
2. Run `esbuild in/main.js --minify --format=esm --target=node22 --outfile=out/main.js`, with `--sourcemap` for the library only. `node22` is the oldest Node both packages declare.
3. Library only: in the new map, strip the `../in/` that esbuild put in front of this repository's sources, so they're relative to the repository root again, then embed their content with `embedProjectSources`, as now. Release builds already name them by tagged `https://` URLs, which esbuild leaves alone.

esbuild's errors and warnings fail the task, with its output and working directory in the message; a clean run prints nothing.

### 4. The CLI package without a map

- `ears.cli.js.npmPackage` copies only the minified `main.js`; `files` lists `main.js` and `bin/ears-check.js`.
- **The `library/` rewrite goes.** `add-cli-on-node` made `embedProjectSources` rename the CLI's libraries' build-machine `file:` URIs to `library/<package path>`. With no map in the CLI package, nothing uses it: the library's dependencies are all named by public URLs. The helper returns to failing on any `file:` URI, so a future dependency that brings one fails the build instead of shipping a machine path.
- A crash inside the CLI shows minified names in its stack trace. The CLI reports every expected failure as a message, so only a bug shows one; that's the usual trade for command-line packages.

### 5. Checks

- **`scripts/check-npm-package`** (library):
  - fails if the packed `main.js` is over 700,000 bytes;
  - looks up, with Node's built-in `SourceMap` (`node:module`), the start of the function exported as `classify`, and fails unless it maps into `Facade.scala`. It's a few lines of JavaScript run by `node -e`, the one place this change writes any: Node's own reader is the authority on what a debugger sees, and doing it in Scala would mean decoding the map's VLQ mappings by hand;
  - the existing checks (publint, Are the Types Wrong, the TypeScript consumer) now run on the minified code.
- **`scripts/check-npm-cli`:**
  - fails if any packed file ends in `.map`, if `main.js` has a `sourceMappingURL` comment, or if `main.js` is over 2,100,000 bytes;
  - its 16 cases compare the minified CLI with the jar, so a minification bug that changes behavior fails there.
- **The limits** are the measured sizes plus about 15%, so dependency updates have room and an unminified build (2.2 and 2.4 times the limits) can't pass. Raising a limit is a deliberate edit in the script, with the reason in its commit.

## Risks / Trade-offs

- **[esbuild changes behavior]** Minifiers rarely do, and Scala.js output is plain ES2015+. The CLI's 16 cases and the library's TypeScript consumer run on the minified code.
- **[esbuild without its install script]** If its launcher can't find the platform executable, apply finds out at the first build. The fallback is to allow scripts for that one install.
- **[CI needs the network for esbuild]** As it already does for the Markdown parser.
- **[Limits too tight after a dependency update]** The check names the size and the limit; the fix is a deliberate raise, or a look at what grew.
- **[Stack traces in the CLI]** Minified, as above.
