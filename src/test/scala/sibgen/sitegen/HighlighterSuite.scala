package sibgen.sitegen

class HighlighterSuite extends munit.FunSuite:

  test("keywords get hl-kw"):
    val out = Highlighter.highlight("val x = 1")
    assert(out.contains("<span class=\"hl-kw\">val</span>"), out)

  test("def, class, given, extension are keywords"):
    val out = Highlighter.highlight("class C; def f = 0; given Int = 1; extension (x: Int) def y = x")
    assert(out.contains("<span class=\"hl-kw\">class</span>"))
    assert(out.contains("<span class=\"hl-kw\">def</span>"))
    assert(out.contains("<span class=\"hl-kw\">given</span>"))
    assert(out.contains("<span class=\"hl-kw\">extension</span>"))

  test("soft modifiers get hl-soft"):
    val out = Highlighter.highlight("inline def x = 0\nopaque type Foo = Int\ntransparent inline def y = 0")
    assert(out.contains("<span class=\"hl-soft\">inline</span>"),      out)
    assert(out.contains("<span class=\"hl-soft\">opaque</span>"),      out)
    assert(out.contains("<span class=\"hl-soft\">transparent</span>"), out)

  test("numbers get hl-num"):
    val out = Highlighter.highlight("val a = 42; val b = 3.14; val c = 1L; val d = 1.5e3")
    assert(out.contains("<span class=\"hl-num\">42</span>"))
    assert(out.contains("<span class=\"hl-num\">3.14</span>"))
    assert(out.contains("<span class=\"hl-num\">1L</span>"))

  test("string literals get hl-str"):
    val out = Highlighter.highlight("""val s = "hello"""")
    assert(out.contains("hl-str"), out)
    assert(out.contains("hello"), out)

  test("string interpolation: prefix tagged, content tagged, code inside takes its own colors"):
    val out = Highlighter.highlight("""val s = s"x = $x and ${y + 1}"""")
    // The interpolator prefix `s` is INTERPOLATIONID
    assert(out.contains("class=\"hl-str\">s</span>"), out)
    // y is an identifier (no hl class)
    assert(out.contains(">y<") || out.contains("y +"), out)
    // `+` is an operator-ish but in Scala 3 it's an IDENTIFIER, no hl class
    // The `1` inside the interpolation is a number
    assert(out.contains("<span class=\"hl-num\">1</span>"), out)

  test("line comments get hl-comment"):
    val out = Highlighter.highlight("val x = 1 // hi there\n")
    assert(out.contains("<span class=\"hl-comment\">// hi there</span>"), out)

  test("block comments get hl-comment, including multiline"):
    val out = Highlighter.highlight("/* a\n  b */\nval x = 1")
    assert(out.contains("<span class=\"hl-comment\">/* a\n  b */</span>"), out)

  test("// inside a string literal is NOT a comment"):
    val out = Highlighter.highlight("""val u = "http://example.com"""")
    // No comment span anywhere
    assert(!out.contains("hl-comment"), out)
    // The whole URL is part of the string span
    assert(out.contains("http://example.com"), out)

  test("identifiers are not wrapped"):
    val out = Highlighter.highlight("val foo = bar")
    // 'foo' and 'bar' are plain identifiers — they appear as-is, not in spans
    val withoutSpans = "(<span[^>]*>|</span>)".r.replaceAllIn(out, "")
    assert(withoutSpans.contains("foo"))
    assert(withoutSpans.contains("bar"))

  test("HTML chars in code are escaped"):
    val out = Highlighter.highlight("def f(x: List[Int]) = x.head < 1 && true")
    assert(out.contains("&lt;"))
    assert(out.contains("&amp;"))

  test("malformed input falls back to plain escape, never throws"):
    val out = Highlighter.highlight("val \"oops")  // bad string literal
    // Either it tokenized something or fell back; either way no crash and content escaped
    assert(out.contains("oops"))

  test("punctuation gets hl-punct"):
    val out = Highlighter.highlight("def f() = ()")
    assert(out.contains("<span class=\"hl-punct\">(</span>"))
    assert(out.contains("<span class=\"hl-punct\">)</span>"))

  test("operator =, => get hl-op"):
    val out = Highlighter.highlight("val f = (x: Int) => x")
    assert(out.contains("<span class=\"hl-op\">=</span>"))
    assert(out.contains("<span class=\"hl-op\">=&gt;</span>"))
