package sibgen.sitegen

import java.nio.file.{Files, Path, StandardCopyOption}

/** Builds the page CSS by shelling out to esbuild, which inlines each font file as a base64
  * data URL via `--loader:.<ext>=dataurl`. The output is a single, fully self-contained CSS
  * string that can go straight into a `<style>` tag.
  *
  * Result is computed once per JVM (lazy val) — repeated renders pay no extra cost.
  */
object Bundler:

  /** Bundled CSS: theme rules + `@font-face` declarations with each font inlined as a data URL. */
  lazy val bundledCss: String = run()

  /** A font to package: where to read it from on the classpath, and the `@font-face` rule
    * that should reference it. The file is copied into the bundle under `fonts/<fileName>`.
    */
  private final case class Font(
    resource: String,        // classpath path
    fileName: String,        // basename written into the bundle dir
    family:   String,        // CSS font-family value
    format:   String,        // "woff2" | "truetype"
    weight:   Int  = 400,
    style:    String = "normal"
  ):
    def fontFace: String =
      s"""@font-face {
         |  font-family: '$family';
         |  src: url('./fonts/$fileName') format('$format');
         |  font-weight: $weight;
         |  font-style: $style;
         |  font-display: swap;
         |}
         |""".stripMargin

  private val fonts: List[Font] = List(
    Font(
      resource = "/sibgen/fonts/SourceSerif4-Regular.woff2",
      fileName = "SourceSerif4-Regular.woff2",
      family   = "Source Serif 4",
      format   = "woff2"
    ),
    Font(
      resource = "/sibgen/fonts/iAWriterMonoS-Regular.ttf",
      fileName = "iAWriterMonoS-Regular.ttf",
      family   = "iA Writer Mono S",
      format   = "truetype"
    ),
    Font(
      resource = "/sibgen/fonts/iAWriterMonoS-Italic.ttf",
      fileName = "iAWriterMonoS-Italic.ttf",
      family   = "iA Writer Mono S",
      format   = "truetype",
      style    = "italic"
    ),
    Font(
      resource = "/sibgen/fonts/iAWriterMonoS-Bold.ttf",
      fileName = "iAWriterMonoS-Bold.ttf",
      family   = "iA Writer Mono S",
      format   = "truetype",
      weight   = 700
    ),
    Font(
      resource = "/sibgen/fonts/iAWriterMonoS-BoldItalic.ttf",
      fileName = "iAWriterMonoS-BoldItalic.ttf",
      family   = "iA Writer Mono S",
      format   = "truetype",
      weight   = 700,
      style    = "italic"
    )
  )

  private def run(): String =
    val tmp = Files.createTempDirectory("sibgen-bundle-")
    try
      val fontsDir = Files.createDirectories(tmp.resolve("fonts"))
      fonts.foreach(f => copyResource(f.resource, fontsDir.resolve(f.fileName)))
      Files.writeString(tmp.resolve("fonts.css"), fonts.map(_.fontFace).mkString)
      Files.writeString(tmp.resolve("theme.css"), Theme.default)
      Files.writeString(tmp.resolve("entry.css"), entryCss)

      val out = tmp.resolve("out.css")
      runEsbuild(tmp.resolve("entry.css"), out)
      Files.readString(out)
    finally deleteRecursively(tmp)

  private val entryCss: String =
    """@import "./fonts.css";
      |@import "./theme.css";
      |""".stripMargin

  private def copyResource(resource: String, target: Path): Unit =
    val in = getClass.getResourceAsStream(resource)
    if in == null then
      throw new RuntimeException(s"font resource not found on classpath: $resource")
    try Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING)
    finally in.close()

  private def runEsbuild(entry: Path, out: Path): Unit =
    val cmd = java.util.List.of(
      "esbuild",
      entry.toString,
      "--bundle",
      "--loader:.woff2=dataurl",
      "--loader:.ttf=dataurl",
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
