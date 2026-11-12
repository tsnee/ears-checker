package io.github.tsnee.ears.releasenotes

import java.time.LocalDate

import zio.test.*

/** The changelog's form, its sections, and the notes written from one. */
object ChangelogSuite extends ZIOSpecDefault:

  private val v011 = Version(0, 1, 1)
  private val v010 = Version(0, 1, 0)

  private def release(version: Version, date: String): Heading =
    Heading.Release(version, LocalDate.parse(date))

  private val valid =
    """# Changelog
      |
      |Every release of ears-checker.
      |
      |## Unreleased
      |
      |### Added
      |
      |- A native binary.
      |
      |## 0.1.1 - 2026-11-05
      |
      |### Fixed
      |
      |- The Scala.js library links without scala-java-locales.
      |
      |## 0.1.0 - 2026-11-03
      |
      |- The first release.
      |""".stripMargin

  /** A changelog with these second-level headings, each over a one-line section. */
  private def headed(headings: String*): String =
    headings.map(heading => s"## $heading\n\nText.\n").mkString("# Changelog\n\n", "\n", "")

  /** A changelog written from sections, as a release's pull request leaves it. */
  private def written(sections: List[(Version, LocalDate, List[String])]): String =
    val releases = sections.map((version, date, lines) =>
      s"## ${version.show} - $date\n\n${lines.mkString("\n")}\n"
    )
    ("# Changelog\n\n## Unreleased\n\n- Next.\n" :: releases).mkString("\n")

  private val genVersions: Gen[Any, List[Version]] =
    Gen
      .setOfBounded(1, 6)(
        Gen.int(0, 3).zip(Gen.int(0, 12)).zip(Gen.int(0, 30)).map(Version(_, _, _))
      )
      .map(_.toList.sorted(using summon[Ordering[Version]].reverse))

  private val genLine: Gen[Any, String] =
    Gen.listOfBounded(1, 5)(Gen.alphaNumericStringBounded(1, 8)).map(_.mkString(" "))

  private val genDate: Gen[Any, LocalDate] =
    Gen.int(0, 3650).map(days => LocalDate.of(2020, 1, 1).plusDays(days.toLong))

  def spec = suite("changelog")(
    suite("form")(
      test("a valid changelog's sections, in order, each with its text"):
        assertTrue(
          Changelog.sections(valid) == Right(
            List(
              Section(Heading.Unreleased, "### Added\n\n- A native binary."),
              Section(
                release(v011, "2026-11-05"),
                "### Fixed\n\n- The Scala.js library links without scala-java-locales."
              ),
              Section(release(v010, "2026-11-03"), "- The first release.")
            )
          )
        )
      ,
      test("a changelog written from any releases, newest first, gives them back"):
        check(
          genVersions.flatMap(versions =>
            Gen.collectAll(
              versions.map(version =>
                genDate
                  .zip(Gen.listOfBounded(1, 4)(genLine))
                  .map((date, lines) => (version, date, lines))
              )
            )
          )
        ) { releases =>
          val expected = Section(Heading.Unreleased, "- Next.") ::
            releases.map((version, date, lines) =>
              Section(Heading.Release(version, date), lines.mkString("\n"))
            )
          assertTrue(Changelog.sections(written(releases)) == Right(expected))
        }
      ,
      test("a setext heading counts, and its underline isn't part of the section"):
        val text = "Unreleased\n----------\n\n- Next.\n\n0.1.0 - 2026-11-03\n---\n\n- First.\n"
        assertTrue(
          Changelog.sections(text) == Right(
            List(
              Section(Heading.Unreleased, "- Next."),
              Section(release(v010, "2026-11-03"), "- First.")
            )
          )
        )
      ,
      test("no second-level heading at all"):
        assertTrue(Changelog.sections("# Changelog\n\nNothing yet.\n") == Left(Problem.NoHeadings))
      ,
      test("Unreleased missing: the first heading is named"):
        assertTrue(
          Changelog.sections(headed("0.1.1 - 2026-11-05", "0.1.0 - 2026-11-03")) ==
            Left(Problem.UnreleasedNotFirst("0.1.1 - 2026-11-05"))
        )
      ,
      test("Unreleased not first: the heading before it is named"):
        assertTrue(
          Changelog.sections(headed("0.2.0 - 2026-11-20", "Unreleased")) ==
            Left(Problem.UnreleasedNotFirst("0.2.0 - 2026-11-20"))
        )
      ,
      test("a heading without a date"):
        assertTrue(
          Changelog.sections(headed("Unreleased", "0.2.0", "0.1.0 - 2026-11-03")) ==
            Left(Problem.Malformed("0.2.0"))
        )
      ,
      test("malformed versions and dates, and a second Unreleased"):
        // One version that isn't one: the heading's version goes through `Version.parse`, and
        // "what isn't a version" owns the rest.
        val headings = List(
          "v0.2.0 - 2026-11-20",
          "0.2.0 - 2026-13-01",
          "0.2.0 - 2026-02-30",
          "0.2.0 - 20261120",
          "0.2.0 2026-11-20",
          "0.2.0 - 2026-11-20 (yanked)",
          "Unreleased"
        )
        assertTrue(
          headings.forall(heading =>
            Changelog.sections(headed("Unreleased", heading)) == Left(Problem.Malformed(heading))
          )
        )
      ,
      test("versions out of order: the first one that isn't older is named"):
        assertTrue(
          Changelog.sections(headed("Unreleased", "0.1.0 - 2026-11-03", "0.1.1 - 2026-11-05")) ==
            Left(Problem.OutOfOrder("0.1.1 - 2026-11-05")),
          Changelog.sections(headed("Unreleased", "0.1.0 - 2026-11-03", "0.1.0 - 2026-11-04")) ==
            Left(Problem.OutOfOrder("0.1.0 - 2026-11-04"))
        )
      ,
      test("versions compare by number, not as text"):
        assertTrue(
          Changelog
            .sections(headed("Unreleased", "0.10.0 - 2026-12-01", "0.9.0 - 2026-11-30"))
            .isRight
        )
      ,
      test("a section is kept verbatim, a ## line in a code block included"):
        val body =
          "### Changed\n\n```markdown\n## Not a heading\n```\n\n    ## Nor this\n\n- *Kept*."
        assertTrue(
          Changelog.sections(s"## Unreleased\n\n$body\n\n## 0.1.0 - 2026-11-03\n\n- First.\n") ==
            Right(
              List(
                Section(Heading.Unreleased, body),
                Section(release(v010, "2026-11-03"), "- First.")
              )
            )
        )
      ,
      test("a first-level heading ends a section"):
        assertTrue(
          Changelog.sections("## Unreleased\n\n- Next.\n\n# Appendix\n\nNot a release.\n") ==
            Right(List(Section(Heading.Unreleased, "- Next.")))
        )
      ,
      test("a heading's inline code and emphasis read as their text"):
        assertTrue(
          Changelog.sections(
            "## *Unreleased*\n\n- Next.\n\n## `0.1.0` - 2026-11-03\n\n- First.\n"
          ) ==
            Right(
              List(
                Section(Heading.Unreleased, "- Next."),
                Section(release(v010, "2026-11-03"), "- First.")
              )
            )
        )
      ,
      test("Windows line endings are read as line breaks"):
        assertTrue(
          Changelog.sections("## Unreleased\r\n\r\n- One.\r\n- Two.\r\n") ==
            Right(List(Section(Heading.Unreleased, "- One.\n- Two.")))
        )
    ),
    suite("notes")(
      test("a version's section, followed by the files description with the version in it"):
        val files = "## Files\n\n- `ears-check-{version}.jar`, for JDK 17 or later ({version}).\n"
        assertTrue(
          Changelog.notes(valid, v011, Some(files)) == Right(
            "### Fixed\n\n- The Scala.js library links without scala-java-locales.\n\n" +
              "## Files\n\n- `ears-check-0.1.1.jar`, for JDK 17 or later (0.1.1).\n"
          )
        )
      ,
      test("without a files description, only the section"):
        assertTrue(Changelog.notes(valid, v010, None) == Right("- The first release.\n"))
      ,
      test("a version with no section is named, and the problem says to rename Unreleased"):
        val result = Changelog.notes(valid, Version(0, 2, 0), None)
        assertTrue(
          result == Left(Problem.NoSection(Version(0, 2, 0))),
          result.left.exists(problem =>
            problem.message.contains("0.2.0") && problem.message.contains("Unreleased")
          )
        )
      ,
      test("a version whose section has no text is named"):
        val text = "## Unreleased\n\n## 0.2.0 - 2026-11-20\n\n\n## 0.1.0 - 2026-11-03\n\n- First.\n"
        val result = Changelog.notes(text, Version(0, 2, 0), None)
        assertTrue(
          result == Left(Problem.EmptySection(Version(0, 2, 0))),
          result.left.exists(_.message.contains("0.2.0"))
        )
      ,
      test("a changelog without its form gives no notes"):
        assertTrue(
          Changelog.notes(headed("Unreleased", "0.1.0"), v010, None) ==
            Left(Problem.Malformed("0.1.0"))
        )
    ),
    suite("messages")(
      test("every problem's message names its heading or version"):
        val named = List(
          Problem.UnreleasedNotFirst("Next") -> "Next",
          Problem.Malformed("0.2.0") -> "0.2.0",
          Problem.OutOfOrder("0.1.1 - 2026-11-05") -> "0.1.1 - 2026-11-05",
          Problem.NoSection(Version(0, 2, 0)) -> "0.2.0",
          Problem.EmptySection(Version(0, 2, 0)) -> "0.2.0",
          Problem.NoHeadings -> "## Unreleased"
        )
        assertTrue(named.forall((problem, name) => problem.message.contains(name)))
    ),
    suite("versions")(
      test("parsing a version and showing it again"):
        check(Gen.int(0, 999).zip(Gen.int(0, 999)).zip(Gen.int(0, 999))) { (major, minor, patch) =>
          val version = Version(major, minor, patch)
          assertTrue(Version.parse(version.show) == Some(version))
        }
      ,
      test("what isn't a version"):
        assertTrue(
          List("", "1", "1.2", "1.2.3.4", "v1.2.3", "01.2.3", "1.2.3-SNAPSHOT", "1.2.99999999999")
            .forall(text => Version.parse(text).isEmpty)
        )
    )
  )
