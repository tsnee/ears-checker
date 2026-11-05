package io.github.tsnee.ears.linkcheck

import java.nio.file.Files
import java.nio.file.Path
import scala.meta.internal.semanticdb.*

import zio.*
import zio.test.*

/** The generator end to end: SemanticDB files in a temporary directory, the program file, and what
  * the console shows.
  */
object MainSuite extends ZIOSpecDefault:

  private def defined(symbol: String): SymbolOccurrence =
    SymbolOccurrence(
      range = Some(Range(0, 0, 0, 1)),
      symbol = symbol,
      role = SymbolOccurrence.Role.DEFINITION
    )

  private def public(symbol: String, kind: SymbolInformation.Kind): SymbolInformation =
    SymbolInformation(symbol = symbol, kind = kind, access = PublicAccess())

  /** A SemanticDB directory with one document holding `methods` of a public object `p.Owner`, in a
    * nested directory as the build writes it.
    */
  private def semanticdb(methods: List[String]): Task[Path] =
    val symbols =
      public("p/Owner.", SymbolInformation.Kind.OBJECT) ::
        methods.map(name => public(s"p/Owner.$name", SymbolInformation.Kind.METHOD))
    val document = TextDocument(
      uri = "p/Owner.scala",
      symbols = symbols,
      occurrences = symbols.map(info => defined(info.symbol))
    )
    ZIO.attemptBlocking {
      val root = Files.createTempDirectory("link-check")
      val nested = Files.createDirectories(root.resolve("META-INF/semanticdb/p"))
      Files.write(
        nested.resolve("Owner.scala.semanticdb"),
        TextDocuments(List(document)).toByteArray
      )
      Files.writeString(nested.resolve("notes.txt"), "not SemanticDB")
      root
    }

  private def outputFile: Task[Path] =
    ZIO.attemptBlocking(Files.createTempDirectory("link-check-out").resolve("LinkCheck.scala"))

  def spec = suite("link-check generator")(
    test("writes the program and prints how many operations it references"):
      for
        root <- semanticdb(List("run()."))
        output <- outputFile
        code <- Main.cli(List(root.toString, output.toString))
        written <- ZIO.attemptBlocking(Files.readString(output))
        out <- TestConsole.output
      yield assertTrue(
        code.code == 0,
        written.contains("_root_.p.Owner.run"),
        out.mkString.trim == LinkCheck.summary(1)
      )
    ,
    test("an operation it can't reference: exit 1, its name on standard error, and no program"):
      for
        root <- semanticdb(List("run().", "run(+1)."))
        output <- outputFile
        code <- Main.cli(List(root.toString, output.toString))
        err <- TestConsole.outputErr
        exists <- ZIO.attemptBlocking(Files.exists(output))
      yield assertTrue(
        code.code == 1,
        err.mkString.contains("Owner.run is overloaded"),
        !exists
      )
    ,
    test("a SemanticDB directory that doesn't exist: exit 1, and a message about reading it"):
      for
        output <- outputFile
        code <- Main.cli(List(output.resolveSibling("missing").toString, output.toString))
        err <- TestConsole.outputErr
      yield assertTrue(
        code.code == 1,
        err.mkString.contains("Cannot read the SemanticDB under")
      )
    ,
    test("an output file it can't write: exit 1, and a message about writing it"):
      for
        root <- semanticdb(List("run()."))
        output <- outputFile
        unwritable = output.resolveSibling("missing").resolve("LinkCheck.scala")
        code <- Main.cli(List(root.toString, unwritable.toString))
        err <- TestConsole.outputErr
      yield assertTrue(
        code.code == 1,
        err.mkString.contains(s"Cannot write the link-check program to $unwritable")
      )
    ,
    test("bad arguments: exit 2 and the usage on standard error"):
      for
        code <- Main.cli(List("only-one"))
        err <- TestConsole.outputErr
      yield assertTrue(code.code == 2, err.mkString.contains("Usage"))
  ) @@ TestAspect.silent
