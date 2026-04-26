package sibgen.md.adt

/** Position of a node in the source text. */
case class SourceSpan(startLine: Int, startCol: Int, length: Int)

object SourceSpan:
  /** Sentinel for nodes built programmatically without a source position. */
  val Unknown: SourceSpan = SourceSpan(-1, -1, 0)

/** A `[label]: url "title"` link reference definition, hoisted out of the block tree onto [[Document.references]]. */
case class LinkReference(
  label: String,
  destination: String,
  title: Option[String]
)

/** Column alignment for a GFM table cell. */
enum Alignment:
  case Left, Center, Right

/** Common parent of every markdown construct in the ADT. Always carries a source span. */
sealed trait Node:
  def span: SourceSpan

/** Root of the markdown tree. Holds top-level blocks and a global table of link reference definitions. */
case class Document(
  span: SourceSpan,
  children: Vector[Block],
  references: Map[String, LinkReference]
) extends Node

/** One item inside a bullet or ordered list. `taskMarker` is `Some(checked)` for GFM task-list items, `None` otherwise. */
case class ListItem(
  span: SourceSpan,
  children: Vector[Block],
  taskMarker: Option[Boolean]
) extends Node

/** A single cell in a GFM table. `header` distinguishes head-row cells from body cells. */
case class TableCell(
  span: SourceSpan,
  header: Boolean,
  alignment: Alignment,
  content: Vector[Inline]
) extends Node

/** One row of a GFM table — a sequence of cells. */
case class TableRow(span: SourceSpan, cells: Vector[TableCell]) extends Node

/** Header section of a GFM table (usually a single row). */
case class TableHead(span: SourceSpan, rows: Vector[TableRow]) extends Node

/** Body section of a GFM table — zero or more data rows. */
case class TableBody(span: SourceSpan, rows: Vector[TableRow]) extends Node

/** A block-level construct: paragraph, heading, list, code block, quote, etc. — anything separated by blank lines at the top level. */
sealed trait Block extends Node

/** A `>` blockquote. Children are the blocks inside the quote. */
case class BlockQuote(span: SourceSpan, children: Vector[Block]) extends Block

/** An ATX (`#`-prefixed) or Setext (`===`/`---`-underlined) heading. `level` is 1–6. */
case class Heading(span: SourceSpan, level: Int, content: Vector[Inline]) extends Block

/** A run of inline content delimited by blank lines from its neighbours. */
case class Paragraph(span: SourceSpan, content: Vector[Inline]) extends Block

/** A horizontal rule produced by `---`, `***`, or `___` on its own line. */
case class ThematicBreak(span: SourceSpan) extends Block

/** A code block written with 4-space indentation. Has no info string and is rendered verbatim. */
case class IndentedCodeBlock(span: SourceSpan, literal: String) extends Block

/** A code block delimited by triple backticks or tildes. `info` is the language tag (e.g. `"scala"`); `literal` is the raw code text. */
case class FencedCodeBlock(
  span: SourceSpan,
  fenceChar: Char,
  fenceLength: Int,
  fenceIndent: Int,
  info: String,
  literal: String
) extends Block

/** A block of raw HTML embedded directly in the markdown source. */
case class HtmlBlock(span: SourceSpan, literal: String) extends Block

/** Common parent of bullet and ordered lists. `tight` is true when items contain no internal blank lines. */
sealed trait ListBlock extends Block:
  def tight: Boolean
  def items: Vector[ListItem]

/** An unordered list. `marker` is the bullet character used (`-`, `+`, or `*`). */
case class BulletList(
  span: SourceSpan,
  tight: Boolean,
  marker: Char,
  items: Vector[ListItem]
) extends ListBlock

/** An ordered list. `startNumber` is the first item's number; `delimiter` is `.` or `)`. */
case class OrderedList(
  span: SourceSpan,
  tight: Boolean,
  startNumber: Int,
  delimiter: Char,
  items: Vector[ListItem]
) extends ListBlock

/** A GFM pipe-table. Stores its head, optional body, and a per-column alignment vector. */
case class Table(
  span: SourceSpan,
  head: TableHead,
  body: Option[TableBody],
  alignments: Vector[Alignment]
) extends Block

/** A `[^label]: …` footnote definition body, referenced by [[FootnoteReference]]. */
case class FootnoteDefinition(
  span: SourceSpan,
  label: String,
  children: Vector[Block]
) extends Block

/** An inline construct (text, emphasis, link, …) found inside a block's content. */
sealed trait Inline extends Node

/** Literal text with no special markdown meaning. */
case class Text(span: SourceSpan, literal: String) extends Inline

/** An inline code span written between backticks: `` `like this` ``. */
case class Code(span: SourceSpan, literal: String) extends Inline

/** Italic emphasis (`*…*` or `_…_`). `delimiter` records which character was used. */
case class Emphasis(span: SourceSpan, delimiter: Char, content: Vector[Inline]) extends Inline

/** Bold emphasis (`**…**` or `__…__`). `delimiter` records which character was used. */
case class StrongEmphasis(span: SourceSpan, delimiter: Char, content: Vector[Inline]) extends Inline

/** A `[text](destination "title")` link, including reference-style links resolved against [[Document.references]]. Children are the link text. */
case class Link(
  span: SourceSpan,
  destination: String,
  title: Option[String],
  content: Vector[Inline]
) extends Inline

/** An `![alt](source "title")` image. Children are the alt text. */
case class Image(
  span: SourceSpan,
  destination: String,
  title: Option[String],
  content: Vector[Inline]
) extends Inline

/** A raw HTML tag or fragment appearing inside inline content. */
case class HtmlInline(span: SourceSpan, literal: String) extends Inline

/** Explicit line break — two trailing spaces or a trailing backslash before a newline. Renders as `<br>`. */
case class HardLineBreak(span: SourceSpan) extends Inline

/** A plain newline inside a paragraph. Typically rendered as a single space; does not produce `<br>`. */
case class SoftLineBreak(span: SourceSpan) extends Inline

/** GFM struck-through text: `~~…~~` (or `~…~`). `delimiter` is the literal run that opened/closed it. */
case class Strikethrough(span: SourceSpan, delimiter: String, content: Vector[Inline]) extends Inline

/** A `[^label]` reference pointing to a [[FootnoteDefinition]] elsewhere in the document. */
case class FootnoteReference(span: SourceSpan, label: String) extends Inline
