package sibgen.sitegen

import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.SimpleName
import dotty.tools.dotc.parsing.Scanners.Scanner
import dotty.tools.dotc.parsing.Tokens
import dotty.tools.dotc.reporting.StoreReporter
import dotty.tools.dotc.util.SourceFile

/** Build-time syntax highlighter for Scala 3 code blocks. Drives the actual `dotty.tools.dotc`
  * `Scanner` to produce token ranges, then a regex pass picks up comments. Output is the
  * inner contents of `<code>` — a sequence of HTML-escaped text and `<span class="hl-X">…</span>`
  * tagged ranges.
  *
  * Falls back to plain-escaped output on any exception so highlighting never blocks rendering.
  */
object Highlighter:

  /** Highlight `code` and return inner-`<code>` HTML (text + `<span>` ranges). */
  def highlight(code: String): String =
    try renderRanges(code, collectRanges(code))
    catch case _: Throwable => escape(code)

  // ---- one-time Scanner context ----

  private lazy val scannerContext: Context =
    val base = new ContextBase
    val ctx  = base.initialCtx.fresh
    ctx.setReporter(new StoreReporter())
    ctx

  // ---- pipeline ----

  private final case class Range(start: Int, end: Int, cls: String)

  private def collectRanges(code: String): List[Range] =
    val tokens   = scanTokens(code)
    val comments = scanComments(code, tokens)
    (tokens ::: comments).sortBy(_.start)

  private def scanTokens(code: String): List[Range] =
    given Context = scannerContext
    val source  = SourceFile.virtual("snippet.scala", code)
    val scanner = new Scanner(source)
    val buf     = scala.collection.mutable.ListBuffer.empty[Range]
    while scanner.token != Tokens.EOF do
      val tok       = scanner.token
      val start     = scanner.offset
      val nameOpt   = Option(scanner.name).map(_.asInstanceOf[SimpleName])
      scanner.nextToken()
      val end       = scanner.lastOffset
      if end > start then
        classify(tok, nameOpt).foreach(cls => buf += Range(start, end, cls))
    buf.toList

  private def classify(tok: Int, name: Option[SimpleName]): Option[String] =
    import Tokens.*
    if alphaKeywords.contains(tok) then Some("hl-kw")
    else tok match
      case INTLIT | LONGLIT | FLOATLIT | DOUBLELIT | DECILIT | EXPOLIT      => Some("hl-num")
      case CHARLIT | STRINGLIT | STRINGPART | INTERPOLATIONID               => Some("hl-str")
      case EQUALS | LARROW | ARROW | CTXARROW | SUBTYPE | SUPERTYPE
         | VIEWBOUND | AT | HASH | TLARROW                                  => Some("hl-op")
      case COMMA | SEMI | DOT | COLONop | COLONfollow
         | LPAREN | RPAREN | LBRACKET | RBRACKET | LBRACE | RBRACE          => Some("hl-punct")
      case IDENTIFIER =>
        name.flatMap { n =>
          val s = n.toString
          if softModifierNames.contains(n) then Some("hl-soft")
          else if ContextualKeywords.contains(s) then Some("hl-kw")
          else None
        }
      case _ => None

  /** Words the parser treats as keywords in context but the Scanner emits as IDENTIFIER
    * (not in `Tokens.softModifierNames` either). Highlight as full keywords for readability.
    */
  private val ContextualKeywords: Set[String] = Set("extension", "using", "derives")

  // Comments aren't emitted as tokens — sweep them with regex over a string-masked copy
  // so `//` and `/*` inside string literals are ignored.
  private val LineCommentRx  = """//[^\n]*""".r
  private val BlockCommentRx = """(?s)/\*.*?\*/""".r

  private def scanComments(code: String, tokenRanges: List[Range]): List[Range] =
    val masked = mask(code, tokenRanges.filter(_.cls == "hl-str"))
    val buf    = scala.collection.mutable.ListBuffer.empty[Range]
    LineCommentRx.findAllMatchIn(masked).foreach(m  => buf += Range(m.start, m.end, "hl-comment"))
    BlockCommentRx.findAllMatchIn(masked).foreach(m => buf += Range(m.start, m.end, "hl-comment"))
    buf.toList

  private def mask(code: String, exclude: List[Range]): String =
    val arr = code.toCharArray
    exclude.foreach { r =>
      var i = r.start
      while i < r.end && i < arr.length do
        if arr(i) != '\n' then arr(i) = ' '
        i += 1
    }
    new String(arr)

  // ---- emit ----

  private def renderRanges(code: String, ranges: List[Range]): String =
    val sb     = StringBuilder()
    var cursor = 0
    ranges.foreach { r =>
      val start = r.start.max(cursor)        // skip overlaps (shouldn't happen, defensive)
      if start > cursor then escAppend(code.substring(cursor, start), sb)
      if r.end > start then
        sb.append("<span class=\"").append(r.cls).append("\">")
        escAppend(code.substring(start, r.end), sb)
        sb.append("</span>")
        cursor = r.end
    }
    if cursor < code.length then escAppend(code.substring(cursor), sb)
    sb.toString

  private def escape(s: String): String =
    val sb = StringBuilder(s.length)
    escAppend(s, sb)
    sb.toString

  private def escAppend(s: String, sb: StringBuilder): Unit =
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '&' => sb.append("&amp;")
        case '<' => sb.append("&lt;")
        case '>' => sb.append("&gt;")
        case c   => sb.append(c)
      i += 1
