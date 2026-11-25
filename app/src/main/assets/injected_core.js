(function () {
  if (window.__privAI_loaded) return;
  window.__privAI_loaded = true;

  function buildSelector(el) {
    if (el.id) return '#' + CSS.escape(el.id);
    if (el.name) {
      const tag = el.tagName.toLowerCase();
      const byName = document.querySelectorAll(tag + '[name="' + CSS.escape(el.name) + '"]');
      if (byName.length === 1) return tag + '[name="' + el.name.replace(/"/g, '\"') + '"]';
    }
    const parts = [];
    let cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.body) {
      let seg = cur.tagName.toLowerCase();
      if (cur.id) {
        parts.unshift('#' + CSS.escape(cur.id));
        break;
      }
      let sib = cur, nth = 1;
      while ((sib = sib.previousElementSibling)) {
        if (sib.tagName === cur.tagName) nth++;
      }
      seg += ':nth-of-type(' + nth + ')';
      parts.unshift(seg);
      cur = cur.parentElement;
    }
    return parts.join(' > ');
  }

  function findLabel(el) {
    if (el.id) {
      const lbl = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
      if (lbl) return lbl.innerText.trim().slice(0, 80);
    }
    const parentLabel = el.closest('label');
    if (parentLabel) return parentLabel.innerText.trim().slice(0, 80);
    if (el.getAttribute('aria-label')) return el.getAttribute('aria-label').trim().slice(0, 80);
    return (el.placeholder || el.name || el.id || '').slice(0, 80);
  }

  window.__privAI_scanSchema = function () {
    const inputs = Array.from(document.querySelectorAll('input, select, textarea'))
      .filter(el => el.type !== 'hidden' && el.type !== 'password' && el.offsetParent !== null)
      .slice(0, 80)
      .map(el => ({
        selector: buildSelector(el),
        tag: el.tagName.toLowerCase(),
        type: el.type || 'text',
        name: el.name || '',
        id: el.id || '',
        label: findLabel(el),
        required: !!el.required,
        options: el.tagName === 'SELECT'
          ? Array.from(el.options).slice(0, 20).map(o => o.text.trim())
          : undefined
      }));

    const tables = Array.from(document.querySelectorAll('table')).slice(0, 10).map((t, idx) => {
      const headers = Array.from(t.querySelectorAll('th')).map(th => th.innerText.trim().slice(0, 40));
      return {
        tableIndex: idx,
        selector: buildSelector(t),
        headers: headers,
        rowCount: t.querySelectorAll('tr').length
      };
    });

    return {
      urlOrigin: window.location.origin,
      urlPath: window.location.pathname,
      title: document.title.slice(0, 100),
      fields: inputs,
      tables: tables
    };
  };

  window.__privAI_fillForm = function (mapping, vault) {
    let filled = 0;
    const details = [];
    for (const [selector, vaultKey] of Object.entries(mapping || {})) {
      if (!(vaultKey in vault)) continue;
      const val = vault[vaultKey];
      if (val === undefined || val === null || val === '') continue;
      const el = document.querySelector(selector);
      if (!el) continue;

      const proto = el.tagName === 'TEXTAREA'
        ? HTMLTextAreaElement.prototype
        : el.tagName === 'SELECT'
          ? HTMLSelectElement.prototype
          : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
      if (setter) setter.call(el, String(val));
      else el.value = String(val);

      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      el.dispatchEvent(new Event('blur', { bubbles: true }));
      filled++;
      details.push({ selector, vaultKey });
    }
    return { filled, details };
  };

  window.__privAI_extract = function (config) {
    const tableSel = (config && config.tableSelector) || 'table';
    const tables = Array.from(document.querySelectorAll(tableSel));
    if (tables.length > 0) {
      const t = tables[0];
      const headers = Array.from(t.querySelectorAll('thead th, tr:first-child th, tr:first-child td'))
        .map((c, i) => c.innerText.trim() || ('col_' + (i + 1)));
      const rows = Array.from(t.querySelectorAll('tbody tr, tr')).slice(headers.length ? 1 : 0);
      return rows.map(tr => {
        const cells = Array.from(tr.querySelectorAll('td, th')).map(td => td.innerText.trim());
        const obj = {};
        cells.forEach((v, idx) => { obj[headers[idx] || ('col_' + (idx + 1))] = v; });
        return obj;
      }).filter(r => Object.keys(r).length > 0);
    }
    if (config && config.rowSelector && config.fields) {
      return Array.from(document.querySelectorAll(config.rowSelector)).map(row => {
        const item = {};
        for (const [k, sel] of Object.entries(config.fields)) {
          const target = row.querySelector(sel);
          item[k] = target ? target.innerText.trim() : '';
        }
        return item;
      });
    }
    return [];
  };
})();
