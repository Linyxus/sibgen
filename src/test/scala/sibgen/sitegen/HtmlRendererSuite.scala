package sibgen.sitegen

import sibgen.md.Markdown

class HtmlRendererSuite extends munit.FunSuite:

  private def html(md: String): String = HtmlRenderer.render(Markdown.parse(md))

  test("heading and paragraph"):
    val out = html("# Hello\n\nWorld.")
    assertEquals(out, "<h1>Hello</h1>\n<p>World.</p>\n")

  test("emphasis, strong, code, soft-break"):
    val out = html("*em* and **st** and `code`\nnext line")
    assert(out.contains("<em>em</em>"))
    assert(out.contains("<strong>st</strong>"))
    assert(out.contains("<code>code</code>"))
    assert(out.contains("\nnext line"))

  test("escapes &, <, > in text"):
    val out = html("a & b < c > d")
    assert(out.contains("a &amp; b &lt; c &gt; d"))

  test("escapes & < > in code"):
    val out = html("`<a&b>`")
    assert(out.contains("<code>&lt;a&amp;b&gt;</code>"))

  test("link with title and attribute escaping"):
    val out = html("""[t](https://x.com/?a=1&b=2 "ti\"tle")""")
    assert(out.contains("<a href=\"https://x.com/?a=1&amp;b=2\" title=\"ti&quot;tle\">t</a>"))

  test("image with alt text"):
    val out = html("![alt *text*](img.png \"title\")")
    assert(out.contains("<img src=\"img.png\" alt=\"alt text\" title=\"title\" />"))

  test("blockquote wraps blocks"):
    val out = html("> hi")
    assert(out.contains("<blockquote>"))
    assert(out.contains("<p>hi</p>"))
    assert(out.contains("</blockquote>"))

  test("thematic break"):
    val out = html("---\n")
    assert(out.contains("<hr />"))

  test("fenced scala block carries language class and emits plain text (CodeMirror highlights at runtime)"):
    val out = html("```scala\nval x = 1\n```\n")
    assert(out.contains("<pre><code class=\"language-scala\">"), out)
    // No more build-time `.hl-*` spans on the Scala path — CM6 takes over on mount.
    assert(out.contains("val x = 1"), out)
    assert(!out.contains("<span class=\"hl-"), out)
    assert(out.contains("</code></pre>"), out)

  test("fenced non-scala block stays plain (no spans)"):
    val out = html("```bash\necho hi\n```\n")
    assert(out.contains("<pre><code class=\"language-bash\">echo hi"), out)
    assert(!out.contains("<span class=\"hl-"), out)

  test("scala snippet is wrapped in a snippet shell with a metadata strip"):
    val out = html("```scala\nval x = 1\n```\n")
    assert(out.contains("class=\"snippet snippet-scala\""), out)
    assert(out.contains("data-snippet-id=\"default-global-snippet\""), out)
    assert(out.contains("<div class=\"snippet-strip\">"), out)
    assert(out.contains("class=\"snippet-status\""), out)
    assert(out.contains("class=\"snippet-check\""), out)
    assert(out.contains("data-action=\"typecheck\""), out)
    assert(out.contains("» type-check</button>"), out)
    assert(out.contains("<div class=\"snippet-detail\" hidden></div>"), out)
    // Order: code first, then strip (status, button, detail inside it).
    val iPre    = out.indexOf("<pre>")
    val iStrip  = out.indexOf("snippet-strip")
    val iStatus = out.indexOf("snippet-status")
    val iBtn    = out.indexOf("snippet-check")
    val iDetail = out.indexOf("snippet-detail")
    assert(iPre < iStrip, out)
    assert(iStrip < iStatus && iStatus < iBtn && iBtn < iDetail, out)

  test("non-scala fenced block has no snippet shell"):
    val out = html("```bash\necho hi\n```\n")
    assert(!out.contains("snippet-check"), out)
    assert(!out.contains("snippet-strip"), out)
    assert(!out.contains("snippet-status"), out)
    assert(!out.contains("snippet-detail"), out)

  test("mermaid fenced block emits raw <pre class=\"mermaid\"> with no <code> wrapper"):
    val out = html("```mermaid\ngraph TD\n  A --> B\n```\n")
    assert(out.contains("<pre class=\"mermaid\">"), out)
    assert(out.contains("graph TD\n  A --&gt; B"), out)  // text-escaped, no syntax highlighting
    assert(!out.contains("<code"), out)
    assert(!out.contains("snippet"), out)
    assert(!out.contains("language-mermaid"), out)
    assert(!out.contains("hl-"), out)

  test("indented code block has no class"):
    val out = html("    val x = 1\n")
    assert(out.contains("<pre><code>val x = 1"))

  test("HTML block passes through verbatim"):
    val out = html("<div class=\"foo\">raw</div>\n")
    assert(out.contains("<div class=\"foo\">raw</div>"))

  test("HTML inline passes through"):
    val out = html("hello <span>x</span> world")
    assert(out.contains("<span>x</span>"))

  test("bullet list with task markers"):
    val out = html("- [x] done\n- [ ] todo\n- plain\n")
    assert(out.contains("<ul>"))
    assert(out.contains("<input type=\"checkbox\" disabled checked />"))
    assert(out.contains("<input type=\"checkbox\" disabled />"))
    val plain = out.split("\n").find(_.contains("plain")).get
    assert(!plain.contains("checkbox"))

  test("ordered list start attr"):
    val out1 = html("1. one\n2. two\n")
    assert(!out1.contains("start="))
    val out3 = html("3. three\n4. four\n")
    assert(out3.contains("<ol start=\"3\">"))

  test("strikethrough"):
    val out = html("~~old~~")
    assert(out.contains("<del>old</del>"))

  test("hard line break"):
    val out = html("foo  \nbar")
    assert(out.contains("<br />"))

  test("table with align attributes"):
    val out = html(
      """| a | b | c |
        ||:--|:-:|--:|
        || 1 | 2 | 3 |
        |""".stripMargin
    )
    assert(out.contains("<table>"))
    assert(out.contains("<th align=\"left\">a</th>"))
    assert(out.contains("<th align=\"center\">b</th>"))
    assert(out.contains("<th align=\"right\">c</th>"))
    assert(out.contains("<td align=\"left\">1</td>"))
    assert(out.contains("<td align=\"right\">3</td>"))

  test("footnote reference and section"):
    val out = html("text[^1]\n\n[^1]: the note\n")
    assert(out.contains("<sup class=\"footnote-ref\"><a href=\"#fn-1\" id=\"fnref-1\">1</a></sup>"))
    assert(out.contains("<section class=\"footnotes\" data-footnotes>"))
    assert(out.contains("<li id=\"fn-1\">"))
    assert(out.contains("the note"))
    assert(out.contains("class=\"footnote-backref\""))
    assert(out.contains("↩"))

  test("two footnotes get sequential indices in reference order"):
    val out = html(
      """a[^second] b[^first]
        |
        |[^first]:  one
        |[^second]: two
        |""".stripMargin
    )
    assert(out.contains("<a href=\"#fn-second\" id=\"fnref-second\">1</a>"))
    assert(out.contains("<a href=\"#fn-first\" id=\"fnref-first\">2</a>"))

  test("link reference definitions are not in the output (hoisted)"):
    val out = html(
      """[link][label]
        |
        |[label]: http://example.com
        |""".stripMargin
    )
    assert(out.contains("<a href=\"http://example.com\">link</a>"))
    assert(!out.contains("[label]:"))

  test("scala snippet without a directive carries the default snippet id"):
    val out = html("```scala\nval x = 1\n```\n")
    assert(out.contains("data-snippet-id=\"default-global-snippet\""), out)

  test("snippetId directive attaches data-snippet-id to the next scala block and is not emitted"):
    val out = html("<!--% snippetId my-channel -->\n```scala\nval x = 1\n```\n")
    assert(out.contains("data-snippet-id=\"my-channel\""), out)
    assert(!out.contains("snippetId"), s"directive comment should not be in output: $out")
    assert(!out.contains("<!--%"),     s"directive comment should not be in output: $out")

  test("snippetId directive escapes special characters in the attribute"):
    val out = html("<!--% snippetId a&b<c -->\n```scala\nval x = 1\n```\n")
    assert(out.contains("data-snippet-id=\"a&amp;b&lt;c\""), out)

  test("non-directive HTML comment passes through and the snippet keeps the default id"):
    val out = html("<!-- ordinary comment -->\n```scala\nval x = 1\n```\n")
    assert(out.contains("<!-- ordinary comment -->"), out)
    assert(out.contains("data-snippet-id=\"default-global-snippet\""), out)

  test("intervening paragraph between directive and scala block clears the pending id"):
    val out = html(
      """<!--% snippetId my-channel -->
        |
        |Some prose between.
        |
        |```scala
        |val x = 1
        |```
        |""".stripMargin
    )
    assert(out.contains("data-snippet-id=\"default-global-snippet\""), out)
    assert(!out.contains("data-snippet-id=\"my-channel\""), out)

  test("directive only applies to the next block, not subsequent ones"):
    val out = html(
      """<!--% snippetId tut -->
        |```scala
        |def foo: Int = 1
        |```
        |
        |```scala
        |val y = foo + 1
        |```
        |""".stripMargin
    )
    assert(out.contains("data-snippet-id=\"tut\""), out)
    assert(out.contains("data-snippet-id=\"default-global-snippet\""), out)

  test("directive before a non-scala fenced block is not surfaced anywhere"):
    val out = html("<!--% snippetId nope -->\n```bash\necho hi\n```\n")
    assert(!out.contains("data-snippet-id"), out)
    assert(!out.contains("snippetId"),       out)
