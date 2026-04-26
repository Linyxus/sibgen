package sibgen.md.commonmark

import sibgen.md.adt

import org.commonmark.node as j
import org.commonmark.ext.footnotes as jfn
import org.commonmark.ext.gfm.strikethrough as jst
import org.commonmark.ext.gfm.tables as jtb
import org.commonmark.ext.task.list.items as jtl

/** Converts a Scala `sibgen.md.adt` ADT tree back into a `org.commonmark.node.Node` tree.
  *
  * Source spans on the ADT are not propagated — commonmark-java only sets them as parser output.
  */
object ToJava:

  def toDocument(d: adt.Document): j.Document =
    val jd = j.Document()
    d.children.foreach(b => jd.appendChild(toBlock(b)))
    d.references.values.foreach { r =>
      jd.appendChild(j.LinkReferenceDefinition(r.label, r.destination, r.title.orNull))
    }
    jd

  def toBlock(b: adt.Block): j.Block = b match
    case adt.BlockQuote(_, children) =>
      val n = j.BlockQuote()
      children.foreach(c => n.appendChild(toBlock(c)))
      n

    case adt.Heading(_, level, content) =>
      val n = j.Heading()
      n.setLevel(level)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.Paragraph(_, content) =>
      val n = j.Paragraph()
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.ThematicBreak(_) =>
      j.ThematicBreak()

    case adt.IndentedCodeBlock(_, lit) =>
      val n = j.IndentedCodeBlock()
      n.setLiteral(lit)
      n

    case adt.FencedCodeBlock(_, fc, fl, fi, info, lit) =>
      val n = j.FencedCodeBlock()
      n.setFenceChar(fc)
      n.setOpeningFenceLength(fl)
      n.setClosingFenceLength(fl)
      n.setFenceIndent(fi)
      n.setInfo(info)
      n.setLiteral(lit)
      n

    case adt.HtmlBlock(_, lit) =>
      val n = j.HtmlBlock()
      n.setLiteral(lit)
      n

    case adt.BulletList(_, tight, marker, items) =>
      val n = j.BulletList()
      n.setTight(tight)
      n.setBulletMarker(marker)
      items.foreach(it => n.appendChild(toListItem(it)))
      n

    case adt.OrderedList(_, tight, start, delim, items) =>
      val n = j.OrderedList()
      n.setTight(tight)
      n.setStartNumber(start)
      n.setDelimiter(delim)
      items.foreach(it => n.appendChild(toListItem(it)))
      n

    case t: adt.Table =>
      toTable(t)

    case adt.FootnoteDefinition(_, label, children) =>
      val n = jfn.FootnoteDefinition(label)
      children.foreach(c => n.appendChild(toBlock(c)))
      n

  def toInline(i: adt.Inline): j.Node = i match
    case adt.Text(_, lit) => j.Text(lit)
    case adt.Code(_, lit) => j.Code(lit)

    case adt.Emphasis(_, d, content) =>
      val n = j.Emphasis(d.toString)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.StrongEmphasis(_, d, content) =>
      val n = j.StrongEmphasis(d.toString * 2)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.Link(_, dest, title, content) =>
      val n = j.Link(dest, title.orNull)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.Image(_, dest, title, content) =>
      val n = j.Image(dest, title.orNull)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.HtmlInline(_, lit) =>
      val n = j.HtmlInline()
      n.setLiteral(lit)
      n

    case adt.HardLineBreak(_) => j.HardLineBreak()
    case adt.SoftLineBreak(_) => j.SoftLineBreak()

    case adt.Strikethrough(_, delim, content) =>
      val n = jst.Strikethrough(delim)
      content.foreach(c => n.appendChild(toInline(c)))
      n

    case adt.FootnoteReference(_, label) =>
      jfn.FootnoteReference(label)

  // ---------- helpers ----------

  private def toListItem(li: adt.ListItem): j.ListItem =
    val n = j.ListItem()
    li.taskMarker.foreach(checked => n.appendChild(jtl.TaskListItemMarker(checked)))
    li.children.foreach(b => n.appendChild(toBlock(b)))
    n

  private def toTable(t: adt.Table): jtb.TableBlock =
    val tb   = jtb.TableBlock()
    val head = jtb.TableHead()
    t.head.rows.foreach(r => head.appendChild(toTableRow(r)))
    tb.appendChild(head)
    t.body.foreach { body =>
      val jb = jtb.TableBody()
      body.rows.foreach(r => jb.appendChild(toTableRow(r)))
      tb.appendChild(jb)
    }
    tb

  private def toTableRow(r: adt.TableRow): jtb.TableRow =
    val jr = jtb.TableRow()
    r.cells.foreach(c => jr.appendChild(toTableCell(c)))
    jr

  private def toTableCell(c: adt.TableCell): jtb.TableCell =
    val jc = jtb.TableCell()
    jc.setHeader(c.header)
    jc.setAlignment(toAlignment(c.alignment))
    c.content.foreach(in => jc.appendChild(toInline(in)))
    jc

  private def toAlignment(a: adt.Alignment): jtb.TableCell.Alignment = a match
    case adt.Alignment.Left   => jtb.TableCell.Alignment.LEFT
    case adt.Alignment.Center => jtb.TableCell.Alignment.CENTER
    case adt.Alignment.Right  => jtb.TableCell.Alignment.RIGHT
