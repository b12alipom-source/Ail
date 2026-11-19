package com.example.study

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.GeneratedScheduleTask
import com.example.data.GeneratedStudyPlanResult
import com.example.data.StudyAttachmentType
import com.example.data.StudyMethod
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.min

private const val TAG = "StudyAttachmentExtractor"
private const val MAX_OFFLINE_PDF_PAGES = 10

data class IngestedAttachment(
    val displayName: String,
    val type: StudyAttachmentType,
    val mimeType: String,
    val localFilePath: String,
    val extractedText: String
)

class StudyAttachmentExtractor(private val context: Context) {

    private val attachmentsRoot: File
        get() = File(context.filesDir, "study-attachments").apply { mkdirs() }

    private val visualsRoot: File
        get() = File(context.filesDir, "study-visuals").apply { mkdirs() }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun ingestDocumentOrPdf(uri: Uri): IngestedAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri)?.lowercase().orEmpty()
        val displayName = resolveDisplayName(uri) ?: if (rawMime.contains("pdf")) "ملف مادة.pdf" else "ملف نصي.txt"
        val isPdf = rawMime.contains("pdf") || displayName.endsWith(".pdf", ignoreCase = true)

        if (isPdf) {
            val destFile = File(attachmentsRoot, "pdf_${UUID.randomUUID()}.pdf")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("تعذر قراءة ملف PDF المحدد.")

            val extracted = extractTextFromPdfPages(destFile)
            IngestedAttachment(
                displayName = displayName,
                type = StudyAttachmentType.PDF,
                mimeType = "application/pdf",
                localFilePath = destFile.absolutePath,
                extractedText = extracted
            )
        } else {
            val textContent = resolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }?.trim().orEmpty()
            if (textContent.isBlank()) {
                throw IllegalStateException("الملف المختار فارغ أو غير قابل للقراءة كنص.")
            }
            val destFile = File(attachmentsRoot, "txt_${UUID.randomUUID()}.txt")
            destFile.writeText(textContent, Charsets.UTF_8)
            IngestedAttachment(
                displayName = displayName,
                type = StudyAttachmentType.TEXT,
                mimeType = "text/plain",
                localFilePath = destFile.absolutePath,
                extractedText = textContent.take(24_000)
            )
        }
    }

    suspend fun ingestLecturePhoto(uri: Uri): IngestedAttachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolveDisplayName(uri) ?: "صورة درس.jpg"
        val destFile = File(attachmentsRoot, "img_${UUID.randomUUID()}.jpg")

        val decodedBitmap = resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("تعذر فتح الصورة المحددة.")

        val scaled = scaleBitmapDown(decodedBitmap, maxDimension = 1800)
        FileOutputStream(destFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        val ocrText = runCatching { recognizeTextFromBitmap(scaled) }.getOrDefault("")
        if (scaled !== decodedBitmap) scaled.recycle()
        decodedBitmap.recycle()

        IngestedAttachment(
            displayName = displayName,
            type = StudyAttachmentType.IMAGE,
            mimeType = "image/jpeg",
            localFilePath = destFile.absolutePath,
            extractedText = ocrText
        )
    }

    suspend fun ingestChatAttachment(uri: Uri): IngestedAttachment = withContext(Dispatchers.IO) {
        val rawMime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        val displayName = resolveDisplayName(uri).orEmpty()
        val isImage = rawMime.startsWith("image/") ||
            displayName.endsWith(".jpg", ignoreCase = true) ||
            displayName.endsWith(".jpeg", ignoreCase = true) ||
            displayName.endsWith(".png", ignoreCase = true) ||
            displayName.endsWith(".webp", ignoreCase = true)
        if (isImage) ingestLecturePhoto(uri) else ingestDocumentOrPdf(uri)
    }

    suspend fun saveVisualBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val destFile = File(visualsRoot, "visual_${UUID.randomUUID()}.png")
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        destFile.absolutePath
    }

    suspend fun saveVisualBytes(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val destFile = File(visualsRoot, "visual_${UUID.randomUUID()}.png")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (decoded != null) {
            FileOutputStream(destFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            decoded.recycle()
        } else {
            destFile.writeBytes(bytes)
        }
        destFile.absolutePath
    }

    suspend fun exportVisualToGallery(localImagePath: String, displayTitle: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(localImagePath)
                require(srcFile.exists() && srcFile.length() > 0L) { "ملف الصورة غير موجود على الجهاز." }

                val safeName = displayTitle.trim()
                    .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
                    .take(40)
                    .ifBlank { "bayan_visual" } + "_${System.currentTimeMillis()}.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Bayan")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("تعذر إنشاء سجل في معرض الصور.")

                    resolver.openOutputStream(insertedUri)?.use { out ->
                        srcFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IllegalStateException("تعذر كتابة الصورة إلى المعرض.")

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(insertedUri, values, null, null)
                    "تم حفظ الصورة في المعرض (Pictures/Bayan)."
                } else {
                    throw UnsupportedOperationException("USE_SHARE_SHEET")
                }
            }
        }

    fun savePlainNoteAttachment(title: String, noteBody: String): IngestedAttachment {
        val cleanTitle = title.trim().ifBlank { "ملاحظة دراسية" }
        val cleanBody = noteBody.trim()
        val destFile = File(attachmentsRoot, "note_${UUID.randomUUID()}.txt")
        destFile.writeText(cleanBody, Charsets.UTF_8)
        return IngestedAttachment(
            displayName = cleanTitle,
            type = StudyAttachmentType.TEXT,
            mimeType = "text/plain",
            localFilePath = destFile.absolutePath,
            extractedText = cleanBody.take(24_000)
        )
    }

    fun deleteLocalFileIfExists(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).delete() }
    }

    private suspend fun extractTextFromPdfPages(pdfFile: File): String {
        val sb = StringBuilder()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = min(renderer.pageCount, MAX_OFFLINE_PDF_PAGES)
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val targetWidth = (page.width * 2).coerceIn(900, 1800)
                val targetHeight = ((targetWidth.toFloat() / page.width.coerceAtLeast(1)) * page.height)
                    .toInt()
                    .coerceIn(900, 2400)
                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageText = runCatching { recognizeTextFromBitmap(bmp) }.getOrDefault("")
                bmp.recycle()
                if (pageText.isNotBlank()) {
                    sb.append("صفحة ${i + 1}:\n").append(pageText).append("\n\n")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Local PDF OCR skipped/partial", e)
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
        return sb.toString().trim().take(24_000)
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(input)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text.trim())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume("")
                }
        }

    private fun scaleBitmapDown(src: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(src.width, src.height)
        if (maxSide <= maxDimension) return src
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }
}

internal fun readFileBytesBounded(path: String, maxBytes: Int): ByteArray? {
    if (path.isBlank()) return null
    val f = File(path)
    if (!f.exists() || !f.isFile || f.length() <= 0L || f.length() > maxBytes.toLong()) return null
    return runCatching { f.readBytes() }.getOrNull()
}

internal fun buildOfflineStudyPlan(
    notebookTitle: String,
    method: StudyMethod,
    dailyMinutes: Int,
    totalDays: Int,
    attachmentTexts: List<Pair<String, String>>
): GeneratedStudyPlanResult {
    val safeDays = totalDays.coerceIn(1, 30)
    val safeMinutes = dailyMinutes.coerceIn(15, 480)
    val cleanTitle = notebookTitle.trim().ifBlank { "المادة الدراسية" }

    val extractedSentences = attachmentTexts
        .flatMap { (name, text) ->
            val lines = text.split('\n', '۔', '.', '!', '?')
                .map { it.trim() }
                .filter { it.length >= 16 }
                .distinct()
                .take(8)
            if (lines.isEmpty()) listOf("مراجعة ملف: $name") else lines.map { "$name — $it" }
        }
        .distinct()

    val keyPointsList = if (extractedSentences.isNotEmpty()) {
        extractedSentences.take(10)
    } else {
        attachmentTexts.map { (name, _) -> "مراجعة وفهم محتوى: $name" }
            .ifEmpty { listOf("تحديد المحاور الأساسية لمادة $cleanTitle وتلخيصها بأسلوبك.") }
    }

    val summaryBody = buildString {
        append("## خطة ومُلخّص ${cleanTitle} (${method.arabicLabel})\n\n")
        if (attachmentTexts.isNotEmpty()) {
            append("**المواد المرفقة (${attachmentTexts.size}):** ")
            append(attachmentTexts.joinToString(" • ") { it.first })
            append("\n\n")
        }
        when (method) {
            StudyMethod.FOCUSED_SUMMARY -> {
                append("### الخلاصة المركّزة للمراجعة السريعة:\n")
                keyPointsList.forEachIndexed { idx, line ->
                    append("${idx + 1}. $line\n")
                }
            }
            StudyMethod.STEP_BY_STEP -> {
                append("### خطوات الفهم والتدريج:\n")
                keyPointsList.forEachIndexed { idx, line ->
                    append("- **الخطوة ${idx + 1}:** اقرأ واستوعب: $line\n")
                }
            }
            StudyMethod.QA_DRILLS -> {
                append("### أسئلة مراجعة ذاتية مقترحة:\n")
                keyPointsList.take(6).forEachIndexed { idx, line ->
                    append("س${idx + 1}: اشرح باختصار النقطة التالية: «${line.take(90)}»؟\n")
                }
            }
            StudyMethod.FLASHCARDS -> {
                append("### بطاقات الحفظ والمراجعة:\n")
                keyPointsList.take(8).forEachIndexed { idx, line ->
                    append("• بطاقة ${idx + 1}: $line\n")
                }
            }
        }
        append("\nملاحظة: تم إعداد هذا الملخص والجدول محلياً على جهازك ليعمل فوراً بدون إنترنت.")
    }

    val topicSource = if (attachmentTexts.isNotEmpty()) {
        attachmentTexts.map { it.first }
    } else {
        listOf(cleanTitle)
    }

    val scheduleTasks = mutableListOf<GeneratedScheduleTask>()
    for (day in 1..safeDays) {
        val primaryTopic = topicSource[(day - 1) % topicSource.size]
        val mainMinutes = (safeMinutes * 0.65f).toInt().coerceAtLeast(15)
        val reviewMinutes = (safeMinutes - mainMinutes).coerceAtLeast(10)

        val focusLabel = when {
            day == safeDays -> "مراجعة شاملة واختبار ذاتي — $cleanTitle"
            else -> "دراسة وفهم: $primaryTopic"
        }

        scheduleTasks.add(
            GeneratedScheduleTask(
                dayNumber = day,
                dayLabel = "اليوم $day",
                timeSlot = "الجلسة الأولى (تركيز عميق)",
                taskTitle = focusLabel,
                taskDetail = "تطبيق أسلوب «${method.arabicLabel}» لمدة $mainMinutes دقيقة مع تدوين أهم النقاط.",
                durationMinutes = mainMinutes
            )
        )

        scheduleTasks.add(
            GeneratedScheduleTask(
                dayNumber = day,
                dayLabel = "اليوم $day",
                timeSlot = "الجلسة الثانية (تثبيت وحفظ)",
                taskTitle = if (day == 1) "استخراج المصطلحات والقواعد الرئيسية" else "حل أسئلة ومراجعة نقاط اليوم السابق",
                taskDetail = "مراجعة سريعة بدون النظر للملخص لترسيخ المعلومات خلال $reviewMinutes دقيقة.",
                durationMinutes = reviewMinutes
            )
        )
    }

    return GeneratedStudyPlanResult(
        summaryMarkdown = summaryBody.trim(),
        keyPointsText = keyPointsList.joinToString("\n") { "• $it" },
        scheduleTasks = scheduleTasks,
        isOfflineFallback = true
    )
}

internal fun answerStudyQuestionOffline(
    question: String,
    summary: String,
    attachmentTexts: List<Pair<String, String>>
): String {
    val tokens = question.lowercase()
        .split(Regex("[\\s،؟?!.,:؛]+"))
        .map { it.trim() }
        .filter { it.length >= 3 }

    val matchingSnippets = mutableListOf<String>()
    attachmentTexts.forEach { (docName, body) ->
        body.split('\n', '.', '۔')
            .map { it.trim() }
            .filter { it.length >= 12 }
            .forEach { sentence ->
                val lower = sentence.lowercase()
                if (tokens.any { tok -> lower.contains(tok) }) {
                    matchingSnippets.add("• [$docName]: $sentence")
                }
            }
    }

    return if (matchingSnippets.isNotEmpty()) {
        buildString {
            append("إجابة سريعة من ملفاتك المحفوظة (الوضع المحلي):\n\n")
            matchingSnippets.distinct().take(5).forEach { append(it).append("\n") }
        }.trim()
    } else if (summary.isNotBlank()) {
        "في الوضع المحلي لم أجد تطابقاً حرفياً لكلمات سؤالك داخل النصوص المستخلصة، لكن إليك خلاصة المادة الحالية للمساعدة:\n\n${summary.take(550)}"
    } else {
        "أنت حالياً في الوضع المحلي ولا يوجد نص مستخلص كافٍ بعد لهذا السؤال. أضف ملاحظة نصية أو فعّل الاتصال السحابي لقراءة الرسوم والملفات الكاملة بذكاء أكبر."
    }
}
