package io.github.tsnee.ears.cli

/** Why an entry of a directory isn't checked. */
enum Skipped derives CanEqual:
  /** Its name starts with a period: `.git`, `.github`'s templates, editors' lock files. */
  case Hidden

  /** A symbolic link, which could loop or lead out of the directory. */
  case Link

  /** Neither a file, a directory, nor a link: reading a named pipe could block. */
  case Other

  /** A file whose name ends in neither .md nor .txt. */
  case NotRequirements

/** What becomes of one entry of a directory, by its path: the directory as given, a slash, and the
  * entry's name.
  */
enum Step derives CanEqual:
  case Check(path: String)
  case Enter(path: String)
  case Skip(path: String, reason: Skipped)

object Step:

  extension (step: Step)
    def path: String = step match
      case Check(path)   => path
      case Enter(path)   => path
      case Skip(path, _) => path

    def reason: Option[Skipped] = step match
      case Skip(_, reason) => Some(reason)
      case _               => None

/** A directory's steps for its log entry: how many files are checked, subdirectories entered, and
  * entries skipped, and the skipped entries that get a line each, with why.
  */
final case class Listing(
    files: Int,
    subdirectories: Int,
    skipped: Int,
    noted: List[(String, String)]
) derives CanEqual

/** Which entries of a directory are checked, in which order, and by what names: decided here, the
  * same on every platform, from the entries a platform lists.
  */
object DirectoryWalk:

  /** The name endings of the files checked, each read as a file named on the command line would be:
    * Markdown and plain text. JSON in a project is mostly not OpenSpec (`package.json`).
    */
  private val checked = List(".md", ".txt")

  /** One step per entry, in order of the entries' names. The names are compared by UTF-16 code unit
    * (`String`'s ordering), the same on every platform, rather than in the order the platform lists
    * them, which depends on the file system, or by a locale's collation, which depends on the
    * platform's version. A subdirectory's step comes where its name sorts, so its files do too.
    *
    * Hidden entries, links, and anything not a file or a directory are skipped: a link could loop
    * or lead out of the directory (a repository's `notes.md -> ~/.aws/credentials` would be quoted
    * in findings), and a named pipe could block a read.
    */
  def steps(directory: String, entries: List[Entry]): List[Step] =
    entries.sortBy(_.name).map { entry =>
      val path = child(directory, entry.name)
      if entry.name.startsWith(".") then Step.Skip(path, Skipped.Hidden)
      else
        entry.kind match
          case EntryKind.Directory => Step.Enter(path)
          case EntryKind.Link      => Step.Skip(path, Skipped.Link)
          case EntryKind.Other     => Step.Skip(path, Skipped.Other)
          case EntryKind.File      =>
            if checked.exists(entry.name.endsWith) then Step.Check(path)
            else Step.Skip(path, Skipped.NotRequirements)
    }

  /** The steps counted, for the log. Links and other entries each get a line: they're rare, and
    * what an operator would ask about. Hidden entries and other files are only counted, since they
    * can number thousands (`.git`, a build folder).
    */
  def summary(steps: List[Step]): Listing =
    Listing(
      files = steps.count {
        case Step.Check(_) => true
        case _             => false
      },
      subdirectories = steps.count {
        case Step.Enter(_) => true
        case _             => false
      },
      skipped = steps.count(_.reason.isDefined),
      noted = steps.collect {
        case Step.Skip(path, Skipped.Link)  => path -> "link"
        case Step.Skip(path, Skipped.Other) => path -> "not a file or directory"
      }
    )

  /** The message for a directory given that holds no file to check. */
  def nothingToCheck(directory: String): String =
    s"Cannot check $directory: it holds no ${checked.mkString(" or ")} files"

  /** The path of `name` in `directory`: joined with a slash, which every platform reads as a
    * separator, so names are the same everywhere; none is added after a directory given with one.
    */
  def child(directory: String, name: String): String =
    if directory.endsWith("/") then directory + name else s"$directory/$name"
