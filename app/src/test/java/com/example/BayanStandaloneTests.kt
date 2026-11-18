package com.example

import com.example.ai.extractErrorFromDummyToken
import com.example.ai.isThrottleDetail
import com.example.export.SubtitleParser
import com.example.media.downmixAndResampleTo16kMono
import com.example.media.segmentActiveSpeechWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class BayanStandaloneTests {

    @Test
    fun parseSrt_extractsMultipleTimedCuesCleanly() {
        val sampleSrt = """
            1
            00:00:01,200 --> 00:00:04,500
            <i>Hello everyone</i>
            Welcome back.

            2
            00:01:05,000 --> 00:01:08,900
            This works offline too!
        """.trimIndent()

        val cues = SubtitleParser.parse(sampleSrt)
        assertEquals(2, cues.size)
        assertEquals(1_200L, cues[0].startMs)
        assertEquals(4_500L, cues[0].endMs)
        assertEquals("Hello everyone\nWelcome back.", cues[0].originalText)
        assertEquals(65_000L, cues[1].startMs)
        assertEquals(68_900L, cues[1].endMs)
    }

    @Test
    fun parseVtt_ignoresHeaderAndParsesShortTimestamps() {
        val sampleVtt = """
            WEBVTT

            00:12.500 --> 00:15.750
            First line in VTT
        """.trimIndent()

        val cues = SubtitleParser.parse(sampleVtt)
        assertEquals(1, cues.size)
        assertEquals(12_500L, cues[0].startMs)
        assertEquals(15_750L, cues[0].endMs)
        assertEquals("First line in VTT", cues[0].originalText)
    }

    @Test
    fun extractErrorFromDummyToken_detectsFirebaseErrorPayload() {
        val dummyToken = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="
        assertEquals("UNKNOWN_ERROR", extractErrorFromDummyToken(dummyToken))
    }

    @Test
    fun extractErrorFromDummyToken_returnsNullForValidJwtShape() {
        val validToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjMifQ.sig"
        assertNull(extractErrorFromDummyToken(validToken))
    }

    @Test
    fun isThrottleDetail_detectsRateLimitErrorsAccurately() {
        assertTrue(isThrottleDetail("Too many attempts."))
        assertTrue(isThrottleDetail("HTTP 429 Quota exceeded"))
        assertFalse(isThrottleDetail("Network timeout"))
    }

    @Test
    fun downmixAndResampleTo16kMono_converts48kStereoCorrectly() {
        // 4800 stereo frames at 48kHz = 100ms -> should resample to ~1600 mono samples at 16kHz
        val frameCount = 4800
        val bb = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val sample = (sin(i * 0.05) * 4000.0).toInt().toShort()
            bb.putShort(sample)
            bb.putShort(sample)
        }
        val out = downmixAndResampleTo16kMono(
            pcmBytes = bb.array(),
            byteOffset = 0,
            byteLength = bb.capacity(),
            sampleRate = 48_000,
            channelCount = 2,
            isFloatPcm = false
        )
        assertEquals(1600, out.size)
    }

    @Test
    fun segmentActiveSpeechWindows_isolatesSpeechBurstsWithTimestamps() {
        // Build 6 seconds at 16kHz:
        // 0.0s - 0.5s: silence
        // 0.5s - 2.0s: speech-level tone
        // 2.0s - 3.5s: silence (> 600ms splits segments)
        // 3.5s - 5.2s: speech-level tone
        // 5.2s - 6.0s: silence
        val totalSamples = 6 * 16_000
        val samples = ShortArray(totalSamples)
        for (i in (16_000 / 2) until (2 * 16_000)) {
            samples[i] = (sin(i * 0.1) * 5000.0).toInt().toShort()
        }
        for (i in (35 * 1_600) until (52 * 1_600)) {
            samples[i] = (sin(i * 0.1) * 5000.0).toInt().toShort()
        }

        val windows = segmentActiveSpeechWindows(samples, baseOffsetMs = 1_000L)
        assertEquals(2, windows.size)
        assertTrue(windows[0].startMs in 1_200L..1_600L)
        assertTrue(windows[0].endMs in 2_900L..3_300L)
        assertTrue(windows[1].startMs in 4_200L..4_600L)
        assertTrue(windows[1].endMs in 6_100L..6_500L)
    }
}
