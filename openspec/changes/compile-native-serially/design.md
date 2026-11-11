# Design

## Context

Observations (2026-11-08; Mill 1.3.1, Scala 3.10.0, Scala Native 0.5.12):

- **The failure:** CI run 42516759834, job `check`, step Compile (`./mill __.compile`), on
  `f99ef39`, whose only change from the passing `d30d66d` (run 42515466052) is under `openspec/`.
  `ears.markdown.native.test.compile` failed with `java.lang.UnsupportedOperationException: tail
  of empty list` during phase `scalanative-prepareInterop-postinline` on `SourceLinesSuite.scala`.
  The trace runs `NirDefinitions.CFuncPtr_apply` (`NirDefinitions.scala:71`) →
  `Denotation.requiredMethod` → `Type.member` → `Type.findMember` → `Nil.tail` at
  `Types.scala:1035`. The other seven jobs passed.
- **`Types.scala:1035`** (Scala 3.10.0) is the `finally` of `findMember`: when the entry count
  read from `ctx.base.findMemberCount` was at least `Config.LogPendingFindMemberThreshold`, it
  pops `ctx.base.pendingMemberSearches`, which the same call pushed. On one thread the push and
  pop pair up, so an empty list there means another thread changed the same `ContextBase`.
- **The plugin's cache** (`nscplugin/src/main/scala-3/.../NirGenUtil.scala`, unchanged from
  v0.5.12 to `main`): `class ContextCached[T]` holds `private var lastContext` and `private var
  cached`, and `get` replaces both when called with a different context, with no locking.
  `NirDefinitions.get` reads a global `object NirDefinitions`'s `ContextCached(NirDefinitions())`.
  `NirDefinitions` evaluates its `@tu lazy val`s with the context it was built with. So a
  compilation racing another can be handed the other's definitions and run lookups on the other's
  compiler state. An object is one per class loader; the crash implies Mill's compiles shared the
  plugin's class loader (not read from Mill's code).
- **Overlap in the failed job** (its log): `ears.markdown.native.test.compile` started at
  19:49:08.10, `ears.native.test.compile` at 16.42, `ears.cli.native.compile` at 17.96; the
  exception was logged at 19:49:30.99, while all three were compiling, in Mill's JVM.
- **Upstream:** no scala-native issue or pull request mentions `ContextCached` or a concurrent
  `NirDefinitions` (GitHub search, 2026-11-08); v0.5.12 is the latest release. But
  scala-native#4950 ("Intermittent compile bug: tail of empty list", open since 2026-06-10,
  Scala Native 0.5.12 on Scala 3.8.3 and 3.8.4, 30–40% of that reporter's CI builds) has the same
  trace through `NirDefinitions.scala:71`; its one reply, from a maintainer, suspects the Scala
  Native runtime library missing from the plugin's classpath (found 2026-11-11).
- **Mill selectors:** `./mill resolve '__:ScalaNativeModule.compile'` gives the six native
  modules' compiles (`ears.native`, `ears.native.test`, `ears.markdown.native`,
  `ears.markdown.native.test`, `ears.cli.native`, `ears.cli.native.test`); `'__:^ScalaNativeModule.compile'`
  gives the other 27.
- **Other places native code compiles:** `native.yml` builds `ears.cli.native.nativeLink`, whose
  only native dependency is `ears.native` (`moduleDeps`), so those two compile in order, then
  `ears.markdown.native` and its test, in order. `release.yml`'s `__.publishArtifacts` covers no native
  module. So `ci.yml`'s Compile step and the local check list are what run native compiles side by
  side.
- **Reproduced** (task 1.1, same day, f99ef39, Apple M4 Max, warm Mill daemon): rounds of
  `./mill clean '__:ScalaNativeModule.compile'` then `./mill '__:ScalaNativeModule.compile'`
  failed in 13 of 60 rounds in parallel: 12 with "tail of empty list" (through
  `CFuncPtr_apply` or `CFuncPtr_fromScalaFunction`), 1 with `object CFuncPtr1 does not have a
  member method fromScalaFunction`, all in `scalanative-prepareInterop-postinline`. With `-j 1`,
  0 of 60 failed. Serial took 3 s (median, warm) against 2 s in parallel (`docs/performance.md`).

## Goals / Non-Goals

**Goals:**
- No CI run, and no run of the documented checks, compiles two Scala Native modules at once.
- Upstream hears about it with enough to fix it.

**Non-Goals:**
- Making every Mill invocation safe: a developer's own `./mill __.test` after a change to native
  sources can still compile them side by side. The build-level alternative is below.
- Patching the plugin locally.

## Decisions

### 1. Two compile steps in CI

- `./mill '__:^ScalaNativeModule.compile'`, then `./mill -j 1 '__:ScalaNativeModule.compile'`.
  The second finds everything non-native compiled, so `-j 1` serializes only the native compiles.
  The Test step, `./mill __.test`, then has nothing to compile. A comment on the step names the
  upstream issue.
- **Alternatives:** `-j 1` for the whole Compile step serializes 27 other compiles for no reason.
  Ordering the native modules in `build.mill` (making each one's sources wait on the previous
  one's compile) would protect every invocation, but it is obscure, adds compiles to
  `nativeLink`, and has to be undone later; the owner chose the step (2026-11-08).

### 2. The documented checks

- CLAUDE.md's and CONTRIBUTING.md's lists run `./mill -j 1 '__:ScalaNativeModule.compile'`
  before `./mill __.test`, with the reason and the issue's link; CLAUDE.md's Build and test notes
  say why. It is a lasting rule until the fix ships, and the roadmap item removes it.

### 3. The upstream report

- A comment on scala-native#4950, not a new issue, drafted in `upstream-issue.md`: why it isn't
  the classpath (the same classpath fails only in parallel), the symptom, the trace, the two
  source lines, why it is a race, the evidence from the log and the local rounds, the other two
  caches, and the fix (synchronize `ContextCached.get`, or keep each cache per `ContextBase`).
  The owner posts it, or approves Claude posting it with `gh issue comment`. The issue's URL goes
  into the CI comment and the docs (owner's choice, 2026-11-11). The owner posted an edited
  version on 2026-11-11:
  https://github.com/scala-native/scala-native/issues/4950#issuecomment-6794284594.

### 4. Cost

- Measured locally (task 1.2): the native compiles after `./mill clean` of the six modules, in
  parallel and with `-j 1`, recorded in `docs/performance.md` with the date and machine. CI's
  cost is read from the first run's Compile steps.

## Risks / Trade-offs

- **[Local runs]** Outside the documented checks, a developer can still hit it; rerunning
  `./mill -j 1 '__:ScalaNativeModule.compile'` clears it. CLAUDE.md says so.
- **[A stale workaround]** The roadmap item and the comment's issue link mark when to drop it.

## Open Questions

- None.
