# markdown-parser-build Specification

## Purpose

Builds cmark-gfm, GitHub's Markdown parser, into the checker on every platform: from a pinned copy of its source and a pinned toolchain, into one WebAssembly module that every build host produces byte for byte, with JVM classes that HotSpot can compile, and with cmark-gfm's license notices in each distribution.

## Requirements

### Requirement: Pinned parser source

The Markdown parser build SHALL compile cmark-gfm from the repository's copy of the source of its release 0.29.0.gfm.13.

#### Scenario: The parser's name in the log

- **WHEN** a developer builds the CLI on any platform and checks a Markdown file
- **THEN** the log entry for the file names the parser `cmark-gfm 0.29.0.gfm.13`

### Requirement: Pinned toolchain

If the downloaded wasi-sdk archive's SHA-256 checksum differs from the one the repository records for that archive, then the Markdown parser build SHALL fail before running any program from the archive.

#### Scenario: A tampered archive

- **WHEN** the build downloads `wasi-sdk-34.0-x86_64-linux.tar.gz` and its checksum isn't `b761e3a0721dbae9c09a0059e5fdb2bf917d1b4a8a7b430fb3b5aafb0984b2c4`
- **THEN** the build fails, naming the archive and both checksums, and runs nothing from it

### Requirement: Same module on every build host

If the built WebAssembly module's SHA-256 checksum differs from the one the repository records, then the Markdown parser build SHALL fail.

#### Scenario: Built on two platforms

- **WHEN** the module is built on macOS arm64 and on Linux x64 from the same commit
- **THEN** both builds produce the recorded checksum

#### Scenario: A wasm-opt on the build host's path

- **WHEN** a developer with binaryen's `wasm-opt` on their `PATH` builds the module
- **THEN** the build produces the recorded checksum, as it does without `wasm-opt`

#### Scenario: A changed C entry point

- **WHEN** a developer changes the C entry point and builds without updating the recorded checksum
- **THEN** the build fails, naming the recorded and the built checksums

### Requirement: Methods HotSpot compiles

If a JVM method generated from the WebAssembly module exceeds 8,000 bytes of bytecode and its function isn't on the repository's list of functions allowed to, then the Markdown parser build SHALL fail.

#### Scenario: Inlining turned back on

- **WHEN** a developer builds the module without `-fno-inline-functions`, so that `cmark_parse_inlines` generates a method of 12,914 bytes
- **THEN** the build fails, naming `cmark_parse_inlines` and its size

### Requirement: Notices in the CLI jar

The Markdown parser build SHALL include cmark-gfm's license notices in the CLI jar.

#### Scenario: The jar's notices

- **WHEN** a developer builds the CLI jar and lists its files
- **THEN** it holds cmark-gfm's `COPYING` text

### Requirement: Notices in the CLI npm package

The Markdown parser build SHALL include cmark-gfm's license notices in the CLI npm package.

#### Scenario: The package's notices

- **WHEN** a developer packs the CLI npm package and lists its files
- **THEN** it holds cmark-gfm's `COPYING` text
