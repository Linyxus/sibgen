package sibgen.md.commonmark

import sibgen.md.adt

import org.commonmark.node as j
import org.commonmark.ext.footnotes as jfn
import org.commonmark.ext.gfm.strikethrough as jst
import org.commonmark.ext.gfm.tables as jtb
import org.commonmark.ext.task.list.items as jtl

/** Converts a `org.commonmark.node.Node` tree into the Scala `sibgen.md.adt` ADT. */
object FromJava:

  /** Convert a parsed commonmark Document. Hoists link reference definitions out of the block tree. */
  def fromDocument(d: j.Document): adt.Document =
    val refs   = collectReferences(d)
    val blocks = childrenOf(d).collect { case b: j.Block if !b.isInstanceOf[j.LinkReferenceDefinition] => fromBlock(b) }.toVector
    adt.Document(spanOf(d), blocks, refs)

  /** Convert any block-level node. `LinkReferenceDefinition`s are not accepted here — they are handled by [[fromDocument]]. */
  def fromBlock(b: j.Block): adt.Block = b match
    case n: j.BlockQuote        => adt.BlockQuote(spanOf(n), childBlocks(n))
    case n: j.Heading           => adt.Heading(spanOf(n), n.getLevel, childInlines(n))
    case n: j.Paragraph         => adt.Paragraph(spanOf(n), childInlines(n))
    case n: j.ThematicBreak     => adt.ThematicBreak(spanOf(n))
    case n: j.IndentedCodeBlock => adt.IndentedCodeBlock(spanOf(n), n.getLiteral)
    case n: j.FencedCodeBlock =>
      adt.FencedCodeBlock(
        spanOf(n),
        fenceChar    = n.getFenceChar,
        fenceLength  = Option(n.getOpeningFenceLength).map(_.intValue).getOrElse(3),
        fenceIndent  = n.getFenceIndent,
        info         = Option(n.getInfo).getOrElse(""),
        literal      = n.getLiteral
      )
    case n: j.HtmlBlock     => adt.HtmlBlock(spanOf(n), n.getLiteral)
    case n: j.BulletList    => adt.BulletList(spanOf(n), n.isTight, n.getBulletMarker, childListItems(n))
    case n: j.OrderedList   => adt.OrderedList(spanOf(n), n.isTight, n.getStartNumber, n.getDelimiter, childListItems(n))
    case n: jtb.TableBlock  => fromTable(n)
    case n: jfn.FootnoteDefinition => adt.FootnoteDefinition(spanOf(n), n.getLabel, childBlocks(n))
    case _: j.LinkReferenceDefinition =>
      throw new IllegalStateException("LinkReferenceDefinition encountered in fromBlock; handled at Document level")
    case other =>
      throw new IllegalArgumentException(s"unsupported block type: ${other.getClass.getName}")

  /** Convert any inline node. */
  def fromInline(n: j.Node): adt.Inline = n match
    case t: j.Text             => adt.Text(spanOf(t), t.getLiteral)
    case c: j.Code             => adt.Code(spanOf(c), c.getLiteral)
    case e: j.Emphasis         => adt.Emphasis(spanOf(e), e.getOpeningDelimiter.charAt(0), childInlines(e))
    case s: j.StrongEmphasis   => adt.StrongEmphasis(spanOf(s), s.getOpeningDelimiter.charAt(0), childInlines(s))
    case l: j.Link             => adt.Link(spanOf(l), l.getDestination, Option(l.getTitle), childInlines(l))
    case i: j.Image            => adt.Image(spanOf(i), i.getDestination, Option(i.getTitle), childInlines(i))
    case h: j.HtmlInline       => adt.HtmlInline(spanOf(h), h.getLiteral)
    case _: j.HardLineBreak    => adt.HardLineBreak(spanOf(n))
    case _: j.SoftLineBreak    => adt.SoftLineBreak(spanOf(n))
    case s: jst.Strikethrough  => adt.Strikethrough(spanOf(s), s.getOpeningDelimiter, childInlines(s))
    case f: jfn.FootnoteReference => adt.FootnoteReference(spanOf(f), f.getLabel)
    case other =>
      throw new IllegalArgumentException(s"unsupported inline type: ${other.getClass.getName}")

  // ---------- helpers ----------

  private def childrenOf(n: j.Node): Iterator[j.Node] =
    Iterator
      .iterate(n.getFirstChild: j.Node | Null)(c => if c == null then null else c.getNext)
      .takeWhile(_ != null)
      .map(_.nn)

  private def spanOf(n: j.Node): adt.SourceSpan =
    val spans = n.getSourceSpans
    if spans.isEmpty then adt.SourceSpan.Unknown
    else
      val s = spans.get(0)
      adt.SourceSpan(s.getLineIndex + 1, s.getColumnIndex + 1, s.getLength)

  private def childBlocks(n: j.Node): Vector[adt.Block] =
    childrenOf(n).collect { case b: j.Block if !b.isInstanceOf[j.LinkReferenceDefinition] => fromBlock(b) }.toVector

  private def childInlines(n: j.Node): Vector[adt.Inline] =
    childrenOf(n).map(fromInline).toVector

  private def childListItems(list: j.ListBlock): Vector[adt.ListItem] =
    childrenOf(list).collect { case li: j.ListItem => fromListItem(li) }.toVector

  private def fromListItem(li: j.ListItem): adt.ListItem =
    li.getFirstChild match
      case marker: jtl.TaskListItemMarker =>
        val rest = Iterator
          .iterate(marker.getNext: j.Node | Null)(c => if c == null then null else c.getNext)
          .takeWhile(_ != null)
          .map(_.nn)
          .collect { case b: j.Block if !b.isInstanceOf[j.LinkReferenceDefinition] => fromBlock(b) }
          .toVector
        adt.ListItem(spanOf(li), rest, taskMarker = Some(marker.isChecked))
      case _ =>
        adt.ListItem(spanOf(li), childBlocks(li), taskMarker = None)

  private def collectReferences(n: j.Node): Map[String, adt.LinkReference] =
    childrenOf(n).foldLeft(Map.empty[String, adt.LinkReference]) {
      case (acc, lrd: j.LinkReferenceDefinition) =>
        acc.updated(
          lrd.getLabel,
          adt.LinkReference(lrd.getLabel, lrd.getDestination, Option(lrd.getTitle))
        )
      case (acc, other) =>
        acc ++ collectReferences(other)
    }

  private def fromTable(tb: jtb.TableBlock): adt.Table =
    var head: Option[adt.TableHead] = None
    var body: Option[adt.TableBody] = None
    childrenOf(tb).foreach {
      case h: jtb.TableHead => head = Some(fromTableHead(h))
      case b: jtb.TableBody => body = Some(fromTableBody(b))
      case _                => ()
    }
    val resolvedHead = head.getOrElse(adt.TableHead(spanOf(tb), Vector.empty))
    val alignments = resolvedHead.rows.headOption.fold(Vector.empty[adt.Alignment])(_.cells.map(_.alignment))
    adt.Table(spanOf(tb), resolvedHead, body, alignments)

  private def fromTableHead(h: jtb.TableHead): adt.TableHead =
    adt.TableHead(spanOf(h), childrenOf(h).collect { case r: jtb.TableRow => fromTableRow(r) }.toVector)

  private def fromTableBody(b: jtb.TableBody): adt.TableBody =
    adt.TableBody(spanOf(b), childrenOf(b).collect { case r: jtb.TableRow => fromTableRow(r) }.toVector)

  private def fromTableRow(r: jtb.TableRow): adt.TableRow =
    adt.TableRow(spanOf(r), childrenOf(r).collect { case c: jtb.TableCell => fromTableCell(c) }.toVector)

  private def fromTableCell(c: jtb.TableCell): adt.TableCell =
    adt.TableCell(spanOf(c), c.isHeader, fromAlignment(c.getAlignment), childInlines(c))

  private def fromAlignment(a: jtb.TableCell.Alignment | Null): adt.Alignment = a match
    case null                          => adt.Alignment.Left
    case jtb.TableCell.Alignment.LEFT  => adt.Alignment.Left
    case jtb.TableCell.Alignment.CENTER=> adt.Alignment.Center
    case jtb.TableCell.Alignment.RIGHT => adt.Alignment.Right
