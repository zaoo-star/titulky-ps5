---
lang: en
---
# What's new

What changes in ReCaption, version by version – the same list players see in the app next to the
**Update** button (only from their version up to the one on offer).

<div id="novinky-seznam">Loading…</div>

<script>
(function () {
  var jazyk = (document.documentElement.lang || 'cs').indexOf('en') === 0 ? 'en' : 'cs';
  var cil = document.getElementById('novinky-seznam');
  fetch('../novinky.json', { cache: 'no-cache' }).then(function (r) { return r.json(); }).then(function (data) {
    var z = (data.zaznamy || []).slice().sort(function (a, b) { return b.vc - a.vc; });
    if (!z.length) { cil.textContent = 'Nothing here yet.'; return; }
    var html = '';
    for (var i = 0; i < z.length; i++) {
      var body = (z[i][jazyk] || z[i].cs || []);
      html += '<h3>' + z[i].verze + '</h3><ul>';
      for (var j = 0; j < body.length; j++) html += '<li>' + body[j].replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</li>';
      html += '</ul>';
    }
    cil.innerHTML = html;
  }).catch(function () { cil.textContent = 'Couldn’t load the list.'; });
})();
</script>

---
*[Česká verze →](../novinky.html)*
