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

  test("renderPage inlines mermaid + init script when the doc contains a mermaid block"):
    val src  = "# Doc\n\n```mermaid\ngraph TD\n  A --> B\n```\n"
    val html = Renderer.renderPage(Markdown.parse(src), "Doc")
    assert(html.contains("<pre class=\"mermaid\">"),       "expected mermaid pre in body")
    assert(html.contains("mermaid.initialize"),            "expected mermaid init script in page")
    assert(html.contains("themeVariables"),                "expected theme variables in init script")
    assert(html.contains("document.fonts"),                "expected fonts.ready gating in init script")
    assert(html.length > 3_000_000,                        s"expected page > 3MB with mermaid bundle, got ${html.length}")

  test("renderPage omits mermaid bundle when the doc has no mermaid block"):
    val html = Renderer.renderPage(Markdown.parse("# Doc\n\nplain text."), "Doc")
    assert(!html.contains("mermaid.initialize"), "did not expect mermaid init script when no mermaid block present")
    assert(!html.contains("pre.mermaid") || !html.contains("mermaid.run"),
           "did not expect mermaid runtime when no mermaid block present")

  test("snippet script references the compiler artifact URLs"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("/assets/main.js"),       "expected /assets/main.js URL in snippet script")
    assert(html.contains("/assets/classpath.bin"), "expected /assets/classpath.bin URL in snippet script")

  test("snippet script bridges window.DottyCompiler and calls loadClasspath"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("window.DottyCompiler"), "expected window.DottyCompiler reference in snippet script")
    assert(html.contains("loadClasspath"),        "expected loadClasspath call in snippet script")

  test("snippet script no longer carries the old stub message"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(!html.contains("(stub) type-check backend"), "expected the stub placeholder message to be gone")

  test("snippet script wires up the loading label and the eager-fetch guard"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("downloading the compiler"),    "expected loading label in snippet script")
    assert(html.contains("document.querySelector('.snippet-check')"),
           "expected eager-fetch guard so non-snippet pages don't fetch the compiler")

  test("snippet script renders ANSI-coloured diagnostics via .ansi-* spans"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("ansiToHtml"),  "expected ANSI→HTML helper in snippet script")
    assert(html.contains("ansi-red"),    "expected ansi-red mapping in the colour table")
    assert(html.contains(".ansi-red"),   "expected .ansi-red CSS rule in bundled theme")

  test("snippet script computes -pagewidth from the live container width"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("-pagewidth"),    "expected -pagewidth in compiler args")
    assert(html.contains("measureColumns"), "expected dynamic column-measurement helper")
    assert(html.contains("clientWidth") || html.contains("offsetWidth"),
           "expected DOM width measurement in column computation")

  test("renderPage inlines the CodeMirror bundle when the doc contains a Scala snippet"):
    val src  = "# Doc\n\n```scala\nval x = 1\n```\n"
    val html = Renderer.renderPage(Markdown.parse(src), "Doc")
    assert(html.contains("class=\"snippet snippet-scala\""),       "expected scala snippet shell")
    assert(html.contains("SibgenSnippets"),                        "expected codemirror bundle to be inlined")
    assert(html.contains("window.SibgenSnippets && window.SibgenSnippets.init();"),
           "expected the on-load init call to be present")

  test("renderPage omits the CodeMirror bundle when the doc has no Scala snippet"):
    val html = Renderer.renderPage(Markdown.parse("# Doc\n\njust prose."), "Doc")
    assert(!html.contains("SibgenSnippets"),
           "did not expect codemirror bundle when no Scala snippet present")

  test("snippet script reads the live editor before falling back to <pre><code>"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("readSnippetSource"),     "expected source-resolution helper")
    assert(html.contains("snippet.cmView"),        "expected click handler to consult the CodeMirror view")
    assert(html.contains("state.doc.toString()"),  "expected the editor's text extraction call")

  test("snippet script concatenates earlier snippets sharing the same data-snippet-id"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("buildSnippetCompileUnit"),
           "expected the channel-aware compile-unit helper")
    assert(html.contains("data-snippet-id"),
           "expected the helper to consult the snippet id attribute")
    assert(html.contains("default-global-snippet"),
           "expected the JS-side default channel name to match the renderer's constant")
    assert(html.contains(".snippet-scala[data-snippet-id="),
           "expected the helper to query siblings by snippet id")

  test("snippet script filters and rebases diagnostics into the current snippet's coordinates"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    assert(html.contains("startLine"),
           "expected the start-line offset threaded through runCheck")
    assert(html.contains("d.line - startLine + 1"),
           "expected the per-diagnostic line rebase formula")

  test("snippet script normalizes 0-indexed compiler positions to 1-indexed before filtering"):
    val html = Renderer.renderPage(Markdown.parse("# Doc"), "Doc")
    // dotty's SourcePosition.line/column are 0-indexed (per SourceFile.offsetToLine docs).
    // We bump both to 1-indexed so the filter's `d.line >= startLine` (with startLine ≥ 1)
    // doesn't silently swallow errors on the snippet's first line.
    assert(html.contains("dn.line   = dn.line   + 1"),
           "expected the compiler-line normalization step")
    assert(html.contains("dn.column = dn.column + 1"),
           "expected the compiler-column normalization step")

  test("renderPage emits data-snippet-id from the directive on the wrapper div"):
    val src  = "# Doc\n\n<!--% snippetId tut -->\n```scala\nval x = 1\n```\n"
    val html = Renderer.renderPage(Markdown.parse(src), "Doc")
    assert(html.contains("data-snippet-id=\"tut\""),
           "expected the directive's id to land on the snippet wrapper")
