package io.github.tsnee.ears.releasenotes

import java.time.LocalDate
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.commonmark.node.Code
import org.commonmark.node.Heading as MarkdownHeading
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** A release's version, `<major>.<minor>.<patch>`. */
final case class Version(major: Int, minor: Int, patch: Int) derives CanEqual

object Version:

  /** `<major>.<minor>.<patch>`, each a number without leading zeros that fits an `Int`. */
  def parse(text: String): Option[Version] = text match
    case s"$major.$minor.$patch" =>
      for
        a <- number(major)
        b <- number(minor)
        c <- number(patch)
      yield Version(a, b, c)
    case _ => None

  given Ordering[Version] = Ordering.by(version => (version.major, version.minor, version.patch))

  extension (version: Version)
    def show: String = s"${version.major}.${version.minor}.${version.patch}"

  private def number(text: String): Option[Int] =
    if text.matches("0|[1-9][0-9]*") then text.toIntOption else None

/** A changelog section's heading: `Unreleased`, or a release's version and date. */
enum Heading derives CanEqual:
  case Unreleased
  case Release(version: Version, date: LocalDate)

/** A second-level heading and the source text under it, up to the next heading of level 1 or 2,
  * without the blank lines around it.
  */
final case class Section(heading: Heading, text: String) derives CanEqual

/** Why a changelog doesn't have the form, or has no notes for a version. */
enum Problem derives CanEqual:
  case NoHeadings
  case UnreleasedNotFirst(heading: String)
  case Malformed(heading: String)
  case OutOfOrder(heading: String)
  case NoSection(version: Version)
  case EmptySection(version: Version)

object Problem:
  extension (problem: Problem)
    def message: String = problem match
      case NoHeadings =>
        "The changelog has no second-level heading: it starts with ## Unreleased"
      case UnreleasedNotFirst(heading) =>
        s"The changelog's first second-level heading is '$heading': it must be 'Unreleased'"
      case Malformed(heading) =>
        s"The changelog's heading '$heading' isn't '<major>.<minor>.<patch> - <YYYY-MM-DD>'"
      case OutOfOrder(heading) =>
        s"The changelog's heading '$heading' isn't older than the one before it: newest first"
      case NoSection(version) =>
        s"The changelog has no section for ${version.show}: rename Unreleased to " +
          s"'${version.show} - <YYYY-MM-DD>' before tagging"
      case EmptySection(version) =>
        s"The changelog's section for ${version.show} has no text"

/** `CHANGELOG.md`: `# Changelog`, then `## Unreleased`, then one
  * `## <major>.<minor>.<patch> - <YYYY-MM-DD>` per release, newest first.
  */
object Changelog:

  private val parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build()

  /** The sections, in order, if the changelog has the form; else the first heading that doesn't
    * fit. Headings come from the parser, so a `## ` line in a code block isn't one; each section's
    * lines are kept as they are, with `\n` between them.
    */
  def sections(markdown: String): Either[Problem, List[Section]] =
    val lines = markdown.split("\r\n|\r|\n", -1).toList
    val headings = children(parser.parse(markdown)).collect {
      case heading: MarkdownHeading if heading.getLevel <= 2 => heading
    }
    val bounds = headings.zip(headings.drop(1).map(start).map(Some(_)) :+ None)
    val texts = bounds.collect {
      case (heading, next) if heading.getLevel == 2 =>
        val body = lines.slice(end(heading) + 1, next.getOrElse(lines.size))
        (title(heading), trimmed(body))
    }
    texts match
      case Nil                                      => Left(Problem.NoHeadings)
      case (first, _) :: _ if first != "Unreleased" => Left(Problem.UnreleasedNotFirst(first))
      case (_, unreleased) :: releases              =>
        releases
          .foldLeft[Either[Problem, List[Section]]](
            Right(List(Section(Heading.Unreleased, unreleased)))
          ) { (sofar, next) =>
            sofar.flatMap(done => release(next._1, done).map(Section(_, next._2) :: done))
          }
          .map(_.reverse)

  /** The section for `version`, then the files description with `{version}` replaced. */
  def notes(markdown: String, version: Version, files: Option[String]): Either[Problem, String] =
    sections(markdown).flatMap(found =>
      found.collectFirst {
        case Section(Heading.Release(v, _), text) if v == version => text
      } match
        case None                       => Left(Problem.NoSection(version))
        case Some(text) if text.isEmpty => Left(Problem.EmptySection(version))
        case Some(text)                 =>
          val described = files.map(f =>
            trimmed(f.replace("{version}", version.show).split("\r\n|\r|\n", -1).toList)
          )
          Right((text :: described.toList).mkString("", "\n\n", "\n"))
    )

  /** A release heading after the sections in `done` (the last one read first): its form, and a
    * version older than the release before it.
    */
  private def release(heading: String, done: List[Section]): Either[Problem, Heading] =
    heading match
      case s"$version - $date" =>
        (Version.parse(version), parseDate(date)) match
          case (Some(v), Some(d)) =>
            val newer = done.collectFirst { case Section(Heading.Release(previous, _), _) =>
              previous
            }
            if newer.exists(previous => summon[Ordering[Version]].lteq(previous, v)) then
              Left(Problem.OutOfOrder(heading))
            else Right(Heading.Release(v, d))
          case _ => Left(Problem.Malformed(heading))
      case _ => Left(Problem.Malformed(heading))

  private def parseDate(text: String): Option[LocalDate] =
    if text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") then Try(LocalDate.parse(text)).toOption
    else None

  /** The heading's text: its text and code, without the Markdown around them. */
  private def title(heading: MarkdownHeading): String =
    preorder(heading).collect {
      case text: Text => Option(text.getLiteral).getOrElse("")
      case code: Code => Option(code.getLiteral).getOrElse("")
    }.mkString

  /** The 0-based line a block starts on, and the last line it covers. */
  private def start(node: Node): Int =
    node.getSourceSpans.asScala.headOption.fold(0)(_.getLineIndex)

  private def end(node: Node): Int =
    node.getSourceSpans.asScala.lastOption.fold(0)(_.getLineIndex)

  private def children(node: Node): List[Node] =
    List.unfold(Option(node.getFirstChild))(child => child.map(c => (c, Option(c.getNext))))

  private def preorder(node: Node): List[Node] = node :: children(node).flatMap(preorder)

  /** The lines without the blank lines before and after them, joined by `\n`. */
  private def trimmed(lines: List[String]): String =
    lines.dropWhile(_.isBlank).reverse.dropWhile(_.isBlank).reverse.mkString("\n")
