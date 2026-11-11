# Proposal

## Why

Both npm packages are several times larger than npm users expect. Scala.js minifies only in a Scala-specific way, meant to be followed by a general-purpose JavaScript minifier, which the build doesn't run; and the CLI package ships a source map larger than its code, which a command-line tool's users don't debug into. Measured today, the CLI package is 1.72 MB to download and 9.4 MB installed, and the library's code is 1.57 MB. Nothing is published yet, so this is the moment to fix it (part of the `batch-node-cli` batch, before publishing).

## What Changes

- **Both packages' code is minified** with esbuild, a general-purpose JavaScript minifier, after Scala.js has linked it. Probes with esbuild found:
  - the library's `main.js` shrinks from 1.57 MB to 608 KB (gzipped, 239 KB to 159 KB), still exporting `classify` and returning the same results;
  - the CLI's from 5.08 MB to 1.85 MB (gzipped, 742 KB to 494 KB), with the same output.
- **The CLI package ships no source map.** Download about 0.5 MB, installed about 1.9 MB.
- **The library package keeps its source map,** carried through minification so a debugger still lands in the Scala sources, with this repository's sources embedded and libraries' sources named by their public URLs, as now.
- **Size limits** on each package's `main.js`, checked by the package checks, so an unminified build or a large new dependency can't slip through: 700 KB for the library, 2.1 MB for the CLI.
- **esbuild becomes a build tool:** pinned in a lockfile and installed by a Mill task with `npm ci`, as the Markdown parser is for the Node tests. The only JavaScript is a few lines in the library check, which ask Node's own source-map reader where `classify` maps.
- **Not in this change:** lighter dependencies, a smaller library source map, or publishing.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `npm-package`: the library's code is minified within a size limit, and its source map maps the minified code back to the Scala sources.
- `cli-npm-package`: the CLI package contains no source map, and its code is minified within a size limit.

## Impact

- **`build.mill`:**
  - a module that installs esbuild from a lockfile (`ears/npm/tools/`), sharing the install logic with the Markdown facade's `npmDependencies`;
  - a minify step in `ears.npm.npmPackage` (with a source map, then the existing embedding of this repository's sources) and in `ears.cli.js.npmPackage` (without one);
  - the CLI package's `files` drops `main.js.map`.
- **Scripts:** `check-npm-package` and `check-npm-cli` check the size limits and the maps.
- **CI:** no new steps. The package checks it already runs build the packages, which installs esbuild through Mill.
- **Docs:** README, CLAUDE.md, and the roadmap item comes off.
- **Behavior:** unchanged. `check-npm-cli` compares the minified CLI with the jar on every case, and `check-npm-package` runs the TypeScript consumer against the minified library.
