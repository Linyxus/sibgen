package sibgen.sitegen

import sibgen.md.Markdown

class RendererSuite extends munit.FunSuite:

  test("renderPage produces a complete HTML document with inlined CSS"):
    val doc  = Markdown.parse("# Hello\n\nWorld.")
    val html = Renderer.renderPage(doc, "Hello")
    assert(html.startsWith("<!doctype html>"))
    assert(html.contains("<title>Hello</title>"))
    assert(html.contains("<style>"))
    assert(html.contains("--bg:"))                  // CSS custom prop from theme
    assert(html.contains("<h1>Hello</h1>"))
    assert(html.contains("<p>World.</p>"))
    assert(html.contains("</html>"))

  test("renderPage handles GFM features"):
    val src =
      """# Doc
        |
        |- [x] done
        |- [ ] todo
        |
        || a | b |
        ||:--|--:|
        || 1 | 2 |
        |
        |~~old~~ text and a footnote[^1].
        |
        |[^1]: note body.
        |""".stripMargin
    val html = Renderer.renderPage(Markdown.parse(src), "Doc")
    assert(html.contains("<table>"))
    assert(html.contains("<thead>"))
    assert(html.contains("<input type=\"checkbox\""))
    assert(html.contains("<del>"))
    assert(html.contains("footnotes") || html.contains("footnote"))

  test("titleOf reads the first H1 as plain text"):
    val doc = Markdown.parse("# *My* `Title`\n\nbody")
    assertEquals(Renderer.titleOf(doc), Some("My Title"))

  test("titleOf returns None when no H1"):
    val doc = Markdown.parse("## Sub\n\nbody")
    assertEquals(Renderer.titleOf(doc), None)

  test("Page.render escapes the title"):
    val out = Page.render("<p>x</p>", "<script>alert(1)</script>")
    assert(out.contains("<title>&lt;script&gt;alert(1)&lt;/script&gt;</title>"))
