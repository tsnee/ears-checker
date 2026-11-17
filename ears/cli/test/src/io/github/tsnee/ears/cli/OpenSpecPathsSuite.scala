package io.github.tsnee.ears.cli

import zio.test.*

/** Which paths are OpenSpec's: decided from the path alone, the same on every platform. */
object OpenSpecPathsSuite extends ZIOSpecDefault:

  def spec = suite("OpenSpecPaths")(
    test("a spec, a change's delta spec, and an archived change's are spec files"):
      assertTrue(
        OpenSpecPaths.isSpec("openspec/specs/scheduling/spec.md"),
        OpenSpecPaths.isSpec("openspec/changes/add-reminders/specs/scheduling/spec.md"),
        OpenSpecPaths.isSpec(
          "openspec/changes/archive/2026-11-17-add-reminders/specs/scheduling/spec.md"
        )
      )
    ,
    test("below a project, with a nested capability, or with Windows separators"):
      assertTrue(
        OpenSpecPaths.isSpec("project/openspec/specs/billing/invoices/spec.md"),
        OpenSpecPaths.isSpec("/home/dev/project/openspec/specs/billing/spec.md"),
        OpenSpecPaths.isSpec("./openspec/specs/billing/spec.md"),
        OpenSpecPaths.isSpec("project\\openspec\\specs\\billing\\spec.md")
      )
    ,
    test("anything else isn't"):
      assertTrue(
        !OpenSpecPaths.isSpec("openspec/changes/add-reminders/proposal.md"),
        !OpenSpecPaths.isSpec("openspec/specs/spec.md"),
        !OpenSpecPaths.isSpec("openspec/changes/add-reminders/spec.md"),
        !OpenSpecPaths.isSpec("openspec/changes/archive/specs/scheduling/spec.md"),
        !OpenSpecPaths.isSpec("docs/specs/scheduling/spec.md"),
        !OpenSpecPaths.isSpec("openspec/specs/scheduling/notes.md"),
        !OpenSpecPaths.isSpec("spec.md")
      )
    ,
    test("a directory is inside openspec when it's one, or below its specs or changes"):
      assertTrue(
        OpenSpecPaths.inside("openspec"),
        OpenSpecPaths.inside("project/openspec/"),
        OpenSpecPaths.inside("project/openspec/changes"),
        OpenSpecPaths.inside("project/openspec/changes/add-reminders"),
        OpenSpecPaths.inside("openspec/specs/scheduling"),
        !OpenSpecPaths.inside("docs"),
        !OpenSpecPaths.inside("openspec-notes")
      )
    ,
    test("other directories below one named openspec don't count"):
      // Such as a clone of the OpenSpec tool's own repository, kept in a directory named openspec.
      assertTrue(
        !OpenSpecPaths.inside("src/openspec/docs"),
        !OpenSpecPaths.inside("src/openspec/docs/guides"),
        !OpenSpecPaths.inside("/work/openspec/billing-service/docs")
      )
    ,
    test("the archive is the archive directory of an openspec directory's changes"):
      assertTrue(
        OpenSpecPaths.isArchive("openspec/changes/archive"),
        OpenSpecPaths.isArchive("project/openspec/changes/archive/"),
        !OpenSpecPaths.isArchive("openspec/changes/archive/2026-11-17-add-reminders"),
        !OpenSpecPaths.isArchive("docs/changes/archive"),
        !OpenSpecPaths.isArchive("openspec/archive")
      )
  )
