# Performance

Runs of `scripts/time-cli`, newest first: `ears-check`'s wall-clock time on inputs made from this repository's specs (Markdown: every `openspec/specs/*/spec.md` concatenated; plain text: their requirements, one per line), repeated `xN` times. One run per cell, so small differences are noise. Each run says the commit, the machine, and the runtimes.

Every table here is rendered from records in `docs/measurements/` by the query beside them (the prose around a table, such as the CI step timings below, is not): `time-cli.jsonl` and `time-cli.jq` for the `time-cli` runs (the three below, from before `time-cli` wrote records, were backfilled from these tables and render back identically), `native-compile-rounds.jsonl` and `.jq` for the compile rounds. Each table follows a `<!-- prettier-ignore -->` comment, so formatting the Markdown leaves it exactly as its query prints it.

## Compiling Scala Native one at a time (compile-native-serially)

Not a `scripts/time-cli` run: the cost of compiling the six Scala Native modules with `-j 1`. Each round ran `./mill clean '__:ScalaNativeModule.compile'`, then timed `./mill '__:ScalaNativeModule.compile'` (all six compiled), in whole wall-clock seconds, with a warm daemon. Rounds that failed (the plugin's race) are left out of the parallel figures. Every round is a record in `docs/measurements/native-compile-rounds.jsonl` (its failure, if any, named from its log), and `docs/measurements/native-compile-rounds.jq` regenerates the table below from them.

2026-11-08 at f99ef39, Apple M4 Max (16 cores, 64 GB), macOS 27.2, Mill 1.3.1, Scala 3.10.0, Scala Native 0.5.12.

<!-- prettier-ignore -->
| Jobs | Rounds timed | Min | Median | Max | Mean |
|---|---|---|---|---|---|
| default (parallel) | 47 | 1 | 2 | 7 | 2.13 |
| `-j 1` | 60 | 3 | 3 | 4 | 3.33 |

In CI (run 43010858605, 2026-11-11, at 8616a82, `ubuntu-latest`, a cold Mill daemon), the step "Compile Scala Native, one module at a time" took 28 s (11:20:29 to 11:20:57), after 57 s for the parallel compile of everything else.

## With cmark-gfm, numbers written without printf (read-markdown-with-cmark-gfm)

The run after the entry point stopped formatting the records' numbers with `snprintf` (see the run below): it writes their digits itself, so the JVM no longer runs `printf_core` in HotSpot's interpreter. Run `2026-11-14T13:20:19Z`, on the uncommitted change over 3f46af1.

2026-11-14 at 3f46af1-dirty, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, --output json, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 1010520 | 1.33 | 0.65 | 0.70 |
| md x16 | 2021040 | 1.42 | 0.98 | 0.90 |
| md x32 | 4042080 | 1.82 | 1.81 | 1.78 |
| md x76 | 9599940 | 2.74 | 3.96 | 4.42 |
| txt x8 | 280768 | 0.64 | 0.28 | 0.15 |
| txt x16 | 561536 | 0.69 | 0.35 | 0.24 |
| txt x32 | 1123072 | 0.76 | 0.50 | 0.42 |
| txt x76 | 2667296 | 0.97 | 0.97 | 0.96 |

Against the baseline before read-markdown-with-cmark-gfm (`time-cli-compare.jq`, `--arg before '2026-11-13T13:41:13Z' --arg after '2026-11-14T13:20:19Z'`):

<!-- prettier-ignore -->
| Input | jvm before | jvm after | jvm ratio | node before | node after | node ratio | native before | native after | native ratio |
|---|---|---|---|---|---|---|---|---|---|
| md x8 | 0.87 | 1.33 | 1.5× | 0.61 | 0.65 | 1.1× | 0.58 | 0.70 | 1.2× |
| md x16 | 1.01 | 1.42 | 1.4× | 0.76 | 0.98 | 1.3× | 0.66 | 0.90 | 1.4× |
| md x32 | 1.28 | 1.82 | 1.4× | 1.30 | 1.81 | 1.4× | 1.29 | 1.78 | 1.4× |
| md x76 | 1.88 | 2.74 | 1.5× | 2.83 | 3.96 | 1.4× | 3.03 | 4.42 | 1.5× |
| txt x8 | 0.60 | 0.64 | 1.1× | 0.27 | 0.28 | 1.0× | 0.14 | 0.15 | 1.1× |
| txt x16 | 0.66 | 0.69 | 1.0× | 0.35 | 0.35 | 1.0× | 0.23 | 0.24 | 1.0× |
| txt x32 | 0.74 | 0.76 | 1.0× | 0.50 | 0.50 | 1.0× | 0.42 | 0.42 | 1.0× |
| txt x76 | 0.91 | 0.97 | 1.1× | 0.95 | 0.97 | 1.0× | 0.95 | 0.96 | 1.0× |

The JVM's Markdown time fell to a third on `md x76` (8.45 to 2.74 s, `time-cli-compare.jq` against `2026-11-14T13:04:04Z`); Node's and the native binary's are where they were. Every build now reads Markdown in 1.1 to 1.5 times the baseline's time, the same on all three, which points at shared code (the records, `CmarkTree`, `CmarkReader`) or cmark-gfm itself more than at any one platform. A second JFR profile of the jar on `md x76` (2026-11-14) had 169 samples, against about 550, with `printf_core` gone and no method above 8; JFR's default stack depth (64 frames) cut off most stacks before a frame of this repository, so it doesn't say which shared code costs most.

## With cmark-gfm on every platform (read-markdown-with-cmark-gfm)

Task 4.4's run: every build reads Markdown with cmark-gfm 0.29.0.gfm.13, the JVM through Chicory's build-time compiled WebAssembly module, Node through its own `WebAssembly`, and the native binary with the C compiled in, each through the C entry point's records and the shared reader. Run `2026-11-14T13:04:04Z`, on the uncommitted change over 3f46af1.

2026-11-14 at 3f46af1-dirty, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, --output json, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 1010520 | 1.73 | 0.66 | 0.50 |
| md x16 | 2021040 | 2.59 | 1.01 | 0.88 |
| md x32 | 4042080 | 4.18 | 1.89 | 1.73 |
| md x76 | 9599940 | 8.45 | 4.07 | 4.18 |
| txt x8 | 280768 | 0.62 | 0.27 | 0.15 |
| txt x16 | 561536 | 0.67 | 0.34 | 0.24 |
| txt x32 | 1123072 | 0.75 | 0.51 | 0.42 |
| txt x76 | 2667296 | 0.96 | 0.98 | 0.97 |

Against the baseline below (`time-cli-compare.jq`, `--arg before '2026-11-13T13:41:13Z' --arg after '2026-11-14T13:04:04Z'`):

<!-- prettier-ignore -->
| Input | jvm before | jvm after | jvm ratio | node before | node after | node ratio | native before | native after | native ratio |
|---|---|---|---|---|---|---|---|---|---|
| md x8 | 0.87 | 1.73 | 2.0× | 0.61 | 0.66 | 1.1× | 0.58 | 0.50 | 0.9× |
| md x16 | 1.01 | 2.59 | 2.6× | 0.76 | 1.01 | 1.3× | 0.66 | 0.88 | 1.3× |
| md x32 | 1.28 | 4.18 | 3.3× | 1.30 | 1.89 | 1.5× | 1.29 | 1.73 | 1.3× |
| md x76 | 1.88 | 8.45 | 4.5× | 2.83 | 4.07 | 1.4× | 3.03 | 4.18 | 1.4× |
| txt x8 | 0.60 | 0.62 | 1.0× | 0.27 | 0.27 | 1.0× | 0.14 | 0.15 | 1.1× |
| txt x16 | 0.66 | 0.67 | 1.0× | 0.35 | 0.34 | 1.0× | 0.23 | 0.24 | 1.0× |
| txt x32 | 0.74 | 0.75 | 1.0× | 0.50 | 0.51 | 1.0× | 0.42 | 0.42 | 1.0× |
| txt x76 | 0.91 | 0.96 | 1.1× | 0.95 | 0.98 | 1.0× | 0.95 | 0.97 | 1.0× |

Plain text, which doesn't touch the parser, is unchanged. Markdown is slower on every build at the larger sizes, and on the JVM far beyond the design's estimate (about 120 ms to load and 2.4 times commonmark-java's parse). A Java Flight Recorder profile of the jar on `md x76` (`-XX:StartFlightRecording=settings=profile`, 2026-11-14, top frames of `jdk.ExecutionSample`) put 438 of about 550 samples in `printf_core`, the C library's formatter, which the entry point calls through `snprintf` for each of the five numbers in every node's record, and which is one of the three functions allowed past HotSpot's compile limit, so it runs in HotSpot's interpreter.

## Before read-markdown-with-cmark-gfm

The baseline for reading Markdown with cmark-gfm on every platform (task 1.1): the JVM reads with commonmark-java, Node with commonmark.js, and the native binary with Homebrew's cmark 0.31.2, each with the table code of read-markdown-tables. Run `2026-11-13T13:41:13Z`.

2026-11-13 at 3f46af1, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, --output json, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 1010520 | 0.87 | 0.61 | 0.58 |
| md x16 | 2021040 | 1.01 | 0.76 | 0.66 |
| md x32 | 4042080 | 1.28 | 1.30 | 1.29 |
| md x76 | 9599940 | 1.88 | 2.83 | 3.03 |
| txt x8 | 280768 | 0.60 | 0.27 | 0.14 |
| txt x16 | 561536 | 0.66 | 0.35 | 0.23 |
| txt x32 | 1123072 | 0.74 | 0.50 | 0.42 |
| txt x76 | 2667296 | 0.91 | 0.95 | 0.95 |

## After speed-up-node-markdown

The classifier fix, and Node reading Markdown with commonmark.js, after the review's fixes. From this run on, each cell writes JSON and must give a valid document.

2026-11-07 at 56113a5-dirty, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, --output json, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 734160 | 0.78 | 0.44 | 0.26 |
| md x16 | 1468320 | 0.91 | 0.61 | 0.47 |
| md x32 | 2936640 | 1.11 | 1.02 | 0.91 |
| md x76 | 6974520 | 1.48 | 2.06 | 2.14 |
| txt x8 | 202944 | 0.61 | 0.25 | 0.12 |
| txt x16 | 405888 | 0.64 | 0.31 | 0.19 |
| txt x32 | 811776 | 0.72 | 0.43 | 0.32 |
| txt x76 | 1927968 | 0.89 | 0.78 | 0.70 |

## The classifier fix alone (speed-up-node-markdown, task 2.1)

The code-span parser built once; Node still on mdast. Uncommitted, on top of the commit shown.

2026-11-07 (local; printed as the UTC date, 2026-11-08) at 56113a5, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 734160 | 0.78 | 0.86 | 0.55 |
| md x16 | 1468320 | 0.89 | 1.91 | 0.61 |
| md x32 | 2936640 | 1.16 | 5.95 | 1.20 |
| md x76 | 6974520 | 1.50 | 26.88 | 2.69 |
| txt x8 | 202944 | 0.59 | 0.25 | 0.16 |
| txt x16 | 405888 | 0.64 | 0.32 | 0.28 |
| txt x32 | 811776 | 0.69 | 0.44 | 0.50 |
| txt x76 | 1927968 | 0.89 | 0.76 | 1.12 |

## Before speed-up-node-markdown

The classifier rebuilt a 65,536-character set for every inline code span, and Node read Markdown with mdast (micromark), whose time grows faster than the document.

2026-11-07 (local; printed as the UTC date, 2026-11-08) at 56113a5, Darwin arm64, Node v25.2.1, openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, wall-clock seconds.

<!-- prettier-ignore -->
| Input | Bytes | jvm | node | native |
|---|---|---|---|---|
| md x8 | 734160 | 1.09 | 2.78 | 9.39 |
| md x16 | 1468320 | 1.46 | 5.61 | 18.92 |
| md x32 | 2936640 | 2.34 | 13.01 | 37.47 |
| md x76 | 6974520 | 4.17 | 42.85 | 88.40 |
| txt x8 | 202944 | 0.85 | 1.85 | 8.09 |
| txt x16 | 405888 | 1.13 | 3.57 | 16.16 |
| txt x32 | 811776 | 1.76 | 6.80 | 32.14 |
| txt x76 | 1927968 | 3.35 | 16.10 | 76.41 |
