# Notes for the owner's review

## POMs at 0.1.0 (task 2.1, built 2026-11-03 with `EARS_RELEASE_VERSION=0.1.0`)

Every POM has the design's permanent values: group `io.github.tsnee`, version `0.1.0`, the description, URL `https://github.com/tsnee/ears-checker`, license "Creative Commons Zero v1.0 Universal" (`https://spdx.org/licenses/CC0-1.0.html`), developer `tsnee` / Tom Snee / `https://github.com/tsnee`, and SCM `scm:git:git://github.com/tsnee/ears-checker.git`. No dependency is a `-SNAPSHOT`.

| Artifact | Dependencies (all compile scope) |
| --- | --- |
| `ears-checker_3` | `org.typelevel:cats-parse_3:1.1.0`, `org.scala-lang:scala-library:3.10.0` |
| `ears-checker_sjs1_3` | `org.typelevel:cats-parse_sjs1_3:1.1.0`, `org.scala-lang:scala3-library_sjs1_3:3.10.0`, `org.scala-js:scalajs-library_2.13:1.22.0` |
| `ears-checker-cli_3` | `dev.zio:zio_3:2.1.27`, `com.monovore:decline_3:2.6.2`, `dev.zio:zio-json_3:1.1.1`, `org.commonmark:commonmark:0.31.0`, `org.scala-lang:scala-library:3.10.0`, `io.github.tsnee:ears-checker_3:0.1.0` |

## Open question: developer email

Mill 1.3.1 always writes `<email>` for a developer, empty when none is set, so the POMs have `<email></email>`. Central requires a developer name, not an email. Setting one means adding `email = "…"` to the `Developer` in `build.mill`; the address is then public in every published POM for good. Leaving it empty is the default; the Portal rehearsal (task 5.2) would report it if Central objected.
