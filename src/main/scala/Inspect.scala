import sibgen.md.Markdown
import sibgen.md.adt.Pretty

import java.nio.file.{Files, Path}

@main def inspect(path: String): Unit =
  val text = Files.readString(Path.of(path))
  print(Pretty.show(Markdown.parse(text)))
