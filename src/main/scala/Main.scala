import sibgen.md.Markdown
import sibgen.sitegen.Renderer

import java.nio.file.{Files, Path}

@main def render(input: String): Unit =
  val text  = Files.readString(Path.of(input))
  val doc   = Markdown.parse(text)
  val title = Renderer.titleOf(doc).getOrElse(defaultTitle(input))
  val html  = Renderer.renderPage(doc, title)
  val out   = outputPath(input)
  Files.writeString(out, html)
  System.err.println(s"wrote $out")

private def defaultTitle(input: String): String =
  val name = Path.of(input).getFileName.toString
  if name.endsWith(".md") then name.dropRight(3) else name

private def outputPath(input: String): Path =
  val p    = Path.of(input).toAbsolutePath
  val name = p.getFileName.toString
  val out  = if name.endsWith(".md") then name.dropRight(3) + ".html" else name + ".html"
  p.resolveSibling(out)
