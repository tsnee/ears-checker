# Tasks

All work is on branch `batch-node-cli`, the second change in its batch; draft pull request #16
carries it, and its CI runs on every push. The owner pushes from their own shell; Claude reads
runs with `gh`. npm commands take `--cache` under a temporary directory or a task's own
directory, never `~/.npm`.

## 1. esbuild, installed by the build

- [x] 1.1 Create `ears/npm/tools/package.json` (private, `devDependencies` `esbuild` exactly `0.28.2`) and its lockfile with `npm install --package-lock-only --ignore-scripts`. Verify with `jq` that the lockfile pins `esbuild` 0.28.2 and lists `@esbuild/linux-x64` and `@esbuild/darwin-arm64`
- [x] 1.2 Add the `NpmInstalled` trait (design Decision 2) with `npm ci --ignore-scripts`, use it in `ears.markdown.js`, and add `ears.npm.tools`. Verify that `ears.markdown.js.test` still passes, and that the esbuild installed by `ears.npm.tools.npmDependencies` prints `0.28.2` for `--version` and minifies a file, without its install script

## 2. The library package (test-first)

- [x] 2.1 Add to `scripts/check-npm-package` the 700,000-byte limit on the packed `main.js` and the source-map lookup of `classify` (design Decision 5). Verify both fail on today's unminified package, the first naming the size and the limit
- [x] 2.2 Add the minify helper (design Decision 3) and use it, with a map, in `ears.npm.npmPackage`. Verify `scripts/check-npm-package` passes (size, lookup, publint, Are the Types Wrong, the TypeScript consumer), that the map still embeds every source of this repository it names and names libraries by `https://` URLs, and record `main.js`'s size and the packed and unpacked sizes. Update CLAUDE.md's npm package note

## 3. The CLI package (test-first)

- [x] 3.1 Add to `scripts/check-npm-cli` the checks for no `.map` file, no `sourceMappingURL` comment, and the 2,100,000-byte limit (design Decision 5). Verify each fails on today's package
- [x] 3.2 Minify `ears.cli.js.npmPackage`'s `main.js` without a map, drop `main.js.map` from it and from `files`, and remove the `library/` rewrite from `embedProjectSources` (design Decision 4). Verify `scripts/check-npm-cli` passes every case, that `ears.npm.npmPackage` still builds, and record the CLI package's packed and unpacked sizes. Update CLAUDE.md's CLI package note and the CLI package's README if it mentions the map

## 4. Checks, review, and docs

- [x] 4.1 Update `README.md` (Building: esbuild installed by Mill, with the network the first time) and `docs/roadmap.md` (the item comes off). Verify with `mq`
- [x] 4.2 Run `./mill __.checkFormat`, `./mill __.test`, every module's `validateCoverageMinimums`, `scripts/check-npm-package`, `scripts/check-npm-cli`, `scripts/check-scalajs-link`, and `scripts/check-openspec`. Verify all pass, report the coverage and both packages' sizes, and add a dated line to `docs/worklog.md`
- [x] 4.3 Run `/code-review` in a subagent on this change's commits, and let the owner choose which findings to fix
- [x] 4.4 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes on pull request #16

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `npm-package` and
  `cli-npm-package`) for the owner to push. The batch's pull request is merged at its ship
  point, not after this change.
