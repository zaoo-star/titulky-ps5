// Zkouška na GitHubu: předstírá aplikaci ReCaption v TV LG. Připojí se k ReCaptionu v simulátoru iPhonu
// (simulátor sdílí síť s Macem, proto 127.0.0.1), čeká na „vitej“, ohlásí se jako TV a čeká na zkušební větu.
// Spuštění: node ios/zkouska/tv.mjs [adresa]   (Node 22 má WebSocket vestavěný)

const adresa = process.argv[2] || 'ws://127.0.0.1:8787';
const limit = Date.now() + 90_000;

function pokus() {
  return new Promise((hotovo) => {
    let vitej = false;
    const ws = new WebSocket(adresa);
    const konec = (vysledek) => { try { ws.close(); } catch (e) { /* nic */ } hotovo(vysledek); };
    const casovac = setTimeout(() => konec(vitej ? 'bez vety' : 'nic'), 20_000);
    ws.onerror = () => { clearTimeout(casovac); konec('nic'); };
    ws.onmessage = (u) => {
      let z;
      try { z = JSON.parse(u.data); } catch (e) { console.log('neplatná zpráva:', u.data); return; }
      console.log('od iPhonu:', JSON.stringify(z));
      if (z.typ === 'vitej') {
        vitej = true;
        ws.send(JSON.stringify({ typ: 'pripojeno', televize: 'Zkouška LG', aplikace: 'ReCaption', platforma: 'webos', verze: '1.5.38', hdmi: 1 }));
      } else if (z.typ === 'ukaz' && typeof z.text === 'string' && z.text.length > 0) {
        clearTimeout(casovac);
        konec('ok');
      }
    };
  });
}

let vysledek = 'nic';
while (Date.now() < limit) {
  vysledek = await pokus();
  if (vysledek === 'ok') break;
  if (vysledek === 'bez vety') break;
  await new Promise((r) => setTimeout(r, 2000));
}
console.log('VYSLEDEK', vysledek);
process.exit(vysledek === 'ok' ? 0 : 1);
