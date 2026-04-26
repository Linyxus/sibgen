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
      |  // Compiler artifacts are served at /assets/{main.js,classpath.bin} (~81 MB combined).
      |  // We eager-fetch on script load so the compiler is usually warm by the time the user clicks.
      |  var compiler = null;            // window.DottyCompiler once loaded
      |  var loading  = null;            // Promise<DottyCompiler> while in flight, null otherwise
      |
      |  // Loading state lives in the status field (left, wide), not the button label —
      |  // keeps the button slim so its absolute box never grows the strip vertically.
      |  function setAllButtonsHidden(hidden) {
      |    var btns = document.querySelectorAll('.snippet-check');
      |    for (var i = 0; i < btns.length; i++) {
      |      btns[i].hidden = hidden;
      |      btns[i].disabled = hidden;
      |    }
      |  }
      |  function setAllStatuses(text) {
      |    var ss = document.querySelectorAll('.snippet-status');
      |    for (var i = 0; i < ss.length; i++) ss[i].textContent = text;
      |  }
      |
      |  function fetchWithProgress(url, onProgress) {
      |    return new Promise(function (resolve, reject) {
      |      var xhr = new XMLHttpRequest();
      |      xhr.open('GET', url, true);
      |      xhr.responseType = 'arraybuffer';
      |      xhr.onprogress = function (e) {
      |        if (!onProgress) return;
      |        if (e.lengthComputable) onProgress({ loaded: e.loaded, total: e.total });
      |        else                    onProgress({ loaded: e.loaded, total: null });
      |      };
      |      xhr.onload = function () {
      |        if (xhr.status >= 200 && xhr.status < 300) {
      |          if (onProgress) onProgress({ loaded: xhr.response.byteLength, total: xhr.response.byteLength });
      |          resolve(xhr.response);
      |        } else reject(new Error('HTTP ' + xhr.status + ' for ' + url));
      |      };
      |      xhr.onerror = function () { reject(new Error('Network error fetching ' + url)); };
      |      xhr.send();
      |    });
      |  }
      |
      |  // main.js is a Scala.js NoModule bundle: top-level `let DottyCompiler` lives in the
      |  // Script scope, not on window. We inject it as a classic <script> via a blob URL,
      |  // then a second inline classic script bridges the binding onto window.
      |  function installCompilerScript(buf) {
      |    return new Promise(function (resolve, reject) {
      |      var blob = new Blob([buf], { type: 'application/javascript' });
      |      var main = document.createElement('script');
      |      main.src = URL.createObjectURL(blob);
      |      main.onload = function () {
      |        URL.revokeObjectURL(main.src);
      |        var bridge = document.createElement('script');
      |        bridge.textContent = 'try { window.DottyCompiler = DottyCompiler; } catch (e) { window.__dottyBridgeError = e; }';
      |        document.head.appendChild(bridge);
      |        if (window.__dottyBridgeError) reject(window.__dottyBridgeError);
      |        else if (!window.DottyCompiler) reject(new Error('main.js loaded but DottyCompiler was not exposed'));
      |        else resolve();
      |      };
      |      main.onerror = function () { URL.revokeObjectURL(main.src); reject(new Error('Failed to load main.js')); };
      |      document.head.appendChild(main);
      |    });
      |  }
      |
      |  function ensureCompiler() {
      |    if (compiler) return Promise.resolve(compiler);
      |    if (loading)  return loading;
      |
      |    var pMain = { loaded: 0, total: null };
      |    var pCp   = { loaded: 0, total: null };
      |    function update() {
      |      var loaded = pMain.loaded + pCp.loaded;
      |      var msg;
      |      if (pMain.total != null && pCp.total != null) {
      |        var pct = Math.floor(100 * loaded / (pMain.total + pCp.total));
      |        msg = 'downloading the compiler · ' + pct + '%';
      |      } else {
      |        msg = 'downloading the compiler · ' + Math.floor(loaded / 1024 / 1024) + ' MB';
      |      }
      |      setAllStatuses(msg);
      |    }
      |
      |    setAllButtonsHidden(true);
      |    setAllStatuses('downloading the compiler · 0%');
      |
      |    loading = Promise.all([
      |      fetchWithProgress('/assets/main.js',       function (e) { pMain = e; update(); }),
      |      fetchWithProgress('/assets/classpath.bin', function (e) { pCp   = e; update(); })
      |    ]).then(function (bufs) {
      |      setAllStatuses('initializing the compiler…');
      |      return installCompilerScript(bufs[0]).then(function () {
      |        window.DottyCompiler.loadClasspath(bufs[1]);
      |        compiler = window.DottyCompiler;
      |        loading  = null;
      |        setAllStatuses('');
      |        setAllButtonsHidden(false);
      |        return compiler;
      |      });
      |    }, function (err) {
      |      loading = null;
      |      setAllStatuses('⚠ ' + (err && err.message ? err.message : String(err)));
      |      setAllButtonsHidden(false);
      |      throw err;
      |    });
      |
      |    return loading;
      |  }
      |
      |  // ANSI SGR → HTML span class converter. Lifted from the scala3-js demo's
      |  // src/util/ansi.ts. CSS classes are defined in Theme.default ('.ansi-*').
      |  var ANSI_COLORS = {
      |    '30': 'ansi-black',  '31': 'ansi-red',     '32': 'ansi-green',
      |    '33': 'ansi-yellow', '34': 'ansi-blue',    '35': 'ansi-magenta',
      |    '36': 'ansi-cyan',   '37': 'ansi-white',
      |    '90': 'ansi-bright-red',     '91': 'ansi-bright-red',
      |    '92': 'ansi-bright-green',   '93': 'ansi-bright-yellow',
      |    '94': 'ansi-bright-blue',    '95': 'ansi-bright-magenta',
      |    '96': 'ansi-bright-cyan',    '97': 'ansi-bright-white'
      |  };
      |  var SGR_RE = /\x1b\[([0-9;]*)m/;
      |
      |  function escapeHtml(s) {
      |    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      |  }
      |
      |  function ansiToHtml(text) {
      |    var out = '';
      |    var open = false;
      |    var parts = text.split(SGR_RE);
      |    for (var i = 0; i < parts.length; i++) {
      |      if (i % 2 === 0) {
      |        out += escapeHtml(parts[i]);
      |      } else {
      |        var raw = parts[i];
      |        if (open) { out += '</span>'; open = false; }
      |        if (raw === '0' || raw === '') continue;
      |        var codes = raw.split(';');
      |        var classes = [];
      |        for (var j = 0; j < codes.length; j++) {
      |          var c = codes[j];
      |          if (c === '1') classes.push('ansi-bold');
      |          else if (ANSI_COLORS[c]) classes.push(ANSI_COLORS[c]);
      |        }
      |        if (classes.length > 0) {
      |          out += '<span class="' + classes.join(' ') + '">';
      |          open = true;
      |        }
      |      }
      |    }
      |    if (open) out += '</span>';
      |    return out;
      |  }
      |
      |  // Returns { summary, detail, kind }: summary is the short toolbar line,
      |  // detail is the full-width diagnostic body (HTML, may be empty).
      |  function formatDiagnostics(diags) {
      |    if (!diags || diags.length === 0) return { summary: '✓ no errors', detail: '', kind: 'is-ok' };
      |    if (window.console && window.console.log) console.log('[type-check] diagnostics:', diags);
      |    var errors = 0, warns = 0;
      |    for (var i = 0; i < diags.length; i++) {
      |      if (diags[i].severity === 'error')        errors++;
      |      else if (diags[i].severity === 'warning') warns++;
      |    }
      |    var summary, kind = 'is-ok';
      |    if (errors > 0) {
      |      summary = '⚠ ' + errors + (errors === 1 ? ' error' : ' errors');
      |      if (warns > 0) summary += ', ' + warns + (warns === 1 ? ' warning' : ' warnings');
      |      kind = 'is-error';
      |    } else if (warns > 0) {
      |      summary = '▲ ' + warns + (warns === 1 ? ' warning' : ' warnings');
      |    } else {
      |      summary = '◌ ' + diags.length + (diags.length === 1 ? ' note' : ' notes');
      |    }
      |    // Each diagnostic gets its own labeled block so warnings stay visible even
      |    // if their messageAndPos rendering is short or visually similar to a neighbor.
      |    var detail = diags.map(function (d) {
      |      var sev = d.severity || 'info';
      |      var pos = (d.line >= 0 && d.column >= 0) ? ' ' + d.line + ':' + d.column : '';
      |      var label = '[' + sev + pos + ']';
      |      var body = (d.message && d.message.replace(/\s+/g, '')) ? ansiToHtml(d.message) : '<span class="ansi-bright-white">(no message)</span>';
      |      return '<div class="snippet-diag" data-severity="' + sev + '">' +
      |             '<span class="snippet-diag__label">' + escapeHtml(label) + '</span>\n' +
      |             body +
      |             '</div>';
      |    }).join('');
      |    return { summary: escapeHtml(summary), detail: detail, kind: kind };
      |  }
      |
      |  function applyResult(snippet, status, detail, btn, kind, summaryHtml, detailHtml) {
      |    snippet.classList.add(kind);
      |    status.innerHTML = summaryHtml;
      |    if (detailHtml) {
      |      detail.innerHTML = detailHtml;
      |      detail.hidden = false;
      |    } else {
      |      detail.innerHTML = '';
      |      detail.hidden = true;
      |    }
      |    btn.textContent = '» check again';
      |  }
      |
      |  // Measure the strip's available text width in monospace columns so the compiler's
      |  // -pagewidth matches the rendered container exactly. Probe lives inside the strip so
      |  // it inherits font / size / feature settings; we subtract the strip's own padding.
      |  function measureColumns(stripEl) {
      |    var probe = document.createElement('span');
      |    probe.style.position   = 'absolute';
      |    probe.style.visibility = 'hidden';
      |    probe.style.whiteSpace = 'pre';
      |    probe.textContent = 'M'.repeat ? 'M'.repeat(100) : new Array(101).join('M');
      |    stripEl.appendChild(probe);
      |    var charW = probe.offsetWidth / 100;
      |    stripEl.removeChild(probe);
      |    var s    = getComputedStyle(stripEl);
      |    var padL = parseFloat(s.paddingLeft)  || 0;
      |    var padR = parseFloat(s.paddingRight) || 0;
      |    var availableW = stripEl.clientWidth - padL - padR;
      |    if (!charW || availableW <= 0) return 80;
      |    return Math.max(20, Math.floor(availableW / charW) - 1);
      |  }
      |
      |  function runCheck(snippet, strip, status, detail, btn, source) {
      |    return ensureCompiler().then(function (c) {
      |      // Yield once so '⋯ type-checking…' paints before compile() blocks the main thread.
      |      return new Promise(function (resolve) {
      |        setTimeout(function () {
      |          try {
      |            var args = ['-pagewidth', String(measureColumns(strip))];
      |            var diags = c.compile(source, args);
      |            var r = formatDiagnostics(diags);
      |            applyResult(snippet, status, detail, btn, r.kind, r.summary, r.detail);
      |          } catch (e) {
      |            applyResult(snippet, status, detail, btn, 'is-error', '⚠ ' + escapeHtml(e && e.message ? e.message : String(e)), '');
      |          }
      |          resolve();
      |        }, 0);
      |      });
      |    }, function (err) {
      |      applyResult(snippet, status, detail, btn, 'is-error', '⚠ ' + escapeHtml(err && err.message ? err.message : String(err)), '');
      |    });
      |  }
      |
      |  document.addEventListener('click', function (e) {
      |    var btn = e.target.closest && e.target.closest('.snippet-check');
      |    if (!btn || btn.disabled) return;
      |    var snippet = btn.closest('.snippet');
      |    if (!snippet) return;
      |    var strip  = snippet.querySelector('.snippet-strip');
      |    var code   = snippet.querySelector('pre code');
      |    var status = snippet.querySelector('.snippet-status');
      |    var detail = snippet.querySelector('.snippet-detail');
      |    if (!strip || !code || !status || !detail) return;
      |    btn.disabled = true;
      |    snippet.classList.remove('is-ok', 'is-error');
      |    detail.hidden = true;
      |    detail.innerHTML = '';
      |    status.textContent = '⋯ type-checking…';
      |    runCheck(snippet, strip, status, detail, btn, code.textContent).then(function () {
      |      btn.disabled = false;
      |    });
      |  });
      |
      |  // Eager kick-off: only fetch the compiler on pages that actually have snippets.
      |  if (document.querySelector('.snippet-check')) ensureCompiler();
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
