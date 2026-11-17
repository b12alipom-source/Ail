package com.example

import com.example.data.ExportFormat
import com.example.data.SubtitleDisplayMode
import com.example.data.db.SubtitleCueEntity
import com.example.export.SubtitleExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExporterTest {

    private val sampleCues = listOf(
        SubtitleCueEntity(
            id = 1L,
            projectId = 1L,
            orderIndex = 0,
            startMs = 1_250L,
            endMs = 4_500L,
            originalText = "Hello world",
            translatedText = "مرحباً بالعالم"
        ),
        SubtitleCueEntity(
            id = 2L,
            projectId = 1L,
            orderIndex = 1,
            startMs = 65_000L,
            endMs = 68_750L,
            originalText = "Instant translation",
            translatedText = "ترجمة فورية"
        )
    )

    @Test
    fun srtFormatting_producesValidTimestampsAndIndices() {
        val srt = SubtitleExporter.buildExportContent(
            cues = sampleCues,
            format = ExportFormat.SRT,
            displayMode = SubtitleDisplayMode.TRANSLATED_ONLY
        )
        assertTrue(srt.contains("1\n00:00:01,250 --> 00:00:04,500\nمرحباً بالعالم"))
        assertTrue(srt.contains("2\n00:01:05,000 --> 00:01:08,750\nترجمة فورية"))
    }

    @Test
    fun vttFormatting_includesWebVttHeaderAndDotMillis() {
        val vtt = SubtitleExporter.buildExportContent(
            cues = sampleCues,
            format = ExportFormat.VTT,
            displayMode = SubtitleDisplayMode.BILINGUAL
        )
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:01.250 --> 00:00:04.500\nمرحباً بالعالم\nHello world"))
    }

    @Test
    fun readableTime_formatsMinutesAndHoursCorrectly() {
        assertEquals("01:05", SubtitleExporter.formatReadableTime(65_000L))
        assertEquals("1:01:05", SubtitleExporter.formatReadableTime(3_665_000L))
    }
}
