// ReCaption na Cloudflare Pages (recaption.pages.dev, od 1.00-rc2, přání majitele: v odkazech pro hráče nemá být GitHub).
// Stránky s pomocí (pomoc/, sestavené Jekyllem) jsou obyčejné soubory; tenhle program navíc podá soubory z vydání
// na GitHubu pod naší adresou, aby se adresa GitHubu hráči nikde neukázala:
//   /d/<kanál>/<soubor>  → github.com/adusa89/ReCaption/releases/download/<kanál>/<soubor>
//   /stahnout, /pc, /mac → krátké adresy na hlavní soubory
// Jen povolené kanály a názvy souborů (žádné jiné adresy přes nás). Bezplatný plán: 100 000 požadavků denně.
const REPO = "https://github.com/adusa89/ReCaption/releases/download/";
const KANALY = new Set(["latest", "zkusebni", "pc", "mac", "mac-zkusebni", "ios"]);
const SOUBOR = /^[A-Za-z0-9][A-Za-z0-9._-]{0,80}$/;
const KRATKE = {
  "/stahnout": "latest/ReCaption.apk",
  // ne /pc a /mac – na ty Cloudflare Pages zkracuje návody pc.html a mac.html (pak se místo návodu stahovalo)
  "/stahnout/pc": "pc/ReCaption-PC.zip",
  "/stahnout/mac": "mac/ReCaption-Mac.dmg",
};
// anonymní počítání stažení instalačních souborů (srovnání se skutečně aktivovanými zařízeními z pingu appky)
const INSTALACNI = new Set(["ReCaption.apk", "ReCaption-PC.zip", "ReCaption-Mac.dmg"]);
const POCITADLO_STAZENI = "https://recaption-hlaseni.recaption.workers.dev/v1/stazeni";

export default {
  async fetch(request, env, context) {
    const url = new URL(request.url);
    let cesta = KRATKE[url.pathname.replace(/\/+$/, "")];
    if (!cesta && url.pathname.startsWith("/d/")) cesta = url.pathname.slice(3);
    if (!cesta) return env.ASSETS.fetch(request);
    const [kanal, soubor, navic] = cesta.split("/");
    if (navic !== undefined || !KANALY.has(kanal) || !SOUBOR.test(soubor || "")) return new Response("Nenalezeno", { status: 404 });
    if (request.method !== "GET" && request.method !== "HEAD") return new Response("Jen GET", { status: 405 });
    // version.txt (kontrola aktualizací) krátce v mezipaměti Cloudflare; velké soubory se jen přepošlou
    const kratce = soubor.endsWith(".txt") || soubor.endsWith(".json");
    const odpoved = await fetch(REPO + kanal + "/" + soubor, {
      redirect: "follow",
      cf: kratce ? { cacheTtl: 60, cacheEverything: true } : { cacheTtl: 300, cacheEverything: true },
    });
    if (!odpoved.ok) return new Response("Nenalezeno", { status: odpoved.status === 404 ? 404 : 502 });
    const h = new Headers();
    for (const k of ["content-type", "content-length", "last-modified", "etag"]) {
      const v = odpoved.headers.get(k);
      if (v) h.set(k, v);
    }
    // stažení z webu (krátké adresy /stahnout…): v názvu souboru i číslo verze, např. ReCaption-1.1.apk (přání majitele)
    let jmeno = soubor;
    if (!kratce && KRATKE[url.pathname.replace(/\/+$/, "")] && soubor.endsWith(".apk")) {
      try {
        const v = await fetch(REPO + kanal + "/version.txt", { redirect: "follow", cf: { cacheTtl: 60, cacheEverything: true } });
        const nazev = v.ok ? ((await v.text()).trim().split(/\s+/)[1] || "") : "";
        if (/^[0-9][0-9A-Za-z.-]{0,20}$/.test(nazev)) jmeno = soubor.replace(/\.apk$/, "-" + nazev + ".apk");
      } catch (_) { /* bez čísla verze */ }
    }
    if (!kratce) h.set("content-disposition", 'attachment; filename="' + jmeno + '"');
    else h.set("cache-control", "no-cache");
    // počítadlo stažení jen při skutečném poslání instalačního souboru (GET, ne HEAD na ověření)
    if (request.method === "GET" && INSTALACNI.has(soubor)) {
      context.waitUntil(fetch(POCITADLO_STAZENI, { method: "POST" }).catch(() => {}));
    }
    return new Response(request.method === "HEAD" ? null : odpoved.body, { status: 200, headers: h });
  },
};
