# Překlady šablony průvodce (core/Pruvodce.kt) do dalších jazyků aplikace.
#
#   python ci/pruvodce-preklady.py vypis <složka>       – zapíše <složka>/zdroj.json: [{"en","cs"}] všech textů z pomoc/pruvodce-data.js
#   python ci/pruvodce-preklady.py zapis <složka>       – z <složka>/<jazyk>.json ({"anglický text": "překlad"}) zapíše
#                                                         app/src/main/java/cz/recaption/core/PruvodcePreklady*.kt
#
# Klíčem je anglické znění: když se v šabloně změní, test PruvodceTest ukáže, který překlad chybí (a který je navíc).
# Ručně lze upravovat i přímo vygenerované PruvodcePreklady<Jazyk>.kt.
import json, os, re, sys

KOREN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
CORE = os.path.join(KOREN, 'app', 'src', 'main', 'java', 'cz', 'recaption', 'core')
JAZYKY = ['sk', 'pl', 'de', 'hu', 'uk', 'es', 'fr', 'it', 'hi']


def kt(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n') + '"'


def vypis(slozka):
    text = open(os.path.join(KOREN, 'pomoc', 'pruvodce-data.js'), encoding='utf-8').read()
    data = json.loads(text[text.index('{'):text.rindex('}') + 1])
    out = {}

    def projdi(x):
        if isinstance(x, list):
            for y in x: projdi(y)
        elif isinstance(x, dict):
            if x.get('t') == 'qr': return
            if isinstance(x.get('cs'), str) and x['cs'] != x.get('en'): out[x['en']] = x['cs']
            for k, v in x.items():
                if k != 'kdy': projdi(v)
    projdi(data)
    os.makedirs(slozka, exist_ok=True)
    json.dump([{'en': e, 'cs': c} for e, c in out.items()], open(os.path.join(slozka, 'zdroj.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(len(out), 'textů')


def zapis(slozka):
    for j in JAZYKY:
        m = json.load(open(os.path.join(slozka, j + '.json'), encoding='utf-8'))
        jm = 'PruvodcePreklady' + j.capitalize()
        radky = ['package cz.recaption.core', '', '// Překlad šablony průvodce (Pruvodce.kt) – jazyk ' + j + '. Klíč = anglické znění v šabloně.',
                 '// Vytvořeno: python ci/pruvodce-preklady.py zapis …; dál jde upravovat ručně.',
                 'internal object ' + jm + ' {', '    val MAPA: Map<String, String> = mapOf(']
        radky += ['        ' + kt(k) + ' to ' + kt(v) + ',' for k, v in m.items()]
        radky += ['    )', '}', '']
        open(os.path.join(CORE, jm + '.kt'), 'w', encoding='utf-8', newline='\n').write('\n'.join(radky))
    hl = ['package cz.recaption.core', '',
          '/** Překlady šablony průvodce ([Pruvodce]) do jazyků aplikace kromě češtiny a angličtiny; klíč = anglické znění. */',
          'object PruvodcePreklady {',
          '    /** Jazyk → (anglický text → překlad). Každý jazyk ve vlastním souboru (jinak by inicializace byla na JVM moc velká). */',
          '    val MAPY: Map<String, Map<String, String>> by lazy { mapOf(']
    hl += ['        ' + kt(j) + ' to PruvodcePreklady' + j.capitalize() + '.MAPA,' for j in JAZYKY]
    hl += ['    ) }', '', '    fun get(lang: String, en: String): String? = MAPY[lang]?.get(en)', '}', '']
    open(os.path.join(CORE, 'PruvodcePreklady.kt'), 'w', encoding='utf-8', newline='\n').write('\n'.join(hl))
    print('zapsáno', len(JAZYKY), 'jazyků')


if __name__ == '__main__':
    {'vypis': vypis, 'zapis': zapis}[sys.argv[1]](sys.argv[2])
