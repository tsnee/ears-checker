# Design

## Context

Today each platform has its own `MarkdownParser` (`ears/markdown/src-jvm`, `js/src`, `native/src`) that turns a document into `ParsedParagraph`s; `Tables` (shared) finds GitHub's tables in their raw lines with `Rows`, re-parses cells through the same parser, and corrects setext headings; `Extraction` (shared) finds the sentences. `CmarkLines` corrects cmark's inline lines, and `SourceLines` gives commonmark.js's inline pieces their lines. The native parser loads an installed cmark (`CmarkLibrary`, `CmarkLoader`); the CLI logs the parser's name, or the libraries it tried (`Logging.parserDetails`). See the proposal for why this goes.

Observations (all 2026-11-13, on the owner's Mac, Apple M4 Max, unless noted):

- **cmark-gfm matches GitHub**: 144 of 144 conformance fixtures, reduced to their blocks and text, against GitHub's Markdown API in `markdown` mode (`docs/measurements/table-parser-probe.jsonl`, query beside it). commonmark-java 0.31.0 with `commonmark-ext-gfm-tables` 0.31.0: 127; micromark 4.0.3 with `micromark-extension-gfm-table` 2.1.2: 139.
- **cmark-gfm's source**: release 0.29.0.gfm.13 (tag commit `587a12bb54d95ac37241377e6ddc93ea0e45439b`, 2023-07-21) is 2 commits behind `master` (`gh api repos/github/cmark-gfm/compare/0.29.0.gfm.13...master`): a CMake minimum and an autolink fix, and the autolink extension isn't used. `src` and `extensions` hold 64 C files and headers, 896,716 bytes, with no file name in both. License: BSD-2-Clause for the code, with further permissive notices in `COPYING` (houdini, buffer, utf8, entities, and others).
- **Building without CMake**: CMake generates three headers (`config.h` from `config.h.in`, `cmark-gfm_version.h`, `cmark-gfm_export.h`); the spike wrote them with `sed` and `printf`, and everything compiled with wasi-sdk 34's clang (`--target=wasm32-wasip1 -mexec-model=reactor`).
- **The WebAssembly module** imports only `clock_time_get`, `fd_close`, `fd_seek`, and `fd_write` from WASI; stubs returning ENOSYS were enough for every fixture and every stress input below.
- **Identical output**: HTML and XML-with-positions output of the module equals native cmark-gfm 0.29.0.gfm.13 (Homebrew) on all 144 fixtures, on Node 25.2.1 and on Chicory 1.7.5 (interpreter, run-time compiler, build-time compiler), for every clang setting tried (`docs/measurements/cmark-gfm-wasm-spike.jsonl`).
- **Speed** (same file; 1.9 MB of this repository's Markdown, warm median): Node 15.9 ms (`-O2`), 17.8 ms (`-O2 -fno-inline-functions`); commonmark-java 0.31.0 35.1 ms; Chicory build-time compiled, `-O2`: 298 ms; `-O2 -fno-inline-functions`: 83 ms, the same with or without `-XX:-DontCompileHugeMethods`. Loading the build-time compiled module: about 120 ms; Node: under 2 ms.
- **HotSpot's limit**: methods over 8,000 bytes of bytecode aren't JIT-compiled (`DontCompileHugeMethods`). With `-O2`, five generated methods exceed it, among them `S_process_line` and `cmark_parse_inlines` (the hot path); with `-fno-inline-functions`, three: `_scan_autolink_email` (14,068 bytes; autolink extension only), `printf_core` (10,556), `_scan_html_block_start` (8,862; lines starting with `<`). Chicory's compiler leaves one function to its interpreter, `cmark_utf8proc_case_fold` (link labels only).
- **clang runs `wasm-opt` from `PATH`**: with binaryen installed, wasi-sdk's clang ran it after linking, which changed the module and stripped its names. With `PATH=/usr/bin:/bin` for clang, it didn't.
- **Stress** (Node, `-O2 -fno-inline-functions`): 10,000 nested block quotes 11.5 ms; 5,000 levels of emphasis 8.4 ms; 100,000 nested list items 1.6 ms; the security review's three inputs 6.2, 2.0 (with links), 6.5, and 15.6 ms; an 80,000-row table 178 ms. None failed.
- **Scala Native compiles C from resources**: in a scratch Mill 1.3.1 project with Scala Native 0.5.12, a `.c` file under `resources/scala-native/` and one written by a task into a directory given as `compileResources` were both compiled and linked into the binary, which called them through `@extern`.
- **cmark-gfm's table positions** (`cmark-gfm --sourcepos -e table -t xml`): body rows and their cells have correct lines, and columns that start after block-quote and list markers (`table_row sourcepos="4:5-4:59"` inside `> - `). The header row and its cells are given the line of the paragraph the table interrupts (line 1 for a header on line 2) with columns that continue from it; that paragraph and its inline text have no positions at all; the table's own start is that paragraph's start, and its end is right. A table's extra cells are dropped from the tree, as GitHub drops them.
- **cmark-gfm's other position bugs** are cmark's: commonmark/cmark#660 (a paragraph after a link definition) reproduces on cmark-gfm 0.29.0.gfm.13, and its inline positions come from the same code as cmark's (#298).
- **Sizes**: the CLI npm package's `main.js` is 1,903,929 bytes of its 2,100,000 limit; the module is 228,431 bytes (`-O2 -fno-inline-functions`, stripped by `wasm-opt --strip-debug --strip-producers`); Chicory's runtime and wasm jars are about 300 KB; the generated classes 456 KB.
- **wasi-sdk 34** (2026-08-25) publishes archives with SHA-256 digests for x86_64 and arm64 Linux, macOS, and Windows, 180 to 620 MB each.
- **The Unsafe warning** the JVM flag silences comes from cats, cats-parse, and decline (compiled with Scala 3.3), not from this change; the module needs no new JVM flag.

## Goals / Non-Goals

**Goals:**

- One parser, cmark-gfm 0.29.0.gfm.13 with its table extension, on all three platforms, built from one copy of its source.
- One shared reader of its output, so position corrections, table warnings, and extraction are written once and measured on the JVM.
- No extra JVM flag, no installed library, no npm dependency.

**Non-Goals:**

- Other GitHub extensions (strikethrough, autolinks, task lists, tag filter): GitHub uses them, but none changes which sentences are read, apart from autolinks, whose text is the URL either way. Adding one later is a one-line change in the C entry point.
- Reading headings, or anything else `markdown-input` doesn't read today.
- A size limit on input (roadmap item 8). cmark-gfm is linear on everything tried, but memory still grows with input.
- Third-party notices for the JVM dependencies already in the jar (ZIO, cats, decline): a gap that predates this change; noted for the roadmap.

## Decisions

### 1. A C entry point that writes the tree out, read by shared Scala

The C entry point (`ears_cmark.c`, about 130 lines with its comments) parses with the table extension and walks the tree in a loop, writing an enter record for each node (its type, its four position numbers, and its literal, length-prefixed so nothing needs escaping) and an exit record when it leaves it. cmark's own iterator gives leaf nodes no exit event, by a list of types internal to cmark; the walk gives every node both, so the records nest as the tree does and the decoder needs no list of cmark's types. cmark-gfm registers its extensions in global lists without locking, so the entry point registers them once under `pthread_once` (in wasi-libc too) and is safe to call from any thread: with first calls on 16 threads at once, unguarded registration aborted 98 of 100 runs, natively (found in task 2.2, 2026-11-14). Every platform calls the same function and gets the same bytes; a shared, pure decoder (`CmarkTree`) turns them into a tree, and a shared reader turns the tree into paragraphs, cells, and warnings.

- _Alternative: call cmark's node API across the boundary_ (`cmark_node_first_child` and so on): one call per node per field, written three times, each slow across WebAssembly.
- _Alternative: parse cmark's own XML renderer output_: no C of ours, but an XML parser in shared Scala for a format that escapes text; the length-prefixed records are simpler to read totally.
- The loop keeps the C walk flat at any nesting depth; the decoder builds the tree with an explicit stack, so neither recurses per level.

### 2. Native: the C source compiled into the binary

A Mill task copies the vendored source, the three generated headers, and the entry point into one directory under `scala-native/`, given to the native module as `compileResources`; `@extern` declares the entry point. The binary links nothing new, and `CmarkLibrary`, the loader, the library path rules, and their CI check go.

- _Alternative: load an installed cmark-gfm, as cmark is loaded today_: users would install it (Homebrew has it; Debian's package was not checked), and the library-path safeguards would stay.

### 3. JVM and Node: one WebAssembly module, built from source with a pinned wasi-sdk

A Mill task downloads the wasi-sdk 34 archive for the build host, checks its SHA-256 against the value recorded in `build.mill`, extracts it, and runs its clang with `-O2 -fno-inline-functions -DNDEBUG` and `PATH` set to the toolchain's own `bin`, so no `wasm-opt` on the host's path runs. `-DNDEBUG` compiles cmark-gfm's assertions out, as its CMake release build does; they would otherwise put the build's file paths into the module (the spike's held 13), so its checksum would depend on where the repository is checked out. The native build defines `NDEBUG` for cmark-gfm's files too (decision 2), so both platforms run the same code and the binary carries no paths, as 0.2.0's carries none. It links with function names (kept for the method check and its messages), then strips them with the archive's `llvm-objcopy` into the module that ships, whose SHA-256 the build compares with the one recorded in `build.mill`.

- _Alternative: commit the built module_: no toolchain in the build, but a 228 KB binary in the repository that its source can't be checked against unless something rebuilds it anyway.
- _Alternative: Homebrew's `llvm`, `lld`, `wasi-libc`, `wasi-runtimes`_: versions move with each upgrade, so modules would differ between machines.
- _Alternative: `-O2` with `-XX:-DontCompileHugeMethods`_: as fast, but one more flag in every launcher; `-fno-inline-functions` costs Node about 2 ms on 1.9 MB.

### 4. JVM: Chicory's build-time compiler, its classes shipped as resources

A Mill task runs Chicory's build-time compiler (`com.dylibso.chicory:build-time-compiler:1.7.5`, on the build's own classpath through the `build.mill` header) on the module, producing the machine classes and the module's `.meta`. They're given to `ears.markdown` and `ears.cli` as resources, so they're in the jar, the assembly, and every test classpath, and as compile resources, so the JVM code compiles against them; it instantiates the generated machine directly (no generated Java to compile), with Chicory's `runtime` (Apache-2.0) as a dependency. The same task checks every generated method's size with ASM (a dependency of the compiler) against the allowed list: `_scan_autolink_email`, `printf_core`, `_scan_html_block_start`.

- _Alternative: Chicory's run-time compiler_: 405 ms to load against 120 ms, and the same speed afterwards.
- _Alternative: the interpreter_: 2.9 s on 1.9 MB.

### 5. Node: the module beside `main.js`, through a three-line JavaScript module

`ears/markdown/js/cmark-gfm-wasm.mjs` exports the module's bytes, read with `readFileSync(new URL("./cmark-gfm.wasm", import.meta.url))`; JavaScript because it's three lines that need `import.meta.url` beside the file, with a comment saying so. Scala.js imports it by a bare name; Mill's import map points the tests at a task directory holding both files (as `commonmark` was pointed at), and the CLI's link at `./cmark-gfm-wasm.mjs`, which the package holds beside `main.js` and `cmark-gfm.wasm`. (The plan had esbuild bundle it into `main.js`, but esbuild only minifies `main.js` and doesn't bundle its imports, which is how `commonmark` was left to `node_modules`; found in task 3.3, 2026-11-14.) Node's own `WebAssembly` runs it.

- _Alternative: embed the module in `main.js` as base64_: about 305 KB more, past the 2,100,000-byte limit.

### 6. Positions: cmark's corrections kept, cmark-gfm's table corrections added

The shared reader keeps `CmarkLines` for inline lines (backward from a paragraph's end line). For tables, from the observations above: the delimiter row is the line before the first body row (or the table's end line when there are none), the header row is the line before that, and a paragraph the table interrupted, which has no positions, ends on the line before the header and starts on the table's reported start line. Each correction gets a conformance fixture, and the fixtures run on all three platforms through the same corrections.

### 7. Extra cells: counted from the row's source

cmark-gfm drops a row's extra cells, so the warning counts them from the source: the row's line up to its end column (bytes, so the line is sliced as UTF-8), without the indentation and block quote markers before it, split at each pipe not preceded by a backslash. (Not from its start column: cmark-gfm gives every body row the table's start column, whatever the row's own prefix, found in review, 2026-11-14.), as GitHub splits (the "pipes in table cells" fixtures). This is the only part of `Rows` that stays.

### 8. The parser's name

The entry point also returns `cmark_version_string()`, so the log names `cmark-gfm 0.29.0.gfm.13` on every platform; `MarkdownUnavailable` and the `attempts` detail go.

### 9. cmark-gfm's source vendored, unedited

The release's `src/`, `extensions/`, and `COPYING` are copied into `ears/markdown/cmark-gfm/` by `scripts/update-cmark-gfm --tag <tag>`, which also writes the directory's README (tag, commit, archive checksum, license, never edited here). `.gitattributes` marks the directory `linguist-vendored`, so GitHub's language statistics count only this repository's code. The owner chose this on 2026-11-13; it's how Python's `cmarkgfm` (`third_party/cmark`) and Ruby's `commonmarker` (v0.23.10, `ext/commonmarker`) include the same library.

- An update is one command and a reviewable diff of what changed upstream; builds need no network for the source and don't depend on GitHub's generated archives staying byte-stable (they changed checksums briefly in January 2023).
- Position bugs stay fixed in the shared reader, not patched in the copy, so the parser stays the code GitHub runs.
- _Alternative: download the tag's archive at build time, checked against a recorded checksum_: the repository stays all CC0 and Scala, but an update is a changed checksum with its content unseen, and every clean build fetches from GitHub.
- _Alternative: a git submodule_: pinned by commit, but every clone and CI checkout needs `--recurse-submodules`, and `~/.gitconfig`'s rewrite to SSH reaches `~/.ssh`, which the sandbox denies.
- Neither way gets security alerts (cmark-gfm is in no registry Dependabot watches), so watching its releases and advisories is a roadmap item either way (task 4.3).

## Risks / Trade-offs

- [The JVM takes about 120 ms more to start reading Markdown] → `scripts/time-cli` measures the CLI before and after on the same inputs, recorded in `docs/measurements/time-cli.jsonl`; if it's worse than the owner accepts, Chicory's class loading is the place to look (fewer, larger generated classes).
- [cmark-gfm's positions around tables are wrong in more ways than observed] → every fixture with a table runs through the corrections on all three platforms; the GitHub probe's 144 fixtures are the starting set, and new shapes get fixtures before code.
- [Fixtures' expected sentences change where CommonMark 0.29 (cmark-gfm) differs from 0.31 (commonmark-java, cmark) outside tables] → the probe found cmark-gfm matching GitHub on all 144, so a changed sentence is a move towards GitHub; each change to an existing test is shown to the owner first, with the fixture's GitHub rendering.
- [C code of ours can't be measured by scoverage] → it's small, has no branches on input beyond the walk, and every fixture runs through it on three platforms.
- [The wasi-sdk download is 180 to 200 MB per host] → CI caches it by archive name and checksum; developers download it once per clean.
- [cmark-gfm's last release is from 2023] → it's what GitHub runs; the copy is pinned and the probe can be rerun against GitHub when updating it.
- [Memory safety of C on Scala Native] → the same exposure as today's cmark, from a fork GitHub runs on untrusted input at scale; on the JVM and Node it runs inside WebAssembly.

## Migration Plan

Nothing is released between this change and 0.3.0: PR #25 merges with this change in it. Rolling back is reverting the branch's commits. The changelog's Unreleased section gains the user-visible lines (the native binary needs no cmark; a definition above a table reads as on GitHub; the npm CLI has no dependencies).

## Open Questions

- Whether CI's arm64 and macOS runners produce the recorded module checksum as the owner's Mac does; the build's checksum check answers it on the first CI run, and a mismatch would mean pinning more of the build environment. (Answered 2026-11-14 by CI run 43605233860: x86-64 Linux, arm64 Linux, and arm64 macOS runners all built the recorded module.)

## Assumptions checked and unchecked

Checked on 2026-11-13: every observation above. Not checked, each with what the plan does either way:

- Debian or Ubuntu packaging of cmark-gfm: irrelevant once it's compiled in.
- That Scala Native compiles cmark-gfm's 64 files from one directory with its default flags, as it did the probe's one file: the first implementation task builds it; if headers aren't found, the task passes `-I` through `nativeCompileOptions`.
- That Chicory's build-time compiler runs on Mill's own JVM classpath without conflicts (ASM versions): if it conflicts, a small runner module runs it in its own process instead.
- That esbuild bundles the three-line module with `import.meta.url` intact: checked by `scripts/check-npm-cli`, which runs the packed package. (Wrong, found in task 3.3: esbuild only minifies `main.js`; the package holds the module beside it instead, decision 5.)
