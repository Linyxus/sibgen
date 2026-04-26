package sibgen.md.commonmark

import org.commonmark.Extension
import org.commonmark.parser.{IncludeSourceSpans, Parser}
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension

import java.util as ju

/** Shared commonmark-java configuration: the extension list and the parser. Visible across the
  * `sibgen` package so renderers can register the same extension list on `HtmlRenderer`.
  */
private[sibgen] object Extensions:

  val all: ju.List[Extension] = ju.List.of(
    TablesExtension.create(),
    StrikethroughExtension.create(),
    TaskListItemsExtension.create(),
    FootnotesExtension.create(),
    AutolinkExtension.create()
  )

  val parser: Parser =
    Parser
      .builder()
      .extensions(all)
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .build()
