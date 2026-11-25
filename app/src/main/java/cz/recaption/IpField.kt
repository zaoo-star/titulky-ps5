package cz.recaption

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText

/**
 * Políčko pro adresu v domácí síti (192.168.1.20). Číselná klávesnice má v češtině místo tečky
 * čárku, proto se otevře klávesnice pro adresy (tečka bývá hned vedle mezery) a čárka nebo mezera
 * napsaná mezi číslice se sama změní na tečku; ostatní znaky kromě číslic se smažou.
 *
 * Opravuje se až hotový text po jednotlivých znacích (Editable.replace/delete), ne filtrem při psaní –
 * filtr by klávesnici rozbil rozepsané slovo a ta by pak část textu vložila znovu. Mezery na začátku
 * a konci vloženého textu (adresa zkopírovaná z chatu) se smažou, ne změní na tečku.
 */
object IpField {
    fun setup(e: EditText) {
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        e.addTextChangedListener(object : TextWatcher {
            private var busy = false
            private var pasted = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { pasted = count > 1 }
            override fun afterTextChanged(s: Editable) {
                if (busy) return
                busy = true
                try { clean(s, pasted) } finally { busy = false }
            }
        })
    }

    /** Upraví text na tvar adresy; [trimEnds] = smazat mezery a čárky na krajích (vložený text). */
    fun clean(s: Editable, trimEnds: Boolean) {
        var i = s.length - 1
        while (i >= 0) {
            val c = s[i]
            when {
                c in '0'..'9' || c == '.' -> {}
                c == ',' || c == ' ' || c == ' ' -> {
                    val edge = i == 0 || (trimEnds && s.substring(i + 1).isBlank())
                    if (edge) s.delete(i, i + 1) else s.replace(i, i + 1, ".")
                }
                else -> s.delete(i, i + 1)
            }
            i--
        }
    }
}
