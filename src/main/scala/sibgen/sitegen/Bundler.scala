package sibgen.sitegen

import java.nio.file.{Files, Path, StandardCopyOption}

/** Builds the page CSS by shelling out to esbuild, which inlines the woff2 font as a base64
  * data URL via `--loader:.woff2=dataurl`. The output is a single, fully self-contained CSS
  * string that can go straight into a `<style>` tag.
  *
  * Result is computed once per JVM (lazy val) — repeated renders pay no extra cost.
  */
object Bundler:

  /** Bundled CSS: theme rules + `@font-face` declarations with the woff2 inlined as a data URL. */
  lazy val bundledCss: String = run()

  private val FontResource = "/sibgen/fonts/SourceSerif4-Regular.woff2"
  private val FontFileName = "SourceSerif4-Regular.woff2"

  private def run(): String =
    val tmp = Files.createTempDirectory("sibgen-bundle-")
    try
      val fontsDir = Files.createDirectories(tmp.resolve("fonts"))
      copyFontResource(fontsDir.resolve(FontFileName))
      Files.writeString(tmp.resolve("fonts.css"), fontsCss)
      Files.writeString(tmp.resolve("theme.css"), Theme.default)
      Files.writeString(tmp.resolve("entry.css"), entryCss)

      val out = tmp.resolve("out.css")
      runEsbuild(tmp.resolve("entry.css"), out)
      Files.readString(out)
    finally deleteRecursively(tmp)

  private val fontsCss: String =
    s"""@font-face {
       |  font-family: 'Source Serif 4';
       |  src: url('./fonts/$FontFileName') format('woff2');
       |  font-weight: 400;
       |  font-style: normal;
       |  font-display: swap;
       |}
       |""".stripMargin

  private val entryCss: String =
    """@import "./fonts.css";
      |@import "./theme.css";
      |""".stripMargin

  private def copyFontResource(target: Path): Unit =
    val in = getClass.getResourceAsStream(FontResource)
    if in == null then
      throw new RuntimeException(s"font resource not found on classpath: $FontResource")
    try Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING)
    finally in.close()

  private def runEsbuild(entry: Path, out: Path): Unit =
    val cmd = java.util.List.of(
      "esbuild",
      entry.toString,
      "--bundle",
      "--loader:.woff2=dataurl",
      "--minify",
      s"--outfile=${out.toString}"
    )
    val pb = ProcessBuilder(cmd).redirectErrorStream(true)
    val proc =
      try pb.start()
      catch
        case e: java.io.IOException =>
          throw new RuntimeException(
            "esbuild not found on PATH. Install it from https://esbuild.github.io/getting-started/#install-esbuild",
            e
          )
    val output = new String(proc.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    val code   = proc.waitFor()
    if code != 0 then
      throw new RuntimeException(s"esbuild exited with code $code:\n$output")

  private def deleteRecursively(p: Path): Unit =
    if Files.exists(p) then
      Files
        .walk(p)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(path => Files.deleteIfExists(path))
