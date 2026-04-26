package sibgen.md.commonmark

import sibgen.md.Markdown
import sibgen.md.adt

class RoundTripSuite extends munit.FunSuite:

  /** Drop source spans recursively so two ADT trees can be compared structurally. */
  private def stripSpans(node: adt.Node): adt.Node = node match
    case d: adt.Document =>
      d.copy(span = adt.SourceSpan.Unknown, children = d.children.map(stripBlock))
    case b: adt.Block  => stripBlock(b)
    case i: adt.Inline => stripInline(i)
    case li: adt.ListItem =>
      li.copy(span = adt.SourceSpan.Unknown, children = li.children.map(stripBlock))
    case c: adt.TableCell =>
      c.copy(span = adt.SourceSpan.Unknown, content = c.content.map(stripInline))
    case r: adt.TableRow =>
      r.copy(span = adt.SourceSpan.Unknown, cells = r.cells.map(c => stripCell(c)))
    case h: adt.TableHead =>
      h.copy(span = adt.SourceSpan.Unknown, rows = h.rows.map(r => stripRow(r)))
    case b: adt.TableBody =>
      b.copy(span = adt.SourceSpan.Unknown, rows = b.rows.map(r => stripRow(r)))

  private def stripBlock(b: adt.Block): adt.Block = b match
    case n: adt.BlockQuote        => n.copy(span = adt.SourceSpan.Unknown, children = n.children.map(stripBlock))
    case n: adt.Heading           => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.Paragraph         => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.ThematicBreak     => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.IndentedCodeBlock => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.FencedCodeBlock   => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.HtmlBlock         => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.BulletList        => n.copy(span = adt.SourceSpan.Unknown, items = n.items.map(stripItem))
    case n: adt.OrderedList       => n.copy(span = adt.SourceSpan.Unknown, items = n.items.map(stripItem))
    case n: adt.Table             => n.copy(span = adt.SourceSpan.Unknown, head = stripHead(n.head), body = n.body.map(stripBody))
    case n: adt.FootnoteDefinition => n.copy(span = adt.SourceSpan.Unknown, children = n.children.map(stripBlock))

  private def stripInline(i: adt.Inline): adt.Inline = i match
    case n: adt.Text              => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.Code              => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.Emphasis          => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.StrongEmphasis    => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.Link              => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.Image             => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.HtmlInline        => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.HardLineBreak     => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.SoftLineBreak     => n.copy(span = adt.SourceSpan.Unknown)
    case n: adt.Strikethrough     => n.copy(span = adt.SourceSpan.Unknown, content = n.content.map(stripInline))
    case n: adt.FootnoteReference => n.copy(span = adt.SourceSpan.Unknown)

  private def stripItem(li: adt.ListItem): adt.ListItem =
    li.copy(span = adt.SourceSpan.Unknown, children = li.children.map(stripBlock))

  private def stripHead(h: adt.TableHead): adt.TableHead =
    h.copy(span = adt.SourceSpan.Unknown, rows = h.rows.map(stripRow))

  private def stripBody(b: adt.TableBody): adt.TableBody =
    b.copy(span = adt.SourceSpan.Unknown, rows = b.rows.map(stripRow))

  private def stripRow(r: adt.TableRow): adt.TableRow =
    r.copy(span = adt.SourceSpan.Unknown, cells = r.cells.map(stripCell))

  private def stripCell(c: adt.TableCell): adt.TableCell =
    c.copy(span = adt.SourceSpan.Unknown, content = c.content.map(stripInline))

  private def stripDocument(d: adt.Document): adt.Document =
    d.copy(span = adt.SourceSpan.Unknown, children = d.children.map(stripBlock))

  private def assertRoundTrip(text: String): Unit =
    val a = Markdown.parse(text)
    val b = FromJava.fromDocument(ToJava.toDocument(a))
    assertEquals(stripDocument(b), stripDocument(a), s"round-trip mismatch for input:\n$text")

  test("round-trip: plain prose"):
    assertRoundTrip(
      """# Title
        |
        |hello *world*, **bold**, `code`, [link](http://x "t").
        |
        |second paragraph
        |""".stripMargin
    )

  test("round-trip: lists with task markers"):
    assertRoundTrip(
      """- [x] done
        |- [ ] todo
        |- plain
        |""".stripMargin
    )

  test("round-trip: ordered list"):
    assertRoundTrip(
      """3. one
        |4. two
        |""".stripMargin
    )

  test("round-trip: blockquote and code"):
    assertRoundTrip(
      """> quoted text
        |
        |```scala
        |val x = 1
        |```
        |""".stripMargin
    )

  test("round-trip: footnotes"):
    assertRoundTrip(
      """text[^1]
        |
        |[^1]: the note
        |""".stripMargin
    )

  test("round-trip: link reference definitions"):
    assertRoundTrip(
      """[link][label]
        |
        |[label]: http://example.com "title"
        |""".stripMargin
    )

  test("round-trip: GFM table"):
    assertRoundTrip(
      """| a | b | c |
        ||:--|:-:|--:|
        || 1 | 2 | 3 |
        |""".stripMargin
    )

  test("round-trip: strikethrough"):
    assertRoundTrip("~~struck~~ and normal")
