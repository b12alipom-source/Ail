package com.example.ai

import android.util.Log
import com.example.data.GeneratedSegment
import com.example.data.LanguageOption
import com.example.data.SupportedLanguages
import com.example.data.TranslationStyle
import com.example.data.VideoTranslationResult
import com.example.data.VoiceTranslationResult
import com.example.data.db.SubtitleCueEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "GeminiVideoTranslator"
private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
private const val FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 60_000L

class GeminiVideoTranslator {

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguageCode" to Schema.string(
                description = "ISO 639-1 two-letter language code of the spoken audio, e.g. en, ar, fr, es"
            ),
            "detectedLanguageNameArabic" to Schema.string(
                description = "Arabic name of the detected spoken language, e.g. الإنجليزية or العربية"
            ),
            "summary" to Schema.string(
                description = "Concise 1-2 sentence summary of the spoken content written in the target language"
            ),
            "segments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Segment start time in milliseconds relative to this clip"),
                        "endMs" to Schema.integer(description = "Segment end time in milliseconds relative to this clip"),
                        "originalText" to Schema.string(description = "Accurate verbatim transcription in original spoken language"),
                        "translatedText" to Schema.string(description = "Natural, context-aware translation into the target language")
                    )
                ),
                description = "Chronological subtitle segments covering all speech in this audio clip"
            )
        )
    )

    private val retranslationSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(
                description = "Concise 1-2 sentence summary written in the target language"
            ),
            "translations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "orderIndex" to Schema.integer(description = "Original orderIndex from input"),
                        "translatedText" to Schema.string(description = "Refined, natural subtitle text in the target language")
                    )
                ),
                description = "Translated lines matching every input orderIndex"
            )
        )
    )

    private val voiceSchema = Schema.obj(
        properties = mapOf(
            "detectedLanguageCode" to Schema.string(
                description = "ISO 639-1 two-letter code of the spoken audio"
            ),
            "detectedLanguageNameArabic" to Schema.string(
                description = "Arabic name of the detected language, e.g. الإنجليزية"
            ),
            "originalText" to Schema.string(
                description = "Accurate verbatim transcription of what was spoken"
            ),
            "translatedText" to Schema.string(
                description = "Natural fluent translation in the requested target language"
            )
        )
    )

    private val transcriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    private val transcriptionFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    private val retranslationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = retranslationSchema
                temperature = 0.2f
            }
        )
    }

    private val retranslationFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = retranslationSchema
                temperature = 0.2f
            }
        )
    }

    private val voiceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceSchema
                temperature = 0.2f
            }
        )
    }

    private val voiceFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun transcribeAndTranslateChunk(
        mediaBytes: ByteArray,
        mimeType: String,
        chunkDurationMs: Long,
        startOffsetMs: Long,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        translationStyle: TranslationStyle = TranslationStyle.BALANCED,
        previousContext: String = "",
        allowEmptySegments: Boolean = false
    ): VideoTranslationResult {
        val sourceHint = if (sourceLanguage.code == SupportedLanguages.autoDetect.code) {
            "Auto-detect the primary spoken language accurately."
        } else {
            "The spoken language is ${sourceLanguage.nativeName} (${sourceLanguage.code})."
        }

        val durationHint = if (chunkDurationMs > 0L) {
            "This audio clip starts at 0 ms and has duration $chunkDurationMs ms. Keep all startMs and endMs within 0..$chunkDurationMs."
        } else {
            "Provide accurate millisecond timestamps starting from 0."
        }

        val contextBlock = if (previousContext.isNotBlank()) {
            "\n9. Continuity context from the immediately preceding clip (preserve terminology, pronouns, names, and tone seamlessly):\n$previousContext"
        } else {
            ""
        }

        val prompt = """
            You are an expert broadcast subtitle engineer, linguist, and literary translator.
            Listen carefully to the provided audio/video stream and produce synchronized, high-precision subtitles.
            Translation Style: ${translationStyle.promptGuidance}
            Rules:
            1. $sourceHint
            2. Transcribe every spoken phrase verbatim in its original script inside `originalText`.
            3. Translate each segment thoughtfully into ${targetLanguage.nativeName} (ISO: ${targetLanguage.code}, Arabic label: ${targetLanguage.arabicName}) inside `translatedText`, respecting context, idioms, and natural grammar rather than stiff word-for-word translation.
            4. Split speech into clean, readable subtitle cues (typically 1.5 to 4.5 seconds each, concise 1-2 lines per cue).
            5. $durationHint Ensure startMs < endMs and segments are ordered chronologically without overlapping.
            6. Write `summary` as a clear 1-2 sentence overview in ${targetLanguage.nativeName}.
            7. Write `detectedLanguageNameArabic` in Arabic (e.g. الإنجليزية, العربية, الفرنسية).
            8. If there is no speech in this clip, return an empty `segments` array.$contextBlock
        """.trimIndent()

        try {
            val response = generateWithFallback(
                primaryModel = transcriptionModel,
                fallbackModel = transcriptionFallbackModel
            ) { model ->
                model.generateContent(
                    content {
                        inlineData(mediaBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("لم يُرجع نموذج الذكاء الاصطناعي أي بيانات.")
            return parseTranslationJson(rawJson, chunkDurationMs, startOffsetMs, allowEmptySegments)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Transcription timed out", e)
            throw IllegalStateException("استغرقت معالجة هذا الجزء وقتاً طويلاً. جرّب اختيار مقطع أقصر أو تأكد من استقرار الشبكة.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI transcription failed", e)
            throw IllegalStateException(
                formatUserFacingError(e, "تعذرت معالجة الصوت والترجمة عبر الذكاء الاصطناعي.")
            )
        }
    }

    suspend fun translateVoiceClip(
        audioBytes: ByteArray,
        mimeType: String,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        translationStyle: TranslationStyle = TranslationStyle.BALANCED,
        conversationContext: String = ""
    ): VoiceTranslationResult {
        val sourceHint = if (sourceLanguage.code == SupportedLanguages.autoDetect.code) {
            "Auto-detect the spoken language."
        } else {
            "The speaker is speaking ${sourceLanguage.nativeName} (${sourceLanguage.code})."
        }

        val contextNote = if (conversationContext.isNotBlank()) {
            "\n5. Recent dialogue context for pronoun and topic continuity:\n$conversationContext"
        } else {
            ""
        }

        val prompt = """
            You are an expert simultaneous voice interpreter.
            Translation Style: ${translationStyle.promptGuidance}
            Listen to the recorded voice message and:
            1. $sourceHint
            2. Transcribe the exact spoken words in `originalText`.
            3. Translate them idiomatically and naturally into ${targetLanguage.nativeName} (${targetLanguage.code}) in `translatedText`, avoiding literal machine translation.
            4. Set `detectedLanguageNameArabic` in Arabic (e.g. العربية, الإنجليزية, التركية).$contextNote
        """.trimIndent()

        try {
            val response = generateWithFallback(
                primaryModel = voiceModel,
                fallbackModel = voiceFallbackModel
            ) { model ->
                model.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: throw IllegalStateException("لم يتم استلام استجابة من المترجم الصوتي.")
            val json = JSONObject(rawJson)
            val detectedCode = json.optString("detectedLanguageCode", sourceLanguage.code)
                .ifBlank { "auto" }
            val detectedAr = json.optString("detectedLanguageNameArabic", "")
                .ifBlank { SupportedLanguages.findByCode(detectedCode).arabicName }
            val originalText = json.optString("originalText", "").trim()
            val translatedText = json.optString("translatedText", "").trim()

            if (originalText.isEmpty() && translatedText.isEmpty()) {
                throw IllegalStateException("لم يُسمع كلام واضح في التسجيل. حاول التحدث بالقرب من الميكروفون.")
            }

            return VoiceTranslationResult(
                detectedLanguageCode = detectedCode,
                detectedLanguageNameArabic = detectedAr,
                originalText = originalText,
                translatedText = translatedText.ifEmpty { originalText }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice translation timed out", e)
            throw IllegalStateException("انتهت مهلة الترجمة الصوتية. حاول مرة أخرى.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice translation failed", e)
            throw IllegalStateException(
                formatUserFacingError(e, "تعذرت ترجمة التسجيل الصوتي.")
            )
        }
    }

    suspend fun retranslateCues(
        cues: List<SubtitleCueEntity>,
        sourceLanguageName: String,
        newTargetLanguage: LanguageOption,
        translationStyle: TranslationStyle = TranslationStyle.BALANCED
    ): Pair<String, Map<Long, String>> {
        val payloadArray = JSONArray()
        cues.forEach { cue ->
            payloadArray.put(
                JSONObject().apply {
                    put("orderIndex", cue.orderIndex)
                    put("originalText", cue.originalText)
                }
            )
        }

        val prompt = """
            You are an expert literary and subtitle translator.
            Translation Style: ${translationStyle.promptGuidance}
            Translate the following connected subtitle segments from $sourceLanguageName into ${newTargetLanguage.nativeName} (ISO: ${newTargetLanguage.code}).
            Use the full sequence context so pronouns, terminology, and sentence flow stay coherent across consecutive `orderIndex` entries.
            Also provide a clear 1-2 sentence `summary` in ${newTargetLanguage.nativeName}.
            Input segments JSON:
            $payloadArray
        """.trimIndent()

        return runBatchTranslationPrompt(prompt, cues)
    }

    suspend fun polishCuesWithContext(
        cues: List<SubtitleCueEntity>,
        sourceLanguageName: String,
        targetLanguage: LanguageOption,
        translationStyle: TranslationStyle
    ): Pair<String, Map<Long, String>> {
        val payloadArray = JSONArray()
        cues.forEach { cue ->
            payloadArray.put(
                JSONObject().apply {
                    put("orderIndex", cue.orderIndex)
                    put("originalText", cue.originalText)
                    put("currentTranslation", cue.translatedText)
                }
            )
        }

        val prompt = """
            You are a senior subtitle editor and bilingual literary stylist.
            Review the ENTIRE transcript below in $sourceLanguageName alongside its draft translation in ${targetLanguage.nativeName} (ISO: ${targetLanguage.code}).
            Style target: ${translationStyle.promptGuidance}
            Task:
            1. Polish every `translatedText` by reading surrounding lines so split sentences, idioms, technical terms, and pronouns flow naturally and cohesively.
            2. Fix any awkward literal phrasing while keeping each cue concise and timed to its `orderIndex`.
            3. Write an insightful, concise 1-2 sentence `summary` in ${targetLanguage.nativeName}.
            Input segments JSON:
            $payloadArray
        """.trimIndent()

        return runBatchTranslationPrompt(prompt, cues)
    }

    private suspend fun runBatchTranslationPrompt(
        prompt: String,
        cues: List<SubtitleCueEntity>
    ): Pair<String, Map<Long, String>> {
        try {
            val response = generateWithFallback(
                primaryModel = retranslationModel,
                fallbackModel = retranslationFallbackModel
            ) { model ->
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: throw IllegalStateException("لم يُرجع النموذج ترجمة محدثة.")
            val json = JSONObject(rawJson)
            val summary = json.optString("summary", "")
            val translationsArray = json.getJSONArray("translations")

            val cueByOrder = cues.associateBy { it.orderIndex }
            val resultMap = mutableMapOf<Long, String>()
            for (i in 0 until translationsArray.length()) {
                val item = translationsArray.getJSONObject(i)
                val orderIndex = item.getInt("orderIndex")
                val translated = item.getString("translatedText").trim()
                val targetCue = cueByOrder[orderIndex]
                if (targetCue != null && translated.isNotEmpty()) {
                    resultMap[targetCue.id] = translated
                }
            }
            return summary to resultMap
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Batch translation timed out", e)
            throw IllegalStateException("انتهت مهلة معالجة النص الذكي. حاول مرة أخرى.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Batch translation failed", e)
            throw IllegalStateException(
                formatUserFacingError(e, "تعذرت إعادة صياغة الترجمة.")
            )
        }
    }

    private suspend fun generateWithFallback(
        primaryModel: GenerativeModel,
        fallbackModel: GenerativeModel,
        call: suspend (GenerativeModel) -> GenerateContentResponse
    ): GenerateContentResponse {
        try {
            return withTimeout(AI_TIMEOUT_MS) { call(primaryModel) }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w(TAG, "Primary model $PRIMARY_MODEL_NAME timed out, switching to fast fallback $FALLBACK_MODEL_NAME")
            return withTimeout(AI_TIMEOUT_MS) { call(fallbackModel) }
        } catch (e: Exception) {
            if (!isTransientCapacityError(e)) throw e
            Log.w(TAG, "Primary model $PRIMARY_MODEL_NAME under high demand, falling back to $FALLBACK_MODEL_NAME", e)
            delay(750L)
            try {
                return withTimeout(AI_TIMEOUT_MS) { call(fallbackModel) }
            } catch (fallbackErr: CancellationException) {
                throw fallbackErr
            } catch (fallbackErr: Exception) {
                if (!isTransientCapacityError(fallbackErr)) throw fallbackErr
                Log.w(TAG, "Fallback model also busy, retrying once after brief delay", fallbackErr)
                delay(1500L)
                return withTimeout(AI_TIMEOUT_MS) { call(fallbackModel) }
            }
        }
    }

    private fun isTransientCapacityError(e: Throwable): Boolean {
        val msg = (e.message ?: e.localizedMessage ?: "").lowercase()
        return msg.contains("high demand") ||
            msg.contains("overloaded") ||
            msg.contains("temporarily unavailable") ||
            msg.contains("service unavailable") ||
            msg.contains("try again later") ||
            msg.contains("resource exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("503") ||
            msg.contains("429")
    }

    private fun formatUserFacingError(e: Throwable, defaultMessage: String): String {
        val msg = (e.message ?: e.localizedMessage ?: "").trim()
        val lower = msg.lowercase()
        return when {
            isTransientCapacityError(e) ->
                "خادم الذكاء الاصطناعي مزدحم مؤقتاً بسبب ضغط الطلبات. انتظر ثوانٍ ثم حاول مجدداً."
            e is IOException || lower.contains("unable to resolve host") || lower.contains("network") ->
                "تعذر الاتصال بالإنترنت. تحقق من الشبكة وحاول مرة أخرى."
            msg.isNotBlank() -> msg
            else -> defaultMessage
        }
    }

    private fun parseTranslationJson(
        rawJson: String,
        chunkDurationMs: Long,
        startOffsetMs: Long,
        allowEmptySegments: Boolean
    ): VideoTranslationResult {
        val json = JSONObject(rawJson)
        val detectedCode = json.optString("detectedLanguageCode", "en").ifBlank { "en" }
        val detectedNameAr = json.optString("detectedLanguageNameArabic", "")
            .ifBlank { SupportedLanguages.findByCode(detectedCode).arabicName }
        val summary = json.optString("summary", "")
        val segmentsJson = json.optJSONArray("segments") ?: JSONArray()

        val parsedSegments = mutableListOf<GeneratedSegment>()
        for (i in 0 until segmentsJson.length()) {
            val item = segmentsJson.getJSONObject(i)
            val original = item.optString("originalText", "").trim()
            val translated = item.optString("translatedText", "").trim()
            if (original.isEmpty() && translated.isEmpty()) continue

            var localStart = item.optLong("startMs", 0L).coerceAtLeast(0L)
            var localEnd = item.optLong("endMs", localStart + 2000L)
            if (chunkDurationMs > 0L) {
                localStart = localStart.coerceAtMost((chunkDurationMs - 400L).coerceAtLeast(0L))
                localEnd = localEnd.coerceIn(localStart + 300L, chunkDurationMs)
            } else if (localEnd <= localStart) {
                localEnd = localStart + 1500L
            }

            parsedSegments.add(
                GeneratedSegment(
                    startMs = startOffsetMs + localStart,
                    endMs = startOffsetMs + localEnd,
                    originalText = original,
                    translatedText = translated.ifEmpty { original }
                )
            )
        }

        if (parsedSegments.isEmpty() && !allowEmptySegments) {
            throw IllegalStateException("لم يتم رصد أي كلام منطوق واضح في هذا المقطع.")
        }

        return VideoTranslationResult(
            detectedLanguageCode = detectedCode,
            detectedLanguageNameArabic = detectedNameAr,
            summary = summary,
            segments = parsedSegments.sortedBy { it.startMs }
        )
    }
}
