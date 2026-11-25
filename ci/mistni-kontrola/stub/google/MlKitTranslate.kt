@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.mlkit.nl.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions

object TranslateLanguage { const val ENGLISH = "en" }
interface Translator {
    fun downloadModelIfNeeded(c: DownloadConditions): Task<Void>
    fun translate(s: String): Task<String>
    fun close()
}
object Translation { fun getClient(o: TranslatorOptions): Translator = TODO() }
class TranslatorOptions {
    class Builder {
        fun setSourceLanguage(s: String): Builder = this
        fun setTargetLanguage(s: String): Builder = this
        fun build() = TranslatorOptions()
    }
}
