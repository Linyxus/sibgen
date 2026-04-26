package sibgen.sitegen

import sibgen.md.adt
import sibgen.md.commonmark.{Extensions, ToJava}

import org.commonmark.renderer.html.HtmlRenderer

/** Renders a parsed markdown ADT into HTML. */
object Renderer:

  private val htmlRenderer: HtmlRenderer =
    HtmlRenderer.builder().extensions(Extensions.all).build()

  /** Render the body HTML for a parsed document (no `<html>`/`<head>` wrapper). */
  def renderHtmlBody(doc: adt.Document): String =
    htmlRenderer.render(ToJava.toDocument(doc))

  /** Render a complete, self-contained HTML page with the iA Writer theme inlined. */
  def renderPage(doc: adt.Document, title: String): String =
    Page.render(renderHtmlBody(doc), title)

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
