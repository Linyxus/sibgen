package sibgen.sitegen

import sibgen.md.adt

/** Renders a parsed markdown ADT into HTML. */
object Renderer:

  /** Render the body HTML for a parsed document (no `<html>`/`<head>` wrapper). */
  def renderHtmlBody(doc: adt.Document): String =
    HtmlRenderer.render(doc)

  /** Render a complete, self-contained HTML page. CSS is bundled by esbuild on first call
    * (font woff2 inlined as a data URL) and cached for the rest of the process.
    * The vendored mermaid bundle is only inlined when the body actually contains a mermaid block.
    */
  def renderPage(doc: adt.Document, title: String): String =
    val body      = renderHtmlBody(doc)
    val mermaidJs = if body.contains("<pre class=\"mermaid\">") then Bundler.mermaidJs else ""
    Page.render(body, title, Bundler.bundledCss, mermaidJs)

  /** Extract a plain-text title from the document's first H1, if any. */
  def titleOf(doc: adt.Document): Option[String] =
    doc.children.collectFirst { case h: adt.Heading if h.level == 1 => plainText(h.content) }

  private def plainText(inlines: Vector[adt.Inline]): String =
    val sb = StringBuilder()
    def go(is: Vector[adt.Inline]): Unit = is.foreach {
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
    go(inlines)
    sb.toString
