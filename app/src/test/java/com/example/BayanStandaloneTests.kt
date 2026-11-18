package com.example

import com.example.ai.extractErrorFromDummyToken
import com.example.export.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        // Base64URL of {"error":"UNKNOWN_ERROR"} is eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ
        val dummyToken = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="
        assertEquals("UNKNOWN_ERROR", extractErrorFromDummyToken(dummyToken))
    }

    @Test
    fun extractErrorFromDummyToken_returnsNullForValidJwtShape() {
        // Base64URL of {"sub":"123"} is eyJzdWIiOiIxMjMifQ
        val validToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjMifQ.sig"
        assertNull(extractErrorFromDummyToken(validToken))
    }
}
