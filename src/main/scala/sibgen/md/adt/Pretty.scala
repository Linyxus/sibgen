package sibgen.md.adt

/** Indented tree-style pretty-printer for the markdown ADT. Useful for debugging and CLI inspection. */
object Pretty:

  def show(node: Node): String =
    val sb = StringBuilder()
    render(node, 0, sb)
    sb.toString

  private def render(node: Node, level: Int, sb: StringBuilder): Unit =
    sb.append("  " * level)
    node match
      case d: Document =>
        sb.append(s"Document (${d.children.size} block(s), ${d.references.size} reference(s))\n")
        d.children.foreach(c => render(c, level + 1, sb))
        if d.references.nonEmpty then
          sb.append("  " * (level + 1)).append("references:\n")
          d.references.toSeq.sortBy(_._1).foreach { case (label, ref) =>
            sb.append("  " * (level + 2))
              .append(s"[$label] -> ${str(ref.destination)}")
              .append(ref.title.fold("")(t => s" title=${str(t)}"))
              .append("\n")
          }

      case n: BlockQuote =>
        sb.append("BlockQuote\n")
        n.children.foreach(c => render(c, level + 1, sb))

      case n: Heading =>
        sb.append(s"Heading[${n.level}]\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: Paragraph =>
        sb.append("Paragraph\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case _: ThematicBreak =>
        sb.append("ThematicBreak\n")

      case n: IndentedCodeBlock =>
        sb.append(s"IndentedCodeBlock ${literal(n.literal)}\n")

      case n: FencedCodeBlock =>
        sb.append(s"FencedCodeBlock info=${str(n.info)} ${literal(n.literal)}\n")

      case n: HtmlBlock =>
        sb.append(s"HtmlBlock ${literal(n.literal)}\n")

      case n: BulletList =>
        sb.append(s"BulletList marker='${n.marker}' tight=${n.tight}\n")
        n.items.foreach(li => render(li, level + 1, sb))

      case n: OrderedList =>
        sb.append(s"OrderedList start=${n.startNumber} delim='${n.delimiter}' tight=${n.tight}\n")
        n.items.foreach(li => render(li, level + 1, sb))

      case n: ListItem =>
        val task = n.taskMarker.fold("")(c => s" task=${if c then "[x]" else "[ ]"}")
        sb.append(s"ListItem$task\n")
        n.children.foreach(c => render(c, level + 1, sb))

      case n: Table =>
        sb.append(s"Table alignments=${n.alignments.mkString(",")}\n")
        render(n.head, level + 1, sb)
        n.body.foreach(b => render(b, level + 1, sb))

      case n: TableHead =>
        sb.append("TableHead\n")
        n.rows.foreach(r => render(r, level + 1, sb))

      case n: TableBody =>
        sb.append("TableBody\n")
        n.rows.foreach(r => render(r, level + 1, sb))

      case n: TableRow =>
        sb.append("TableRow\n")
        n.cells.foreach(c => render(c, level + 1, sb))

      case n: TableCell =>
        sb.append(s"TableCell align=${n.alignment}${if n.header then " header" else ""}\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: FootnoteDefinition =>
        sb.append(s"FootnoteDefinition[${n.label}]\n")
        n.children.foreach(c => render(c, level + 1, sb))

      case n: Text =>
        sb.append(s"Text ${str(n.literal)}\n")

      case n: Code =>
        sb.append(s"Code ${str(n.literal)}\n")

      case n: Emphasis =>
        sb.append(s"Emphasis '${n.delimiter}'\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: StrongEmphasis =>
        sb.append(s"StrongEmphasis '${n.delimiter}'\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: Link =>
        sb.append(s"Link -> ${str(n.destination)}${n.title.fold("")(t => s" title=${str(t)}")}\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: Image =>
        sb.append(s"Image -> ${str(n.destination)}${n.title.fold("")(t => s" title=${str(t)}")}\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: HtmlInline =>
        sb.append(s"HtmlInline ${str(n.literal)}\n")

      case _: HardLineBreak =>
        sb.append("HardLineBreak\n")

      case _: SoftLineBreak =>
        sb.append("SoftLineBreak\n")

      case n: Strikethrough =>
        sb.append(s"Strikethrough '${n.delimiter}'\n")
        n.content.foreach(c => render(c, level + 1, sb))

      case n: FootnoteReference =>
        sb.append(s"FootnoteReference[${n.label}]\n")

  private def str(s: String): String =
    "\"" + s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\t", "\\t") + "\""

  private def literal(s: String): String =
    if s.length > 60 then str(s.take(57)) + s" ... (${s.length} chars)"
    else str(s)
