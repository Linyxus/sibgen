package sibgen.md

import sibgen.md.adt

class MarkdownSuite extends munit.FunSuite:

  test("heading and paragraph"):
    val d = Markdown.parse("# Title\n\nhello world")
    assertEquals(d.children.size, 2)
    val h = d.children(0).asInstanceOf[adt.Heading]
    assertEquals(h.level, 1)
    assertEquals(h.content.head.asInstanceOf[adt.Text].literal, "Title")
    val p = d.children(1).asInstanceOf[adt.Paragraph]
    assertEquals(p.content.head.asInstanceOf[adt.Text].literal, "hello world")

  test("emphasis and strong emphasis preserve delimiter"):
    val d = Markdown.parse("*em* and **st**")
    val p = d.children.head.asInstanceOf[adt.Paragraph]
    val em = p.content.collectFirst { case e: adt.Emphasis => e }.get
    val st = p.content.collectFirst { case s: adt.StrongEmphasis => s }.get
    assertEquals(em.delimiter, '*')
    assertEquals(st.delimiter, '*')

  test("fenced code block carries info string and literal"):
    val d = Markdown.parse("```scala\nval x = 1\n```\n")
    val cb = d.children.head.asInstanceOf[adt.FencedCodeBlock]
    assertEquals(cb.info, "scala")
    assertEquals(cb.literal, "val x = 1\n")
    assertEquals(cb.fenceChar, '`')

  test("strikethrough"):
    val d = Markdown.parse("~~old~~")
    val p = d.children.head.asInstanceOf[adt.Paragraph]
    val s = p.content.collectFirst { case x: adt.Strikethrough => x }.get
    assertEquals(s.content.head.asInstanceOf[adt.Text].literal, "old")

  test("task list items collapse the marker into ListItem.taskMarker"):
    val d = Markdown.parse("- [x] done\n- [ ] todo\n- plain\n")
    val list = d.children.head.asInstanceOf[adt.BulletList]
    assertEquals(list.items.size, 3)
    assertEquals(list.items(0).taskMarker, Some(true))
    assertEquals(list.items(1).taskMarker, Some(false))
    assertEquals(list.items(2).taskMarker, None)
    list.items.foreach(it => assert(!it.children.exists(_ => false), "no marker should leak into children"))

  test("ordered list start number and delimiter"):
    val d = Markdown.parse("3. one\n4. two\n")
    val list = d.children.head.asInstanceOf[adt.OrderedList]
    assertEquals(list.startNumber, 3)
    assertEquals(list.delimiter, '.')
    assertEquals(list.items.size, 2)

  test("link reference definitions are hoisted to Document.references"):
    val d = Markdown.parse(
      """[link][label]
        |
        |[label]: http://example.com "title"
        |""".stripMargin
    )
    assertEquals(d.references.size, 1)
    val ref = d.references("label")
    assertEquals(ref.destination, "http://example.com")
    assertEquals(ref.title, Some("title"))
    val p = d.children.head.asInstanceOf[adt.Paragraph]
    val link = p.content.collectFirst { case l: adt.Link => l }.get
    assertEquals(link.destination, "http://example.com")
    assert(d.children.forall(b => !b.isInstanceOf[adt.FootnoteDefinition]))

  test("footnotes: definition is a block, reference is an inline"):
    val d = Markdown.parse("text[^1]\n\n[^1]: the note\n")
    val p = d.children.head.asInstanceOf[adt.Paragraph]
    val ref = p.content.collectFirst { case r: adt.FootnoteReference => r }.get
    assertEquals(ref.label, "1")
    val defn = d.children.collectFirst { case f: adt.FootnoteDefinition => f }.get
    assertEquals(defn.label, "1")
    val noteText = defn.children.head.asInstanceOf[adt.Paragraph].content.head.asInstanceOf[adt.Text].literal
    assertEquals(noteText, "the note")

  test("GFM table: head, body, alignments"):
    val d = Markdown.parse(
      """| a | b | c |
        ||:--|:-:|--:|
        || 1 | 2 | 3 |
        |""".stripMargin
    )
    val tbl = d.children.head.asInstanceOf[adt.Table]
    assertEquals(tbl.alignments, Vector(adt.Alignment.Left, adt.Alignment.Center, adt.Alignment.Right))
    assertEquals(tbl.head.rows.size, 1)
    val headerCells = tbl.head.rows.head.cells
    assertEquals(headerCells.map(_.content.head.asInstanceOf[adt.Text].literal), Vector("a", "b", "c"))
    val bodyCells = tbl.body.get.rows.head.cells
    assertEquals(bodyCells.map(_.content.head.asInstanceOf[adt.Text].literal), Vector("1", "2", "3"))

  test("source spans are populated for blocks"):
    val d = Markdown.parse("# Hi\n\npara\n")
    assert(d.children(0).span.startLine >= 1)
    assert(d.children(1).span.startLine >= 1)
    assertNotEquals(d.children(0).span, adt.SourceSpan.Unknown)
