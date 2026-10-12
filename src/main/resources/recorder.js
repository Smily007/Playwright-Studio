(() => {
  if (window.__studioInstalled) return;
  window.__studioInstalled = true;
  const send = (o) => { try { o.url = location.href; window.__studioRecord(JSON.stringify(o)); } catch (e) {} };
  const clean = (s) => (s || '').replace(/\s+/g, ' ').trim();
  const TEXT_TYPES = ['', 'text', 'email', 'search', 'tel', 'url', 'number', 'password', 'date', 'time', 'datetime-local', 'month', 'week'];
  const INTERACTIVE = 'button,a[href],input,select,textarea,label,summary,[role=button],[role=link],[role=menuitem],[role=tab],[role=checkbox],[role=radio],[role=option],[onclick]';
  const lastFilled = new WeakMap();

  const implicitRole = (el) => {
    const explicit = el.getAttribute('role'); if (explicit) return explicit.split(' ')[0];
    const tag = el.tagName.toLowerCase();
    if (tag === 'button') return 'button';
    if (tag === 'a' && el.hasAttribute('href')) return 'link';
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (/^h[1-6]$/.test(tag)) return 'heading';
    if (tag === 'img' && el.getAttribute('alt') !== '') return 'img';
    if (tag === 'option') return 'option';
    if (tag === 'input') {
      const t = (el.getAttribute('type') || 'text').toLowerCase();
      if (['button', 'submit', 'reset', 'image'].includes(t)) return 'button';
      if (t === 'checkbox') return 'checkbox';
      if (t === 'radio') return 'radio';
      if (t === 'search') return 'searchbox';
      if (t === 'number') return 'spinbutton';
      if (t === 'range') return 'slider';
      if (['text', 'email', 'tel', 'url'].includes(t)) return 'textbox';
      return '';
    }
    return '';
  };
  const labelText = (el) => {
    const al = el.getAttribute('aria-label'); if (al) return clean(al);
    const lb = el.getAttribute('aria-labelledby');
    if (lb) {
      const t = lb.split(/\s+/).map((id) => { const n = document.getElementById(id); return n ? n.textContent : ''; }).join(' ');
      if (clean(t)) return clean(t);
    }
    if (el.labels && el.labels.length) { const t = clean(el.labels[0].textContent); if (t) return t; }
    return '';
  };
  const accName = (el) => {
    const l = labelText(el); if (l) return l;
    const tag = el.tagName.toLowerCase();
    if (tag === 'input') {
      const t = (el.getAttribute('type') || '').toLowerCase();
      if (['button', 'submit', 'reset'].includes(t)) return clean(el.value);
      if (t === 'image') return clean(el.getAttribute('alt'));
      return '';
    }
    if (tag === 'img') return clean(el.getAttribute('alt'));
    const txt = clean(el.innerText || el.textContent);
    if (txt) return txt.length > 80 ? '' : txt;
    return clean(el.getAttribute('title'));
  };
  const isStableId = (id) => id && !/\d{4,}/.test(id) && !/^[a-f0-9-]{16,}$/i.test(id) && !/^(ember|react|:r|rc-|ext-|mui-)/i.test(id);
  const cssFor = (el) => {
    if (el.id && isStableId(el.id)) return '#' + CSS.escape(el.id);
    const parts = []; let cur = el;
    while (cur && cur.nodeType === 1 && parts.length < 4) {
      let part = cur.tagName.toLowerCase();
      if (cur.id && isStableId(cur.id)) { parts.unshift('#' + CSS.escape(cur.id)); break; }
      const nm = cur.getAttribute('name');
      if (nm) part += '[name="' + nm + '"]';
      else {
        const sib = cur.parentElement ? Array.from(cur.parentElement.children).filter((c) => c.tagName === cur.tagName) : [];
        if (sib.length > 1) part += ':nth-of-type(' + (sib.indexOf(cur) + 1) + ')';
      }
      parts.unshift(part); cur = cur.parentElement;
    }
    return parts.join(' > ');
  };
  const xpathFor = (el) => {
    const segs = [];
    for (let cur = el; cur && cur.nodeType === 1; cur = cur.parentElement) {
      let i = 1;
      for (let s = cur.previousElementSibling; s; s = s.previousElementSibling) { if (s.tagName === cur.tagName) i++; }
      segs.unshift(cur.tagName.toLowerCase() + '[' + i + ']');
    }
    return '/' + segs.join('/');
  };
  const xpathLiteral = (value) => {
    if (!value.includes("'")) return "'" + value + "'";
    if (!value.includes('"')) return '"' + value + '"';
    return "concat('" + value.split("'").join("', \"'\", '") + "')";
  };
  const relativeXPathFor = (el) => {
    const segments = [];
    for (let cur = el; cur && cur.nodeType === 1; cur = cur.parentElement) {
      let segment = cur.tagName.toLowerCase();
      const siblings = cur.parentElement ? Array.from(cur.parentElement.children).filter((s) => s.tagName === cur.tagName) : [];
      if (siblings.length > 1) segment += '[' + (siblings.indexOf(cur) + 1) + ']';
      segments.push(segment);

      const candidates = [['id', cur.id], ['data-testid', cur.getAttribute('data-testid')],
        ['name', cur.getAttribute('name')], ['aria-label', cur.getAttribute('aria-label')]];
      for (const [attribute, value] of candidates) {
        if (!value) continue;
        const candidate = '//*[' + '@' + attribute + '=' + xpathLiteral(value) + ']';
        if (document.evaluate('count(' + candidate + ')', document, null, XPathResult.NUMBER_TYPE, null).numberValue === 1) {
          segments.pop();
          return candidate + (segments.length ? '/' + segments.reverse().join('/') : '');
        }
      }
    }
    return '//' + segments.reverse().join('/');
  };
  const describe = (el) => ({
    tag: el.tagName.toLowerCase(), id: el.id || '', type: el.getAttribute('type') || '',
    role: implicitRole(el), name: accName(el), label: labelText(el),
    placeholder: el.getAttribute('placeholder') || '', testid: el.getAttribute('data-testid') || '',
    alt: el.getAttribute('alt') || '', title: el.getAttribute('title') || '',
    text: clean(el.innerText || el.textContent).slice(0, 80),
    css: cssFor(el), xpath: xpathFor(el)
  });
  const frameSel = () => {
    if (window === window.top) return '';
    try {
      const fe = window.frameElement;
      if (fe) {
        if (fe.getAttribute('name')) return 'iframe[name="' + fe.getAttribute('name') + '"]';
        if (fe.id) return 'iframe[id="' + fe.id + '"]';
        const s = fe.getAttribute('src'); if (s) return 'iframe[src="' + s + '"]';
      }
    } catch (e) {}
    return '';
  };
  const target = (e) => {
    const t = e.target && e.target.nodeType === 1 ? e.target : (e.target && e.target.parentElement);
    return (t && t.closest && t.closest(INTERACTIVE)) || t;
  };
  const emit = (action, el, value, extra) => send(Object.assign({
    action, el: el ? describe(el) : null, value: value === undefined ? '' : value, frameSel: frameSel()
  }, extra || {}));
  const isTextControl = (el) => {
    const tag = el.tagName.toLowerCase(); const type = (el.getAttribute('type') || '').toLowerCase();
    return tag === 'textarea' || (tag === 'input' && TEXT_TYPES.includes(type));
  };
  let inspectMode = false;
  let inspectElement = null;
  let previousOutline = '';
  let previousOutlineOffset = '';
  const xpathHighlights = new Map();
  const clearInspectHighlight = () => {
    if (!inspectElement) return;
    inspectElement.style.outline = previousOutline;
    inspectElement.style.outlineOffset = previousOutlineOffset;
    inspectElement = null;
  };
  window.__studioSetInspectMode = (enabled) => {
    inspectMode = Boolean(enabled);
    if (!inspectMode) clearInspectHighlight();
  };
  window.__studioHighlightXPath = (expression) => {
    for (const [el, style] of xpathHighlights) el.style.boxShadow = style;
    xpathHighlights.clear();
    const xpath = String(expression || '').replace(/^xpath=/i, '').trim();
    if (!xpath) return 0;
    const result = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
    for (let i = 0; i < result.snapshotLength; i++) {
      const el = result.snapshotItem(i);
      if (!(el instanceof Element)) continue;
      if (!xpathHighlights.has(el)) xpathHighlights.set(el, el.style.boxShadow);
      el.style.boxShadow = '0 0 0 3px #ff9800';
    }
    return result.snapshotLength;
  };
  const inspectTarget = (e) => {
    const el = e.target && e.target.nodeType === 1 ? e.target : (e.target && e.target.parentElement);
    if (!el || !inspectMode) return;
    if (inspectElement !== el) {
      clearInspectHighlight();
      inspectElement = el;
      previousOutline = el.style.outline;
      previousOutlineOffset = el.style.outlineOffset;
      el.style.outline = '2px solid #1a73e8';
      el.style.outlineOffset = '2px';
    }
  };
  document.addEventListener('pointerover', inspectTarget, true);
  document.addEventListener('click', (e) => {
    if (!inspectMode) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    const el = e.target && e.target.nodeType === 1 ? e.target : (e.target && e.target.parentElement);
    if (!el) return;
    inspectMode = false;
    clearInspectHighlight();
    try { window.__studioInspect(xpathFor(el), relativeXPathFor(el), frameSel()); } catch (error) {}
  }, true);
  document.addEventListener('keydown', (e) => {
    if (!inspectMode || e.key !== 'Escape') return;
    e.preventDefault();
    e.stopImmediatePropagation();
    inspectMode = false;
    clearInspectHighlight();
    try { window.__studioInspect('', '', ''); } catch (error) {}
  }, true);

  document.addEventListener('click', (e) => {
    const el = target(e); if (!el) return;
    const tag = el.tagName.toLowerCase(); const type = (el.getAttribute('type') || '').toLowerCase();
    if (tag === 'select' || tag === 'option') return;
    if (tag === 'input' && ['checkbox', 'radio', 'file'].includes(type)) return;
    if (tag === 'label' && el.control && ['checkbox', 'radio'].includes(el.control.type)) return;
    if (isTextControl(el)) return;
    emit('CLICK', el);
  }, true);
  document.addEventListener('dblclick', (e) => { const el = target(e); if (el) emit('DBLCLICK', el); }, true);
  document.addEventListener('contextmenu', (e) => { const el = target(e); if (el) emit('CLICK_RIGHT', el); }, true);
  document.addEventListener('change', (e) => {
    const el = e.target; if (!el || el.nodeType !== 1) return;
    const tag = el.tagName.toLowerCase(); const type = (el.getAttribute('type') || '').toLowerCase();
    if (tag === 'select') { emit('SELECT_OPTION', el, el.value); return; }
    if (tag === 'input' && type === 'checkbox') { emit(el.checked ? 'CHECK' : 'UNCHECK', el); return; }
    if (tag === 'input' && type === 'radio') { emit('CHECK', el); return; }
    if (tag === 'input' && type === 'file') { emit('SET_INPUT_FILES', el, Array.from(el.files).map((f) => f.name).join(',')); return; }
    if (isTextControl(el)) {
      if (lastFilled.get(el) === el.value) return;
      lastFilled.set(el, el.value);
      emit('FILL', el, el.value, { sensitive: type === 'password' });
    }
  }, true);
  document.addEventListener('keydown', (e) => {
    const el = e.target; if (!el || el.nodeType !== 1) return;
    if (!['Enter', 'Tab', 'Escape'].includes(e.key)) return;
    const tag = el.tagName.toLowerCase(); const type = (el.getAttribute('type') || '').toLowerCase();
    if (isTextControl(el) && lastFilled.get(el) !== el.value) {
      lastFilled.set(el, el.value);
      emit('FILL', el, el.value, { sensitive: type === 'password' });
    }
    if ((e.key === 'Enter' && tag === 'input') || e.key === 'Escape') emit('PRESS', el, e.key);
  }, true);

  if (window === window.top) {
    try {
      const nav = performance.getEntriesByType('navigation')[0];
      if (nav && nav.type === 'reload') send({ action: 'RELOAD', value: '', frameSel: '' });
    } catch (e) {}
  }
})();
