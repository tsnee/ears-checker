package io.github.tsnee.ears.cli

import zio.test.*

/** What a directory's entries become: the same on every platform, whatever order they're listed in.
  */
object DirectoryWalkSuite extends ZIOSpecDefault:

  private def file(name: String) = Entry(name, EntryKind.File)

  private def directory(name: String) = Entry(name, EntryKind.Directory)

  def spec = suite("DirectoryWalk")(
    test("Markdown and plain-text files are checked, under the directory's name"):
      assertTrue(
        DirectoryWalk.steps("docs", List(file("scheduling.md"), file("billing.txt"))) ==
          List(Step.Check("docs/billing.txt"), Step.Check("docs/scheduling.md"))
      )
    ,
    test("other files are skipped as not requirements"):
      assertTrue(
        DirectoryWalk.steps("docs", List(file("package.json"), file("logo.png"), file("md"))) ==
          List(
            Step.Skip("docs/logo.png", Skipped.NotRequirements),
            Step.Skip("docs/md", Skipped.NotRequirements),
            Step.Skip("docs/package.json", Skipped.NotRequirements)
          )
      )
    ,
    test("subdirectories are entered"):
      assertTrue(
        DirectoryWalk.steps("docs", List(directory("billing"))) ==
          List(Step.Enter("docs/billing")),
        Step.Enter("docs/billing").path == "docs/billing",
        Step.Check("docs/billing.md").path == "docs/billing.md",
        Step.Enter("docs/billing").reason.isEmpty
      )
    ,
    test("hidden entries are skipped, whatever their kind"):
      val hidden = List(
        file(".draft.md"),
        directory(".github"),
        Entry(".linked.md", EntryKind.Link),
        Entry(".pipe.md", EntryKind.Other)
      )
      assertTrue(
        DirectoryWalk.steps("docs", hidden).map(_.reason) == List.fill(4)(Some(Skipped.Hidden))
      )
    ,
    test("links and other entries are skipped, whatever their names"):
      assertTrue(
        DirectoryWalk.steps(
          "docs",
          List(Entry("keys.md", EntryKind.Link), Entry("feed.md", EntryKind.Other))
        ) ==
          List(
            Step.Skip("docs/feed.md", Skipped.Other),
            Step.Skip("docs/keys.md", Skipped.Link)
          )
      )
    ,
    test("entries are in order of their names by code unit, a subdirectory where its name sorts"):
      val listed = List(file("b.md"), directory("b"), file("a.md"), file("B.md"))
      assertTrue(
        DirectoryWalk.steps("docs", listed) == List(
          Step.Check("docs/B.md"),
          Step.Check("docs/a.md"),
          Step.Enter("docs/b"),
          Step.Check("docs/b.md")
        )
      )
    ,
    test("one step per entry, in name order, however they're listed"):
      val names =
        Gen.elements("a.md", "B.md", "b", "\u00e9.md", "e\u0301.md", ".x", "z.txt", "c.json")
      val kinds = Gen.elements(EntryKind.values.toList*)
      check(Gen.setOf(names), Gen.boolean) { (set, reversed) =>
        val listed = if reversed then set.toList.reverse else set.toList
        val entries = listed.map(name => Entry(name, EntryKind.File))
        assertTrue(DirectoryWalk.steps("d", entries).map(_.path) == listed.sorted.map("d/" + _))
      } && check(Gen.listOf(kinds)) { kindList =>
        val entries = kindList.zipWithIndex.map((kind, i) => Entry(s"n$i.md", kind))
        assertTrue(DirectoryWalk.steps("d", entries).size == entries.size)
      }
    ,
    test("names keep their code points"):
      val decomposed = "e\u0301.md"
      assertTrue(
        DirectoryWalk.steps("docs", List(file(decomposed))) ==
          List(Step.Check(s"docs/$decomposed"))
      )
    ,
    test("a listing's summary: its counts, and the skipped entries worth a log line each"):
      val steps = List(
        Step.Check("d/a.md"),
        Step.Check("d/b.txt"),
        Step.Enter("d/sub"),
        Step.Skip("d/.git", Skipped.Hidden),
        Step.Skip("d/keys.md", Skipped.Link),
        Step.Skip("d/feed.md", Skipped.Other),
        Step.Skip("d/logo.png", Skipped.NotRequirements)
      )
      assertTrue(
        DirectoryWalk.summary(steps) == Listing(
          files = 2,
          subdirectories = 1,
          skipped = 4,
          noted = List("d/keys.md" -> "link", "d/feed.md" -> "not a file or directory")
        )
      )
    ,
    test("inside openspec, only OpenSpec spec files are checked"):
      assertTrue(
        DirectoryWalk.steps(
          "openspec",
          List(file("config.yaml"), file("AGENTS.md"), directory("specs"), directory("changes"))
        ) == List(
          Step.Skip("openspec/AGENTS.md", Skipped.NotRequirements),
          Step.Enter("openspec/changes"),
          Step.Skip("openspec/config.yaml", Skipped.NotRequirements),
          Step.Enter("openspec/specs")
        ),
        DirectoryWalk.steps("openspec/specs/scheduling", List(file("spec.md"), file("notes.md"))) ==
          List(
            Step.Skip("openspec/specs/scheduling/notes.md", Skipped.NotRequirements),
            Step.Check("openspec/specs/scheduling/spec.md")
          ),
        DirectoryWalk.steps(
          "project/openspec/changes/add-reminders",
          List(file("proposal.md"), file("tasks.md"), directory("specs"))
        ) == List(
          Step.Skip("project/openspec/changes/add-reminders/proposal.md", Skipped.NotRequirements),
          Step.Enter("project/openspec/changes/add-reminders/specs"),
          Step.Skip("project/openspec/changes/add-reminders/tasks.md", Skipped.NotRequirements)
        )
      )
    ,
    test("the archive is skipped when found, and its specs checked when it's walked"):
      assertTrue(
        DirectoryWalk.steps("openspec/changes", List(directory("archive"), directory("add"))) ==
          List(
            Step.Enter("openspec/changes/add"),
            Step.Skip("openspec/changes/archive", Skipped.Archive)
          ),
        DirectoryWalk.steps(
          "openspec/changes/archive/2026-11-17-add/specs/x",
          List(file("spec.md"))
        ) ==
          List(Step.Check("openspec/changes/archive/2026-11-17-add/specs/x/spec.md"))
      )
    ,
    test("an archive is noted in the listing's summary"):
      assertTrue(
        DirectoryWalk.summary(List(Step.Skip("openspec/changes/archive", Skipped.Archive))).noted ==
          List("openspec/changes/archive" -> "archive")
      )
    ,
    test("nothing to check: the message names what was looked for"):
      assertTrue(
        DirectoryWalk.nothingToCheck("notes", throughOpenSpec = false) ==
          "Cannot check notes: it holds no .md or .txt files",
        DirectoryWalk.nothingToCheck("openspec", throughOpenSpec = true) ==
          "Cannot check openspec: it holds no OpenSpec spec files",
        DirectoryWalk.nothingToCheck("project/openspec/changes", throughOpenSpec = true) ==
          "Cannot check project/openspec/changes: it holds no OpenSpec spec files",
        DirectoryWalk.nothingToCheck("project", throughOpenSpec = true) ==
          "Cannot check project: it holds no .md or .txt files outside openspec, " +
          "and no OpenSpec spec files"
      )
    ,
    test("a directory named openspec that isn't OpenSpec's: its files are read as any others"):
      assertTrue(
        DirectoryWalk.steps("src/openspec/docs", List(file("guide.md"))) ==
          List(Step.Check("src/openspec/docs/guide.md"))
      )
    ,
    test("a child's name adds no slash after a directory given with one"):
      assertTrue(
        DirectoryWalk.child("docs/", "scheduling.md") == "docs/scheduling.md",
        DirectoryWalk.child("docs", "scheduling.md") == "docs/scheduling.md",
        DirectoryWalk.child("/", "scheduling.md") == "/scheduling.md",
        DirectoryWalk.child(".", "scheduling.md") == "./scheduling.md"
      )
  )
