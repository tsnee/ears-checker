package io.github.tsnee.ears.cli

import java.nio.file.Files
import java.nio.file.Paths

import zio.*
import zio.test.*

/** The third-party notices the CLI jar carries: cmark-gfm's, which its Markdown parser is built
  * from, on the class path, so in the jar.
  */
object NoticesSuite extends ZIOSpecDefault:

  def spec = suite("notices")(
    test("cmark-gfm's COPYING is on the class path at META-INF/cmark-gfm/COPYING, unchanged"):
      for
        vendored <- Live
          .live(System.env("CMARK_GFM_DIR"))
          .someOrFail(IllegalStateException("CMARK_GFM_DIR is not set"))
        expected <- ZIO.attemptBlocking(Files.readAllBytes(Paths.get(vendored, "COPYING")))
        found <- ZIO.attemptBlocking(
          Option(getClass.getResourceAsStream("/META-INF/cmark-gfm/COPYING"))
            .map(stream => scala.util.Using.resource(stream)(_.readAllBytes()))
        )
      yield assertTrue(found.exists(_.sameElements(expected)))
  )
