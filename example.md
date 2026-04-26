# Sibgen — Example Document

A small note to exercise every construct the renderer supports. Nothing here matters; it is only here to look at.

## Prose

Markdown is, in the end, *just paragraphs*: lines of text separated by blank lines, with the occasional **emphasis** or `inline code` for the bits that need attention. Sometimes a sentence stretches further than it should and wraps onto a soft line
break, which the renderer collapses into a single space.

Reading is the point. The column is narrow on purpose — about 38 rem — so the eye returns to the left margin without effort. The font stack prefers iA Writer's own families when they are installed, and falls back to whatever monospace the operating system provides.

## Quotes

> The most valuable of all talents is that of never using two words when one will do.
>
> — Thomas Jefferson

A quote can carry several paragraphs. It is set in italic and offset by a hairline rule on the left, so it reads as a slight aside rather than a separate object on the page.

## Lists

A few things to do, by way of example:

- [x] Decide on a font stack
- [x] Write a CSS theme
- [ ] Bundle web fonts as base64
- [ ] Add a table-of-contents sidebar
- A plain item with no checkbox, just to show that mixed lists are fine

Ordered lists work the same way:

1. Parse the markdown into the ADT.
2. Convert the ADT back to commonmark-java's tree.
3. Render that tree to HTML.
4. Wrap the HTML in a styled page template.

## Code

Inline code looks like `Markdown.parse(text)` and a fenced block can be tagged with a language:

```scala
def renderPage(doc: adt.Document, title: String): String =
  Page.render(renderHtmlBody(doc), title)
```

```bash
sbt 'runMain render example.md'
```

The renderer does not tokenize the code; syntax highlighting would need a separate pass (Shiki at build time is the obvious choice).

## A table

| Feature        | Status      | Notes                          |
|:---------------|:-----------:|-------------------------------:|
| CommonMark     |  supported  |               full coverage    |
| GFM tables     |  supported  |             alignment honored  |
| Task lists     |  supported  |        absorbed into ListItem  |
| Footnotes     |  supported  |    rendered at end of document  |
| Math (MathJax) |   pending   |                       —        |

## Links and references

You can write links inline: [iA Writer](https://ia.net/writer "iA Writer's home page"). You can also collect them at the bottom and reference them by label, which keeps the prose readable: see the [commonmark spec][cmark] for how this works.

## Footnotes

Footnotes[^1] are useful for quiet asides[^digression] without breaking the flow of the main text.

[^1]: The first footnote, kept short.

[^digression]: The second footnote, slightly longer, to show that the renderer wraps and indents the content properly.

## A horizontal rule

Below this line is a thematic break:

---

…and that is the end of the example.

[cmark]: https://spec.commonmark.org/ "CommonMark specification"
