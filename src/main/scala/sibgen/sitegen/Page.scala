package sibgen.sitegen

/** Wraps a fragment of body HTML in a complete, self-contained HTML document. */
object Page:

  /** Render a full HTML document with the given body and title. The CSS theme is inlined. */
  def render(bodyHtml: String, title: String, css: String = Theme.default): String =
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
       |</body>
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
      |    var result = snippet.querySelector('.snippet-result');
      |    if (!code || !result) return;
      |    btn.disabled = true;
      |    result.hidden = false;
      |    result.className = 'snippet-result';
      |    result.textContent = 'Type-checking…';
      |    runCheck(code.textContent).then(function (r) {
      |      result.className = 'snippet-result ' + (r.ok ? 'is-ok' : 'is-error');
      |      result.textContent = r.message;
      |      btn.disabled = false;
      |    }).catch(function (err) {
      |      result.className = 'snippet-result is-error';
      |      result.textContent = String(err);
      |      btn.disabled = false;
      |    });
      |  });
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
