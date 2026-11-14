package io.github.tsnee.ears.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

import io.github.tsnee.ears.*
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import zio.*
import zio.test.*

/** Every requirement statement in the repository's OpenSpec specs is strict EARS. */
object OwnSpecsSuite extends ZIOSpecDefault:

  private final case class Statement(file: Path, name: String, text: String)

  private def siblings(first: Option[Node]): List[Node] =
    List.unfold(first)(node => node.map(n => (n, Option(n.getNext))))

  // This traversal deliberately doesn't reuse MarkdownInput: the suite is an independent check
  // on the specs, so a bug that made MarkdownInput skip statements can't also hide them here.
  // Inline code keeps its backticks, so the classifier judges the text OpenSpec's JSON gives
  // scripts/check-openspec.
  private def literal(node: Node): String = node match
    case text: Text       => Option(text.getLiteral).getOrElse("")
    case code: Code       => s"`${Option(code.getLiteral).getOrElse("")}`"
    case _: SoftLineBreak => " "
    case _: HardLineBreak => " "
    case other            => siblings(Option(other.getFirstChild)).map(literal).mkString

  /** Each "### Requirement: name" heading and the paragraph right after it, but under "## REMOVED
    * Requirements" in a change's delta, where a requirement has a reason and a migration instead of
    * a statement (OpenSpec's JSON gives the reason as its text, and `scripts/check-openspec` checks
    * only added and modified ones). Any other section is read, so a new kind fails here.
    */
  private def statements(file: Path, markdown: String): List[Statement] =
    val nodes = siblings(Option(Parser.builder().build().parse(markdown).getFirstChild))
    // Whether each node is in a REMOVED section: set by each level-2 heading, until the next.
    val removed = nodes
      .scanLeft(false) {
        case (_, heading: Heading) if heading.getLevel == 2 =>
          literal(heading).trim == "REMOVED Requirements"
        case (inside, _) => inside
      }
      .drop(1)
    nodes.zip(nodes.drop(1)).zip(removed).collect {
      case ((heading: Heading, paragraph: Paragraph), false)
          if heading.getLevel == 3 && literal(heading).startsWith("Requirement:") =>
        Statement(file, literal(heading).stripPrefix("Requirement:").trim, literal(paragraph))
    }

  private val specFiles: Task[List[Path]] =
    for
      root <- Live
        .live(System.env("OPENSPEC_DIR"))
        .someOrFail(new IllegalStateException("OPENSPEC_DIR is not set"))
      files <- ZIO.attemptBlocking(
        Files
          .walk(Paths.get(root))
          .iterator
          .asScala
          .filter(_.getFileName.toString == "spec.md")
          .toList
      )
    yield files

  def spec = suite("Own OpenSpec specs")(
    test("every requirement statement classifies under the OpenSpec profile"):
      for
        files <- specFiles
        found <- ZIO.foreach(files)(file =>
          ZIO.attemptBlocking(statements(file, Files.readString(file)))
        )
        all = found.flatten
        failures = all.flatMap { statement =>
          Requirement.classify(Profile.OpenSpec, statement.text).left.toOption.map { problems =>
            (statement.file.toString, statement.name, problems.toList.map(_.kind))
          }
        }
      yield assertTrue(all.size >= 50, failures.isEmpty)
    ,
    test("inline code keeps its backticks, as in OpenSpec's JSON"):
      val markdown = "### Requirement: Done\nThe checker SHALL write `Done.` when it finishes.\n"
      val read = statements(Paths.get("spec.md"), markdown)
      assertTrue(
        read.map(_.text) == List("The checker SHALL write `Done.` when it finishes."),
        read.forall(statement => Requirement.classify(Profile.OpenSpec, statement.text).isRight)
      )
    ,
    test("a hard line break inside a statement reads as a space"):
      val markdown =
        "### Requirement: Upcoming\nThe scheduler SHALL list\\\nupcoming events.\n\n#### Scenario: One\n- **WHEN** x\n- **THEN** y\n"
      assertTrue(
        statements(Paths.get("spec.md"), markdown).map(_.text) ==
          List("The scheduler SHALL list upcoming events.")
      )
    ,
    test("a removed requirement has no statement; the sections around it are read"):
      val markdown =
        "## ADDED Requirements\n\n### Requirement: Upcoming\nThe scheduler SHALL list events.\n\n" +
          "## REMOVED Requirements\n\n### Requirement: Reminders\n**Reason**: Replaced.\n\n" +
          "**Migration**: None.\n\n## MODIFIED Requirements\n\n### Requirement: Sync\n" +
          "The calendar SHALL sync hourly.\n"
      assertTrue(
        statements(Paths.get("spec.md"), markdown).map(_.name) == List("Upcoming", "Sync")
      )
  )
