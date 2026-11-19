package com.example.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.StudyDossierData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val MAX_INTERNAL_VIDEO_COPY_BYTES = 512L * 1024L * 1024L

data class InternalStorageStats(
    val savedVideosBytes: Long = 0L,
    val studyAssetsBytes: Long = 0L,
    val tempCacheBytes: Long = 0L,
    val savedVideosCount: Int = 0
) {
    val totalBytes: Long
        get() = savedVideosBytes + studyAssetsBytes + tempCacheBytes
}

fun selectPlayableUriCandidates(primaryUri: String, archivedUri: String): List<String> =
    listOf(primaryUri.trim(), archivedUri.trim())
        .filter { it.isNotBlank() }
        .distinct()

fun formatByteSizeArabic(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe بايت"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f ك.ب", safe / 1024.0)
        safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f م.ب", safe / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f غ.ب", safe / (1024.0 * 1024.0 * 1024.0))
    }
}

fun formatStudySummaryForShare(
    title: String,
    methodLabel: String,
    keyPoints: String,
    summaryMarkdown: String,
    dossier: StudyDossierData?
): String = buildString {
    appendLine("📚 $title")
    if (methodLabel.isNotBlank()) {
        appendLine("الأسلوب: $methodLabel")
    }
    appendLine()

    if (dossier != null) {
        if (dossier.overview.isNotBlank()) {
            appendLine("النظرة الشاملة:")
            appendLine(dossier.overview.trim())
            appendLine()
        }
        if (keyPoints.isNotBlank()) {
            appendLine("أهم النقاط المركّزة:")
            appendLine(keyPoints.trim())
            appendLine()
        }
        if (dossier.sections.isNotEmpty()) {
            appendLine("التفاصيل والمحاور:")
            dossier.sections.forEachIndexed { idx, sec ->
                appendLine("${idx + 1}. ${sec.heading}")
                if (sec.explanation.isNotBlank()) appendLine(sec.explanation.trim())
                if (sec.keyFormulaOrRule.isNotBlank()) appendLine("القاعدة: ${sec.keyFormulaOrRule.trim()}")
                if (sec.example.isNotBlank()) appendLine("مثال: ${sec.example.trim()}")
                appendLine()
            }
        }
        if (dossier.glossary.isNotEmpty()) {
            appendLine("قاموس المصطلحات:")
            dossier.glossary.forEach { item ->
                appendLine("• ${item.term}: ${item.definition}")
            }
            appendLine()
        }
        if (dossier.examTips.isNotEmpty()) {
            appendLine("تنبيهات مهمة:")
            dossier.examTips.forEach { tip ->
                appendLine("• $tip")
            }
            appendLine()
        }
    } else {
        if (keyPoints.isNotBlank()) {
            appendLine("أهم النقاط المركّزة:")
            appendLine(keyPoints.trim())
            appendLine()
        }
        if (summaryMarkdown.isNotBlank()) {
            appendLine(summaryMarkdown.trim())
        }
    }
}.trim()

class InternalMediaArchive(private val context: Context) {

    private val savedVideosDir: File
        get() = File(context.filesDir, "saved_videos").apply { mkdirs() }

    private val studyAttachmentsDir: File
        get() = File(context.filesDir, "study-attachments")

    private val studyVisualsDir: File
        get() = File(context.filesDir, "study-visuals")

    suspend fun archiveVideoCopy(projectId: Long, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        if (projectId <= 0L) return@withContext ""
        val resolver = context.contentResolver

        val declaredSize = runCatching {
            resolver.query(sourceUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } ?: -1L
        }.getOrDefault(-1L)

        if (declaredSize > MAX_INTERNAL_VIDEO_COPY_BYTES) {
            return@withContext ""
        }

        val ext = resolveVideoExtension(sourceUri)
        val dir = savedVideosDir
        val targetFile = File(dir, "video_${projectId}.${ext}")
        val tempFile = File(dir, "video_${projectId}.${ext}.tmp")

        val copiedOk = runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                var totalCopied = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalCopied += read
                        if (totalCopied > MAX_INTERNAL_VIDEO_COPY_BYTES) {
                            throw IllegalStateException("Video exceeds internal archive size cap")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
                totalCopied > 0L
            } ?: false
        }.getOrDefault(false)

        if (!copiedOk) {
            runCatching { tempFile.delete() }
            return@withContext ""
        }

        // Remove any previous copy for this project with a different extension
        dir.listFiles()?.forEach { sibling ->
            if (sibling.isFile &&
                sibling.name.startsWith("video_${projectId}.") &&
                sibling.absolutePath != tempFile.absolutePath &&
                sibling.absolutePath != targetFile.absolutePath
            ) {
                runCatching { sibling.delete() }
            }
        }

        if (targetFile.exists()) {
            runCatching { targetFile.delete() }
        }
        return@withContext if (tempFile.renameTo(targetFile)) {
            Uri.fromFile(targetFile).toString()
        } else {
            runCatching { tempFile.delete() }
            ""
        }
    }

    suspend fun deleteArchivedVideo(archivedUri: String) = withContext(Dispatchers.IO) {
        if (archivedUri.isBlank()) return@withContext
        runCatching {
            val parsed = Uri.parse(archivedUri)
            if (parsed.scheme == "file") {
                val path = parsed.path ?: return@runCatching
                val target = File(path).canonicalFile
                val allowedRoot = savedVideosDir.canonicalFile
                if (target.path.startsWith(allowedRoot.path + File.separator) && target.exists()) {
                    target.delete()
                }
            }
        }
    }

    suspend fun inspectStorageStats(): InternalStorageStats = withContext(Dispatchers.IO) {
        val videoFiles = savedVideosDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        val savedVideosBytes = videoFiles.sumOf { it.length() }
        val studyBytes = computeDirSize(studyAttachmentsDir) + computeDirSize(studyVisualsDir)
        val cacheBytes = computeDirSize(context.cacheDir)
        InternalStorageStats(
            savedVideosBytes = savedVideosBytes,
            studyAssetsBytes = studyBytes,
            tempCacheBytes = cacheBytes,
            savedVideosCount = videoFiles.size
        )
    }

    suspend fun clearTempCache(): Long = withContext(Dispatchers.IO) {
        val before = computeDirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { child ->
            runCatching { child.deleteRecursively() }
        }
        val after = computeDirSize(context.cacheDir)
        (before - after).coerceAtLeast(0L)
    }

    private fun resolveVideoExtension(uri: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase().orEmpty()
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull().orEmpty()
        val fromName = displayName.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }
        if (fromName in setOf("mp4", "mov", "mkv", "webm", "m4v", "3gp")) {
            return fromName
        }
        return when {
            mime.contains("quicktime") -> "mov"
            mime.contains("webm") -> "webm"
            mime.contains("x-matroska") -> "mkv"
            else -> "mp4"
        }
    }

    private fun computeDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
