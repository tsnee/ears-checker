package io.github.tsnee.ears.markdown

import zio.test.*

/** The JVM's call of the C entry point, where it differs from the other platforms': the stubs it
  * gives the module for its WASI imports.
  */
object CmarkGfmJvmSuite extends ZIOSpecDefault:

  def spec = suite("CmarkGfm on the JVM")(
    test("a WASI import's stub fails with ENOSYS (52), whatever its arguments"):
      assertTrue(
        CmarkGfm.instance().map(running => CmarkGfm.enosys.apply(running, 1L, 2L).toList) ==
          Right(List(52L))
      )
  )
