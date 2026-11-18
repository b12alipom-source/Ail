package com.example.export

import com.example.data.GeneratedSegment

object SubtitleParser {

    private val TIME_LINE_REGEX = Regex(
        """^\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}).*$"""
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    fun parse(rawContent: String): List<GeneratedSegment> {
        val normalized = rawContent
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val blocks = normalized.split(Regex("""\n{2,}"""))
        val segments = mutableListOf<GeneratedSegment>()

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val firstUpper = lines.first().trim().uppercase()
            if (firstUpper.startsWith("WEBVTT") ||
                firstUpper.startsWith("NOTE") ||
                firstUpper.startsWith("STYLE") ||
                firstUpper.startsWith("REGION")
            ) {
                continue
            }

            val timeLineIndex = lines.indexOfFirst { TIME_LINE_REGEX.matches(it) }
            if (timeLineIndex < 0) continue

            val match = TIME_LINE_REGEX.matchEntire(lines[timeLineIndex]) ?: continue
            val startMs = parseTimestampMs(match.groupValues[1]) ?: continue
            val endMs = parseTimestampMs(match.groupValues[2])?.coerceAtLeast(startMs + 400L)
                ?: (startMs + 2_000L)

            val textLines = lines.drop(timeLineIndex + 1)
                .map { it.replace(HTML_TAG_REGEX, "").trim() }
                .filter { it.isNotEmpty() }

            if (textLines.isEmpty()) continue
            val combinedText = textLines.joinToString("\n")
            segments.add(
                GeneratedSegment(
                    startMs = startMs,
                    endMs = endMs,
                    originalText = combinedText,
                    translatedText = combinedText
                )
            )
        }

        return segments.sortedBy { it.startMs }
    }

    internal fun parseTimestampMs(timestamp: String): Long? {
        val clean = timestamp.trim().replace(',', '.')
        val mainAndMillis = clean.split('.')
        val hmsParts = mainAndMillis[0].split(':').mapNotNull { it.toLongOrNull() }
        val millisRaw = mainAndMillis.getOrNull(1)?.padEnd(3, '0')?.take(3) ?: "000"
        val millis = millisRaw.toLongOrNull() ?: 0L

        return when (hmsParts.size) {
            3 -> ((hmsParts[0] * 3600L) + (hmsParts[1] * 60L) + hmsParts[2]) * 1000L + millis
            2 -> ((hmsParts[0] * 60L) + hmsParts[1]) * 1000L + millis
            else -> null
        }
    }
}
