(function () {
  try {
    const cap = 100;
    const absolute = value => { try { return new URL(value, location.href).href; } catch (_) { return value; } };
    const scriptSources = [...document.scripts].filter(s => s.src).map(s => s.src);
    const links = [...new Set([...document.querySelectorAll('a[href]')].map(a => a.href))];
    const assets = [...document.querySelectorAll('script[src],img[src],link[href],iframe[src]')].map(e => absolute(e.getAttribute('src') || e.getAttribute('href')));
    const forms = [...document.forms].slice(0, cap).map(f => ({
      action: f.action || location.href, method: f.method.toUpperCase(),
      fields: [...f.elements].slice(0, cap).map(e => ({name: e.name, type: e.type})),
      passwordFieldWithGet: f.method.toLowerCase() === 'get' && !!f.querySelector('input[type=password]')
    }));
    return JSON.stringify({
      note: 'Top-level DOM snapshot only. URLs can contain sensitive query parameters. Field values, cookies and storage values are not collected. Findings need manual review.',
      page: {title: document.title, url: location.href, secureContext: window.isSecureContext},
      counts: {links: links.length, externalScripts: scriptSources.length, forms: document.forms.length, iframes: document.querySelectorAll('iframe').length},
      metaCsp: [...document.querySelectorAll('meta[http-equiv]')].filter(e => e.httpEquiv.toLowerCase() === 'content-security-policy').map(e => e.content),
      externalScriptHosts: [...new Set(scriptSources.map(s => new URL(s).host))].filter(h => h !== location.host).slice(0, cap),
      scripts: scriptSources.slice(0, cap), forms,
      httpAssetReferencesOnHttps: location.protocol === 'https:' ? assets.filter(u => u.startsWith('http:')).slice(0, cap) : [],
      links: links.slice(0, cap),
      limits: 'At most 100 entries per list; dynamic requests and inaccessible frames are not covered.'
    }, null, 2);
  } catch (error) { return 'Page map failed: ' + error.message; }
})();
