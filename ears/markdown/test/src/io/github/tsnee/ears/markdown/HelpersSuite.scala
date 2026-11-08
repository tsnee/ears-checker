package io.github.tsnee.ears.markdown

import zio.test.*

/** The pure helpers behind the parsers, on every platform: `CmarkLines` (cmark's lines, which only
  * the native parser uses) and `Walk`; `SourceLinesSuite` tests the Node parser's helper.
  */
object HelpersSuite extends ZIOSpecDefault:

  private def textAt(text: String, start: Int, end: Int) =
    CmarkPiece.Inline(text, false, start, end)

  private def lines(paragraph: Paragraph): List[Option[Int]] = paragraph.pieces.map(_.line)

  def spec = suite("Markdown helpers")(
    suite("CmarkLines")(
      test("after a hard break cmark doesn't advance; the stretch after it is on the next line"):
        val found = CmarkLines.paragraph(
          List(textAt("Intro.", 1, 1), CmarkPiece.Break, textAt("The scheduler", 1, 1)),
          2
        )
        assertTrue(found.line == 1, lines(found) == List(Some(1), None, Some(2)))
      ,
      test("after a reference definition everything is a line early; the end line puts it right"):
        val found = CmarkLines.paragraph(List(textAt("The scheduler", 1, 1)), 2)
        assertTrue(found.line == 2, lines(found) == List(Some(2)))
      ,
      test("a code span across lines keeps its stretch's lines relative to each other"):
        val found = CmarkLines.paragraph(
          List(
            textAt("The ", 1, 1),
            CmarkPiece.Inline("a b", true, 1, 2),
            textAt(" c.", 2, 2),
            CmarkPiece.Break,
            textAt("The scheduler", 3, 3)
          ),
          3
        )
        assertTrue(
          found.line == 1,
          lines(found) == List(Some(1), Some(1), Some(2), None, Some(3)),
          found.pieces(1).code
        )
      ,
      test("a paragraph with no pieces is on its end line"):
        assertTrue(CmarkLines.paragraph(Nil, 4) == Paragraph(4, Nil))
      ,
      test("a break before any text starts a stretch of its own"):
        val found = CmarkLines.paragraph(List(CmarkPiece.Break, textAt("x", 1, 1)), 2)
        assertTrue(found.line == 1, lines(found) == List(None, Some(2)))
    ),
    suite("Walk")(
      test("every node, root first, in document order"):
        val tree = Map(1 -> List(2, 5), 2 -> List(3, 4), 5 -> List(6))
        assertTrue(Walk.preorder[Int](1, tree.getOrElse(_, Nil)) == List(1, 2, 3, 4, 5, 6))
      ,
      test("a chain 100,000 deep, without running out of stack"):
        val depth = 100000
        val found = Walk.preorder[Int](0, n => if n < depth then List(n + 1) else Nil)
        assertTrue(found.size == depth + 1, found.lastOption.contains(depth))
    ),
    suite("CmarkLibrary")(
      test("macOS: no bare name, only each directory's unversioned and versioned files"):
        val listing = List(
          "/opt/homebrew/lib" ->
            List("libcmark.0.30.3.dylib", "libcmark.dylib", "libcmark.0.31.2.dylib", "libz.dylib")
        )
        assertTrue(
          CmarkLibrary.candidates(Platform.MacOs, listing) == List(
            "/opt/homebrew/lib/libcmark.dylib",
            "/opt/homebrew/lib/libcmark.0.31.2.dylib",
            "/opt/homebrew/lib/libcmark.0.30.3.dylib"
          )
        )
      ,
      test("Linux: a runtime package's versioned file, with no unversioned one"):
        val listing = List("/usr/lib/x86_64-linux-gnu" -> List("libcmark.so.0.30.0", "libc.so.6"))
        assertTrue(
          CmarkLibrary.candidates(Platform.Linux, listing) ==
            List("libcmark.so", "/usr/lib/x86_64-linux-gnu/libcmark.so.0.30.0")
        )
      ,
      test("versions are compared as numbers; other libraries' files are ignored"):
        val listing = List(
          "/usr/lib" ->
            List(
              "libcmark.so.0.31.9",
              "libcmark-gfm.so.0.29.0",
              "libcmark.so.0.31.10",
              "libcmarkx.so"
            )
        )
        assertTrue(
          CmarkLibrary.candidates(Platform.Linux, listing) ==
            List("libcmark.so", "/usr/lib/libcmark.so.0.31.10", "/usr/lib/libcmark.so.0.31.9")
        )
      ,
      test("directories: the loader path's, Homebrew's prefix, then the usual ones, each once"):
        val macOs = CmarkLibrary.directories(
          Platform.MacOs,
          Some("/opt/x/lib::/usr/local/lib"),
          Some("/Users/sam/brew")
        )
        assertTrue(
          macOs == List(
            "/opt/x/lib",
            "/usr/local/lib",
            "/Users/sam/brew/lib",
            "/opt/homebrew/lib",
            "/opt/local/lib"
          ),
          CmarkLibrary.directories(Platform.Linux, None, None).contains("/usr/lib/x86_64-linux-gnu")
        )
      ,
      test("directories: relative loader-path entries and a relative prefix are ignored"):
        assertTrue(
          CmarkLibrary.directories(Platform.MacOs, Some(".:lib:/opt/x/lib"), Some("brew")) ==
            List("/opt/x/lib", "/opt/homebrew/lib", "/usr/local/lib", "/opt/local/lib")
        )
      ,
      test("every candidate is an absolute path, apart from Linux's bare name"):
        val entry = Gen.elements("", ".", "lib", "./lib", "../x", "/opt/x/lib", "/usr/lib")
        val files = Gen.listOf(
          Gen.elements(
            "libcmark.dylib",
            "libcmark.0.31.2.dylib",
            "libcmark.so",
            "libcmark.so.0.30.0",
            "libz.dylib"
          )
        )
        check(
          Gen.elements(Platform.MacOs, Platform.Linux),
          Gen.option(Gen.listOf(entry).map(_.mkString(":"))),
          Gen.option(Gen.elements("", ".", "brew", "/opt/brew")),
          files
        ) { (platform, loaderPath, prefix, names) =>
          val listing =
            CmarkLibrary.directories(platform, loaderPath, prefix).map(_ -> names)
          val found = CmarkLibrary.candidates(platform, listing)
          val relative = found.filterNot(_.startsWith("/"))
          assertTrue(
            platform match
              case Platform.MacOs => relative.isEmpty
              case Platform.Linux => relative == List("libcmark.so")
          )
        }
      ,
      test("the first candidate that loads is used, and the ones after it aren't needed"):
        val attempt: String => Either[CmarkAttempt, String] =
          case "/b" => Right("loaded /b")
          case path => Left(CmarkAttempt.Unopened(path, "no file"))
        assertTrue(
          CmarkLibrary.firstLoaded(List("/a", "/b", "/c"), attempt) == Right("loaded /b"),
          CmarkLibrary.firstLoaded(List("/a", "/c"), attempt) ==
            Left(
              List(CmarkAttempt.Unopened("/a", "no file"), CmarkAttempt.Unopened("/c", "no file"))
            )
        )
      ,
      test("without a usable library: too old over a missing function over not found"):
        val tooOld = CmarkAttempt.TooOld("/usr/local/lib/libcmark.so", 0x001d00)
        val older = CmarkAttempt.TooOld("/usr/lib/libcmark.so.0.28.0", 0x001c00)
        val missing = CmarkAttempt.MissingFunction("/opt/x/libcmark.so", "cmark_node_free")
        val unopened = CmarkAttempt.Unopened("libcmark.so", "cannot open shared object file")
        assertTrue(
          CmarkLibrary.unavailable(List(unopened, older, missing, tooOld)).message ==
            CmarkLibrary.tooOld(0x001d00),
          CmarkLibrary.unavailable(List(unopened, missing)).message ==
            CmarkLibrary.missingFunction("/opt/x/libcmark.so", "cmark_node_free"),
          CmarkLibrary.unavailable(List(unopened)).message == CmarkLibrary.notFound,
          CmarkLibrary.unavailable(Nil).message == CmarkLibrary.notFound
        )
      ,
      test("without a usable library, every attempt is described"):
        val found = CmarkLibrary.unavailable(
          List(
            CmarkAttempt.Unopened("libcmark.so", "cannot open shared object file"),
            CmarkAttempt.TooOld("/usr/local/lib/libcmark.so", 0x001d00),
            CmarkAttempt.MissingFunction("/opt/x/libcmark.so", "cmark_node_free")
          )
        )
        assertTrue(
          found.attempts == List(
            "libcmark.so: cannot open shared object file",
            "/usr/local/lib/libcmark.so: release 0.29.0",
            "/opt/x/libcmark.so: no cmark_node_free"
          )
        )
      ,
      test("a loaded library is named by release and path"):
        assertTrue(
          CmarkLibrary.loaded("/opt/homebrew/lib/libcmark.dylib", 0x001f02) ==
            "cmark 0.31.2 at /opt/homebrew/lib/libcmark.dylib"
        )
      ,
      test("releases from 0.30.0 are supported"):
        assertTrue(
          CmarkLibrary.supported(0x001e00),
          CmarkLibrary.supported(0x001f02),
          !CmarkLibrary.supported(0x001d00)
        )
      ,
      test("the messages say what to install, and which release was found"):
        assertTrue(
          CmarkLibrary.notFound.contains("brew install cmark"),
          CmarkLibrary.notFound.contains("apt install cmark"),
          CmarkLibrary.tooOld(0x001d00).contains("0.29.0"),
          CmarkLibrary.tooOld(0x001d00).contains("brew install cmark"),
          CmarkLibrary
            .missingFunction("/opt/x/libcmark.so", "cmark_node_free")
            .contains("/opt/x/libcmark.so"),
          CmarkLibrary
            .missingFunction("/opt/x/libcmark.so", "cmark_node_free")
            .contains("brew install cmark")
        )
    ),
    suite("library path safety")(
      test("unsafeEntries: unset, empty, and all-absolute values are safe on both platforms"):
        val safe = List(None, Some(""), Some("/opt/lib"), Some("/opt/lib:/usr/local/lib"))
        assertTrue(
          safe.forall(value =>
            CmarkLibrary.unsafeEntries(Platform.Linux, value).isEmpty &&
              CmarkLibrary.unsafeEntries(Platform.MacOs, value).isEmpty
          )
        )
      ,
      test("unsafeEntries on Linux: relative entries and empty ones, each once"):
        assertTrue(
          CmarkLibrary.unsafeEntries(Platform.Linux, Some(":/opt/lib")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some("/opt/lib:")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some(":")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some(".")) == List("."),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some("lib::/opt/lib:lib")) == List("lib", "")
        )
      ,
      test("unsafeEntries on Linux: a semicolon separates entries too, as in glibc"):
        assertTrue(
          CmarkLibrary.unsafeEntries(Platform.Linux, Some("/opt/lib;")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some("/opt/lib;.")) == List("."),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some(";/opt/lib")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some(";")) == List(""),
          CmarkLibrary.unsafeEntries(Platform.Linux, Some("/opt/lib;/srv/lib")).isEmpty
        )
      ,
      test("unsafeEntries on macOS: a semicolon is part of a path, as dyld reads it"):
        assertTrue(CmarkLibrary.unsafeEntries(Platform.MacOs, Some("/opt/lib;.")).isEmpty)
      ,
      test("directories on Linux: entries split at semicolons as well as colons"):
        assertTrue(
          CmarkLibrary
            .directories(Platform.Linux, Some("/opt/lib;/srv/lib"), None)
            .take(2) == List("/opt/lib", "/srv/lib")
        )
      ,
      test("unsafeEntries on macOS: relative entries only, since dyld ignores empty ones"):
        assertTrue(
          CmarkLibrary.unsafeEntries(Platform.MacOs, Some(":/opt/lib")).isEmpty,
          CmarkLibrary.unsafeEntries(Platform.MacOs, Some(":")).isEmpty,
          CmarkLibrary.unsafeEntries(Platform.MacOs, Some(".")) == List("."),
          CmarkLibrary.unsafeEntries(Platform.MacOs, Some("/opt/lib:lib:")) == List("lib")
        )
      ,
      test("loadable on Linux: the bare name only while LD_LIBRARY_PATH is safe"):
        val listing = List("/usr/lib/x86_64-linux-gnu" -> List("libcmark.so.0.30.2"))
        val all = List("libcmark.so", "/usr/lib/x86_64-linux-gnu/libcmark.so.0.30.2")
        assertTrue(
          CmarkLibrary.loadable(Platform.Linux, listing, None) == Right(all),
          CmarkLibrary.loadable(Platform.Linux, listing, Some("/opt/lib")) == Right(all),
          CmarkLibrary.loadable(Platform.Linux, listing, Some(":/opt/lib")) ==
            Right(List("/usr/lib/x86_64-linux-gnu/libcmark.so.0.30.2"))
        )
      ,
      test("loadable on macOS: nothing to try while DYLD_LIBRARY_PATH has a relative entry"):
        val listing = List("/opt/homebrew/lib" -> List("libcmark.dylib"))
        val declined = CmarkLibrary.loadable(Platform.MacOs, listing, Some("/opt/lib:."))
        assertTrue(
          CmarkLibrary.loadable(Platform.MacOs, listing, Some(":/opt/lib")) ==
            Right(List("/opt/homebrew/lib/libcmark.dylib")),
          declined.left.exists(unavailable =>
            unavailable.attempts.isEmpty &&
              unavailable.message.contains("DYLD_LIBRARY_PATH") &&
              unavailable.message.contains("'.'")
          )
        )
      ,
      test("the macOS message names every relative entry"):
        val message = CmarkLibrary.relativeLibraryPath(List(".", "lib"))
        assertTrue(
          message.contains("'.'"),
          message.contains("'lib'"),
          message.contains("relative to the current one")
        )
    ),
    suite("Extraction")(
      test("code without backticks: one backtick each side"):
        assertTrue(Extraction.codeSpan("notes.md") == "`notes.md`")
      ,
      test("code holding a run of backticks: a fence one longer"):
        assertTrue(
          Extraction.codeSpan("a ` b") == "``a ` b``",
          Extraction.codeSpan("a``b") == "```a``b```"
        )
      ,
      test("code starting or ending with a backtick: a space of padding each side"):
        assertTrue(
          Extraction.codeSpan("`a") == "`` `a ``",
          Extraction.codeSpan("a`") == "`` a` ``"
        )
      ,
      test("text: each backtick escaped, other backslashes left alone"):
        assertTrue(Extraction.escapedText("a `b` \\c", beforeCode = false) == "a \\`b\\` \\c")
      ,
      test("text: backslashes right before a backtick are doubled"):
        assertTrue(
          Extraction.escapedText("\\`", beforeCode = false) == "\\\\\\`",
          Extraction.escapedText("\\\\`", beforeCode = false) == "\\\\\\\\\\`"
        )
      ,
      test("text before code: its trailing backslashes are doubled"):
        assertTrue(
          Extraction.escapedText("C:\\", beforeCode = true) == "C:\\\\",
          Extraction.escapedText("C:\\", beforeCode = false) == "C:\\"
        )
      ,
      test("a code span written by codeSpan closes only at its end, by the classifier's rule"):
        check(Gen.string1(Gen.elements('a', '`', ' ', '.'))) { literal =>
          val written = Extraction.codeSpan(literal)
          val fence = written.takeWhile(_ == '`')
          val inner = written.drop(fence.length).dropRight(fence.length)
          val runs = "`+".r.findAllIn(inner).map(_.length).toList
          assertTrue(
            written.endsWith(fence),
            fence.length == runs.maxOption.getOrElse(0) + 1,
            !inner.startsWith("`"),
            !inner.endsWith("`"),
            !runs.contains(fence.length)
          )
        }
    )
  )
