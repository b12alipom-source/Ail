package com.example.media

import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

private const val TAG = "AudioExtractor"
private const val MAX_SINGLE_INLINE_BYTES = 14 * 1024 * 1024
private const val CHUNK_DURATION_US = 45_000_000L // 45 seconds per chunk for fast, reliable upload & inference
private const val MAX_CHUNK_RAW_BYTES = 2 * 1024 * 1024 // 2 MB ceiling per chunk

data class AudioChunk(
    val bytes: ByteArray,
    val mimeType: String,
    val startOffsetMs: Long,
    val durationMs: Long
)

data class PreparedChunkedMedia(
    val title: String,
    val totalDurationMs: Long,
    val chunks: List<AudioChunk>
)

class MediaPreparationException(message: String, cause: Throwable? = null) : Exception(message, cause)

object AudioExtractor {

    suspend fun prepareChunkedForTranslation(
        context: Context,
        uri: Uri
    ): PreparedChunkedMedia = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val title = resolveDisplayName(context, uri)
        val durationMs = resolveDurationMs(context, uri)
        val originalMime = context.contentResolver.getType(uri) ?: "video/mp4"

        // Extract AAC audio into time-windowed chunks (handles both short & very long videos)
        try {
            val chunks = extractChunkedAudioTracks(context, uri, durationMs)
            if (chunks.isNotEmpty()) {
                Log.d(TAG, "Prepared ${chunks.size} compact audio chunk(s), totalDuration=${durationMs}ms")
                return@withContext PreparedChunkedMedia(
                    title = title,
                    totalDurationMs = durationMs,
                    chunks = chunks
                )
            }
        } catch (e: MediaPreparationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Chunked audio extraction failed, checking raw media fallback", e)
        }

        if (!hasAudioTrack(context, uri)) {
            throw MediaPreparationException("لا يحتوي مقطع الفيديو على مسار صوتي قابل للتحليل.")
        }

        val rawSize = resolveFileSize(context, uri)
        if (rawSize > MAX_SINGLE_INLINE_BYTES) {
            throw MediaPreparationException(
                "تنسيق الصوت غير مدعوم للتقسيم وحجم الفيديو يتجاوز 14 ميجابايت."
            )
        }

        val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = stream.readBytes()
            if (buffer.size > MAX_SINGLE_INLINE_BYTES) {
                throw MediaPreparationException("حجم الملف يتجاوز الحد المسموح (14 ميجابايت).")
            }
            buffer
        } ?: throw MediaPreparationException("تعذر قراءة ملف الفيديو المختار.")

        PreparedChunkedMedia(
            title = title,
            totalDurationMs = durationMs,
            chunks = listOf(
                AudioChunk(
                    bytes = rawBytes,
                    mimeType = originalMime,
                    startOffsetMs = 0L,
                    durationMs = durationMs
                )
            )
        )
    }

    private fun extractChunkedAudioTracks(
        context: Context,
        uri: Uri,
        totalDurationMs: Long
    ): List<AudioChunk> {
        val extractor = MediaExtractor()
        val tempFiles = mutableListOf<File>()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                throw MediaPreparationException("لا يحتوي مقطع الفيديو على مسار صوتي قابل للتحليل.")
            }

            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (!audioMime.equals("audio/mp4a-latm", ignoreCase = true)) {
                Log.d(TAG, "Audio mime $audioMime is not AAC; using fallback path")
                return emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            val chunks = mutableListOf<AudioChunk>()
            var currentFile = File(context.cacheDir, "bayan_chunk_${UUID.randomUUID()}.m4a")
            tempFiles.add(currentFile)
            muxer = MediaMuxer(currentFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            var chunkStartUs = -1L
            var lastSampleUs = 0L
            var bytesInChunk = 0L
            var samplesInChunk = 0

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                if (chunkStartUs < 0L) {
                    chunkStartUs = sampleTimeUs
                }

                val elapsedChunkUs = sampleTimeUs - chunkStartUs
                if (samplesInChunk > 0 &&
                    (elapsedChunkUs >= CHUNK_DURATION_US || bytesInChunk + sampleSize >= MAX_CHUNK_RAW_BYTES)
                ) {
                    runCatching { muxer?.stop() }
                    runCatching { muxer?.release() }
                    muxer = null

                    if (currentFile.exists() && currentFile.length() > 0L) {
                        val chunkDurMs = ((lastSampleUs - chunkStartUs) / 1000L).coerceAtLeast(1000L)
                        chunks.add(
                            AudioChunk(
                                bytes = currentFile.readBytes(),
                                mimeType = "audio/mp4",
                                startOffsetMs = chunkStartUs / 1000L,
                                durationMs = chunkDurMs
                            )
                        )
                    }
                    currentFile.delete()

                    currentFile = File(context.cacheDir, "bayan_chunk_${UUID.randomUUID()}.m4a")
                    tempFiles.add(currentFile)
                    muxer = MediaMuxer(
                        currentFile.absolutePath,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    )
                    muxerTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()

                    chunkStartUs = sampleTimeUs
                    bytesInChunk = 0L
                    samplesInChunk = 0
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = (sampleTimeUs - chunkStartUs).coerceAtLeast(0L)
                @Suppress("DEPRECATION")
                bufferInfo.flags = extractor.sampleFlags
                muxer?.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                lastSampleUs = sampleTimeUs
                bytesInChunk += sampleSize
                samplesInChunk++
                extractor.advance()
            }

            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null

            if (samplesInChunk > 0 && currentFile.exists() && currentFile.length() > 0L) {
                val startOffsetMs = (chunkStartUs.coerceAtLeast(0L)) / 1000L
                val fallbackChunkDur = if (totalDurationMs > startOffsetMs) {
                    totalDurationMs - startOffsetMs
                } else {
                    ((lastSampleUs - chunkStartUs.coerceAtLeast(0L)) / 1000L).coerceAtLeast(1000L)
                }
                chunks.add(
                    AudioChunk(
                        bytes = currentFile.readBytes(),
                        mimeType = "audio/mp4",
                        startOffsetMs = startOffsetMs,
                        durationMs = fallbackChunkDur
                    )
                )
            }

            return chunks
        } finally {
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            tempFiles.forEach { file ->
                if (file.exists()) file.delete()
            }
        }
    }

    private fun hasAudioTrack(context: Context, uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).any { idx ->
                val mime = extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed probing tracks", e)
            true
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val raw = cursor.getString(nameIndex)
                        if (!raw.isNullOrBlank()) {
                            return raw.substringBeforeLast('.')
                        }
                    }
                }
            }
        return "مقطع فيديو"
    }

    private fun resolveFileSize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
        return 0L
    }

    private fun resolveDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading video duration", e)
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
