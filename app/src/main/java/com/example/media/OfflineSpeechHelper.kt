package com.example.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

private const val TAG = "OfflineSpeechHelper"

class OfflineSpeechHelper(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun isOnDeviceSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    }

    fun startListening(
        languageCode: String,
        preferOffline: Boolean,
        onRmsChanged: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopAndDestroy()
        if (!isAvailable()) {
            onError("خدمة التعرف الصوتي المحلي غير متوفرة على هذا الجهاز.")
            return
        }

        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isOnDeviceSupported()) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = created

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                onRmsChanged(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "تعذر التقاط الصوت من الميكروفون."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح إذن الميكروفون أولاً."
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يُسمع كلام واضح. تحدث بوضوح وحاول مجدداً."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تعذر التعرف الصوتي عبر الشبكة. تأكد من تنزيل حزمة التعرف الصوتي أو استخدم الإدخال النصي."
                    else -> "تعذر التعرف الصوتي المحلي (رمز $error)."
                }
                Log.w(TAG, "SpeechRecognizer error=$error")
                stopAndDestroy()
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                stopAndDestroy()
                if (spoken.isBlank()) {
                    onError("لم يُرصد كلام واضح في التسجيل.")
                } else {
                    onResult(spoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (languageCode != "auto" && languageCode.isNotBlank()) {
                val bcpTag = when (languageCode.lowercase()) {
                    "ar" -> "ar-IQ"
                    "en" -> "en-US"
                    "fr" -> "fr-FR"
                    "es" -> "es-ES"
                    "de" -> "de-DE"
                    "tr" -> "tr-TR"
                    else -> languageCode
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcpTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcpTag)
            }
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        created.startListening(intent)
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun stopAndDestroy() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
