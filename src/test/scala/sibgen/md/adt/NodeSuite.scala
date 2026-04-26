package sibgen.md.adt

class NodeSuite extends munit.FunSuite:

  private val s = SourceSpan.Unknown

  private val sample: Document =
    Document(
      span = s,
      children = Vector(
        Heading(s, 1, Vector(Text(s, "Hi"))),
        Paragraph(
          s,
          Vector(
            Text(s, "hello "),
            StrongEmphasis(s, '*', Vector(Text(s, "world"))),
            SoftLineBreak(s),
            Link(s, "https://example.com", Some("ex"), Vector(Text(s, "ex"))),
            Strikethrough(s, "~~", Vector(Text(s, "old"))),
            FootnoteReference(s, "1")
          )
        ),
        BlockQuote(s, Vector(Paragraph(s, Vector(Text(s, "quoted"))))),
        BulletList(
          s,
          tight = true,
          marker = '-',
          items = Vector(
            ListItem(s, Vector(Paragraph(s, Vector(Text(s, "a")))), taskMarker = None),
            ListItem(s, Vector(Paragraph(s, Vector(Text(s, "b")))), taskMarker = Some(true))
          )
        ),
        FencedCodeBlock(s, '`', 3, 0, "scala", "val x = 1\n"),
        Table(
          s,
          head = TableHead(s, Vector(TableRow(s, Vector(
            TableCell(s, header = true, Alignment.Left, Vector(Text(s, "h")))
          )))),
          body = Some(TableBody(s, Vector(TableRow(s, Vector(
            TableCell(s, header = false, Alignment.Right, Vector(Text(s, "1")))
          ))))),
          alignments = Vector(Alignment.Left)
        ),
        FootnoteDefinition(s, "1", Vector(Paragraph(s, Vector(Text(s, "note")))))
      ),
      references = Map(
        "ex" -> LinkReference("ex", "https://example.com", Some("ex"))
      )
    )

  test("span is accessible polymorphically through Node"):
    val n: Node = sample
    assertEquals(n.span, SourceSpan.Unknown)

  test("Block hierarchy is exhaustively matchable"):
    def blockKind(b: Block): String = b match
      case _: BlockQuote         => "blockquote"
      case _: Heading            => "heading"
      case _: Paragraph          => "paragraph"
      case _: ThematicBreak      => "thematic"
      case _: IndentedCodeBlock  => "indented-code"
      case _: FencedCodeBlock    => "fenced-code"
      case _: HtmlBlock          => "html"
      case _: BulletList         => "bullet-list"
      case _: OrderedList        => "ordered-list"
      case _: Table              => "table"
      case _: FootnoteDefinition => "footnote-def"
    assertEquals(blockKind(sample.children.head), "heading")

  test("Inline hierarchy is exhaustively matchable"):
    def inlineKind(i: Inline): String = i match
      case _: Text              => "text"
      case _: Code              => "code"
      case _: Emphasis          => "em"
      case _: StrongEmphasis    => "strong"
      case _: Link              => "link"
      case _: Image             => "image"
      case _: HtmlInline        => "html-inline"
      case _: HardLineBreak     => "hard-break"
      case _: SoftLineBreak     => "soft-break"
      case _: Strikethrough     => "strike"
      case _: FootnoteReference => "footnote-ref"
    val para = sample.children(1).asInstanceOf[Paragraph]
    assertEquals(inlineKind(para.content.head), "text")
    assertEquals(inlineKind(para.content(1)), "strong")

  test("typed children: BlockQuote contains Block, Paragraph contains Inline"):
    val bq: BlockQuote = BlockQuote(s, Vector(Paragraph(s, Vector(Text(s, "x")))))
    val first: Block = bq.children.head
    val p: Paragraph = first.asInstanceOf[Paragraph]
    val inl: Inline = p.content.head
    assert(inl.isInstanceOf[Text])

  test("ListBlock common interface"):
    val lb: ListBlock = sample.children.collectFirst { case b: BulletList => b }.get
    assert(lb.tight)
    assertEquals(lb.items.size, 2)
