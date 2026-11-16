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
    test("a child's name adds no slash after a directory given with one"):
      assertTrue(
        DirectoryWalk.child("docs/", "scheduling.md") == "docs/scheduling.md",
        DirectoryWalk.child("docs", "scheduling.md") == "docs/scheduling.md",
        DirectoryWalk.child("/", "scheduling.md") == "/scheduling.md",
        DirectoryWalk.child(".", "scheduling.md") == "./scheduling.md"
      )
  )
