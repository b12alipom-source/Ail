package com.example.media

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.ai.OfflineTranslationEngine
import com.example.data.GeneratedSegment
import com.example.data.LanguageOption
import com.example.data.SupportedLanguages
import com.example.data.VideoTranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.sqrt

private const val TAG = "OnDeviceVideoTranscriber"
private const val TARGET_SAMPLE_RATE = 16_000
private const val FRAME_SAMPLES_100MS = TARGET_SAMPLE_RATE / 10 // 1600 samples = 100ms
private const val MACRO_WINDOW_DURATION_US = 60_000_000L // decode audio in 60s batches to keep RAM low
private const val MAX_WINDOW_DURATION_MS = 9_500L
private const val MIN_WINDOW_DURATION_MS = 800L
private const val SILENCE_MERGE_FRAMES = 6 // 600ms

data class SpeechPcmWindow(
    val startMs: Long,
    val endMs: Long,
    val pcm16kMonoBytes: ByteArray
)

class LocalVideoFallbackException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class OnDeviceVideoTranscriber(
    private val context: Context,
    private val offlineTranslator: OfflineTranslationEngine
) {

    suspend fun transcribeAndTranslateLocally(
        uri: Uri,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        resumeFromMs: Long = 0L,
        onStageUpdate: (stageText: String, current: Int, total: Int) -> Unit
    ): VideoTranslationResult {
        // 1. Check for embedded subtitle / timed-text track inside the video container (works on all APIs)
        onStageUpdate("جاري فحص مسارات الترجمة المدمجة في الفيديو…", 1, 3)
        val embeddedSegments = withContext(Dispatchers.IO) {
            tryExtractEmbeddedSubtitles(context, uri)
        }.filter { it.endMs > resumeFromMs }

        if (embeddedSegments.isNotEmpty()) {
            onStageUpdate("جاري ترجمة المسار النصي المدمج محلياً…", 2, 3)
            val resolvedSourceCode = resolveDetectedCodeFromText(
                sampleText = embeddedSegments.take(6).joinToString(" ") { it.originalText },
                requestedSource = sourceLanguage
            )
            val translatedLines = offlineTranslator.translateBatch(
                lines = embeddedSegments.map { it.originalText },
                sourceCode = resolvedSourceCode,
                targetCode = targetLanguage.code
            )
            val finalSegments = embeddedSegments.mapIndexed { idx, seg ->
                seg.copy(translatedText = translatedLines.getOrElse(idx) { seg.originalText })
            }
            val srcOpt = SupportedLanguages.findByCode(resolvedSourceCode)
            return VideoTranslationResult(
                detectedLanguageCode = resolvedSourceCode,
                detectedLanguageNameArabic = "${srcOpt.arabicName} (محلي)",
                summary = "تم استخلاص وترجمة النص المدمج محلياً على جهازك (${finalSegments.size} مقطعاً).",
                segments = finalSegments
            )
        }

        // 2. On Android 13+ (API 33), pipe decoded 16kHz PCM audio into SpeechRecognizer EXTRA_AUDIO_SOURCE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw LocalVideoFallbackException(
                "التفريغ الصوتي التلقائي للفيديو أوفلاين يتطلب أندرويد 13 أو أحدث، أو استيراد ملف SRT/VTT مرفق."
            )
        }

        val recognitionLangTag = resolveSpeechLanguageTag(sourceLanguage, targetLanguage)
        val effectiveSourceCode = if (sourceLanguage.code == SupportedLanguages.autoDetect.code) {
            recognitionLangTag.substringBefore('-').lowercase()
        } else {
            sourceLanguage.code
        }

        val allRecognized = mutableListOf<GeneratedSegment>()
        var macroOffsetUs = (resumeFromMs.coerceAtLeast(0L)) * 1000L
        var macroIndex = 0

        while (true) {
            val batch = withContext(Dispatchers.IO) {
                decodeAudioBatchTo16kMono(
                    context = context,
                    uri = uri,
                    startTimeUs = macroOffsetUs,
                    maxBatchDurationUs = MACRO_WINDOW_DURATION_US
                )
            } ?: break

            macroIndex++
            val windows = segmentActiveSpeechWindows(
                samples16k = batch.samples,
                baseOffsetMs = batch.actualStartMs
            )

            if (windows.isNotEmpty()) {
                var preferOffline = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                windows.forEachIndexed { idx, window ->
                    onStageUpdate(
                        "التفريغ الصوتي المحلي (دفعة $macroIndex: مقطع ${idx + 1}/${windows.size})…",
                        idx + 1,
                        windows.size
                    )
                    val outcome = recognizePcmWindowApi33(
                        context = context,
                        window = window,
                        languageTag = recognitionLangTag,
                        preferOffline = preferOffline
                    )
                    if (outcome.shouldFallbackToSystemRecognizer && preferOffline) {
                        preferOffline = false
                        val retry = recognizePcmWindowApi33(
                            context = context,
                            window = window,
                            languageTag = recognitionLangTag,
                            preferOffline = false
                        )
                        if (!retry.text.isNullOrBlank()) {
                            allRecognized.add(
                                GeneratedSegment(
                                    startMs = window.startMs,
                                    endMs = window.endMs,
                                    originalText = retry.text.trim(),
                                    translatedText = retry.text.trim()
                                )
                            )
                        } else if (retry.fatalErrorMessage != null && allRecognized.isEmpty()) {
                            throw LocalVideoFallbackException(retry.fatalErrorMessage)
                        }
                    } else if (!outcome.text.isNullOrBlank()) {
                        allRecognized.add(
                            GeneratedSegment(
                                startMs = window.startMs,
                                endMs = window.endMs,
                                originalText = outcome.text.trim(),
                                translatedText = outcome.text.trim()
                            )
                        )
                    } else if (outcome.fatalErrorMessage != null && allRecognized.isEmpty()) {
                        throw LocalVideoFallbackException(outcome.fatalErrorMessage)
                    }
                }
            }

            if (batch.reachedEndOfTrack || batch.nextStartUs <= macroOffsetUs) {
                break
            }
            macroOffsetUs = batch.nextStartUs
        }

        if (allRecognized.isEmpty()) {
            throw LocalVideoFallbackException(
                "لم يتمكن محرك الصوت المحلي من رصد كلام واضح في الفيديو. تأكد من تثبيت حزمة اللغة الصوتية على الجهاز أو استورد ملف SRT."
            )
        }

        onStageUpdate("جاري ترجمة النص المفرّغ محلياً عبر ML Kit…", allRecognized.size, allRecognized.size)
        val detectedCode = resolveDetectedCodeFromText(
            sampleText = allRecognized.take(6).joinToString(" ") { it.originalText },
            requestedSource = if (sourceLanguage.code == SupportedLanguages.autoDetect.code) {
                SupportedLanguages.findByCode(effectiveSourceCode)
            } else {
                sourceLanguage
            }
        )

        val translatedLines = offlineTranslator.translateBatch(
            lines = allRecognized.map { it.originalText },
            sourceCode = detectedCode,
            targetCode = targetLanguage.code
        )

        val finalSegments = allRecognized.mapIndexed { idx, seg ->
            seg.copy(translatedText = translatedLines.getOrElse(idx) { seg.originalText })
        }
        val detectedOpt = SupportedLanguages.findByCode(detectedCode)
        return VideoTranslationResult(
            detectedLanguageCode = detectedCode,
            detectedLanguageNameArabic = "${detectedOpt.arabicName} (محلي)",
            summary = "تم التفريغ الصوتي والترجمة محلياً على جهازك (${finalSegments.size} مقطعاً) بدون الاعتماد على السحابة.",
            segments = finalSegments
        )
    }

    private suspend fun resolveDetectedCodeFromText(
        sampleText: String,
        requestedSource: LanguageOption
    ): String {
        return if (requestedSource.code == SupportedLanguages.autoDetect.code) {
            offlineTranslator.detectLanguageCode(sampleText)
        } else {
            requestedSource.code
        }
    }

    private data class RecognitionWindowOutcome(
        val text: String? = null,
        val fatalErrorMessage: String? = null,
        val shouldFallbackToSystemRecognizer: Boolean = false
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognizePcmWindowApi33(
        context: Context,
        window: SpeechPcmWindow,
        languageTag: String,
        preferOffline: Boolean
    ): RecognitionWindowOutcome = withContext(Dispatchers.Main.immediate) {
        val windowDurationMs = (window.endMs - window.startMs).coerceAtLeast(800L)
        val pipe = ParcelFileDescriptor.createPipe()
        val readPfd = pipe[0]
        val writePfd = pipe[1]

        val recognizer = try {
            if (preferOffline && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            runCatching { readPfd.close() }
            runCatching { writePfd.close() }
            return@withContext RecognitionWindowOutcome(
                fatalErrorMessage = "تعذر تشغيل محرك التعرف الصوتي المحلي على هذا الجهاز.",
                shouldFallbackToSystemRecognizer = preferOffline
            )
        }

        val feederJob = launch(Dispatchers.IO) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { out ->
                    val chunkBytes = 3_200 // 100ms of 16kHz 16-bit mono PCM
                    val data = window.pcm16kMonoBytes
                    var offset = 0
                    while (offset < data.size && isActive) {
                        val toWrite = minOf(chunkBytes, data.size - offset)
                        out.write(data, offset, toWrite)
                        out.flush()
                        offset += toWrite
                        if (offset < data.size) {
                            delay(92L)
                        }
                    }
                    // Trailing 400ms silence helps the recognizer finalize the trailing words cleanly
                    val trailingSilence = ByteArray(12_800)
                    var sOff = 0
                    while (sOff < trailingSilence.size && isActive) {
                        val toWrite = minOf(chunkBytes, trailingSilence.size - sOff)
                        out.write(trailingSilence, sOff, toWrite)
                        out.flush()
                        sOff += toWrite
                        delay(80L)
                    }
                }
            } catch (_: CancellationException) {
                // normal cancellation
            } catch (e: Exception) {
                Log.w(TAG, "PCM pipe write ended early", e)
            } finally {
                runCatching { writePfd.close() }
            }
        }

        try {
            val outcome = withTimeoutOrNull(windowDurationMs + 9_000L) {
                suspendCancellableCoroutine { cont ->
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val best = matches?.firstOrNull()?.trim()
                            if (cont.isActive) {
                                cont.resume(RecognitionWindowOutcome(text = best))
                            }
                        }

                        override fun onError(error: Int) {
                            val result = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                    RecognitionWindowOutcome(text = null)

                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
                                    RecognitionWindowOutcome(
                                        fatalErrorMessage = "حزمة التعرف الصوتي للغة ($languageTag) غير مثبتة محلياً. ثبّتها من إعدادات الصوت في أندرويد أو استخدم ملف SRT.",
                                        shouldFallbackToSystemRecognizer = true
                                    )

                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                    RecognitionWindowOutcome(
                                        fatalErrorMessage = "يحتاج محرك الصوت المحلي إلى صلاحية الميكروفون من تبويب المترجم الصوتي أو الإعدادات."
                                    )

                                else ->
                                    RecognitionWindowOutcome(
                                        text = null,
                                        shouldFallbackToSystemRecognizer = preferOffline
                                    )
                            }
                            if (cont.isActive) cont.resume(result)
                        }
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readPfd)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, TARGET_SAMPLE_RATE)
                    }
                    try {
                        recognizer.startListening(intent)
                    } catch (e: SecurityException) {
                        if (cont.isActive) {
                            cont.resume(
                                RecognitionWindowOutcome(
                                    fatalErrorMessage = "يرجى السماح بصلاحية الصوت لتشغيل التفريغ المحلي."
                                )
                            )
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resume(
                                RecognitionWindowOutcome(
                                    text = null,
                                    shouldFallbackToSystemRecognizer = true
                                )
                            )
                        }
                    }
                }
            }
            outcome ?: RecognitionWindowOutcome(text = null)
        } finally {
            feederJob.cancel()
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
            runCatching { readPfd.close() }
            runCatching { writePfd.close() }
        }
    }

    private fun tryExtractEmbeddedSubtitles(context: Context, uri: Uri): List<GeneratedSegment> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var subtitleTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.lowercase() ?: continue
                if (mime.contains("subrip") ||
                    mime.contains("vtt") ||
                    mime.contains("x-ssa") ||
                    mime.contains("ttml") ||
                    mime == "text/plain"
                ) {
                    subtitleTrack = i
                    break
                }
            }
            if (subtitleTrack == -1) return emptyList()

            extractor.selectTrack(subtitleTrack)
            val buffer = ByteBuffer.allocate(64 * 1024)
            val rawCues = mutableListOf<Pair<Long, String>>()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val timeMs = (extractor.sampleTime / 1000L).coerceAtLeast(0L)
                val bytes = ByteArray(size)
                buffer.position(0)
                buffer.get(bytes, 0, size)
                val cleaned = String(bytes, Charsets.UTF_8)
                    .replace(Regex("<[^>]+>"), "")
                    .replace(Regex("\\{\\\\[^}]+\\}"), "")
                    .trim()
                if (cleaned.isNotEmpty()) {
                    rawCues.add(timeMs to cleaned)
                }
                extractor.advance()
            }

            rawCues.mapIndexed { idx, (startMs, text) ->
                val nextStart = rawCues.getOrNull(idx + 1)?.first
                val endMs = if (nextStart != null && nextStart > startMs) {
                    minOf(nextStart, startMs + 5_000L)
                } else {
                    startMs + 3_000L
                }
                GeneratedSegment(
                    startMs = startMs,
                    endMs = endMs,
                    originalText = text,
                    translatedText = text
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedded subtitle probe skipped", e)
            emptyList()
        } finally {
            runCatching { extractor.release() }
        }
    }

    private data class DecodedAudioBatch(
        val samples: ShortArray,
        val actualStartMs: Long,
        val nextStartUs: Long,
        val reachedEndOfTrack: Boolean
    )

    private fun decodeAudioBatchTo16kMono(
        context: Context,
        uri: Uri,
        startTimeUs: Long,
        maxBatchDurationUs: Long
    ): DecodedAudioBatch? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || inputFormat == null) {
                throw LocalVideoFallbackException("لا يحتوي مقطع الفيديو على مسار صوتي قابل للتفريغ.")
            }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            extractor.selectTrack(audioTrackIndex)
            if (startTimeUs > 0L) {
                extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44_100
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1
            var isFloatPcm = inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT

            val bufferInfo = MediaCodec.BufferInfo()
            val accumulated = mutableListOf<ShortArray>()
            var totalSamples = 0
            var actualStartUs = -1L
            var lastPtsUs = startTimeUs
            var inputDone = false
            var outputDone = false
            var reachedTrackEnd = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val ptsUs = extractor.sampleTime
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            reachedTrackEnd = true
                        } else if (actualStartUs >= 0L && ptsUs - actualStartUs >= maxBatchDurationUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                            reachedTrackEnd = false
                            lastPtsUs = ptsUs
                        } else {
                            if (actualStartUs < 0L) actualStartUs = ptsUs.coerceAtLeast(0L)
                            lastPtsUs = ptsUs.coerceAtLeast(0L)
                            codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        isFloatPcm = outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(bytes)
                            val converted = downmixAndResampleTo16kMono(
                                pcmBytes = bytes,
                                byteOffset = 0,
                                byteLength = bytes.size,
                                sampleRate = sampleRate,
                                channelCount = channelCount,
                                isFloatPcm = isFloatPcm
                            )
                            if (converted.isNotEmpty()) {
                                accumulated.add(converted)
                                totalSamples += converted.size
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (totalSamples == 0) return null
            val merged = ShortArray(totalSamples)
            var cursor = 0
            accumulated.forEach { chunk ->
                System.arraycopy(chunk, 0, merged, cursor, chunk.size)
                cursor += chunk.size
            }

            return DecodedAudioBatch(
                samples = merged,
                actualStartMs = (actualStartUs.coerceAtLeast(0L)) / 1000L,
                nextStartUs = lastPtsUs,
                reachedEndOfTrack = reachedTrackEnd
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resolveSpeechLanguageTag(source: LanguageOption, target: LanguageOption): String {
        val code = if (source.code == SupportedLanguages.autoDetect.code) {
            if (target.code == "ar") "en" else "ar"
        } else {
            source.code.lowercase()
        }
        return when (code) {
            "ar" -> "ar-SA"
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "de" -> "de-DE"
            "tr" -> "tr-TR"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "zh" -> "zh-CN"
            "hi" -> "hi-IN"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "ru" -> "ru-RU"
            else -> "en-US"
        }
    }
}

internal fun downmixAndResampleTo16kMono(
    pcmBytes: ByteArray,
    byteOffset: Int,
    byteLength: Int,
    sampleRate: Int,
    channelCount: Int,
    isFloatPcm: Boolean
): ShortArray {
    if (byteLength <= 0 || sampleRate <= 0 || channelCount <= 0) return ShortArray(0)
    val bytesPerSample = if (isFloatPcm) 4 else 2
    val frameBytes = bytesPerSample * channelCount
    val frameCount = byteLength / frameBytes
    if (frameCount <= 0) return ShortArray(0)

    val mono = FloatArray(frameCount)
    val bb = ByteBuffer.wrap(pcmBytes, byteOffset, frameCount * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until frameCount) {
        var sum = 0f
        for (ch in 0 until channelCount) {
            val s = if (isFloatPcm) {
                bb.getFloat().coerceIn(-1f, 1f) * 32767f
            } else {
                bb.getShort().toFloat()
            }
            sum += s
        }
        mono[i] = sum / channelCount
    }

    if (sampleRate == TARGET_SAMPLE_RATE) {
        return ShortArray(frameCount) { idx ->
            mono[idx].toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    val outLength = ((frameCount.toLong() * TARGET_SAMPLE_RATE) / sampleRate).toInt().coerceAtLeast(1)
    val ratio = frameCount.toDouble() / outLength.toDouble()
    return ShortArray(outLength) { outIdx ->
        val srcPos = outIdx * ratio
        val idx0 = srcPos.toInt().coerceIn(0, frameCount - 1)
        val idx1 = (idx0 + 1).coerceIn(0, frameCount - 1)
        val frac = (srcPos - idx0).toFloat()
        val interpolated = mono[idx0] * (1f - frac) + mono[idx1] * frac
        interpolated.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

internal fun segmentActiveSpeechWindows(
    samples16k: ShortArray,
    baseOffsetMs: Long = 0L
): List<SpeechPcmWindow> {
    if (samples16k.size < FRAME_SAMPLES_100MS * 4) return emptyList()

    val frameCount = samples16k.size / FRAME_SAMPLES_100MS
    val frameRms = DoubleArray(frameCount) { f ->
        val start = f * FRAME_SAMPLES_100MS
        var sumSq = 0.0
        for (i in start until start + FRAME_SAMPLES_100MS) {
            val v = samples16k[i].toDouble()
            sumSq += v * v
        }
        sqrt(sumSq / FRAME_SAMPLES_100MS)
    }

    val maxRms = frameRms.maxOrNull() ?: 0.0
    if (maxRms < 60.0) return emptyList()
    val threshold = minOf(280.0, maxOf(110.0, maxRms * 0.14))

    val activeRanges = mutableListOf<IntRange>()
    var segStart = -1
    var silentRun = 0
    for (f in 0 until frameCount) {
        if (frameRms[f] >= threshold) {
            if (segStart == -1) segStart = (f - 2).coerceAtLeast(0)
            silentRun = 0
        } else if (segStart != -1) {
            silentRun++
            if (silentRun >= SILENCE_MERGE_FRAMES) {
                val segEnd = (f - silentRun + 2).coerceAtMost(frameCount - 1)
                activeRanges.add(segStart..segEnd)
                segStart = -1
                silentRun = 0
            }
        }
    }
    if (segStart != -1) {
        activeRanges.add(segStart..(frameCount - 1))
    }

    if (activeRanges.isEmpty()) return emptyList()

    val maxFramesPerWindow = (MAX_WINDOW_DURATION_MS / 100L).toInt()
    val minFramesPerWindow = (MIN_WINDOW_DURATION_MS / 100L).toInt()
    val boundedRanges = mutableListOf<IntRange>()

    for (range in activeRanges) {
        var curStart = range.first
        while (range.last - curStart + 1 > maxFramesPerWindow) {
            val searchStart = curStart + (maxFramesPerWindow * 2) / 3
            val searchEnd = (curStart + maxFramesPerWindow).coerceAtMost(range.last)
            var bestSplit = searchEnd
            var lowestRms = Double.MAX_VALUE
            for (f in searchStart..searchEnd) {
                if (frameRms[f] < lowestRms) {
                    lowestRms = frameRms[f]
                    bestSplit = f
                }
            }
            boundedRanges.add(curStart..bestSplit)
            curStart = (bestSplit + 1).coerceAtMost(range.last)
        }
        if (range.last - curStart + 1 >= minFramesPerWindow) {
            boundedRanges.add(curStart..range.last)
        }
    }

    return boundedRanges.map { range ->
        val sampleStart = (range.first * FRAME_SAMPLES_100MS).coerceIn(0, samples16k.size - 1)
        val sampleEnd = ((range.last + 1) * FRAME_SAMPLES_100MS).coerceIn(sampleStart + 1, samples16k.size)
        val len = sampleEnd - sampleStart
        val byteBuf = ByteBuffer.allocate(len * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in sampleStart until sampleEnd) {
            byteBuf.putShort(samples16k[i])
        }
        SpeechPcmWindow(
            startMs = baseOffsetMs + (range.first * 100L),
            endMs = baseOffsetMs + ((range.last + 1) * 100L),
            pcm16kMonoBytes = byteBuf.array()
        )
    }
}
