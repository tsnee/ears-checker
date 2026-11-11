# Draft comment for scala-native/scala-native#4950

https://github.com/scala-native/scala-native/issues/4950 ("Intermittent compile bug: tail of empty list", open since 2026-06-10). The comment below goes on it, instead of a new issue.

## Comment

We hit the same crash, and it looks like a race in the plugin rather than a classpath problem: on the same classpath, it fails only when Scala Native modules compile at the same time in one JVM, and never when they compile one at a time.

**Versions:** Scala Native 0.5.12, Scala 3.10.0, Mill 1.3.1 (Zinc); GitHub's `ubuntu-latest` and macOS on Apple silicon.

**What happened:** `./mill __.compile` compiled three Scala Native modules in parallel in Mill's JVM, and one failed with the trace in this issue:

```
unhandled exception while running scalanative-prepareInterop-postinline on .../SourceLinesSuite.scala
java.lang.UnsupportedOperationException: tail of empty list
  scala.collection.immutable.Nil$.tail(List.scala:666)
  dotty.tools.dotc.core.Types$Type.findMember(Types.scala:1035)
  dotty.tools.dotc.core.Types$Type.memberBasedOnFlags(Types.scala:802)
  dotty.tools.dotc.core.Types$Type.member(Types.scala:786)
  dotty.tools.dotc.core.Denotations$Denotation.requiredMethod(Denotations.scala:321)
  scala.scalanative.nscplugin.NirDefinitions.CFuncPtr_apply$$anonfun$1(NirDefinitions.scala:71)
  ...
  scala.scalanative.nscplugin.PostInlineNativeInterop.transformApply(PostInlineNativeInterop.scala:72)
```

The same sources compiled in the run before. The three compiles started at 19:49:08.10, 19:49:16.42, and 19:49:17.96, and the exception was logged at 19:49:30.99, while all three were compiling.

**Reproduction:** each round cleaned six Scala Native modules' compiles and compiled them together (macOS, Apple silicon, a warm Mill daemon). In parallel, 13 of 60 rounds failed: 12 with "tail of empty list" (through `CFuncPtr_apply` or `CFuncPtr_fromScalaFunction`, `NirDefinitions.scala:71` and `72`), and 1 with `object CFuncPtr1 does not have a member method fromScalaFunction`, all in `scalanative-prepareInterop-postinline`. The same rounds with Mill's `-j 1` failed in 0 of 60.

**Why it looks like a race:** `Types.scala:1035` (3.10.0) is `findMember`'s `finally`, which pops `ctx.base.pendingMemberSearches` when the count it read from `ctx.base.findMemberCount` was at least `Config.LogPendingFindMemberThreshold`. On one thread that pop always follows the matching push, so `Nil.tail` means another thread was using the same `ContextBase`.

`NirDefinitions.get` returns `object NirDefinitions`'s `ContextCached(NirDefinitions())`, and `ContextCached` (`NirGenUtil.scala`) is:

```scala
class ContextCached[T](init: Context ?=> T) {
  private var lastContext: Context = uninitialized
  private var cached: T = uninitialized

  def get(using Context): T = {
    if (lastContext != ctx) {
      cached = init
      lastContext = ctx
    }
    cached
  }
}
```

The object is global to the plugin's class loader, and `get` reads and writes the two fields with no locking. When a build tool runs two compilations in one JVM with the same plugin class loader (Mill evidently does, in parallel; we inferred this from the crash, not from Mill's code), compilation A can return the `NirDefinitions` that compilation B just built with B's context. A's lazy definitions (`CFuncPtr_apply` here) then run lookups on B's compiler state while B is using it. That would also explain why it's intermittent and why a rerun often passes. Two other global caches have the same shape: `object LazyValsDefns` (`AdaptLazyVals.scala`) and `EnumerationsContext` (`PrepNativeInterop.scala`).

**A possible fix:** make `ContextCached.get` synchronized, so the check, `init`, and both writes are atomic; that covers all three caches. Each caller then gets definitions built with its own context; under contention they're rebuilt more often, which is cheap compared with a crash. Keeping each cache per `ContextBase` (for example in a `ContextBase`-keyed weak map, or a `Property` on the root context) would avoid the rebuilds.

**Workaround:** compile the Scala Native modules one at a time, for example `./mill -j 1 '__:ScalaNativeModule.compile'` before the rest of the build.
