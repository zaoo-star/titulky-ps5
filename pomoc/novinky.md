# Novinky

Co se v ReCaptionu mění, verzi po verzi – stejný seznam vidí hráč v appce vedle tlačítka
**Aktualizovat** (jen od jeho verze po tu nabízenou).

<div id="novinky-seznam">Načítám…</div>

<script>
(function () {
  var jazyk = (document.documentElement.lang || 'cs').indexOf('en') === 0 ? 'en' : 'cs';
  var cil = document.getElementById('novinky-seznam');
  fetch('novinky.json', { cache: 'no-cache' }).then(function (r) { return r.json(); }).then(function (data) {
    var z = (data.zaznamy || []).slice().sort(function (a, b) { return b.vc - a.vc; });
    if (!z.length) { cil.textContent = jazyk === 'en' ? 'Nothing here yet.' : 'Zatím tu nic není.'; return; }
    var html = '';
    for (var i = 0; i < z.length; i++) {
      var body = (z[i][jazyk] || z[i].cs || []);
      html += '<h3>' + z[i].verze + '</h3><ul>';
      for (var j = 0; j < body.length; j++) html += '<li>' + body[j].replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</li>';
      html += '</ul>';
    }
    cil.innerHTML = html;
  }).catch(function () { cil.textContent = jazyk === 'en' ? 'Couldn’t load the list.' : 'Seznam se nepodařilo načíst.'; });
})();
</script>

---
*[English version →](en/novinky.html)*
