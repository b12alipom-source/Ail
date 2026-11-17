package com.example.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.ExportFormat
import com.example.data.SubtitleDisplayMode
import com.example.data.db.SubtitleCueEntity
import java.io.File
import java.util.Locale

object SubtitleExporter {

    fun buildExportContent(
        cues: List<SubtitleCueEntity>,
        format: ExportFormat,
        displayMode: SubtitleDisplayMode
    ): String {
        return when (format) {
            ExportFormat.SRT -> buildSrt(cues, displayMode)
            ExportFormat.VTT -> buildVtt(cues, displayMode)
            ExportFormat.TXT_TIMESTAMPS -> buildTxtWithTimestamps(cues, displayMode)
            ExportFormat.TXT_CLEAN -> buildCleanTxt(cues, displayMode)
        }
    }

    private fun resolveCueText(cue: SubtitleCueEntity, mode: SubtitleDisplayMode): String {
        return when (mode) {
            SubtitleDisplayMode.TRANSLATED_ONLY -> cue.translatedText
            SubtitleDisplayMode.ORIGINAL_ONLY -> cue.originalText
            SubtitleDisplayMode.BILINGUAL -> "${cue.translatedText}\n${cue.originalText}"
        }
    }

    private fun buildSrt(cues: List<SubtitleCueEntity>, mode: SubtitleDisplayMode): String {
        return buildString {
            cues.forEachIndexed { index, cue ->
                append(index + 1).append('\n')
                append(formatSrtTime(cue.startMs))
                append(" --> ")
                append(formatSrtTime(cue.endMs))
                append('\n')
                append(resolveCueText(cue, mode)).append("\n\n")
            }
        }.trimEnd()
    }

    private fun buildVtt(cues: List<SubtitleCueEntity>, mode: SubtitleDisplayMode): String {
        return buildString {
            append("WEBVTT\n\n")
            cues.forEach { cue ->
                append(formatVttTime(cue.startMs))
                append(" --> ")
                append(formatVttTime(cue.endMs))
                append('\n')
                append(resolveCueText(cue, mode)).append("\n\n")
            }
        }.trimEnd()
    }

    private fun buildTxtWithTimestamps(
        cues: List<SubtitleCueEntity>,
        mode: SubtitleDisplayMode
    ): String {
        return cues.joinToString("\n\n") { cue ->
            "[${formatReadableTime(cue.startMs)} - ${formatReadableTime(cue.endMs)}]\n${resolveCueText(cue, mode)}"
        }
    }

    private fun buildCleanTxt(
        cues: List<SubtitleCueEntity>,
        mode: SubtitleDisplayMode
    ): String {
        return cues.joinToString("\n") { cue ->
            resolveCueText(cue, mode)
        }
    }

    fun createShareIntent(
        context: Context,
        projectTitle: String,
        cues: List<SubtitleCueEntity>,
        format: ExportFormat,
        displayMode: SubtitleDisplayMode
    ): Intent {
        val content = buildExportContent(cues, format, displayMode)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = projectTitle.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(32)
            .ifBlank { "bayan_subtitles" }
        val file = File(exportsDir, "${safeTitle}.${format.extension}")
        file.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$projectTitle (${format.extension.uppercase(Locale.ROOT)})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "مشاركة ملف الترجمة")
    }

    fun formatSrtTime(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1_000
        val millis = totalMs % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun formatVttTime(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1_000
        val millis = totalMs % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    fun formatReadableTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
