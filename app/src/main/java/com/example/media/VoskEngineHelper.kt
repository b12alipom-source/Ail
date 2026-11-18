package com.example.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.data.GeneratedSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private const val TAG = "VoskEngineHelper"
private const val VOSK_SAMPLE_RATE = 16_000f
private const val VOSK_SAMPLE_RATE_INT = 16_000
private const val MAX_CUE_DURATION_MS = 3_800L
private const val MAX_WORDS_PER_CUE = 10
private const val PAUSE_SPLIT_GAP_MS = 650L

@Serializable
data class VoskTimedWord(
    val word: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0,
    val conf: Double = 1.0
)

@Serializable
private data class VoskRecognitionPayload(
    val text: String = "",
    val partial: String = "",
    val result: List<VoskTimedWord> = emptyList()
)

private val voskJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseVoskResultJson(jsonString: String): Pair<String, List<VoskTimedWord>> {
    if (jsonString.isBlank()) return "" to emptyList()
    return runCatching {
        val payload = voskJson.decodeFromString<VoskRecognitionPayload>(jsonString)
        val cleanText = payload.text.trim().ifBlank { payload.partial.trim() }
        val cleanWords = payload.result.filter { it.word.isNotBlank() }
        cleanText to cleanWords
    }.getOrDefault("" to emptyList())
}

internal fun groupVoskWordsIntoSegments(
    words: List<VoskTimedWord>,
    fallbackText: String,
    windowStartMs: Long,
    windowEndMs: Long
): List<GeneratedSegment> {
    val safeWindowEnd = maxOf(windowStartMs + 500L, windowEndMs)
    if (words.isEmpty()) {
        val trimmed = fallbackText.trim()
        if (trimmed.isBlank()) return emptyList()
        return listOf(
            GeneratedSegment(
                startMs = windowStartMs,
                endMs = safeWindowEnd,
                originalText = trimmed,
                translatedText = trimmed
            )
        )
    }

    val segments = mutableListOf<GeneratedSegment>()
    val bucket = mutableListOf<VoskTimedWord>()

    fun flushBucket() {
        if (bucket.isEmpty()) return
        val text = bucket.joinToString(" ") { it.word.trim() }.trim()
        if (text.isNotEmpty()) {
            val firstMs = (windowStartMs + (bucket.first().start * 1000.0).toLong())
                .coerceIn(windowStartMs, safeWindowEnd - 300L)
            val rawEndMs = windowStartMs + (bucket.last().end * 1000.0).toLong()
            val endMs = maxOf(firstMs + 450L, rawEndMs).coerceAtMost(safeWindowEnd)
            segments.add(
                GeneratedSegment(
                    startMs = firstMs,
                    endMs = endMs,
                    originalText = text,
                    translatedText = text
                )
            )
        }
        bucket.clear()
    }

    for (w in words) {
        if (bucket.isNotEmpty()) {
            val bucketStartMs = (bucket.first().start * 1000.0).toLong()
            val prevEndMs = (bucket.last().end * 1000.0).toLong()
            val curStartMs = (w.start * 1000.0).toLong()
            val curEndMs = (w.end * 1000.0).toLong()
            val gapMs = curStartMs - prevEndMs
            val spanMs = curEndMs - bucketStartMs

            if (gapMs > PAUSE_SPLIT_GAP_MS ||
                spanMs > MAX_CUE_DURATION_MS ||
                bucket.size >= MAX_WORDS_PER_CUE
            ) {
                flushBucket()
            }
        }
        bucket.add(w)
    }
    flushBucket()

    return if (segments.isEmpty() && fallbackText.isNotBlank()) {
        listOf(
            GeneratedSegment(
                startMs = windowStartMs,
                endMs = safeWindowEnd,
                originalText = fallbackText.trim(),
                translatedText = fallbackText.trim()
            )
        )
    } else {
        segments
    }
}

object VoskEngineHelper {

    fun openModel(modelDir: File): Model {
        return Model(modelDir.absolutePath)
    }

    fun transcribePcmWindow(
        model: Model,
        window: SpeechPcmWindow
    ): List<GeneratedSegment> {
        val pcm = window.pcm16kMonoBytes
        if (pcm.isEmpty()) return emptyList()

        val recognizer = Recognizer(model, VOSK_SAMPLE_RATE)
        return try {
            recognizer.setWords(true)
            val collectedWords = mutableListOf<VoskTimedWord>()
            val fallbackTextParts = mutableListOf<String>()

            val chunkSize = 4_096
            var offset = 0
            while (offset < pcm.size) {
                val len = minOf(chunkSize, pcm.size - offset)
                val slice = pcm.copyOfRange(offset, offset + len)
                if (recognizer.acceptWaveForm(slice, len)) {
                    val (txt, wds) = parseVoskResultJson(recognizer.result)
                    if (txt.isNotBlank()) fallbackTextParts.add(txt)
                    collectedWords.addAll(wds)
                }
                offset += len
            }

            // Trailing 300ms silence helps flush the final acoustic frame cleanly
            val trailingSilence = ByteArray(9_600)
            recognizer.acceptWaveForm(trailingSilence, trailingSilence.size)

            val (finalTxt, finalWords) = parseVoskResultJson(recognizer.finalResult)
            if (finalTxt.isNotBlank()) fallbackTextParts.add(finalTxt)
            collectedWords.addAll(finalWords)

            groupVoskWordsIntoSegments(
                words = collectedWords,
                fallbackText = fallbackTextParts.joinToString(" ").trim(),
                windowStartMs = window.startMs,
                windowEndMs = window.endMs
            )
        } finally {
            runCatching { recognizer.close() }
        }
    }
}

class VoskLiveMicRecognizer {

    private var recordJob: Job? = null
    @Volatile
    private var shouldStop = false

    @SuppressLint("MissingPermission")
    fun startListening(
        scope: CoroutineScope,
        modelDir: File,
        onRmsChanged: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopAndCancel()
        shouldStop = false

        recordJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            var model: Model? = null
            var recognizer: Recognizer? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    VOSK_SAMPLE_RATE_INT,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4_096)

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    VOSK_SAMPLE_RATE_INT,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    withContext(Dispatchers.Main) {
                        onError("تعذر تهيئة الميكروفون للتسجيل المحلي.")
                    }
                    return@launch
                }
                recorder = audioRecord

                model = VoskEngineHelper.openModel(modelDir)
                val rec = Recognizer(model, VOSK_SAMPLE_RATE)
                recognizer = rec

                val textParts = mutableListOf<String>()
                val readBuf = ByteArray(4_096)
                audioRecord.startRecording()

                while (isActive && !shouldStop) {
                    val bytesRead = audioRecord.read(readBuf, 0, readBuf.size)
                    if (bytesRead > 0) {
                        val rmsNorm = computeNormalizedPcmAmplitude(readBuf, bytesRead)
                        withContext(Dispatchers.Main) {
                            onRmsChanged(rmsNorm)
                        }
                        if (rec.acceptWaveForm(readBuf, bytesRead)) {
                            val (txt, _) = parseVoskResultJson(rec.result)
                            if (txt.isNotBlank()) textParts.add(txt)
                        }
                    }
                }

                runCatching { audioRecord.stop() }
                val (finalTxt, _) = parseVoskResultJson(rec.finalResult)
                if (finalTxt.isNotBlank()) textParts.add(finalTxt)
                val combined = textParts.joinToString(" ").trim()

                withContext(Dispatchers.Main) {
                    if (combined.isBlank()) {
                        onError("لم يُرصد كلام واضح في التسجيل. حاول التحدث بصوت أوضح.")
                    } else {
                        onResult(combined)
                    }
                }
            } catch (_: CancellationException) {
                // Normal cancel
            } catch (e: Exception) {
                Log.e(TAG, "Vosk live mic recognition failed", e)
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "تعذر التعرف الصوتي المحلي عبر Vosk.")
                }
            } finally {
                runCatching { recognizer?.close() }
                runCatching { model?.close() }
                runCatching { recorder?.release() }
            }
        }
    }

    fun stopListening() {
        shouldStop = true
    }

    fun stopAndCancel() {
        shouldStop = true
        recordJob?.cancel()
        recordJob = null
    }

    private fun computeNormalizedPcmAmplitude(pcmBytes: ByteArray, length: Int): Float {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0.08f
        val bb = ByteBuffer.wrap(pcmBytes, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val s = bb.getShort().toDouble()
            sumSq += s * s
        }
        val rms = sqrt(sumSq / sampleCount)
        return (rms / 6000.0).toFloat().coerceIn(0.08f, 1.0f)
    }
}
