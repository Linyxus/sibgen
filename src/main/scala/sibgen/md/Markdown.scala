package sibgen.md

import sibgen.md.adt
import sibgen.md.commonmark.{Extensions, FromJava}

import org.commonmark.node as j

/** Top-level entry point for parsing markdown into the Scala ADT. */
object Markdown:

  /** Parse a markdown string into the [[sibgen.md.adt.Document]] ADT.
    *
    * GFM tables, strikethrough, task list items, footnotes, and autolink are always enabled.
    */
  def parse(text: String): adt.Document =
    FromJava.fromDocument(Extensions.parser.parse(text).asInstanceOf[j.Document])
