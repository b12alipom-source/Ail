package com.example.study

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.ai.AppCheckTokenOutcome
import com.example.ai.isAppCheckRateLimited
import com.example.ai.isThrottleDetail
import com.example.ai.markAppCheckRateLimited
import com.example.ai.verifyOrRefreshAppCheckToken
import com.example.data.GeneratedScheduleTask
import com.example.data.GeneratedStudyPlanResult
import com.example.data.MindMapBranch
import com.example.data.StudyAiTier
import com.example.data.StudyAttachmentType
import com.example.data.StudyDossierData
import com.example.data.StudyDossierSection
import com.example.data.StudyEngineConfig
import com.example.data.StudyGlossaryTerm
import com.example.data.StudyMethod
import com.example.data.StudyVisualStyle
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min

private const val TAG = "StudyAiAssistant"
private const val MAX_INLINE_FILE_BYTES = 15 * 1024 * 1024 // 15 MB per inline attachment
private const val TOTAL_INLINE_BUDGET_BYTES = 18 * 1024 * 1024 // Safely under 20 MB Gemini limit
private const val AI_TIMEOUT_MS = 120_000L

class StudyAiException(
    override val message: String,
    val isAppCheckOrAuthError: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

data class GeneratedVisualResult(
    val bitmap: Bitmap?,
    val rawBytes: ByteArray?,
    val title: String,
    val promptSummary: String,
    val engineLabel: String
)

data class TutorStreamResult(
    val fullText: String,
    val engineLabel: String,
    val isOffline: Boolean
)

@Serializable
private data class StudyPlanPayload(
    val overview: String = "",
    val summaryMarkdown: String = "",
    val sections: List<StudyDossierSection> = emptyList(),
    val glossary: List<StudyGlossaryTerm> = emptyList(),
    val examTips: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val mindMapRootTitle: String = "",
    val mindMapBranches: List<MindMapBranch> = emptyList(),
    val schedule: List<StudyScheduleJsonItem> = emptyList()
)

@Serializable
private data class StudyScheduleJsonItem(
    val dayNumber: Int = 1,
    val dayLabel: String = "",
    val timeSlot: String = "",
    val taskTitle: String = "",
    val taskDetail: String = "",
    val durationMinutes: Int = 30
)

private val studyJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

class StudyAiAssistant(
    private val restClient: PersonalGeminiRestClient = PersonalGeminiRestClient()
) {

    private val studyPlanSchema = Schema.obj(
        properties = mapOf(
            "overview" to Schema.string(
                description = "Comprehensive 3-5 sentence executive Arabic overview of the whole subject or attached lessons."
            ),
            "sections" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "heading" to Schema.string(description = "Clear Arabic section title"),
                        "explanation" to Schema.string(description = "Detailed, easy-to-grasp Arabic explanation tailored to the chosen study method"),
                        "keyFormulaOrRule" to Schema.string(description = "Important law, rule, formula, or core principle if applicable, else empty string"),
                        "example" to Schema.string(description = "Concrete solved example or real-world illustration in Arabic")
                    )
                ),
                description = "4 to 8 deep, structured Arabic study sections covering all attached content thoroughly"
            ),
            "glossary" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "term" to Schema.string(description = "Scientific or technical term (Arabic + original English term if helpful)"),
                        "definition" to Schema.string(description = "Concise, accurate Arabic definition")
                    )
                ),
                description = "5 to 12 essential terminology flashcards"
            ),
            "examTips" to Schema.array(
                items = Schema.string(description = "High-yield exam warning, common student mistake, or memory trick in Arabic"),
                description = "4 to 7 practical exam tips"
            ),
            "keyPoints" to Schema.array(
                items = Schema.string(description = "High-yield bullet takeaway in Arabic"),
                description = "6 to 12 essential revision bullet points"
            ),
            "mindMapRootTitle" to Schema.string(
                description = "Short central Arabic topic title for the mind map root"
            ),
            "mindMapBranches" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Main branch title in Arabic"),
                        "colorTag" to Schema.string(description = "One of: sapphire, emerald, amber, violet"),
                        "subtopics" to Schema.array(
                            items = Schema.string(description = "Concise child node text in Arabic"),
                            description = "2 to 5 child nodes for this branch"
                        )
                    )
                ),
                description = "4 to 6 structured mind map branches showing how topics connect"
            ),
            "schedule" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "dayNumber" to Schema.integer(description = "Day index starting from 1"),
                        "dayLabel" to Schema.string(description = "Arabic label such as اليوم 1"),
                        "timeSlot" to Schema.string(description = "Session block e.g. الجلسة الصباحية or الجلسة المسائية"),
                        "taskTitle" to Schema.string(description = "Focused study task title in Arabic"),
                        "taskDetail" to Schema.string(description = "Concrete study instructions based on the attached content"),
                        "durationMinutes" to Schema.integer(description = "Estimated minutes for this session")
                    )
                ),
                description = "Custom day-by-day study schedule matching totalDays and dailyMinutes"
            )
        )
    )

    // Lazy Firebase AI Logic flagship models (no -lite models)
    private val flashDossierModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = StudyAiTier.BALANCED.firebaseModel,
            generationConfig = generationConfig {
                temperature = 0.25f
                responseMimeType = "application/json"
                responseSchema = studyPlanSchema
            }
        )
    }

    private val argonDossierModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = StudyAiTier.DEEP_REASONING.firebaseModel,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = studyPlanSchema
            }
        )
    }

    private val flashChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = StudyAiTier.BALANCED.firebaseModel,
            generationConfig = generationConfig { temperature = 0.35f }
        )
    }

    private val argonChatModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = StudyAiTier.DEEP_REASONING.firebaseModel,
            generationConfig = generationConfig { temperature = 0.3f }
        )
    }

    private val proVisualModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3-pro-image",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val nanoBananaVisualModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun verifyPersonalKey(apiKey: String): Result<String> =
        restClient.verifyApiKey(apiKey)

    suspend fun generateStudyPlan(
        config: StudyEngineConfig,
        notebookTitle: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int,
        attachments: List<StudyAttachmentEntity>,
        explicitOfflineOnly: Boolean
    ): GeneratedStudyPlanResult {
        if (explicitOfflineOnly) {
            val attachmentTexts = attachments.map { it.displayName to it.extractedText }
            return buildOfflineStudyPlan(notebookTitle, method, dailyMinutes, totalDays, attachmentTexts)
        }

        val prompt = buildDossierPrompt(notebookTitle, method, dailyMinutes, totalDays)

        return try {
            withTimeout(AI_TIMEOUT_MS) {
                if (config.isUsingPersonalKey) {
                    runPersonalStudyPlan(config, prompt, attachments, dailyMinutes, totalDays)
                } else {
                    try {
                        ensureFirebaseAppCheckReady()
                        val inputContent = buildFirebaseMultimodalContent(prompt, attachments)
                        val models = when (config.tier) {
                            StudyAiTier.DEEP_REASONING -> listOf(
                                StudyAiTier.DEEP_REASONING.firebaseModel to argonDossierModel,
                                StudyAiTier.DEEP_REASONING.firebaseFallbackModel to flashDossierModel
                            )
                            StudyAiTier.BALANCED -> listOf(
                                StudyAiTier.BALANCED.firebaseModel to flashDossierModel,
                                StudyAiTier.DEEP_REASONING.firebaseModel to argonDossierModel
                            )
                        }
                        val (raw, usedModel) = executeFirebaseUnaryWithFallback(models, inputContent)
                        parseStudyPlanJson(
                            rawJson = raw,
                            fallbackDailyMinutes = dailyMinutes,
                            fallbackTotalDays = totalDays,
                            engineLabel = "سحابي • $usedModel"
                        )
                    } catch (e: StudyAiException) {
                        // If Firebase App Check fails on a sideloaded APK but the user already has a key saved, retry automatically
                        if (e.isAppCheckOrAuthError && config.hasPersonalKey) {
                            Log.i(TAG, "Firebase App Check unavailable; automatically using saved Personal Gemini Key.")
                            runPersonalStudyPlan(config, prompt, attachments, dailyMinutes, totalDays)
                        } else {
                            throw e
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                throw StudyAiException("انتهت مهلة التحليل العميق (120 ثانية). جرّب تقليل حجم المرفقات أو التحويل لخيار Flash السريع.", cause = e)
            }
            throw e
        } catch (e: StudyAiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Study dossier generation failed", e)
            throw classifyAiException(e, config.isUsingPersonalKey)
        }
    }

    private suspend fun runPersonalStudyPlan(
        config: StudyEngineConfig,
        prompt: String,
        attachments: List<StudyAttachmentEntity>,
        dailyMinutes: Int,
        totalDays: Int
    ): GeneratedStudyPlanResult {
        val jsonSchemaInstruction = """
            $prompt

            أعد الرد بصيغة JSON صالحة ومباشرة فقط (بدون أي كلام قبلها أو بعدها وبدون markdown) وتحتوي بدقة على الحقول التالية:
            {
              "overview": "نظرة عامة أكاديمية شاملة للمادة من 4-6 جمل بالعربية",
              "sections": [
                {
                  "heading": "عنوان المحور",
                  "explanation": "شرح تفصيلي وعميق سهل الفهم يغطي جميع المفاهيم والآليات والرسوم في المرفقات",
                  "keyFormulaOrRule": "القانون أو القاعدة العلمية أو المعادلة الجوهرية (أو نص فارغ إن لم يوجد)",
                  "example": "مثال تطبيقي محلول خطوة بخطوة أو حالة عملية من المادة"
                }
              ],
              "glossary": [{"term": "المصطلح العربي (والأجنبي)", "definition": "التعريف الدقيق"}],
              "examTips": ["تنبيه أو سر امتحاني مهم"],
              "keyPoints": ["نقطة مراجعة مركّزة"],
              "mindMapRootTitle": "العنوان المركزي للخريطة الذهنية",
              "mindMapBranches": [{"title": "الفرع الرئيسي", "colorTag": "sapphire|emerald|amber|violet", "subtopics": ["فكرة فرعية 1", "فكرة فرعية 2"]}],
              "schedule": [{"dayNumber": 1, "dayLabel": "اليوم 1", "timeSlot": "الجلسة الصباحية", "taskTitle": "عنوان المهمة", "taskDetail": "تفاصيل المذاكرة المحددة", "durationMinutes": 30}]
            }
        """.trimIndent()

        val turns = listOf(
            RestContentTurn(
                role = "user",
                parts = buildRestParts(
                    instructionPrompt = jsonSchemaInstruction,
                    attachments = attachments
                )
            )
        )
        val thinking = if (config.tier == StudyAiTier.DEEP_REASONING) "medium" else "low"
        val (raw, usedModel) = restClient.generateStructuredJson(
            apiKey = config.personalApiKey,
            modelCandidates = config.tier.personalModelChain(),
            turns = turns,
            systemInstruction = "أنت بروفيسور أكاديمي عربي ومصمم مناهج خبير في تطبيق بيان. مهمتك إنتاج تحليل علمي شامل وعميق بدون اختصار مخل.",
            thinkingLevel = thinking,
            maxOutputTokens = 16384
        )
        return parseStudyPlanJson(
            rawJson = raw,
            fallbackDailyMinutes = dailyMinutes,
            fallbackTotalDays = totalDays,
            engineLabel = "مفتاح شخصي • $usedModel"
        )
    }

    suspend fun generateStudyVisual(
        config: StudyEngineConfig,
        notebookTitle: String,
        style: StudyVisualStyle,
        dossier: StudyDossierData?,
        fallbackSummary: String,
        attachments: List<StudyAttachmentEntity>
    ): GeneratedVisualResult {
        val visualPrompt = buildVisualPosterPrompt(
            notebookTitle = notebookTitle,
            style = style,
            dossier = dossier,
            fallbackSummary = fallbackSummary,
            attachments = attachments
        )
        val preferPro = config.tier == StudyAiTier.DEEP_REASONING

        return try {
            withTimeout(AI_TIMEOUT_MS) {
                if (config.isUsingPersonalKey) {
                    runPersonalVisual(config.personalApiKey, notebookTitle, style, visualPrompt, attachments, preferPro)
                } else {
                    try {
                        ensureFirebaseAppCheckReady()
                        val firstImageBytes = attachments.firstOrNull {
                            StudyAttachmentType.fromKey(it.attachmentType) == StudyAttachmentType.IMAGE
                        }?.let { readFileBytesBounded(it.localFilePath, 6 * 1024 * 1024) }

                        val visualContent = content {
                            text(visualPrompt)
                            if (firstImageBytes != null) {
                                inlineData(firstImageBytes, "image/jpeg")
                            }
                        }

                        val candidates = if (preferPro) {
                            listOf(
                                "gemini-3-pro-image" to proVisualModel,
                                "gemini-nano-banana-2.1" to nanoBananaVisualModel
                            )
                        } else {
                            listOf(
                                "gemini-nano-banana-2.1" to nanoBananaVisualModel,
                                "gemini-3-pro-image" to proVisualModel
                            )
                        }
                        var lastErr: Throwable? = null
                        for ((name, model) in candidates) {
                            try {
                                val response = model.generateContent(visualContent)
                                val imagePart = response.candidates.firstOrNull()
                                    ?.content?.parts?.filterIsInstance<ImagePart>()
                                    ?.firstOrNull()
                                val bmp = imagePart?.image
                                if (bmp != null) {
                                    return@withTimeout GeneratedVisualResult(
                                        bitmap = bmp,
                                        rawBytes = null,
                                        title = "${style.arabicLabel} — $notebookTitle",
                                        promptSummary = visualPrompt.take(280),
                                        engineLabel = "سحابي • $name"
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Image generation attempt failed on $name", e)
                                lastErr = e
                                val classified = classifyAiException(e, isPersonalKey = false)
                                if (classified.isAppCheckOrAuthError) throw classified
                            }
                        }
                        throw (lastErr ?: IllegalStateException("لم يُرجع نموذج الصور أي صورة هذه المرة."))
                    } catch (e: StudyAiException) {
                        if (e.isAppCheckOrAuthError && config.hasPersonalKey) {
                            runPersonalVisual(config.personalApiKey, notebookTitle, style, visualPrompt, attachments, preferPro)
                        } else {
                            throw e
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                throw StudyAiException("استغرق توليد الإنفوجرافيك أكثر من 120 ثانية. يرجى المحاولة مجدداً.", cause = e)
            }
            throw e
        } catch (e: StudyAiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Visual study generation failed", e)
            throw classifyAiException(e, config.isUsingPersonalKey)
        }
    }

    private suspend fun runPersonalVisual(
        apiKey: String,
        notebookTitle: String,
        style: StudyVisualStyle,
        visualPrompt: String,
        attachments: List<StudyAttachmentEntity>,
        preferPro: Boolean
    ): GeneratedVisualResult {
        val parts = buildList {
            add(RestPart.Text(visualPrompt))
            attachments.firstOrNull {
                StudyAttachmentType.fromKey(it.attachmentType) == StudyAttachmentType.IMAGE
            }?.let { imgAtt ->
                readFileBytesBounded(imgAtt.localFilePath, 6 * 1024 * 1024)?.let { bytes ->
                    add(RestPart.InlineData(imgAtt.mimeType, bytes))
                }
            }
        }
        val (imgBytes, usedModel) = restClient.generateImagePngBytes(
            apiKey = apiKey,
            turns = listOf(RestContentTurn(role = "user", parts = parts)),
            preferProModel = preferPro
        )
        return GeneratedVisualResult(
            bitmap = null,
            rawBytes = imgBytes,
            title = "${style.arabicLabel} — $notebookTitle",
            promptSummary = visualPrompt.take(280),
            engineLabel = "مفتاح شخصي • $usedModel"
        )
    }

    suspend fun streamStudyTutorReply(
        config: StudyEngineConfig,
        notebookTitle: String,
        method: StudyMethod,
        summary: String,
        dossierJson: String,
        attachments: List<StudyAttachmentEntity>,
        history: List<StudyMessageEntity>,
        userQuestion: String,
        inlineAttachment: IngestedAttachment?,
        explicitOfflineOnly: Boolean,
        onChunk: suspend (String) -> Unit
    ): TutorStreamResult {
        if (explicitOfflineOnly) {
            val attachmentTexts = attachments.map { it.displayName to it.extractedText }
            val offlineAns = answerStudyQuestionOffline(userQuestion, summary, attachmentTexts)
            onChunk(offlineAns)
            return TutorStreamResult(
                fullText = offlineAns,
                engineLabel = "وضع أوفلاين محلي",
                isOffline = true
            )
        }

        val systemInstruction = buildTutorSystemInstruction(
            notebookTitle = notebookTitle,
            method = method,
            summary = summary,
            dossierJson = dossierJson
        )

        return try {
            withTimeout(AI_TIMEOUT_MS) {
                if (config.isUsingPersonalKey) {
                    runPersonalTutorStream(
                        config = config,
                        systemInstruction = systemInstruction,
                        attachments = attachments,
                        history = history,
                        userQuestion = userQuestion,
                        inlineAttachment = inlineAttachment,
                        onChunk = onChunk
                    )
                } else {
                    try {
                        ensureFirebaseAppCheckReady()
                        val fullPrompt = buildString {
                            appendLine(systemInstruction)
                            appendHistoryBlock(history)
                            if (inlineAttachment != null) {
                                appendLine("\nأرفق الطالب الآن ملف/صورة جديدة مع هذا السؤال اسمها: ${inlineAttachment.displayName}")
                            }
                            appendLine("\nسؤال الطالب الحالي:\n$userQuestion")
                        }
                        val inputContent = buildFirebaseMultimodalContent(
                            instructionPrompt = fullPrompt,
                            attachments = attachments,
                            extraAttachment = inlineAttachment
                        )
                        val models = when (config.tier) {
                            StudyAiTier.DEEP_REASONING -> listOf(
                                StudyAiTier.DEEP_REASONING.firebaseModel to argonChatModel,
                                StudyAiTier.DEEP_REASONING.firebaseFallbackModel to flashChatModel
                            )
                            StudyAiTier.BALANCED -> listOf(
                                StudyAiTier.BALANCED.firebaseModel to flashChatModel,
                                StudyAiTier.DEEP_REASONING.firebaseModel to argonChatModel
                            )
                        }
                        val (fullText, usedModel) = executeFirebaseStreamWithFallback(models, inputContent, onChunk)
                        TutorStreamResult(
                            fullText = fullText,
                            engineLabel = "سحابي • $usedModel",
                            isOffline = false
                        )
                    } catch (e: StudyAiException) {
                        if (e.isAppCheckOrAuthError && config.hasPersonalKey) {
                            runPersonalTutorStream(
                                config = config,
                                systemInstruction = systemInstruction,
                                attachments = attachments,
                                history = history,
                                userQuestion = userQuestion,
                                inlineAttachment = inlineAttachment,
                                onChunk = onChunk
                            )
                        } else {
                            throw e
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                throw StudyAiException("استغرق رد المعلم وقتاً أطول من 120 ثانية. حاول صياغة سؤال أقصر أو أعد المحاولة.", cause = e)
            }
            throw e
        } catch (e: StudyAiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tutor stream failed", e)
            throw classifyAiException(e, config.isUsingPersonalKey)
        }
    }

    private suspend fun runPersonalTutorStream(
        config: StudyEngineConfig,
        systemInstruction: String,
        attachments: List<StudyAttachmentEntity>,
        history: List<StudyMessageEntity>,
        userQuestion: String,
        inlineAttachment: IngestedAttachment?,
        onChunk: suspend (String) -> Unit
    ): TutorStreamResult {
        val turns = buildList {
            // 1. Seed attached course materials as initial user context turn if attachments exist
            if (attachments.isNotEmpty()) {
                add(
                    RestContentTurn(
                        role = "user",
                        parts = buildRestParts(
                            instructionPrompt = "هذه هي مرفقات ومصادر المادة الدراسية المعتمدة في نقاشنا:",
                            attachments = attachments
                        )
                    )
                )
                add(
                    RestContentTurn(
                        role = "model",
                        parts = listOf(RestPart.Text("تم استلام مرفقات المادة ودراستها بالكامل. تفضل بطرح سؤالك أو إرسال صورة المسألة."))
                    )
                )
            }
            // 2. Real multi-turn conversation history
            history.takeLast(10).forEach { msg ->
                val msgText = buildString {
                    if (msg.attachmentName.isNotBlank()) {
                        append("[أرفق: ${msg.attachmentName}] ")
                    }
                    append(msg.messageText)
                }.trim()
                if (msgText.isNotEmpty()) {
                    add(
                        RestContentTurn(
                            role = if (msg.isUser) "user" else "model",
                            parts = listOf(RestPart.Text(msgText))
                        )
                    )
                }
            }
            // 3. Current question + optional inline image/PDF
            val currentPrompt = buildString {
                if (inlineAttachment != null) {
                    appendLine("أرفق الطالب مع هذا السؤال: ${inlineAttachment.displayName}")
                }
                append(userQuestion)
            }
            add(
                RestContentTurn(
                    role = "user",
                    parts = buildRestParts(
                        instructionPrompt = currentPrompt,
                        attachments = emptyList(),
                        extraAttachment = inlineAttachment
                    )
                )
            )
        }

        val accumulated = StringBuilder()
        val thinking = if (config.tier == StudyAiTier.DEEP_REASONING) "medium" else "low"
        val (full, usedModel) = restClient.streamText(
            apiKey = config.personalApiKey,
            modelCandidates = config.tier.personalModelChain(),
            turns = turns,
            systemInstruction = systemInstruction,
            thinkingLevel = thinking,
            maxOutputTokens = 8192,
            onChunk = { delta ->
                accumulated.append(delta)
                onChunk(accumulated.toString())
            }
        )
        return TutorStreamResult(
            fullText = full,
            engineLabel = "مفتاح شخصي • $usedModel",
            isOffline = false
        )
    }

    private suspend fun ensureFirebaseAppCheckReady() {
        if (isAppCheckRateLimited()) {
            throw StudyAiException(
                message = "خدمة App Check في فترة تهدئة مؤقتة لكثرة المحاولات. انتظر قليلاً أو أدخل مفتاح Gemini شخصي في «إعدادات المحرك الذكي».",
                isAppCheckOrAuthError = true
            )
        }
        when (val outcome = verifyOrRefreshAppCheckToken(forceRefresh = false)) {
            is AppCheckTokenOutcome.Valid -> Unit
            is AppCheckTokenOutcome.Failed -> {
                if (outcome.isRateLimited || isThrottleDetail(outcome.detail)) {
                    markAppCheckRateLimited(outcome.detail)
                }
                throw StudyAiException(
                    message = "تعذر التحقق الأمني من خادم Firebase (${outcome.detail}). إذا كان التطبيق مثبتاً بصيغة APK جانبية، أضف مفتاح Gemini شخصي في الإعدادات ليشتغل فوراً بأقصى قوة.",
                    isAppCheckOrAuthError = true
                )
            }
        }
    }

    private suspend fun executeFirebaseUnaryWithFallback(
        models: List<Pair<String, GenerativeModel>>,
        inputContent: Content
    ): Pair<String, String> {
        var lastErr: Throwable? = null
        for ((name, model) in models) {
            try {
                val out = model.generateContent(inputContent).text.orEmpty()
                if (out.isNotBlank()) return out to name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
                val classified = classifyAiException(e, isPersonalKey = false)
                if (classified.isAppCheckOrAuthError) throw classified
            }
        }
        throw lastErr ?: IllegalStateException("أرجع الخادم رداً فارغاً.")
    }

    private suspend fun executeFirebaseStreamWithFallback(
        models: List<Pair<String, GenerativeModel>>,
        inputContent: Content,
        onChunk: suspend (String) -> Unit
    ): Pair<String, String> {
        var lastErr: Throwable? = null
        for ((name, model) in models) {
            var emitted = false
            try {
                val sb = StringBuilder()
                model.generateContentStream(inputContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        emitted = true
                        sb.append(piece)
                        onChunk(sb.toString())
                    }
                }
                val text = sb.toString().trim()
                if (text.isNotBlank()) return text to name
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
                val classified = classifyAiException(e, isPersonalKey = false)
                if (emitted || classified.isAppCheckOrAuthError) throw classified
            }
        }
        throw lastErr ?: IllegalStateException("تعذر استلام بث الإجابة من المعلم الذكي.")
    }

    private fun buildDossierPrompt(
        notebookTitle: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int
    ): String = buildString {
        appendLine("أنت معلم أكاديمي خبير ومصمم مناهج ذكية داخل تطبيق «بيان».")
        appendLine("المطلوب: إعداد ملف دراسة شامل وعميق للمادة «$notebookTitle» اعتماداً على كافة المرفقات المرسلة (ملفات PDF، صور، ملاحظات، نصوص فيديوهات).")
        appendLine("أسلوب الدراسة المطلوب: ${method.arabicLabel} — ${method.promptInstruction}")
        appendLine("متطلبات الجودة (استخدم كامل قدرتك العلمية بدون اختصار):")
        appendLine("1. نظرة عامة مركّزة ومفهومة (overview).")
        appendLine("2. تقسيم المادة إلى محاور تفصيلية متعمقة (sections) مع شرح وافٍ، وإبراز القانون أو القاعدة الجوهرية (keyFormulaOrRule) ومثال تطبيقي محلول خطوة بخطوة (example).")
        appendLine("3. قاموس لأهم المصطلحات العلمية (glossary) بالعربية ومعها المصطلح الأجنبي عند الحاجة.")
        appendLine("4. تنبيهات وأسرار الامتحان والأخطاء الشائعة التي يقع فيها الطلاب (examTips).")
        appendLine("5. شجرة خريطة ذهنية هرمية دقيقة (mindMapRootTitle + mindMapBranches) توضح ترابط المفاهيم بدقة.")
        appendLine("6. جدول دراسي عملي لـ $totalDays أيام بمعدل $dailyMinutes دقيقة يومياً مقسم إلى جلستين واضحتين لكل يوم.")
        appendLine("اجعل الشرح بالعربية الفصحى السلسة، غنياً بالمعلومات والأمثلة، ومرتبطاً حرفياً بمحتوى المرفقات.")
    }

    private fun buildVisualPosterPrompt(
        notebookTitle: String,
        style: StudyVisualStyle,
        dossier: StudyDossierData?,
        fallbackSummary: String,
        attachments: List<StudyAttachmentEntity>
    ): String {
        val coreSummary = buildString {
            if (dossier != null) {
                if (dossier.overview.isNotBlank()) appendLine("Topic overview: ${dossier.overview.take(450)}")
                if (dossier.sections.isNotEmpty()) {
                    appendLine("Key pillars:")
                    dossier.sections.take(5).forEachIndexed { idx, s ->
                        appendLine("${idx + 1}. ${s.heading}: ${s.keyFormulaOrRule.ifBlank { s.explanation.take(120) }}")
                    }
                }
                if (dossier.mindMapBranches.isNotEmpty()) {
                    appendLine("Branches: " + dossier.mindMapBranches.take(5).joinToString("; ") { b ->
                        "${b.title} (${b.subtopics.take(3).joinToString(", ")})"
                    })
                }
            } else if (fallbackSummary.isNotBlank()) {
                appendLine(fallbackSummary.take(700))
            } else {
                appendLine("Attachments: " + attachments.joinToString(", ") { it.displayName })
            }
        }

        return buildString {
            appendLine("Create a high-resolution, publication-grade educational study poster infographic for subject: \"$notebookTitle\".")
            appendLine("Visual Format Style: ${style.arabicLabel} (${style.artDirection})")
            appendLine("Key concepts and educational structure to illustrate:")
            appendLine(coreSummary.trim())
            appendLine("Design rules: Clean scholarly layout with distinct visual cards, clear scientific illustrations/icons, directional flow arrows, key formulas, and crisp readable labels (Arabic / scientific symbols where clear). High visual hierarchy, zero clutter.")
        }
    }

    private fun buildTutorSystemInstruction(
        notebookTitle: String,
        method: StudyMethod,
        summary: String,
        dossierJson: String
    ): String = buildString {
        appendLine("أنت «معلم بيان الذكي» — أستاذ أكاديمي متمكن ومتعمق في شرح ومناقشة مادة «$notebookTitle».")
        appendLine("أسلوب الطالب المفضل: ${method.arabicLabel} (${method.promptInstruction}).")
        appendLine("قواعد الإجابة الصارمة:")
        appendLine("- قدّم إجابات علمية دقيقة ومتعمقة ومنسقة بعناوين واضحة وخطوات مرتبة — لا تقتصر على إجابات سطحية.")
        appendLine("- إذا أرفق الطالب صورة مسألة أو صفحة كتاب أو ملف PDF، اقرأ كل تفاصيله بدقة، واستخرج المعطيات، واشرح الحل خطوة بخطوة بوضوح تام.")
        appendLine("- اربط إجابتك بمرفقات المادة، واختم بتحقق سريع للفهم أو مثال تطبيقي إضافي عند الحاجة.")

        val digest = dossierJson.ifBlank { summary }.take(4500)
        if (digest.isNotBlank()) {
            appendLine("\nمرجع ملخص المادة الحالي:\n$digest")
        }
    }

    private fun StringBuilder.appendHistoryBlock(history: List<StudyMessageEntity>) {
        val recentHistory = history.takeLast(8)
        if (recentHistory.isNotEmpty()) {
            appendLine("\nسياق المحادثة السابقة:")
            recentHistory.forEach { msg ->
                val role = if (msg.isUser) "الطالب" else "المعلم"
                val attHint = if (msg.attachmentName.isNotBlank()) " [أرفق: ${msg.attachmentName}]" else ""
                appendLine("$role$attHint: ${msg.messageText}")
            }
        }
    }

    private fun buildFirebaseMultimodalContent(
        instructionPrompt: String,
        attachments: List<StudyAttachmentEntity>,
        extraAttachment: IngestedAttachment? = null
    ): Content = content {
        text(instructionPrompt)
        var attachedBytesBudget = 0

        if (extraAttachment != null) {
            val extraBytes = if (extraAttachment.type == StudyAttachmentType.PDF || extraAttachment.type == StudyAttachmentType.IMAGE) {
                readFileBytesBounded(extraAttachment.localFilePath, MAX_INLINE_FILE_BYTES)
            } else null

            if (extraBytes != null) {
                attachedBytesBudget += extraBytes.size
                text("\n--- مرفق السؤال المباشر (${extraAttachment.type.arabicLabel}): ${extraAttachment.displayName} ---")
                inlineData(extraBytes, extraAttachment.mimeType)
            } else {
                if (extraAttachment.type == StudyAttachmentType.PDF) {
                    val pageImages = renderPdfFallbackImages(extraAttachment.localFilePath, maxPages = 6)
                    if (pageImages.isNotEmpty()) {
                        text("\n--- صفحات مصورة من PDF (${extraAttachment.displayName}) ---")
                        pageImages.forEach { imgBytes ->
                            if (attachedBytesBudget + imgBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                                attachedBytesBudget += imgBytes.size
                                inlineData(imgBytes, "image/jpeg")
                            }
                        }
                    }
                }
                if (extraAttachment.extractedText.isNotBlank()) {
                    text("\n--- نص مرفق السؤال: ${extraAttachment.displayName} ---\n${extraAttachment.extractedText.take(12_000)}")
                }
            }
        }

        attachments.forEach { att ->
            val type = StudyAttachmentType.fromKey(att.attachmentType)
            val fileBytes = if ((type == StudyAttachmentType.PDF || type == StudyAttachmentType.IMAGE) &&
                attachedBytesBudget < TOTAL_INLINE_BUDGET_BYTES
            ) {
                readFileBytesBounded(att.localFilePath, MAX_INLINE_FILE_BYTES)
            } else null

            if (fileBytes != null && attachedBytesBudget + fileBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                attachedBytesBudget += fileBytes.size
                text("\n--- مرفق المادة (${type.arabicLabel}): ${att.displayName} ---")
                inlineData(fileBytes, att.mimeType)
            } else {
                if (type == StudyAttachmentType.PDF && attachedBytesBudget < TOTAL_INLINE_BUDGET_BYTES) {
                    val pageImages = renderPdfFallbackImages(att.localFilePath, maxPages = 8)
                    if (pageImages.isNotEmpty()) {
                        text("\n--- صفحات مصورة من PDF الكبير (${att.displayName}) ---")
                        pageImages.forEach { imgBytes ->
                            if (attachedBytesBudget + imgBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                                attachedBytesBudget += imgBytes.size
                                inlineData(imgBytes, "image/jpeg")
                            }
                        }
                    }
                }
                if (att.extractedText.isNotBlank()) {
                    text("\n--- محتوى المادة (${type.arabicLabel}): ${att.displayName} ---\n${att.extractedText.take(15_000)}")
                }
            }
        }
    }

    private fun buildRestParts(
        instructionPrompt: String,
        attachments: List<StudyAttachmentEntity>,
        extraAttachment: IngestedAttachment? = null
    ): List<RestPart> = buildList {
        add(RestPart.Text(instructionPrompt))
        var attachedBytesBudget = 0

        if (extraAttachment != null) {
            val extraBytes = if (extraAttachment.type == StudyAttachmentType.PDF || extraAttachment.type == StudyAttachmentType.IMAGE) {
                readFileBytesBounded(extraAttachment.localFilePath, MAX_INLINE_FILE_BYTES)
            } else null
            if (extraBytes != null) {
                attachedBytesBudget += extraBytes.size
                add(RestPart.Text("\n--- مرفق السؤال المباشر: ${extraAttachment.displayName} ---"))
                add(RestPart.InlineData(extraAttachment.mimeType, extraBytes))
            } else {
                if (extraAttachment.type == StudyAttachmentType.PDF) {
                    val pageImages = renderPdfFallbackImages(extraAttachment.localFilePath, maxPages = 6)
                    if (pageImages.isNotEmpty()) {
                        add(RestPart.Text("\n--- صفحات مصورة من PDF (${extraAttachment.displayName}) ---"))
                        pageImages.forEach { imgBytes ->
                            if (attachedBytesBudget + imgBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                                attachedBytesBudget += imgBytes.size
                                add(RestPart.InlineData("image/jpeg", imgBytes))
                            }
                        }
                    }
                }
                if (extraAttachment.extractedText.isNotBlank()) {
                    add(RestPart.Text("\n--- نص مرفق السؤال: ${extraAttachment.displayName} ---\n${extraAttachment.extractedText.take(12_000)}"))
                }
            }
        }

        attachments.forEach { att ->
            val type = StudyAttachmentType.fromKey(att.attachmentType)
            val fileBytes = if ((type == StudyAttachmentType.PDF || type == StudyAttachmentType.IMAGE) &&
                attachedBytesBudget < TOTAL_INLINE_BUDGET_BYTES
            ) {
                readFileBytesBounded(att.localFilePath, MAX_INLINE_FILE_BYTES)
            } else null
            if (fileBytes != null && attachedBytesBudget + fileBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                attachedBytesBudget += fileBytes.size
                add(RestPart.Text("\n--- مرفق المادة (${type.arabicLabel}): ${att.displayName} ---"))
                add(RestPart.InlineData(att.mimeType, fileBytes))
            } else {
                if (type == StudyAttachmentType.PDF && attachedBytesBudget < TOTAL_INLINE_BUDGET_BYTES) {
                    val pageImages = renderPdfFallbackImages(att.localFilePath, maxPages = 8)
                    if (pageImages.isNotEmpty()) {
                        add(RestPart.Text("\n--- صفحات مصورة من PDF الكبير (${att.displayName}) ---"))
                        pageImages.forEach { imgBytes ->
                            if (attachedBytesBudget + imgBytes.size <= TOTAL_INLINE_BUDGET_BYTES) {
                                attachedBytesBudget += imgBytes.size
                                add(RestPart.InlineData("image/jpeg", imgBytes))
                            }
                        }
                    }
                }
                if (att.extractedText.isNotBlank()) {
                    add(RestPart.Text("\n--- محتوى المادة (${type.arabicLabel}): ${att.displayName} ---\n${att.extractedText.take(15_000)}"))
                }
            }
        }
    }

    private fun renderPdfFallbackImages(localFilePath: String, maxPages: Int): List<ByteArray> {
        if (localFilePath.isBlank()) return emptyList()
        val file = File(localFilePath)
        if (!file.exists() || !file.isFile) return emptyList()
        val outList = mutableListOf<ByteArray>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = min(renderer.pageCount, maxPages)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val targetW = (page.width * 1.5f).toInt().coerceIn(800, 1400)
                val targetH = ((targetW.toFloat() / page.width.coerceAtLeast(1)) * page.height)
                    .toInt()
                    .coerceIn(800, 1900)
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                bmp.recycle()
                val bytes = baos.toByteArray()
                if (bytes.isNotEmpty()) outList.add(bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed rendering fallback pages for oversized PDF", e)
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
        return outList
    }

    private fun classifyAiException(e: Throwable, isPersonalKey: Boolean): StudyAiException {
        if (e is StudyAiException) return e
        val raw = (e.message ?: e.localizedMessage ?: e.javaClass.simpleName).trim()
        val lower = raw.lowercase()

        if (!isPersonalKey && (isThrottleDetail(raw) || lower.contains("too many attempts") || lower.contains("429"))) {
            markAppCheckRateLimited(raw)
        }

        val isAppCheck = !isPersonalKey && (
            lower.contains("app check") ||
                lower.contains("appcheck") ||
                lower.contains("attestation") ||
                lower.contains("403") ||
                lower.contains("too many attempts")
            )

        val friendly = when {
            isAppCheck ->
                "تم رفض التحقق الأمني (App Check) للخادم ($raw). إذا كان التطبيق مثبتاً كـ APK جانبي، أدخل مفتاح Gemini شخصي من «إعدادات المحرك الذكي»."
            lower.contains("billing") || lower.contains("blaze") || lower.contains("paid") || lower.contains("permission_denied") ->
                "هذا النموذج أو توليد الصور يتطلب خطة Blaze في Firebase أو استخدام مفتاح Gemini شخصي ($raw)."
            lower.contains("quota") || lower.contains("429") ->
                "تم بلوغ الحد المؤقت للطلبات ($raw). انتظر لحظات أو بدّل بين المسار السحابي والمفتاح الشخصي."
            lower.contains("unable to resolve host") || lower.contains("network") || lower.contains("connect") ->
                "تعذر الاتصال بالشبكة ($raw). تأكد من اتصال الإنترنت ثم أعد المحاولة."
            else -> "تعذر تنفيذ طلب الذكاء الاصطناعي: $raw"
        }
        return StudyAiException(message = friendly, isAppCheckOrAuthError = isAppCheck, cause = e)
    }
}

internal fun parseStudyDossierJson(rawJson: String): StudyDossierData? {
    if (rawJson.isBlank()) return null
    return runCatching {
        studyJson.decodeFromString<StudyDossierData>(rawJson)
    }.getOrNull()
}

internal fun parseStudyPlanJson(
    rawJson: String,
    fallbackDailyMinutes: Int,
    fallbackTotalDays: Int,
    engineLabel: String = ""
): GeneratedStudyPlanResult {
    val start = rawJson.indexOf('{')
    val end = rawJson.lastIndexOf('}')
    val cleanJson = if (start >= 0 && end > start) rawJson.substring(start, end + 1) else rawJson
    val parsed = studyJson.decodeFromString<StudyPlanPayload>(cleanJson)

    val dossier = StudyDossierData(
        overview = parsed.overview.trim(),
        sections = parsed.sections.filter { it.heading.isNotBlank() || it.explanation.isNotBlank() },
        glossary = parsed.glossary.filter { it.term.isNotBlank() && it.definition.isNotBlank() },
        examTips = parsed.examTips.filter { it.isNotBlank() },
        mindMapRootTitle = parsed.mindMapRootTitle.trim(),
        mindMapBranches = parsed.mindMapBranches.filter { it.title.isNotBlank() }
    )

    val synthesizedSummary = if (parsed.summaryMarkdown.isNotBlank()) {
        parsed.summaryMarkdown.trim()
    } else {
        buildString {
            if (dossier.overview.isNotBlank()) {
                append("## النظرة العامة\n").append(dossier.overview).append("\n\n")
            }
            dossier.sections.forEach { sec ->
                if (sec.heading.isNotBlank()) append("### ${sec.heading}\n")
                if (sec.explanation.isNotBlank()) append("${sec.explanation}\n")
                if (sec.keyFormulaOrRule.isNotBlank()) append("• القاعدة/القانون: ${sec.keyFormulaOrRule}\n")
                if (sec.example.isNotBlank()) append("• مثال تطبيقي: ${sec.example}\n")
                append("\n")
            }
            if (dossier.glossary.isNotEmpty()) {
                append("### أهم المصطلحات\n")
                dossier.glossary.forEach { g -> append("- **${g.term}:** ${g.definition}\n") }
                append("\n")
            }
            if (dossier.examTips.isNotEmpty()) {
                append("### نصائح الامتحان\n")
                dossier.examTips.forEach { tip -> append("• $tip\n") }
            }
        }.trim()
    }

    val tasks = if (parsed.schedule.isNotEmpty()) {
        parsed.schedule.mapIndexed { idx, item ->
            val dayNum = if (item.dayNumber > 0) item.dayNumber else (idx / 2) + 1
            GeneratedScheduleTask(
                dayNumber = dayNum,
                dayLabel = item.dayLabel.ifBlank { "اليوم $dayNum" },
                timeSlot = item.timeSlot.ifBlank { "جلسة دراسية" },
                taskTitle = item.taskTitle.ifBlank { "مراجعة المادة" },
                taskDetail = item.taskDetail.ifBlank { "تركيز ومراجعة أهم النقاط." },
                durationMinutes = item.durationMinutes.coerceIn(10, 240)
            )
        }
    } else {
        (1..fallbackTotalDays.coerceIn(1, 14)).map { d ->
            GeneratedScheduleTask(
                dayNumber = d,
                dayLabel = "اليوم $d",
                timeSlot = "الجلسة الرئيسية",
                taskTitle = "مراجعة المحور رقم $d",
                taskDetail = "تطبيق الملخص المرفق واختبار الفهم.",
                durationMinutes = fallbackDailyMinutes.coerceIn(15, 240)
            )
        }
    }

    val hasStructuredDossier = dossier.overview.isNotBlank() ||
        dossier.sections.isNotEmpty() ||
        dossier.glossary.isNotEmpty() ||
        dossier.mindMapBranches.isNotEmpty()

    val dossierJsonStr = if (hasStructuredDossier) studyJson.encodeToString(dossier) else ""
    val mindMapJsonStr = if (dossier.mindMapBranches.isNotEmpty()) {
        studyJson.encodeToString(
            StudyDossierData(
                mindMapRootTitle = dossier.mindMapRootTitle,
                mindMapBranches = dossier.mindMapBranches
            )
        )
    } else ""

    return GeneratedStudyPlanResult(
        summaryMarkdown = synthesizedSummary,
        keyPointsText = parsed.keyPoints.filter { it.isNotBlank() }.joinToString("\n") { "• $it" },
        scheduleTasks = tasks,
        isOfflineFallback = false,
        dossierJson = dossierJsonStr,
        mindMapJson = mindMapJsonStr,
        engineUsedLabel = engineLabel
    )
}
