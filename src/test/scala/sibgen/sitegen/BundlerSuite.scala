package sibgen.sitegen

class BundlerSuite extends munit.FunSuite:

  private val esbuildAvailable: Boolean =
    try
      val proc = ProcessBuilder("esbuild", "--version").redirectErrorStream(true).start()
      proc.getInputStream.readAllBytes()
      proc.waitFor() == 0
    catch case _: java.io.IOException => false

  override def munitTests(): Seq[munit.Test] =
    if esbuildAvailable then super.munitTests()
    else
      println("[BundlerSuite] esbuild not on PATH — skipping suite")
      Seq.empty

  test("bundle inlines the woff2 serif font as a data URL"):
    val css = Bundler.bundledCss
    assert(css.contains("data:font/woff2;base64,"), "expected base64-inlined woff2 font in bundled CSS")

  test("bundle inlines the ttf monospace font as a data URL"):
    val css = Bundler.bundledCss
    assert(css.contains("data:font/ttf;base64,"), "expected base64-inlined ttf font in bundled CSS")

  test("bundle declares @font-face for both Source Serif 4 and iA Writer Mono S"):
    val css = Bundler.bundledCss
    assert(css.contains("Source Serif 4"),  "expected Source Serif 4 @font-face in bundled CSS")
    assert(css.contains("iA Writer Mono S"), "expected iA Writer Mono S @font-face in bundled CSS")

  test("bundle includes all four iA Writer Mono S faces (regular, italic, bold, bold-italic)"):
    val css = Bundler.bundledCss
    // Minified CSS combines weight & style as e.g. font:italic 400 12px/x or
    // separate `font-style:italic` / `font-weight:700` declarations. Just count data URLs:
    // Source Serif 4 woff2 + four iA Writer Mono ttfs = 1 woff2 + 4 ttfs.
    val woff2Count = "data:font/woff2;base64,".r.findAllIn(css).length
    val ttfCount   = "data:font/ttf;base64,".r.findAllIn(css).length
    assertEquals(woff2Count, 1, s"expected 1 woff2 data URL, got $woff2Count")
    assertEquals(ttfCount,   4, s"expected 4 ttf data URLs, got $ttfCount")

  test("bundle includes theme rules"):
    val css = Bundler.bundledCss
    assert(css.contains("--bg"), "expected theme custom property --bg in bundled CSS")
    assert(css.contains("--font-serif"), "expected --font-serif in bundled CSS")

  test("bundle is cached across calls"):
    val a = Bundler.bundledCss
    val b = Bundler.bundledCss
    assert(a eq b, "bundledCss should be the same instance on repeat access (cached)")

  test("mermaidJs loads the vendored UMD bundle and is non-trivial in size"):
    val js = Bundler.mermaidJs
    assert(js.length > 1_000_000, s"expected mermaid bundle to be > 1MB, got ${js.length} chars")
    assert(js.contains("mermaid"), "expected the bundle to mention 'mermaid'")

  test("mermaidJs is cached across calls"):
    val a = Bundler.mermaidJs
    val b = Bundler.mermaidJs
    assert(a eq b, "mermaidJs should be the same instance on repeat access (cached)")
