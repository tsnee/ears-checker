package io.github.tsnee.ears.cli

/** Which paths are OpenSpec's, decided from the path as given, split at `/` and at `\` (Windows'
  * separator, in a path typed there): an OpenSpec project keeps its specs and changes in a
  * directory named `openspec`, which is how OpenSpec itself finds them.
  */
private[cli] object OpenSpecPaths:

  /** A spec file: `spec.md` below one or more capability directories in the `specs` directory of an
    * `openspec` directory, of one of its changes, or of an archived change.
    */
  def isSpec(path: String): Boolean =
    afterOpenSpec(path).exists {
      case "specs" :: below                                => capabilitySpec(below)
      case "changes" :: "archive" :: _ :: "specs" :: below => capabilitySpec(below)
      case "changes" :: change :: "specs" :: below => change != "archive" && capabilitySpec(below)
      case _                                       => false
    }

  /** Whether a directory is an `openspec` directory, or below its `specs` or `changes`, so only
    * spec files are checked in it. Another directory below one named `openspec` (`openspec/docs`,
    * in a clone of the OpenSpec tool's own repository) isn't OpenSpec's layout, and is walked as
    * any other.
    */
  def inside(directory: String): Boolean =
    afterOpenSpec(directory).exists {
      case Nil                        => true
      case ("specs" | "changes") :: _ => true
      case _                          => false
    }

  /** The archive of an `openspec` directory's changes, skipped when a walk comes to it. */
  def isArchive(path: String): Boolean =
    afterOpenSpec(path).contains(List("changes", "archive"))

  /** What follows each directory named `openspec` in the path. */
  private def afterOpenSpec(path: String): List[List[String]] =
    components(path).tails.collect { case "openspec" :: rest => rest }.toList

  /** One or more capability directories, then `spec.md`. */
  private def capabilitySpec(below: List[String]): Boolean =
    below.size >= 2 && below.lastOption.contains("spec.md")

  private def components(path: String): List[String] =
    path.split("[/\\\\]").toList.filter(_.nonEmpty)
