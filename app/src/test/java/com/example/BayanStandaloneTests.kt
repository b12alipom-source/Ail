package com.example

import com.example.ai.extractErrorFromDummyToken
import com.example.ai.isThrottleDetail
import com.example.data.StudyAiTier
import com.example.data.StudyMethod
import com.example.export.SubtitleParser
import com.example.media.VoskCatalogItem
import com.example.media.VoskTimedWord
import com.example.media.downmixAndResampleTo16kMono
import com.example.media.groupVoskWordsIntoSegments
import com.example.media.isSafeZipDestination
import com.example.media.parseVoskResultJson
import com.example.media.segmentActiveSpeechWindows
import com.example.media.selectBestCatalogModelForLanguage
import com.example.media.stripTopArchiveDirectory
import com.example.study.answerStudyQuestionOffline
import com.example.study.buildOfflineStudyPlan
import com.example.study.extractBase64ImageFromGeminiRestJson
import com.example.study.extractSseDataPayload
import com.example.study.extractTextFromGeminiRestJson
import com.example.study.formatRestHttpError
import com.example.study.parseStudyDossierJson
import com.example.study.parseStudyPlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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

    @Test
    fun parseVoskResultJson_extractsWordsAndTimings() {
        val sampleJson = """
            {
              "result": [
                {"conf": 0.98, "end": 0.62, "start": 0.15, "word": "hello"},
                {"conf": 0.95, "end": 1.10, "start": 0.65, "word": "world"}
              ],
              "text": "hello world"
            }
        """.trimIndent()

        val (text, words) = parseVoskResultJson(sampleJson)
        assertEquals("hello world", text)
        assertEquals(2, words.size)
        assertEquals("hello", words[0].word)
        assertEquals(0.15, words[0].start, 0.001)
        assertEquals(1.10, words[1].end, 0.001)
    }

    @Test
    fun groupVoskWordsIntoSegments_splitsOnPausesAndRebasesTimestamps() {
        val words = listOf(
            VoskTimedWord("welcome", start = 0.2, end = 0.7),
            VoskTimedWord("back", start = 0.75, end = 1.1),
            // 1.0s pause -> triggers cue split
            VoskTimedWord("let", start = 2.1, end = 2.3),
            VoskTimedWord("us", start = 2.35, end = 2.5),
            VoskTimedWord("begin", start = 2.55, end = 3.0)
        )

        val segments = groupVoskWordsIntoSegments(
            words = words,
            fallbackText = "welcome back let us begin",
            windowStartMs = 10_000L,
            windowEndMs = 14_000L
        )
        assertEquals(2, segments.size)
        assertEquals("welcome back", segments[0].originalText)
        assertEquals(10_200L, segments[0].startMs)
        assertEquals(11_100L, segments[0].endMs)
        assertEquals("let us begin", segments[1].originalText)
        assertEquals(12_100L, segments[1].startMs)
        assertEquals(13_000L, segments[1].endMs)
    }

    @Test
    fun selectBestCatalogModelForLanguage_prefersSmallNonObsoleteModels() {
        val catalog = listOf(
            VoskCatalogItem(lang = "en-us", name = "vosk-model-en-us-0.22", type = "big", size = 1_800_000_000, url = "https://alphacephei.com/vosk/models/big.zip", obsolete = "false"),
            VoskCatalogItem(lang = "en-us", name = "vosk-model-small-en-us-0.15", type = "small", size = 40_000_000, url = "https://alphacephei.com/vosk/models/small.zip", obsolete = "false"),
            VoskCatalogItem(lang = "ar", name = "vosk-model-small-ar-tn-0.1-linto", type = "small", size = 35_000_000, url = "https://alphacephei.com/vosk/models/ar-tn.zip", obsolete = "false"),
            VoskCatalogItem(lang = "ar", name = "vosk-model-ar-mgb2-0.4", type = "big", size = 318_000_000, url = "https://alphacephei.com/vosk/models/ar-mgb2.zip", obsolete = "false")
        )

        val enBest = selectBestCatalogModelForLanguage(catalog, "en")
        assertNotNull(enBest)
        assertEquals("vosk-model-small-en-us-0.15", enBest?.name)

        val arBest = selectBestCatalogModelForLanguage(catalog, "ar")
        assertNotNull(arBest)
        assertEquals("vosk-model-ar-mgb2-0.4", arBest?.name)
    }

    @Test
    fun zipHelpers_stripTopFolderAndBlockPathTraversal() {
        assertEquals("conf/mfcc.conf", stripTopArchiveDirectory("vosk-model-small-en-us-0.15/conf/mfcc.conf"))
        val root = File("/tmp/bayan-vosk-test")
        assertTrue(isSafeZipDestination(root, File(root, "conf/mfcc.conf")))
        assertFalse(isSafeZipDestination(root, File(root, "../evil.sh")))
    }

    @Test
    fun buildOfflineStudyPlan_generatesTailoredSummaryAndDailyTasks() {
        val attachments = listOf(
            "ملخص الخلية.pdf" to "الميتوكوندريا مسؤولة عن إنتاج الطاقة الخلوية ATP. الغشاء البلازمي ينظم دخول وخروج المواد.",
            "فيديو الشرح" to "الانقسام المتساوي يحافظ على العدد الصبغي للخلية."
        )
        val plan = buildOfflineStudyPlan(
            notebookTitle = "أحياء الفصل الأول",
            method = StudyMethod.QA_DRILLS,
            dailyMinutes = 90,
            totalDays = 3,
            attachmentTexts = attachments
        )
        assertTrue(plan.isOfflineFallback)
        assertTrue(plan.summaryMarkdown.contains("أحياء الفصل الأول"))
        assertTrue(plan.keyPointsText.contains("الميتوكوندريا"))
        // 2 sessions per day * 3 days = 6 schedule tasks
        assertEquals(6, plan.scheduleTasks.size)
        assertEquals(1, plan.scheduleTasks.first().dayNumber)
        assertEquals(3, plan.scheduleTasks.last().dayNumber)
    }

    @Test
    fun answerStudyQuestionOffline_retrievesMatchingLinesFromAttachments() {
        val attachments = listOf(
            "الفيزياء" to "ينص قانون أوم على أن شدة التيار تتناسب طردياً مع الجهد الكهربائي عند ثبوت درجة الحرارة."
        )
        val answer = answerStudyQuestionOffline(
            question = "ما هو قانون أوم والجهد الكهربائي؟",
            summary = "",
            attachmentTexts = attachments
        )
        assertTrue(answer.contains("الفيزياء"))
        assertTrue(answer.contains("قانون أوم"))
    }

    @Test
    fun parseStudyPlanJson_parsesWrappedJsonAndNormalizesFields() {
        val raw = """
            ```json
            {
              "summaryMarkdown": "ملخص واضح ومنظم للمادة",
              "keyPoints": ["نقطة أولى", "نقطة ثانية"],
              "schedule": [
                {
                  "dayNumber": 1,
                  "dayLabel": "اليوم 1",
                  "timeSlot": "الصباح",
                  "taskTitle": "قراءة الفصل الأول",
                  "taskDetail": "حل التمارين الأساسية",
                  "durationMinutes": 45
                }
              ]
            }
            ```
        """.trimIndent()

        val parsed = parseStudyPlanJson(raw, fallbackDailyMinutes = 60, fallbackTotalDays = 4)
        assertFalse(parsed.isOfflineFallback)
        assertEquals("ملخص واضح ومنظم للمادة", parsed.summaryMarkdown)
        assertTrue(parsed.keyPointsText.contains("• نقطة أولى"))
        assertEquals(1, parsed.scheduleTasks.size)
        assertEquals(45, parsed.scheduleTasks[0].durationMinutes)
    }

    @Test
    fun parseStudyPlanJson_parsesRichDossierAndMindMapBranches() {
        val raw = """
            {
              "overview": "دراسة شاملة لقوانين الحركة الكلاسيكية وتطبيقاتها.",
              "sections": [
                {
                  "heading": "قانون نيوتن الثاني",
                  "explanation": "يربط بين القوة المحصلة والكتلة والتسارع.",
                  "keyFormulaOrRule": "F = m × a",
                  "example": "جسم كتلته 2 كغ بتسارع 3 م/ث² يتأثر بقوة 6 نيوتن."
                }
              ],
              "glossary": [
                { "term": "التسارع (Acceleration)", "definition": "معدل تغير السرعة بالنسبة للزمن." }
              ],
              "examTips": ["تأكد من تحويل الكتلة إلى الكيلوغرام قبل التعويض."],
              "keyPoints": ["القوة تساوي الكتلة ضرب التسارع"],
              "mindMapRootTitle": "الميكانيكا",
              "mindMapBranches": [
                {
                  "title": "القوى والحركة",
                  "colorTag": "emerald",
                  "subtopics": ["القصور الذاتي", "F = ma", "رد الفعل"]
                }
              ],
              "schedule": []
            }
        """.trimIndent()

        val parsed = parseStudyPlanJson(
            rawJson = raw,
            fallbackDailyMinutes = 60,
            fallbackTotalDays = 2,
            engineLabel = "سحابي • gemini-4-argon"
        )
        assertEquals("سحابي • gemini-4-argon", parsed.engineUsedLabel)
        assertTrue(parsed.summaryMarkdown.contains("قانون نيوتن الثاني"))
        assertTrue(parsed.summaryMarkdown.contains("F = m × a"))
        assertEquals(2, parsed.scheduleTasks.size)

        val dossier = parseStudyDossierJson(parsed.dossierJson)
        assertNotNull(dossier)
        assertEquals(1, dossier?.sections?.size)
        assertEquals("F = m × a", dossier?.sections?.first()?.keyFormulaOrRule)
        assertEquals("الميكانيكا", dossier?.mindMapRootTitle)
        assertEquals(1, dossier?.mindMapBranches?.size)
        assertEquals(3, dossier?.mindMapBranches?.first()?.subtopics?.size)
    }

    @Test
    fun personalRestHelpers_extractTextImageAndSsePayloadsCleanly() {
        assertNull(extractSseDataPayload("data: [DONE]"))
        assertEquals("{\"candidates\":[]}", extractSseDataPayload("data: {\"candidates\":[]}"))

        val restTextJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "مرحباً "},
                      {"text": "بالطالب"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("مرحباً بالطالب", extractTextFromGeminiRestJson(restTextJson))

        val restImageJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"inlineData": {"mimeType": "image/png", "data": "iVBORw0KGgo="}}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        assertEquals("iVBORw0KGgo=", extractBase64ImageFromGeminiRestJson(restImageJson))

        val err = formatRestHttpError(403, """{"error":{"message":"API key not valid"}}""")
        assertTrue(err.contains("403"))
        assertTrue(err.contains("API key not valid"))
    }

    @Test
    fun extractTextFromGeminiRestJson_skipsInternalThoughtParts() {
        val restWithThoughtJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"thought": true, "text": "Let me analyze the question step by step..."},
                      {"text": "{\"overview\": \"ملخص نهائي\"}"},
                      {"thought": false, "text": " متكامل"}
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val extracted = extractTextFromGeminiRestJson(restWithThoughtJson)
        assertFalse(extracted.contains("step by step"))
        assertEquals("{\"overview\": \"ملخص نهائي\"} متكامل", extracted)
    }

    @Test
    fun studyAiTier_usesFlagshipNonLiteNonDeprecatedModels() {
        assertEquals(StudyAiTier.DEEP_REASONING, StudyAiTier.fromKey("unknown_key"))
        StudyAiTier.entries.forEach { tier ->
            val allModels = listOf(tier.firebaseModel, tier.firebaseFallbackModel) + tier.personalModelChain()
            allModels.forEach { modelId ->
                assertFalse("Model $modelId must not use deprecated 2.5", modelId.contains("2.5"))
                assertFalse("Model $modelId must not use -lite", modelId.contains("lite", ignoreCase = true))
            }
        }
        assertEquals("gemini-3.1-pro-preview", StudyAiTier.DEEP_REASONING.personalRestModel)
        assertTrue(StudyAiTier.DEEP_REASONING.personalModelChain().contains("gemini-3-flash-preview"))
    }
}
