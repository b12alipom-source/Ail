package com.example.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TAG = "VoiceSpeaker"

class VoiceSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (!isReady) {
                Log.w(TAG, "TextToSpeech init failed with status $status")
            }
        }
    }

    fun speak(text: String, languageCode: String) {
        val engine = tts ?: return
        if (!isReady || text.isBlank()) return

        val locale = Locale.forLanguageTag(languageCode)
        val availability = engine.isLanguageAvailable(locale)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bayan_tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
    }
}
