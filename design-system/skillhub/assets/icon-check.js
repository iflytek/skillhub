/* Offline icon self-check.  Loaded by every preview page so a missing mask
   never fails silently: bad icons are outlined red, tagged with
   data-icon-error, and reported once on the console.

   Checks:
   - .sh-i  -> mask resolves, has a non-zero box, and the data-URI payload
     still contains a valid black paint + at least one shape.  This catches
     the double-encoded `%2523` colour regression, which leaves every icon
     blank while `mask-image` still reports a value.
   - use    -> external sprite reference has a non-zero box (HTTP only).
   - .icon / .icon-12…24 -> legacy wrapper must contain a glyph or inline SVG
     with a non-zero box. */
(function () {
  var bad = [];
  var nodes = document.querySelectorAll(
    '.sh-i, use, .icon, .icon-12, .icon-14, .icon-16, .icon-18, .icon-20, .icon-24'
  );

  function fail(el) {
    if (bad.indexOf(el) < 0) bad.push(el);
  }

  function maskValue(el) {
    return (getComputedStyle(el).getPropertyValue('--sh-icon-mask') || '').trim();
  }

  function maskUrl(value) {
    var m = value.match(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)/);
    return m ? (m[1] || m[2] || m[3] || '').trim() : '';
  }

  /* Decode the data URI and make sure the SVG still paints. */
  function maskSane(url) {
    if (!/^data:image\/svg\+xml,/.test(url)) return false;
    var svg;
    try {
      svg = decodeURIComponent(url.slice(url.indexOf(',') + 1));
    } catch (e) {
      return false;
    }
    if (svg.indexOf('<svg') < 0 || svg.indexOf('</svg>') < 0) return false;
    if (/%25/i.test(svg)) return false;
    if (!/stroke=['"]#000['"]|fill=['"]#000['"]/.test(svg)) return false;
    if (!/<(path|circle|rect|line|polyline|polygon|ellipse)\b/.test(svg)) return false;
    return true;
  }

  nodes.forEach(function (el) {
    var tag = el.tagName.toLowerCase();
    /* Hidden by design (theme toggle shows only sun or moon, collapsed panels,
       tabs) is not a failure: it has no client rect. */
    if (!el.getClientRects().length) return;
    if (tag === 'use') {
      try {
        var b = el.getBBox();
        if (!(b && (b.width || b.height))) fail(el);
      } catch (e) {
        fail(el);
      }
    } else if (el.classList.contains('sh-i')) {
      var cs = getComputedStyle(el);
      var mask = cs.maskImage || cs.webkitMaskImage || 'none';
      var r = el.getBoundingClientRect();
      var url = maskUrl(maskValue(el));
      if (mask === 'none' || !r.width || !r.height || !maskSane(url)) fail(el);
    } else if (tag === 'svg') {
      try {
        var sb = el.getBBox();
        if (!(sb && (sb.width || sb.height))) fail(el);
      } catch (e) {
        fail(el);
      }
    } else {
      /* Legacy .icon span: it must wrap a glyph or text, otherwise it renders
         an empty box (the phase1 copy-button bug). */
      if (!el.childElementCount && !el.textContent.trim()) fail(el);
    }
  });

  bad.forEach(function (el) {
    el.style.outline = '2px solid #dc2626';
    el.setAttribute('data-icon-error', 'not-rendered');
  });
  if (bad.length) {
    console.error('[icon-check] ' + bad.length + ' icon(s) not rendered');
  }
  window.__iconCheckFailures = bad.length;
  window.__iconCheckDone = true;
})();
