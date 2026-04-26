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

  test("bundle includes theme rules"):
    val css = Bundler.bundledCss
    assert(css.contains("--bg"), "expected theme custom property --bg in bundled CSS")
    assert(css.contains("--font-serif"), "expected --font-serif in bundled CSS")

  test("bundle is cached across calls"):
    val a = Bundler.bundledCss
    val b = Bundler.bundledCss
    assert(a eq b, "bundledCss should be the same instance on repeat access (cached)")
