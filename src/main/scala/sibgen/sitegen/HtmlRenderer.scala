package sibgen.sitegen

import sibgen.md.adt

/** Scala-native HTML renderer. Walks the [[sibgen.md.adt]] tree and emits CommonMark-shaped HTML.
  * No dependency on commonmark-java's `HtmlRenderer`.
  */
object HtmlRenderer:

  /** Default `data-snippet-id` for Scala snippets that aren't tagged with a `<!--% snippetId … -->`
    * directive — they all share one compilation channel so plain prose-style pages "just work". */
  val DefaultSnippetId: String = "default-global-snippet"

  /** Render the document body (no `<html>`/`<head>`/`<body>` wrapper). */
  def render(doc: adt.Document): String =
    val sb = StringBuilder()
    // Pre-pass: collect every `<!--% global scalacOptions … -->` directive at top level so the
    // resulting flag list applies to every Scala snippet on the page, regardless of whether the
    // snippet appears before or after the directive.
    val state = State(collectGlobalScalacOptionss(doc))
    doc.children.foreach(b => renderBlock(b, sb, state))
    if state.footnotes.nonEmpty then renderFootnotes(sb, state)
    sb.toString

  // ---------- state ----------

  private final class State(val globalScalacOptions: Vector[String]):
    /** Footnote definitions, in the order their references first appeared. */
    val footnotes = collection.mutable.LinkedHashMap.empty[String, adt.FootnoteDefinition]
    /** Number assigned to each label, in reference order. */
    val refIndex  = collection.mutable.LinkedHashMap.empty[String, Int]
    /** Snippet id parsed from the most recent `<!--% snippetId X -->` directive in the current
      * directive stack. Cleared by `renderBlock` after every render so only the *immediately*
      * following block (chain of directive comments) can consume it. */
    var pendingSnippetId: Option[String] = None
    /** Compiler options accumulated from `<!--% scalacOptions ... -->` directive(s) in the
      * current directive stack. Multiple directives concatenate in source order. */
    var pendingScalacOptions: Vector[String] = Vector.empty

    /** Index for `label`, registering it on first use. */
    def indexOf(label: String): Int =
      refIndex.getOrElseUpdate(label, refIndex.size + 1)

  private enum Directive:
    case SnippetId(id: String)
    case ScalacOptions(options: Vector[String])
    case GlobalScalacOptions(options: Vector[String])

  private val SnippetIdDirectiveRx          = """^\s*<!--%\s+snippetId\s+(\S+)\s*-->\s*$""".r
  private val ScalacOptionsDirectiveRx      = """^\s*<!--%\s+scalacOptions\s+(.+?)\s*-->\s*$""".r
  private val GlobalScalacOptionsDirectiveRx = """^\s*<!--%\s+global\s+scalacOptions\s+(.+?)\s*-->\s*$""".r
  private val DirectivePrefixRx             = """^\s*<!--%\s""".r

  private def parseDirective(literal: String): Option[Directive] =
    val s = literal.stripSuffix("\n")
    SnippetIdDirectiveRx.findFirstMatchIn(s).map(m => Directive.SnippetId(m.group(1)))
      .orElse(ScalacOptionsDirectiveRx.findFirstMatchIn(s).map { m =>
        Directive.ScalacOptions(m.group(1).trim.split("""\s+""").toVector.filter(_.nonEmpty))
      })
      .orElse(GlobalScalacOptionsDirectiveRx.findFirstMatchIn(s).map { m =>
        Directive.GlobalScalacOptions(m.group(1).trim.split("""\s+""").toVector.filter(_.nonEmpty))
      })

  /** Walk the document's top-level blocks and gather every `global scalacOptions` directive's
    * flag list, in source order. Directives nested inside blockquotes / lists are not collected
    * — globals are a top-of-document construct. */
  private def collectGlobalScalacOptionss(doc: adt.Document): Vector[String] =
    val buf = Vector.newBuilder[String]
    doc.children.foreach {
      case h: adt.HtmlBlock =>
        parseDirective(h.literal) match
          case Some(Directive.GlobalScalacOptions(opts)) => buf ++= opts
          case _ => ()
      case _ => ()
    }
    buf.result()

  /** True if the literal looks like a `<!--% ... -->` directive comment, even if no specific
    * directive parser recognized it. Used to distinguish typo'd directives (drop silently,
    * preserve pending state) from ordinary HTML comments (pass through, clear pending state). */
  private def isDirectiveSyntax(literal: String): Boolean =
    DirectivePrefixRx.findFirstIn(literal.stripSuffix("\n")).isDefined

  // ---------- blocks ----------

  private def renderBlock(b: adt.Block, sb: StringBuilder, st: State): Unit =
    // Cache-then-clear both pendings: only the very next block can consume them. The HtmlBlock
    // case below selectively restores them so a chain of stacked directive comments accumulates,
    // while any non-directive intervening block (paragraph, ordinary HTML comment, …) breaks the
    // chain by leaving both pendings cleared.
    val pendingId   = st.pendingSnippetId
    val pendingOpts = st.pendingScalacOptions
    st.pendingSnippetId     = None
    st.pendingScalacOptions = Vector.empty
    b match
    case bq: adt.BlockQuote =>
      sb.append("<blockquote>\n")
      bq.children.foreach(c => renderBlock(c, sb, st))
      sb.append("</blockquote>\n")

    case h: adt.Heading =>
      sb.append("<h").append(h.level).append('>')
      renderInlines(h.content, sb, st)
      sb.append("</h").append(h.level).append(">\n")

    case p: adt.Paragraph =>
      sb.append("<p>")
      renderInlines(p.content, sb, st)
      sb.append("</p>\n")

    case _: adt.ThematicBreak =>
      sb.append("<hr />\n")

    case c: adt.IndentedCodeBlock =>
      sb.append("<pre><code>")
      escText(c.literal, sb)
      sb.append("</code></pre>\n")

    case c: adt.FencedCodeBlock =>
      val lang    = c.info.trim.takeWhile(!_.isWhitespace)
      val isScala = lang == "scala" || lang == "scala3"
      // commonmark-java preserves the newline immediately before the closing fence.
      // Stripping one keeps the rendered <pre> (and the CodeMirror view that swaps in
      // for it) from showing a phantom empty trailing line.
      val literal = c.literal.stripSuffix("\n")
      if lang == "mermaid" then
        // Raw source goes straight into <pre class="mermaid"> — mermaid reads textContent.
        sb.append("<pre class=\"mermaid\">")
        escText(literal, sb)
        sb.append("</pre>\n")
      else
        if isScala then
          val id = pendingId.getOrElse(DefaultSnippetId)
          sb.append("<div class=\"snippet snippet-scala\" data-snippet-id=\"")
            .append(escAttr(id)).append('"')
          // Page-wide globals come first, per-snippet pendings layer on top. Order matters
          // for compiler flags whose later-wins semantics let a snippet override a page setting.
          val combinedOpts = st.globalScalacOptions ++ pendingOpts
          if combinedOpts.nonEmpty then
            sb.append(" data-scalac-options=\"")
              .append(escAttr(combinedOpts.mkString(" "))).append('"')
          sb.append(">\n")
        sb.append("<pre><code")
        if lang.nonEmpty then sb.append(" class=\"language-").append(escAttr(lang)).append('"')
        sb.append('>')
        // Scala blocks render as plain text — CodeMirror 6 takes over on mount
        // and applies the .hl-* classes against the editable buffer. See
        // sibgen.sitegen.Page.snippetScript + js-src/codemirror/entry.ts.
        escText(literal, sb)
        sb.append("</code></pre>\n")
        if isScala then
          sb.append("<div class=\"snippet-strip\">\n")
          sb.append("<span class=\"snippet-status\" aria-live=\"polite\"></span>\n")
          sb.append("<button type=\"button\" class=\"snippet-check\" data-action=\"typecheck\">» type-check</button>\n")
          sb.append("<div class=\"snippet-detail\" hidden></div>\n")
          sb.append("</div>\n")
          sb.append("</div>\n")

    case h: adt.HtmlBlock =>
      // Recognized `<!--% ... -->` directives arm pending state and emit nothing; siblings are
      // preserved so a stack of directive comments accumulates. An unrecognized but
      // directive-shaped comment is also dropped silently with pendings preserved (so a typo
      // can't quietly break the chain). Ordinary HTML comments pass through and break the chain.
      parseDirective(h.literal) match
        case Some(Directive.SnippetId(id)) =>
          st.pendingSnippetId     = Some(id)
          st.pendingScalacOptions = pendingOpts
        case Some(Directive.ScalacOptions(opts)) =>
          st.pendingSnippetId     = pendingId
          st.pendingScalacOptions = pendingOpts ++ opts
        case Some(Directive.GlobalScalacOptions(_)) =>
          // Already collected in the pre-pass and threaded onto every Scala snippet via
          // st.globalScalacOptions. Just preserve sibling pendings so a directive stack with
          // a `global` line interleaved doesn't break the chain.
          st.pendingSnippetId     = pendingId
          st.pendingScalacOptions = pendingOpts
        case None =>
          if isDirectiveSyntax(h.literal) then
            st.pendingSnippetId     = pendingId
            st.pendingScalacOptions = pendingOpts
          else
            sb.append(h.literal)
            if !h.literal.endsWith("\n") then sb.append('\n')

    case bl: adt.BulletList =>
      sb.append("<ul>\n")
      bl.items.foreach(li => renderListItem(li, sb, st))
      sb.append("</ul>\n")

    case ol: adt.OrderedList =>
      sb.append("<ol")
      if ol.startNumber != 1 then sb.append(" start=\"").append(ol.startNumber).append('"')
      sb.append(">\n")
      ol.items.foreach(li => renderListItem(li, sb, st))
      sb.append("</ol>\n")

    case t: adt.Table =>
      renderTable(t, sb, st)

    case fd: adt.FootnoteDefinition =>
      // Defer: the section is emitted at the end, even if the def is encountered mid-stream.
      st.footnotes.update(fd.label, fd)

  private def renderListItem(li: adt.ListItem, sb: StringBuilder, st: State): Unit =
    sb.append("<li>")
    li.taskMarker match
      case Some(true)  => sb.append("<input type=\"checkbox\" disabled checked /> ")
      case Some(false) => sb.append("<input type=\"checkbox\" disabled /> ")
      case None        => ()
    // Tight items: unwrap a single Paragraph child to inline its content (matches CommonMark).
    li.children match
      case Vector(p: adt.Paragraph) =>
        renderInlines(p.content, sb, st)
      case blocks =>
        sb.append('\n')
        blocks.foreach(b => renderBlock(b, sb, st))
    sb.append("</li>\n")

  private def renderTable(t: adt.Table, sb: StringBuilder, st: State): Unit =
    sb.append("<table>\n")
    if t.head.rows.nonEmpty then
      sb.append("<thead>\n")
      t.head.rows.foreach(r => renderTableRow(r, sb, st))
      sb.append("</thead>\n")
    t.body.foreach { body =>
      if body.rows.nonEmpty then
        sb.append("<tbody>\n")
        body.rows.foreach(r => renderTableRow(r, sb, st))
        sb.append("</tbody>\n")
    }
    sb.append("</table>\n")

  private def renderTableRow(r: adt.TableRow, sb: StringBuilder, st: State): Unit =
    sb.append("<tr>\n")
    r.cells.foreach { c =>
      val tag = if c.header then "th" else "td"
      val align = c.alignment match
        case adt.Alignment.Left   => "left"
        case adt.Alignment.Center => "center"
        case adt.Alignment.Right  => "right"
      sb.append('<').append(tag).append(" align=\"").append(align).append("\">")
      renderInlines(c.content, sb, st)
      sb.append("</").append(tag).append(">\n")
    }
    sb.append("</tr>\n")

  private def renderFootnotes(sb: StringBuilder, st: State): Unit =
    sb.append("<section class=\"footnotes\" data-footnotes>\n<ol>\n")
    // Emit only the footnotes actually referenced in the text, in reference order.
    st.refIndex.keys.foreach { label =>
      st.footnotes.get(label) match
        case Some(fd) => renderFootnoteItem(label, fd, sb, st)
        case None     => () // referenced but undefined — skip silently
    }
    sb.append("</ol>\n</section>\n")

  private def renderFootnoteItem(label: String, fd: adt.FootnoteDefinition, sb: StringBuilder, st: State): Unit =
    sb.append("<li id=\"fn-").append(escAttr(label)).append("\">\n")
    // Render the children, then splice the backref into the trailing paragraph (matches commonmark-java).
    fd.children match
      case init :+ (p: adt.Paragraph) =>
        init.foreach(b => renderBlock(b, sb, st))
        sb.append("<p>")
        renderInlines(p.content, sb, st)
        sb.append(' ')
        renderBackref(label, sb)
        sb.append("</p>\n")
      case blocks =>
        blocks.foreach(b => renderBlock(b, sb, st))
        renderBackref(label, sb)
        sb.append('\n')
    sb.append("</li>\n")

  private def renderBackref(label: String, sb: StringBuilder): Unit =
    sb.append("<a href=\"#fnref-").append(escAttr(label))
      .append("\" class=\"footnote-backref\" aria-label=\"Back to reference\">↩</a>")

  // ---------- inlines ----------

  private def renderInlines(is: Vector[adt.Inline], sb: StringBuilder, st: State): Unit =
    is.foreach(i => renderInline(i, sb, st))

  private def renderInline(i: adt.Inline, sb: StringBuilder, st: State): Unit = i match
    case t: adt.Text =>
      escText(t.literal, sb)

    case c: adt.Code =>
      sb.append("<code>")
      escText(c.literal, sb)
      sb.append("</code>")

    case e: adt.Emphasis =>
      sb.append("<em>")
      renderInlines(e.content, sb, st)
      sb.append("</em>")

    case s: adt.StrongEmphasis =>
      sb.append("<strong>")
      renderInlines(s.content, sb, st)
      sb.append("</strong>")

    case l: adt.Link =>
      sb.append("<a href=\"").append(escAttr(l.destination)).append('"')
      l.title.foreach(t => sb.append(" title=\"").append(escAttr(t)).append('"'))
      sb.append('>')
      renderInlines(l.content, sb, st)
      sb.append("</a>")

    case img: adt.Image =>
      sb.append("<img src=\"").append(escAttr(img.destination)).append('"')
      sb.append(" alt=\"").append(escAttr(plainText(img.content))).append('"')
      img.title.foreach(t => sb.append(" title=\"").append(escAttr(t)).append('"'))
      sb.append(" />")

    case h: adt.HtmlInline =>
      sb.append(h.literal)

    case _: adt.HardLineBreak =>
      sb.append("<br />\n")

    case _: adt.SoftLineBreak =>
      sb.append('\n')

    case s: adt.Strikethrough =>
      sb.append("<del>")
      renderInlines(s.content, sb, st)
      sb.append("</del>")

    case fr: adt.FootnoteReference =>
      val idx = st.indexOf(fr.label)
      sb.append("<sup class=\"footnote-ref\"><a href=\"#fn-")
        .append(escAttr(fr.label))
        .append("\" id=\"fnref-")
        .append(escAttr(fr.label))
        .append("\">")
        .append(idx)
        .append("</a></sup>")

  // ---------- escaping ----------

  private def escText(s: String, sb: StringBuilder): Unit =
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '&' => sb.append("&amp;")
        case '<' => sb.append("&lt;")
        case '>' => sb.append("&gt;")
        case c   => sb.append(c)
      i += 1

  private def escAttr(s: String): String =
    val sb = StringBuilder(s.length)
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '&' => sb.append("&amp;")
        case '<' => sb.append("&lt;")
        case '>' => sb.append("&gt;")
        case '"' => sb.append("&quot;")
        case c   => sb.append(c)
      i += 1
    sb.toString

  private def plainText(is: Vector[adt.Inline]): String =
    val sb = StringBuilder()
    def go(xs: Vector[adt.Inline]): Unit = xs.foreach {
      case t: adt.Text              => sb.append(t.literal)
      case c: adt.Code              => sb.append(c.literal)
      case e: adt.Emphasis          => go(e.content)
      case s: adt.StrongEmphasis    => go(s.content)
      case l: adt.Link              => go(l.content)
      case s: adt.Strikethrough     => go(s.content)
      case _: adt.Image             => ()
      case _: adt.HtmlInline        => ()
      case _: adt.HardLineBreak     => sb.append(' ')
      case _: adt.SoftLineBreak     => sb.append(' ')
      case _: adt.FootnoteReference => ()
    }
    go(is)
    sb.toString
