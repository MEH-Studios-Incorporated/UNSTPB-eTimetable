// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary
//
// Stand-in for the design canvas runtime: renders a .dc.html mockup as a
// static page so render.mjs can screenshot it. Covers only what the
// mockups use: holes, sc-for, sc-if and dc-import.
window.DCLogic = class { constructor(props) { this.props = props || {}; this.state = {}; } setState(s) { Object.assign(this.state, s); } forceUpdate() {} };
const HOLE = /\{\{\s*([^}]+?)\s*\}\}/g;
function lookup(scope, path) {
  if (path === 'true') return true; if (path === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(path)) return Number(path);
  return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), scope);
}
function whole(v) { const m = /^\s*\{\{\s*([^}]+?)\s*\}\}\s*$/.exec(v); return m ? m[1] : null; }
function interp(v, scope) { return v.replace(HOLE, (_, p) => { const x = lookup(scope, p); return x == null ? '' : String(x); }); }
function loadComponent(doc) {
  const s = doc.querySelector('script[data-dc-script]');
  const Cls = new Function('DCLogic', s.textContent + '\nreturn Component;')(window.DCLogic);
  return Cls;
}
function renderInto(parent, nodes, scope) {
  for (const n of nodes) {
    if (n.nodeType === 3) { parent.appendChild(document.createTextNode(interp(n.textContent, scope))); continue; }
    if (n.nodeType !== 1) continue;
    const tag = n.localName;
    if (tag === 'helmet') continue;
    if (tag === 'sc-for') {
      const list = lookup(scope, whole(n.getAttribute('list'))) || [];
      const as = n.getAttribute('as') || 'item';
      list.forEach((it, i) => renderInto(parent, n.childNodes, { ...scope, [as]: it, $index: i }));
      continue;
    }
    if (tag === 'sc-if') { if (lookup(scope, whole(n.getAttribute('value')))) renderInto(parent, n.childNodes, scope); continue; }
    if (tag === 'dc-import') {
      const props = {};
      for (const a of n.attributes) {
        if (a.name === 'name' || a.name.startsWith('hint-')) continue;
        const key = a.name.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
        const w = whole(a.value); props[key] = w ? lookup(scope, w) : interp(a.value, scope);
      }
      const xhr = new XMLHttpRequest(); xhr.open('GET', n.getAttribute('name') + '.dc.html', false); xhr.send();
      const doc = new DOMParser().parseFromString(xhr.responseText, 'text/html');
      renderComponent(parent, doc, props);
      continue;
    }
    const el = n.cloneNode(false);
    for (const a of [...el.attributes]) {
      const w = whole(a.value);
      if (w !== null) {
        const v = lookup(scope, w);
        if (typeof v === 'boolean') { if (v) el.setAttribute(a.name, ''); else el.removeAttribute(a.name); }
        else if (typeof v === 'function' || v == null) el.removeAttribute(a.name);
        else el.setAttribute(a.name, String(v));
      } else if (a.value.includes('{{')) el.setAttribute(a.name, interp(a.value, scope));
    }
    parent.appendChild(el);
    renderInto(el.localName === 'template' ? el.content : el, n.childNodes, scope);
  }
}
function renderComponent(parent, doc, props) {
  const Cls = loadComponent(doc);
  const c = new Cls(props);
  const vals = c.renderVals();
  for (const h of doc.querySelectorAll('helmet > *')) document.head.appendChild(h.cloneNode(true));
  renderInto(parent, doc.querySelector('x-dc').childNodes, vals);
}
document.addEventListener('DOMContentLoaded', () => {
  const root = document.createElement('div'); root.id = 'dc-root';
  renderComponent(root, document, {});
  document.querySelector('x-dc').remove();
  document.body.appendChild(root);
  window.__rendered = true;
});
