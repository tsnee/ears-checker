package io.github.tsnee.ears.markdown

/** The operating systems the native build looks for cmark on. */
private[markdown] enum Platform derives CanEqual:
  case MacOs, Linux

/** One library the native parser tried and couldn't use. */
private[markdown] enum CmarkAttempt derives CanEqual:
  /** `dlopen` failed, with `dlerror`'s text. */
  case Unopened(path: String, error: String)

  /** It loaded, but its release (`0xMMmmpp`) is older than the oldest supported. */
  case TooOld(path: String, version: Int)

  /** It loaded, but lacks one of the functions the parser uses. */
  case MissingFunction(path: String, function: String)

/** Which cmark library to load on Scala Native, and what to say when there's none: pure, so it's
  * measured on the JVM; the native parser only lists directories and loads. cmark names its library
  * by full release (`libcmark.0.31.2.dylib`, `libcmark.so.0.30.0`), so it's found when the program
  * runs, whatever release is installed, not recorded when it's linked.
  *
  * Only absolute paths are loaded, apart from Linux's bare `libcmark.so`, which glibc resolves from
  * the system's own library directories; a library in the current directory would run as the user.
  * The loader-path variable can still lead the system's loader to the current directory: on Linux,
  * a relative or empty `LD_LIBRARY_PATH` entry when it resolves the bare name, so then the bare
  * name isn't tried; on macOS, a relative `DYLD_LIBRARY_PATH` entry even for an absolute path, so
  * then nothing is loaded (`loadable`).
  */
private[markdown] object CmarkLibrary:

  /** The oldest release the conformance suite runs on (CI's Ubuntu has 0.30.2). */
  private val oldest = 0x001e00

  /** Where to look, each once: the directories the loader-path variable names (as `:`-separated
    * text, when it's set), then Homebrew's prefix's `lib` (`HOMEBREW_PREFIX`, which `brew shellenv`
    * sets), then the platform's usual ones. Only absolute directories are kept.
    */
  def directories(
      platform: Platform,
      loaderPath: Option[String],
      homebrewPrefix: Option[String]
  ): List[String] =
    val usual = platform match
      case Platform.MacOs => List("/opt/homebrew/lib", "/usr/local/lib", "/opt/local/lib")
      case Platform.Linux =>
        List(
          "/usr/lib/x86_64-linux-gnu",
          "/usr/lib/aarch64-linux-gnu",
          "/usr/lib64",
          "/usr/lib",
          "/usr/local/lib"
        )
    val named = loaderPath.toList.flatMap(entries(platform, _)) ++ homebrewPrefix.map(_ + "/lib")
    (named.filter(_.startsWith("/")) ++ usual).distinct

  /** What to try loading, in order: on Linux the bare name (which the loader's cache resolves),
    * then, for each directory in `listing` (its path and the names of its files), its unversioned
    * library and its versioned ones, newest release first.
    */
  def candidates(platform: Platform, listing: List[(String, List[String])]): List[String] =
    val (bare, versioned) = platform match
      case Platform.MacOs => ("libcmark.dylib", """libcmark\.(\d+)\.(\d+)\.(\d+)\.dylib""".r)
      case Platform.Linux => ("libcmark.so", """libcmark\.so\.(\d+)\.(\d+)\.(\d+)""".r)
    val inDirectories = listing.flatMap { (directory, files) =>
      val releases = files
        .collect { case name @ versioned(major, minor, patch) =>
          (
            List[String | Null](major, minor, patch).flatMap(part =>
              Option(part).flatMap(_.toIntOption)
            ),
            name
          )
        }
        .sortBy(_._1)(using Ordering.Implicits.seqOrdering[List, Int].reverse)
        .map(_._2)
      (files.filter(_ == bare) ++ releases).map(name => s"$directory/$name")
    }
    val first = platform match
      case Platform.MacOs => Nil
      case Platform.Linux => List(bare)
    (first ++ inDirectories).distinct

  /** The entries of the loader-path variable (`LD_LIBRARY_PATH`, `DYLD_LIBRARY_PATH`) that would
    * let the system's loader search the current directory, each once. On Linux, a relative entry or
    * an empty one, which glibc reads as the current directory; on macOS, a relative entry only,
    * since dyld ignores empty ones. An empty value, like no value, is safe: both loaders ignore it.
    */
  def unsafeEntries(platform: Platform, value: Option[String]): List[String] =
    value
      .filter(_.nonEmpty)
      .toList
      .flatMap(entries(platform, _))
      .filter(entry => !entry.startsWith("/") && (platform == Platform.Linux || entry.nonEmpty))
      .distinct

  /** The variable's entries, empty ones included: glibc separates them with `:` or `;`, dyld with
    * `:` only (a `;` is part of a path name there).
    */
  private def entries(platform: Platform, value: String): List[String] = platform match
    case Platform.Linux => value.split("[:;]", -1).toList
    case Platform.MacOs => value.split(":", -1).toList

  /** What to try loading, given the loader-path variable: `candidates`, except that on Linux, while
    * the variable is unsafe, the bare name is left out (glibc resolves only a bare name through it,
    * and opens an absolute path as given); and that on macOS, while it's unsafe, nothing is tried
    * (dyld searches it first even for an absolute path, and the program can't change that).
    */
  def loadable(
      platform: Platform,
      listing: List[(String, List[String])],
      loaderPath: Option[String]
  ): Either[MarkdownUnavailable, List[String]] =
    val unsafe = unsafeEntries(platform, loaderPath)
    val all = candidates(platform, listing)
    platform match
      case _ if unsafe.isEmpty => Right(all)
      case Platform.Linux      => Right(all.filter(_.startsWith("/")))
      case Platform.MacOs      => Left(MarkdownUnavailable(relativeLibraryPath(unsafe), Nil))

  /** The first candidate `attempt` can use, trying no more after it; or every failed attempt. */
  def firstLoaded[A](
      candidates: List[String],
      attempt: String => Either[CmarkAttempt, A]
  ): Either[List[CmarkAttempt], A] =
    @annotation.tailrec
    def loop(rest: List[String], failed: List[CmarkAttempt]): Either[List[CmarkAttempt], A] =
      rest match
        case Nil                 => Left(failed.reverse)
        case candidate :: others =>
          attempt(candidate) match
            case Right(loaded) => Right(loaded)
            case Left(failure) => loop(others, failure :: failed)
    loop(candidates, Nil)

  /** Why Markdown can't be read, from every failed attempt: a library too old (the newest such
    * release), else one lacking a function, else none found; and each attempt described.
    */
  def unavailable(attempts: List[CmarkAttempt]): MarkdownUnavailable =
    val tooOldRelease = attempts.collect { case CmarkAttempt.TooOld(_, version) =>
      version
    }.maxOption
    val missing = attempts.collectFirst { case CmarkAttempt.MissingFunction(path, function) =>
      missingFunction(path, function)
    }
    MarkdownUnavailable(
      tooOldRelease.map(tooOld).orElse(missing).getOrElse(notFound),
      attempts.map(describe)
    )

  /** A loaded library, for the log. */
  def loaded(path: String, version: Int): String = s"cmark ${release(version)} at $path"

  /** Whether a release, as `cmark_version()` gives it (`0xMMmmpp`), is new enough. */
  def supported(version: Int): Boolean = version >= oldest

  private val install =
    "install it with brew install cmark, or apt install cmark on Debian and Ubuntu"

  /** The message when no cmark library loads. */
  val notFound: String =
    s"Markdown needs the cmark library (0.30 or later), which isn't installed; $install"

  /** The message when the cmark library that loaded is too old. */
  def tooOld(version: Int): String =
    s"Markdown needs the cmark library 0.30 or later, but cmark ${release(version)} is " +
      s"installed; $install"

  /** The message when the library that loaded lacks a function the parser uses. */
  def missingFunction(path: String, function: String): String =
    s"Markdown needs the cmark library (0.30 or later), but $path isn't a usable cmark (it has " +
      s"no $function); $install"

  /** The message when `DYLD_LIBRARY_PATH` has relative entries, so cmark isn't loaded. */
  def relativeLibraryPath(entries: List[String]): String =
    val named = entries.map(entry => s"'$entry'").mkString(", ")
    val (noun, what, fix) =
      if entries.sizeIs == 1 then ("the entry", "an absolute directory", "remove it or make it")
      else ("the entries", "absolute directories", "remove them or make them")
    s"cmark wasn't loaded: DYLD_LIBRARY_PATH has $noun $named, not $what, so macOS would look " +
      s"for cmark in a directory relative to the current one first; $fix absolute"

  private def describe(attempt: CmarkAttempt): String = attempt match
    case CmarkAttempt.Unopened(path, error)           => s"$path: $error"
    case CmarkAttempt.TooOld(path, version)           => s"$path: release ${release(version)}"
    case CmarkAttempt.MissingFunction(path, function) => s"$path: no $function"

  private def release(version: Int): String =
    s"${(version >> 16) & 0xff}.${(version >> 8) & 0xff}.${version & 0xff}"
