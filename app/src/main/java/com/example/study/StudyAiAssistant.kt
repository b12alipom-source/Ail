package com.example.study

import android.util.Log
import com.example.ai.isAppCheckRateLimited
import com.example.ai.markAppCheckRateLimited
import com.example.data.GeneratedScheduleTask
import com.example.data.GeneratedStudyPlanResult
import com.example.data.StudyAttachmentType
import com.example.data.StudyMethod
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "StudyAiAssistant"
private const val PRIMARY_MODEL = "gemini-3.8-flash"
private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
private const val MAX_INLINE_FILE_BYTES = 10 * 1024 * 1024 // 10 MB per inline attachment
private const val AI_TIMEOUT_MS = 60_000L

@Serializable
private data class StudyPlanPayload(
    val summaryMarkdown: String = "",
    val keyPoints: List<String> = emptyList(),
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
}

class StudyAiAssistant {

    private val studyPlanSchema = Schema.obj(
        properties = mapOf(
            "summaryMarkdown" to Schema.string(
                description = "Detailed, easy-to-study Arabic summary formatted cleanly with clear headings and bullet points tailored to the user's preferred study method."
            ),
            "keyPoints" to Schema.array(
                items = Schema.string(description = "Key high-yield takeaway or concept in Arabic"),
                description = "5 to 10 essential takeaways for quick revision"
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

    suspend fun generateStudyPlan(
        notebookTitle: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int,
        attachments: List<StudyAttachmentEntity>,
        preferOffline: Boolean
    ): GeneratedStudyPlanResult {
        val attachmentTexts = attachments.map { it.displayName to it.extractedText }

        if (preferOffline || isAppCheckRateLimited()) {
            return buildOfflineStudyPlan(notebookTitle, method, dailyMinutes, totalDays, attachmentTexts)
        }

        val cloudAttempt = runCatching {
            withTimeout(AI_TIMEOUT_MS) {
                requestCloudStudyPlan(
                    notebookTitle = notebookTitle,
                    method = method,
                    dailyMinutes = dailyMinutes,
                    totalDays = totalDays,
                    attachments = attachments
                )
            }
        }

        return cloudAttempt.getOrElse { err ->
            if (err is CancellationException) throw err
            val msg = (err.message ?: err.localizedMessage ?: "")
            if (msg.contains("Too many attempts", ignoreCase = true) ||
                msg.contains("App Check", ignoreCase = true) ||
                msg.contains("429")
            ) {
                markAppCheckRateLimited(msg.ifBlank { "Too many attempts" })
            }
            Log.w(TAG, "Cloud study plan generation failed; using offline builder", err)
            buildOfflineStudyPlan(notebookTitle, method, dailyMinutes, totalDays, attachmentTexts)
        }
    }

    suspend fun askStudyTutor(
        notebookTitle: String,
        method: StudyMethod,
        summary: String,
        attachments: List<StudyAttachmentEntity>,
        history: List<StudyMessageEntity>,
        userQuestion: String,
        preferOffline: Boolean
    ): Pair<String, Boolean> {
        val attachmentTexts = attachments.map { it.displayName to it.extractedText }
        if (preferOffline || isAppCheckRateLimited()) {
            return answerStudyQuestionOffline(userQuestion, summary, attachmentTexts) to true
        }

        val cloudReply = runCatching {
            withTimeout(AI_TIMEOUT_MS) {
                requestCloudTutorAnswer(
                    notebookTitle = notebookTitle,
                    method = method,
                    summary = summary,
                    attachments = attachments,
                    history = history,
                    userQuestion = userQuestion
                )
            }
        }

        return cloudReply.fold(
            onSuccess = { answer -> answer to false },
            onFailure = { err ->
                if (err is CancellationException) throw err
                val msg = (err.message ?: err.localizedMessage ?: "")
                if (msg.contains("Too many attempts", ignoreCase = true) ||
                    msg.contains("App Check", ignoreCase = true) ||
                    msg.contains("429")
                ) {
                    markAppCheckRateLimited(msg.ifBlank { "Too many attempts" })
                }
                Log.w(TAG, "Cloud tutor chat failed; answering from local content", err)
                answerStudyQuestionOffline(userQuestion, summary, attachmentTexts) to true
            }
        )
    }

    private suspend fun requestCloudStudyPlan(
        notebookTitle: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int,
        attachments: List<StudyAttachmentEntity>
    ): GeneratedStudyPlanResult {
        val prompt = buildString {
            appendLine("أنت مساعد دراسي ومعلم خبير داخل تطبيق بيان.")
            appendLine("المطلوب: مساعدة الطالب على دراسة مادة «$notebookTitle» بناءً على المرفقات المرسلة.")
            appendLine("أسلوب الدراسة المفضل: ${method.arabicLabel} (${method.promptInstruction})")
            appendLine("الجدول الزمني المطلوب: $totalDays أيام، بمعدل تقريبي $dailyMinutes دقيقة يومياً (قسّم كل يوم إلى جلستين واضحتين).")
            appendLine("أعد الرد بصيغة JSON مطابقة للـ Schema باللغة العربية الواضحة والمنظمة، وضمّن أهم المصطلحات العلمية بلغتها الأصلية مع شرحها بالعربية.")
        }

        val inputContent = buildMultimodalContent(
            instructionPrompt = prompt,
            attachments = attachments
        )

        val response = generateWithModelFallback(
            modelNames = listOf(PRIMARY_MODEL, FALLBACK_MODEL),
            useJsonSchema = true,
            inputContent = inputContent
        )
        return parseStudyPlanJson(response, dailyMinutes, totalDays)
    }

    private suspend fun requestCloudTutorAnswer(
        notebookTitle: String,
        method: StudyMethod,
        summary: String,
        attachments: List<StudyAttachmentEntity>,
        history: List<StudyMessageEntity>,
        userQuestion: String
    ): String {
        val recentHistory = history.takeLast(6).joinToString("\n") { msg ->
            if (msg.isUser) "الطالب: ${msg.messageText}" else "المعلم: ${msg.messageText}"
        }
        val prompt = buildString {
            appendLine("أنت المعلم الذكي الشخصي للطالب في مادة «$notebookTitle».")
            appendLine("أسلوب الشرح المفضل للطالب: ${method.arabicLabel} (${method.promptInstruction})")
            if (summary.isNotBlank()) {
                appendLine("الملخص المعتمد للمادة:\n${summary.take(2500)}\n")
            }
            if (recentHistory.isNotBlank()) {
                appendLine("المحادثة السابقة:\n$recentHistory\n")
            }
            appendLine("سؤال الطالب الآن: $userQuestion")
            appendLine("أجب بالعربية بوضوح ودقة، مع خطوات عملية وأمثلة أو اختبار قصير إذا كان مفيداً.")
        }

        val inputContent = buildMultimodalContent(
            instructionPrompt = prompt,
            attachments = attachments
        )

        val reply = generateWithModelFallback(
            modelNames = listOf(PRIMARY_MODEL, FALLBACK_MODEL),
            useJsonSchema = false,
            inputContent = inputContent
        )
        return reply.trim().ifBlank {
            throw IllegalStateException("رد فارغ من المساعد الذكي")
        }
    }

    private fun buildMultimodalContent(
        instructionPrompt: String,
        attachments: List<StudyAttachmentEntity>
    ) = content {
        text(instructionPrompt)
        var attachedBytesBudget = 0
        attachments.forEach { att ->
            val type = StudyAttachmentType.fromKey(att.attachmentType)
            val fileBytes = if ((type == StudyAttachmentType.PDF || type == StudyAttachmentType.IMAGE) &&
                attachedBytesBudget < 16 * 1024 * 1024
            ) {
                readFileBytesBounded(att.localFilePath, MAX_INLINE_FILE_BYTES)
            } else null

            if (fileBytes != null) {
                attachedBytesBudget += fileBytes.size
                text("\n--- مرفق (${type.arabicLabel}): ${att.displayName} ---")
                inlineData(fileBytes, att.mimeType)
            } else if (att.extractedText.isNotBlank()) {
                text("\n--- محتوى (${type.arabicLabel}): ${att.displayName} ---\n${att.extractedText.take(12_000)}")
            }
        }
    }

    private suspend fun generateWithModelFallback(
        modelNames: List<String>,
        useJsonSchema: Boolean,
        inputContent: com.google.firebase.ai.type.Content
    ): String {
        var lastErr: Throwable? = null
        for (modelName in modelNames) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        temperature = 0.35f
                        if (useJsonSchema) {
                            responseMimeType = "application/json"
                            responseSchema = studyPlanSchema
                        }
                    }
                )
                val out = model.generateContent(inputContent).text.orEmpty()
                if (out.isNotBlank()) return out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw lastErr ?: IllegalStateException("تعذر الاتصال بالمساعد الدراسي.")
    }
}

internal fun parseStudyPlanJson(
    rawJson: String,
    fallbackDailyMinutes: Int,
    fallbackTotalDays: Int
): GeneratedStudyPlanResult {
    val start = rawJson.indexOf('{')
    val end = rawJson.lastIndexOf('}')
    val cleanJson = if (start >= 0 && end > start) rawJson.substring(start, end + 1) else rawJson
    val parsed = studyJson.decodeFromString<StudyPlanPayload>(cleanJson)

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

    return GeneratedStudyPlanResult(
        summaryMarkdown = parsed.summaryMarkdown.trim(),
        keyPointsText = parsed.keyPoints.filter { it.isNotBlank() }.joinToString("\n") { "• $it" },
        scheduleTasks = tasks,
        isOfflineFallback = false
    )
}
