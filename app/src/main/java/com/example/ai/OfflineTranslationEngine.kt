package com.example.ai

import android.util.Log
import com.example.data.LanguageOption
import com.example.data.SupportedLanguages
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OfflineTranslation"

data class OfflineTranslationResult(
    val detectedLanguageCode: String,
    val detectedLanguageNameArabic: String,
    val translatedText: String
)

class OfflineTranslationEngine {

    private val modelManager: RemoteModelManager by lazy {
        RemoteModelManager.getInstance()
    }

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    fun isLanguageSupported(code: String): Boolean {
        return mapToMlKitCode(code) != null
    }

    fun mapToMlKitCode(code: String): String? {
        return when (code.lowercase()) {
            "ar" -> TranslateLanguage.ARABIC
            "en" -> TranslateLanguage.ENGLISH
            "fr" -> TranslateLanguage.FRENCH
            "es" -> TranslateLanguage.SPANISH
            "de" -> TranslateLanguage.GERMAN
            "tr" -> TranslateLanguage.TURKISH
            "ja" -> TranslateLanguage.JAPANESE
            "ko" -> TranslateLanguage.KOREAN
            "zh" -> TranslateLanguage.CHINESE
            "hi" -> TranslateLanguage.HINDI
            "it" -> TranslateLanguage.ITALIAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "ru" -> TranslateLanguage.RUSSIAN
            else -> TranslateLanguage.fromLanguageTag(code.lowercase())
        }
    }

    suspend fun getDownloadedLanguageCodes(): Set<String> = suspendCancellableCoroutine { cont ->
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val codes = models.map { it.language }.toSet()
                if (cont.isActive) cont.resume(codes)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed listing downloaded offline models", e)
                if (cont.isActive) cont.resume(emptySet())
            }
    }

    suspend fun downloadLanguagePack(code: String): Result<Unit> {
        val mlKitCode = mapToMlKitCode(code)
            ?: return Result.failure(IllegalArgumentException("هذه اللغة غير مدعومة في الحزم المحلية: $code"))
        return runCatching {
            downloadModelSuspend(mlKitCode)
        }
    }

    suspend fun deleteLanguagePack(code: String): Result<Unit> {
        val mlKitCode = mapToMlKitCode(code)
            ?: return Result.failure(IllegalArgumentException("لغة غير مدعومة: $code"))
        return suspendCancellableCoroutine { cont ->
            val model = TranslateRemoteModel.Builder(mlKitCode).build()
            modelManager.deleteDownloadedModel(model)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
        }
    }

    suspend fun detectLanguageCode(text: String): String = suspendCancellableCoroutine { cont ->
        if (text.isBlank()) {
            if (cont.isActive) cont.resume("en")
            return@suspendCancellableCoroutine
        }
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { langTag ->
                val normalized = if (langTag == "und" || langTag.isBlank()) {
                    guessLanguageHeuristic(text)
                } else {
                    langTag.substringBefore('-').lowercase()
                }
                if (cont.isActive) cont.resume(normalized)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(guessLanguageHeuristic(text))
            }
    }

    suspend fun translateText(
        text: String,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption
    ): OfflineTranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("أدخل نصاً للترجمة أولاً.")
        }

        val resolvedSourceCode = if (sourceLanguage.code == SupportedLanguages.autoDetect.code) {
            detectLanguageCode(trimmed)
        } else {
            sourceLanguage.code
        }

        val resolvedSourceOption = SupportedLanguages.findByCode(resolvedSourceCode)

        if (resolvedSourceCode.equals(targetLanguage.code, ignoreCase = true)) {
            return OfflineTranslationResult(
                detectedLanguageCode = resolvedSourceCode,
                detectedLanguageNameArabic = resolvedSourceOption.arabicName,
                translatedText = trimmed
            )
        }

        val mlSource = mapToMlKitCode(resolvedSourceCode)
            ?: throw IllegalStateException("لغة المصدر (${resolvedSourceOption.arabicName}) غير مدعومة في الوضع المحلي.")
        val mlTarget = mapToMlKitCode(targetLanguage.code)
            ?: throw IllegalStateException("لغة الهدف (${targetLanguage.arabicName}) غير مدعومة في الوضع المحلي.")

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(mlSource)
                .setTargetLanguage(mlTarget)
                .build()
        )
        return try {
            ensureTranslatorReady(translator, resolvedSourceOption, targetLanguage)
            val translated = translateSuspend(translator, trimmed)
            OfflineTranslationResult(
                detectedLanguageCode = resolvedSourceCode,
                detectedLanguageNameArabic = resolvedSourceOption.arabicName,
                translatedText = translated
            )
        } finally {
            runCatching { translator.close() }
        }
    }

    suspend fun translateBatch(
        lines: List<String>,
        sourceCode: String,
        targetCode: String
    ): List<String> {
        if (lines.isEmpty()) return emptyList()
        val effectiveSource = if (sourceCode == SupportedLanguages.autoDetect.code || sourceCode.isBlank()) {
            val sample = lines.take(5).joinToString(" ")
            detectLanguageCode(sample)
        } else {
            sourceCode
        }

        if (effectiveSource.equals(targetCode, ignoreCase = true)) {
            return lines
        }

        val mlSource = mapToMlKitCode(effectiveSource)
            ?: throw IllegalStateException("اللغة ($effectiveSource) غير مدعومة أوفلاين.")
        val mlTarget = mapToMlKitCode(targetCode)
            ?: throw IllegalStateException("اللغة ($targetCode) غير مدعومة أوفلاين.")

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(mlSource)
                .setTargetLanguage(mlTarget)
                .build()
        )
        return try {
            ensureTranslatorReady(
                translator,
                SupportedLanguages.findByCode(effectiveSource),
                SupportedLanguages.findByCode(targetCode)
            )
            lines.map { line ->
                if (line.isBlank()) "" else translateSuspend(translator, line)
            }
        } finally {
            runCatching { translator.close() }
        }
    }

    private suspend fun downloadModelSuspend(mlKitCode: String): Unit =
        suspendCancellableCoroutine { cont ->
            val model = TranslateRemoteModel.Builder(mlKitCode).build()
            val conditions = DownloadConditions.Builder().build()
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    if (cont.isActive) cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "تعذر تنزيل حزمة اللغة المحلية. تأكد من الاتصال بالإنترنت لمرة واحدة لتنزيل الحزمة.",
                                e
                            )
                        )
                    }
                }
        }

    private suspend fun ensureTranslatorReady(
        translator: com.google.mlkit.nl.translate.Translator,
        sourceOpt: LanguageOption,
        targetOpt: LanguageOption
    ): Unit = suspendCancellableCoroutine { cont ->
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                if (cont.isActive) cont.resume(Unit)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException(
                            "تحتاج ترجمة (${sourceOpt.arabicName} ← ${targetOpt.arabicName}) أوفلاين لتنزيل حزمة اللغة لمرة واحدة من مركز اللغات بالإنترنت.",
                            e
                        )
                    )
                }
            }
    }

    private suspend fun translateSuspend(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String = suspendCancellableCoroutine { cont ->
        translator.translate(text)
            .addOnSuccessListener { output ->
                if (cont.isActive) cont.resume(output)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    private fun guessLanguageHeuristic(text: String): String {
        return if (text.any { it in '\u0600'..'\u06FF' }) "ar" else "en"
    }
}
