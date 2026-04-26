package sibgen.sitegen

/** Wraps a fragment of body HTML in a complete, self-contained HTML document. */
object Page:

  /** Render a full HTML document with the given body and title. The CSS theme is inlined.
    * If `mermaidJs` is non-empty, the bundle is inlined and an init script runs `mermaid.run`
    * once `document.fonts.ready` resolves (so labels measure against the real bundled font).
    */
  def render(bodyHtml: String, title: String,
             css: String = Theme.default,
             mermaidJs: String = ""): String =
    val mermaidBlock =
      if mermaidJs.nonEmpty then
        s"""<script>
           |$mermaidJs</script>
           |<script>
           |$mermaidInitScript</script>
           |""".stripMargin
      else ""
    s"""<!doctype html>
       |<html lang="en">
       |<head>
       |<meta charset="utf-8">
       |<meta name="viewport" content="width=device-width, initial-scale=1">
       |<title>${escape(title)}</title>
       |<style>
       |$css</style>
       |</head>
       |<body>
       |<main>
       |$bodyHtml</main>
       |<script>
       |$snippetScript</script>
       |${mermaidBlock}</body>
       |</html>
       |""".stripMargin

  /** Click handler for the per-snippet `type-check` button. Stub: shows a placeholder result.
    * Real backend wiring (compile-via-WASM or a worker) will replace the body of `runCheck`.
    */
  private val snippetScript: String =
    """(function () {
      |  function runCheck(source) {
      |    return new Promise(function (resolve) {
      |      setTimeout(function () {
      |        resolve({ ok: true, message: '(stub) type-check backend not yet wired up.' });
      |      }, 120);
      |    });
      |  }
      |  document.addEventListener('click', function (e) {
      |    var btn = e.target.closest && e.target.closest('.snippet-check');
      |    if (!btn) return;
      |    var snippet = btn.closest('.snippet');
      |    if (!snippet) return;
      |    var code   = snippet.querySelector('pre code');
      |    var status = snippet.querySelector('.snippet-status');
      |    if (!code || !status) return;
      |    btn.disabled = true;
      |    snippet.classList.remove('is-ok', 'is-error');
      |    status.textContent = '⋯ type-checking…';
      |    runCheck(code.textContent).then(function (r) {
      |      snippet.classList.add(r.ok ? 'is-ok' : 'is-error');
      |      status.textContent = (r.ok ? '✓ ' : '⚠ ') + r.message;
      |      btn.textContent = '» check again';
      |      btn.disabled = false;
      |    }).catch(function (err) {
      |      snippet.classList.add('is-error');
      |      status.textContent = '⚠ ' + String(err);
      |      btn.textContent = '» check again';
      |      btn.disabled = false;
      |    });
      |  });
      |})();
      |""".stripMargin

  /** Initialize mermaid with the editorial palette + iA Writer Mono S, then run after fonts load.
    * Dark/light is detected once at init; live re-render on prefers-color-scheme toggle is a follow-up.
    */
  private val mermaidInitScript: String =
    """(function () {
      |  var dark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
      |  var lightVars = {
      |    fontFamily: "'iA Writer Mono S', ui-monospace, monospace",
      |    fontSize: '14px',
      |    primaryColor: '#fbfbfd', primaryTextColor: '#2b2b2b', primaryBorderColor: '#2b2b2b',
      |    lineColor: '#2b2b2b',    secondaryColor: '#f5f5f5',  tertiaryColor: '#ffffff',
      |    noteBkgColor: '#fbfbfd', noteTextColor: '#2b2b2b',   noteBorderColor: '#2b2b2b',
      |    clusterBkg:  '#fbfbfd',  clusterBorder: '#2b2b2b',   edgeLabelBackground: '#ffffff'
      |  };
      |  var darkVars = {
      |    fontFamily: "'iA Writer Mono S', ui-monospace, monospace",
      |    fontSize: '14px',
      |    primaryColor: '#22252a', primaryTextColor: '#d8d8d4', primaryBorderColor: '#d8d8d4',
      |    lineColor: '#d8d8d4',    secondaryColor: '#2c2e30',  tertiaryColor: '#1d1f21',
      |    noteBkgColor: '#22252a', noteTextColor: '#d8d8d4',   noteBorderColor: '#d8d8d4',
      |    clusterBkg:  '#22252a',  clusterBorder: '#d8d8d4',   edgeLabelBackground: '#1d1f21'
      |  };
      |  mermaid.initialize({
      |    startOnLoad: false,
      |    theme: 'base',
      |    themeVariables: dark ? darkVars : lightVars
      |  });
      |  var run = function () { mermaid.run({ querySelector: 'pre.mermaid' }); };
      |  if (document.fonts && document.fonts.ready) document.fonts.ready.then(run);
      |  else run();
      |})();
      |""".stripMargin

  private def escape(s: String): String =
    val sb = StringBuilder(s.length)
    s.foreach {
      case '&' => sb.append("&amp;")
      case '<' => sb.append("&lt;")
      case '>' => sb.append("&gt;")
      case '"' => sb.append("&quot;")
      case c   => sb.append(c)
    }
    sb.toString
