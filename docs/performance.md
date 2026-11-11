# Performance

Runs of `scripts/time-cli`, newest first: `ears-check`'s wall-clock time on inputs made from this
repository's specs (Markdown: every `openspec/specs/*/spec.md` concatenated; plain text: their
requirements, one per line), repeated `xN` times. One run per cell, so small differences are
noise. Each run says the commit, the machine, and the runtimes.

## Compiling Scala Native one at a time (compile-native-serially)

Not a `scripts/time-cli` run: the cost of compiling the six Scala Native modules with `-j 1`.
Each round ran `./mill clean '__:ScalaNativeModule.compile'`, then timed
`./mill '__:ScalaNativeModule.compile'` (all six compiled), in whole wall-clock seconds, with a
warm daemon. Rounds that failed (the plugin's race) are left out of the parallel figures.

2026-11-08 at f99ef39, Apple M4 Max (16 cores, 64 GB), macOS 27.2, Mill 1.3.1, Scala 3.10.0,
Scala Native 0.5.12.

| Jobs | Rounds timed | Min | Median | Max | Mean |
|---|---|---|---|---|---|
| default (parallel) | 47 | 1 | 2 | 7 | 2.13 |
| `-j 1` | 60 | 3 | 3 | 4 | 3.33 |

## After speed-up-node-markdown

The classifier fix, and Node reading Markdown with commonmark.js, after the review's fixes. From
this run on, each cell writes JSON and must give a valid document.

2026-11-07 at 56113a5-dirty, Darwin arm64, Node v25.2.1,
openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, --output json, wall-clock seconds.

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

2026-11-07 (local; printed as the UTC date, 2026-11-08) at 56113a5, Darwin arm64, Node v25.2.1,
openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, wall-clock seconds.

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

The classifier rebuilt a 65,536-character set for every inline code span, and Node read Markdown
with mdast (micromark), whose time grows faster than the document.

2026-11-07 (local; printed as the UTC date, 2026-11-08) at 56113a5, Darwin arm64, Node v25.2.1,
openjdk version "27.0.1.0.3" 2026-10-24; one run per cell, wall-clock seconds.

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
