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

  test("bundle declares @font-face for Source Serif 4 and the iA Writer V (variable) families"):
    val css = Bundler.bundledCss
    assert(css.contains("Source Serif 4"),     "expected Source Serif 4 @font-face in bundled CSS")
    assert(css.contains("iA Writer Mono V"),    "expected iA Writer Mono V @font-face in bundled CSS")
    assert(css.contains("iA Writer Quattro V"), "expected iA Writer Quattro V @font-face in bundled CSS")

  test("bundle includes upright + italic for both iA Writer V families"):
    val css = Bundler.bundledCss
    // Source Serif 4 Variable woff2 + (Mono V upright/italic + Quattro V upright/italic) ttfs.
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
