// CodeMirror 6 entry — bundled into a single IIFE by scripts/build-codemirror.sh
// and vendored at src/main/resources/sibgen/js/codemirror.iife.js.
//
// Exposes one global, window.SibgenSnippets, with a single method `init()` that
// walks every `.snippet-scala pre code` on the page, replaces the <pre> with a
// minimal CM6 EditorView, and stashes the view on the parent .snippet element
// so the type-check button can read its current contents.
//
// Token classes are mapped to our existing `.hl-*` CSS rules (defined in
// sibgen.sitegen.Theme), so light/dark colour palettes carry over automatically.

import { EditorState, Extension } from '@codemirror/state';
import { EditorView, keymap }     from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { StreamLanguage, HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { scala } from '@codemirror/legacy-modes/mode/clike';
import { tags as t } from '@lezer/highlight';

// Mapping mirrors the categories dotty's own SyntaxHighlighting paints in
// compiler diagnostics (KeywordColor / DefinitionColor / TypeColor / LiteralColor /
// CommentColor). The legacy-modes Scala tokenizer emits CM5 token names which
// CM6 maps to these lezer-highlight tags:
//   keyword  -> tags.keyword           (val, def, class, …)
//   type     -> tags.typeName          (Int, String, UpperCase identifiers)
//   def      -> tags.definition(...)   (the name being defined: `def foo`, `val x`)
//   atom     -> tags.atom              (true, false, null)
//   meta     -> tags.meta              (annotations like @main, interpolation prefix)
//   string/number/comment/operator     (literals, comments, ops)
const highlightStyle = HighlightStyle.define([
  { tag: [t.keyword, t.controlKeyword, t.definitionKeyword],             class: 'hl-kw' },
  { tag: t.modifier,                                                     class: 'hl-soft' },
  { tag: [t.number, t.bool, t.null, t.atom],                             class: 'hl-num' },
  { tag: [t.string, t.character, t.special(t.string), t.regexp],         class: 'hl-str' },
  { tag: [t.lineComment, t.blockComment, t.docComment, t.comment],       class: 'hl-comment' },
  { tag: t.operator,                                                     class: 'hl-op' },
  { tag: [t.punctuation, t.brace, t.bracket, t.paren, t.separator],      class: 'hl-punct' },
  { tag: [t.typeName, t.className, t.namespace, t.meta, t.annotation],   class: 'hl-type' },
  { tag: [t.definition(t.variableName), t.definition(t.propertyName)],   class: 'hl-def' },
]);

const extensions: Extension[] = [
  history(),
  keymap.of([...defaultKeymap, ...historyKeymap]),
  StreamLanguage.define(scala),
  syntaxHighlighting(highlightStyle),
];

interface SnippetHost extends HTMLElement {
  cmView?: EditorView;
}

declare global {
  interface Window {
    SibgenSnippets?: { init: () => void };
  }
}

window.SibgenSnippets = {
  init(): void {
    const codes = document.querySelectorAll<HTMLElement>('.snippet-scala pre code');
    codes.forEach((codeEl) => {
      const snippet = codeEl.closest<HTMLElement>('.snippet') as SnippetHost | null;
      const preEl   = codeEl.closest<HTMLElement>('pre');
      if (!snippet || !preEl || snippet.cmView) return;            // skip if already mounted

      const doc  = codeEl.textContent ?? '';
      const view = new EditorView({
        state: EditorState.create({ doc, extensions }),
      });
      snippet.replaceChild(view.dom, preEl);
      snippet.cmView = view;
    });
  },
};
