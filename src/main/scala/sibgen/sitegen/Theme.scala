package sibgen.sitegen

/** CSS themes for [[Page]]. */
object Theme:

  /** Default theme: monospace-flavoured stack, narrow column, hairline rules, generous whitespace,
    * gentle dark mode. Self-contained — no external font requests.
    */
  val default: String =
    """:root {
      |  --bg:           #ffffff;
      |  --fg:           #2b2b2b;
      |  --fg-soft:      #6c6c6c;
      |  --fg-fade:      #a0a098;
      |  --accent:       #0e84b5;
      |  --rule:         #e6e6e6;
      |  --code-bg:      #fbfbfd;
      |  --code-border:  #d6dfeb;
      |  --quote-rule:   #cccccc;
      |}
      |
      |@media (prefers-color-scheme: dark) {
      |  :root {
      |    --bg:           #1d1f21;
      |    --fg:           #d8d8d4;
      |    --fg-soft:      #9a9a96;
      |    --fg-fade:      #6c6c68;
      |    --accent:       #58c4ff;
      |    --rule:         #2c2e30;
      |    --code-bg:      #22252a;
      |    --code-border:  #38465a;
      |    --quote-rule:   #3a3c3e;
      |  }
      |}
      |
      |* { box-sizing: border-box; }
      |html, body { margin: 0; padding: 0; }
      |
      |body {
      |  background: var(--bg);
      |  color: var(--fg);
      |  font-family: 'iA Writer Quattro S', 'iA Writer Quattro', 'iA Writer Duo S',
      |               ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, Consolas, monospace;
      |  font-size: 17px;
      |  line-height: 1.6;
      |  font-feature-settings: "kern", "liga", "calt";
      |  -webkit-font-smoothing: antialiased;
      |  -moz-osx-font-smoothing: grayscale;
      |  text-rendering: optimizeLegibility;
      |}
      |
      |main {
      |  max-width: 44rem;
      |  margin: 4rem auto 6rem;
      |  padding: 0 1.5rem;
      |}
      |
      |h1, h2, h3, h4, h5, h6 {
      |  color: var(--fg);
      |  font-weight: 700;
      |  line-height: 1.25;
      |  margin: 2.4em 0 0.6em;
      |  letter-spacing: -0.005em;
      |}
      |h1 { font-size: 1.7em; margin-top: 0; }
      |h2 { font-size: 1.35em; }
      |h3 { font-size: 1.15em; }
      |h4, h5, h6 { font-size: 1em; }
      |
      |p { margin: 1em 0; }
      |
      |a {
      |  color: var(--accent);
      |  text-decoration: none;
      |  border-bottom: 1px solid currentColor;
      |  transition: opacity 0.15s ease;
      |}
      |a:hover { opacity: 0.6; }
      |
      |strong { font-weight: 700; }
      |em     { font-style: italic; }
      |del    { color: var(--fg-soft); }
      |
      |blockquote {
      |  margin: 1.5em 0;
      |  padding: 0 0 0 1.2em;
      |  border-left: 2px solid var(--quote-rule);
      |  color: var(--fg-soft);
      |  font-style: italic;
      |}
      |blockquote p { margin: 0.5em 0; }
      |
      |hr {
      |  border: none;
      |  border-top: 1px solid var(--rule);
      |  margin: 2.5em 0;
      |}
      |
      |pre, code, kbd, samp {
      |  font-family: 'iA Writer Mono S', 'iA Writer Mono',
      |               ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, Consolas, monospace;
      |  font-size: 0.92em;
      |}
      |code {
      |  background: var(--code-bg);
      |  padding: 0.12em 0.4em;
      |  border-radius: 0;
      |}
      |pre {
      |  background: var(--code-bg);
      |  border: 1px solid var(--code-border);
      |  border-radius: 0;
      |  padding: 0.7em 1em;
      |  margin: 1.5em 0;
      |  overflow-x: auto;
      |  line-height: 1.55;
      |}
      |pre code {
      |  background: transparent;
      |  padding: 0;
      |  border: 0;
      |  border-radius: 0;
      |}
      |
      |ul, ol {
      |  padding-left: 1.6em;
      |  margin: 1em 0;
      |}
      |li { margin: 0.3em 0; }
      |li > p { margin: 0.4em 0; }
      |
      |li:has(> input[type="checkbox"]) {
      |  list-style: none;
      |}
      |li > input[type="checkbox"] {
      |  margin-right: 0.5em;
      |  margin-left: -1.4em;
      |  vertical-align: 0.05em;
      |  accent-color: var(--accent);
      |}
      |
      |img {
      |  max-width: 100%;
      |  display: block;
      |  margin: 1.5em auto;
      |}
      |
      |table {
      |  border-collapse: collapse;
      |  margin: 1.5em 0;
      |  width: 100%;
      |  font-size: 0.95em;
      |}
      |th, td {
      |  text-align: left;
      |  padding: 0.5em 0.85em;
      |  border-bottom: 1px solid var(--rule);
      |}
      |th {
      |  font-weight: 700;
      |  border-bottom: 2px solid var(--rule);
      |}
      |td[align="center"], th[align="center"] { text-align: center; }
      |td[align="right"],  th[align="right"]  { text-align: right; }
      |
      |/* Footnotes — commonmark-java emits an <ol class="footnotes"> at the end. */
      |.footnotes {
      |  margin-top: 4em;
      |  padding-top: 1.5em;
      |  border-top: 1px solid var(--rule);
      |  font-size: 0.9em;
      |  color: var(--fg-soft);
      |}
      |.footnotes ol { padding-left: 1.4em; }
      |sup, .footnote-ref { font-size: 0.78em; }
      |sup a, .footnote-ref a, .footnote-backref { border-bottom: none; }
      |""".stripMargin
