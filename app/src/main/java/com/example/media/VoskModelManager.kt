package com.example.media

import android.content.Context
import android.util.Log
import com.example.data.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

private const val TAG = "VoskModelManager"
private const val MODEL_LIST_JSON_URL = "https://alphacephei.com/vosk/models/model-list.json"

@Serializable
data class VoskCatalogItem(
    val lang: String = "",
    val lang_text: String = "",
    val name: String = "",
    val obsolete: String = "false",
    val size: Long = 0L,
    val size_text: String = "",
    val type: String = "",
    val url: String = ""
)

data class VoskModelDescriptor(
    val langCode: String,
    val arabicName: String,
    val modelName: String,
    val downloadUrl: String,
    val sizeLabel: String
)

data class VoskDownloadProgress(
    val langCode: String,
    val progressPercent: Int?,
    val statusLabel: String
)

class VoskModelManager(private val context: Context) {

    private val mutex = Mutex()
    private val modelsRootDir: File
        get() = File(context.filesDir, "vosk-models")

    @Volatile
    private var cachedLiveCatalog: List<VoskCatalogItem>? = null

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getSupportedCatalog(): List<VoskModelDescriptor> = curatedCatalog

    fun getDescriptorForLanguage(langCode: String): VoskModelDescriptor {
        val normalized = normalizeLanguageCode(langCode)
        return curatedCatalog.firstOrNull { it.langCode == normalized }
            ?: curatedCatalog.first { it.langCode == "en" }
    }

    fun getDownloadedLanguageCodes(): Set<String> {
        val root = modelsRootDir
        if (!root.exists()) return emptySet()
        return root.listFiles()
            ?.filter { it.isDirectory && isValidModelDirectory(it) }
            ?.map { it.name.lowercase() }
            ?.toSet()
            ?: emptySet()
    }

    fun getModelDirectoryIfReady(langCode: String): File? {
        val dir = File(modelsRootDir, normalizeLanguageCode(langCode))
        return if (isValidModelDirectory(dir)) dir else null
    }

    fun resolvePreferredInstalledLanguage(targetCode: String): String? {
        val installed = getDownloadedLanguageCodes()
        if (installed.isEmpty()) return null
        if (targetCode.equals("ar", ignoreCase = true) && installed.contains("en")) return "en"
        return installed.firstOrNull { !it.equals(targetCode, ignoreCase = true) }
            ?: installed.firstOrNull()
    }

    suspend fun ensureModelDownloaded(
        langCode: String,
        onProgress: (VoskDownloadProgress) -> Unit = {}
    ): Result<File> = mutex.withLock {
        val normalized = normalizeLanguageCode(langCode)
        val existing = getModelDirectoryIfReady(normalized)
        if (existing != null) {
            return@withLock Result.success(existing)
        }

        return@withLock runCatching {
            withContext(Dispatchers.IO) {
                val descriptor = resolveLiveOrCuratedDescriptor(normalized)
                val langNameAr = SupportedLanguages.findByCode(normalized).arabicName
                onProgress(
                    VoskDownloadProgress(
                        langCode = normalized,
                        progressPercent = 0,
                        statusLabel = "جاري تنزيل نموذج الكلام ($langNameAr • ${descriptor.sizeLabel})…"
                    )
                )

                val root = modelsRootDir.apply { mkdirs() }
                val tempZip = File(context.cacheDir, "vosk_${normalized}_${System.currentTimeMillis()}.zip.tmp")
                val stagingDir = File(root, "${normalized}_staging_${System.currentTimeMillis()}")
                val finalDir = File(root, normalized)

                try {
                    downloadArchiveWithProgress(
                        urlString = descriptor.downloadUrl,
                        destination = tempZip
                    ) { readBytes, totalBytes ->
                        val pct = if (totalBytes > 0L) {
                            ((readBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                        } else null
                        val mbRead = String.format(java.util.Locale.US, "%.1f", readBytes / (1024.0 * 1024.0))
                        val label = if (pct != null) {
                            "تنزيل نموذج $langNameAr ($pct% • $mbRead MB)…"
                        } else {
                            "تنزيل نموذج $langNameAr ($mbRead MB)…"
                        }
                        onProgress(VoskDownloadProgress(normalized, pct, label))
                    }

                    coroutineContext.ensureActive()
                    onProgress(
                        VoskDownloadProgress(
                            langCode = normalized,
                            progressPercent = 100,
                            statusLabel = "جاري فك ضغط وتجهيز نموذج $langNameAr…"
                        )
                    )

                    stagingDir.deleteRecursively()
                    stagingDir.mkdirs()
                    extractVoskZipSafely(tempZip, stagingDir)

                    if (!isValidModelDirectory(stagingDir)) {
                        throw IOException("محتوى أرشيف النموذج الصوتي غير مكتمل للغة $langNameAr.")
                    }

                    if (finalDir.exists()) {
                        finalDir.deleteRecursively()
                    }
                    if (!stagingDir.renameTo(finalDir)) {
                        stagingDir.copyRecursively(finalDir, overwrite = true)
                        stagingDir.deleteRecursively()
                    }
                    finalDir
                } catch (e: Exception) {
                    stagingDir.deleteRecursively()
                    throw e
                } finally {
                    tempZip.delete()
                }
            }
        }
    }

    suspend fun deleteModel(langCode: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = File(modelsRootDir, normalizeLanguageCode(langCode))
            if (dir.exists()) dir.deleteRecursively() else false
        }
    }

    private suspend fun resolveLiveOrCuratedDescriptor(langCode: String): VoskModelDescriptor {
        val curated = getDescriptorForLanguage(langCode)
        val liveList = fetchLiveCatalogOrNull() ?: return curated
        val best = selectBestCatalogModelForLanguage(liveList, langCode) ?: return curated
        if (!best.url.startsWith("https://")) return curated
        return curated.copy(
            modelName = best.name.ifBlank { curated.modelName },
            downloadUrl = best.url,
            sizeLabel = best.size_text.ifBlank { curated.sizeLabel }
        )
    }

    private fun fetchLiveCatalogOrNull(): List<VoskCatalogItem>? {
        cachedLiveCatalog?.let { return it }
        return runCatching {
            val conn = (URL(MODEL_LIST_JSON_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = jsonParser.decodeFromString<List<VoskCatalogItem>>(body)
                    cachedLiveCatalog = parsed
                    parsed
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to fetch live Vosk model-list.json; using curated URLs", err)
        }.getOrNull()
    }

    private suspend fun downloadArchiveWithProgress(
        urlString: String,
        destination: File,
        onBytes: (Long, Long) -> Unit
    ) {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("رابط تنزيل غير مكتمل (HTTP $code).")
                    if (!location.startsWith("https://")) {
                        throw IOException("تم حظر تحويل غير مشفر أثناء التنزيل.")
                    }
                    currentUrl = location
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("تعذر تنزيل النموذج الصوتي (HTTP $code).")
                }

                val totalBytes = conn.contentLengthLong
                val buf = ByteArray(32 * 1024)
                var downloaded = 0L
                var lastReported = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    BufferedOutputStream(FileOutputStream(destination)).use { output ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            if (downloaded - lastReported >= 256 * 1024L || (totalBytes > 0 && downloaded >= totalBytes)) {
                                lastReported = downloaded
                                onBytes(downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("تجاوز التنزيل عدد التحويلات المسموح.")
    }

    private fun extractVoskZipSafely(zipFile: File, targetDir: File) {
        val buf = ByteArray(16 * 1024)
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val stripped = stripTopArchiveDirectory(entry.name)
                if (stripped.isBlank()) {
                    zis.closeEntry()
                    continue
                }
                val destFile = File(targetDir, stripped)
                if (!isSafeZipDestination(targetDir, destFile)) {
                    throw SecurityException("مسار أرشيف غير آمن: ${entry.name}")
                }
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(destFile)).use { out ->
                        while (true) {
                            val count = zis.read(buf)
                            if (count < 0) break
                            out.write(buf, 0, count)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun isValidModelDirectory(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        val hasConf = File(dir, "conf").isDirectory
        val hasAm = File(dir, "am").isDirectory
        return hasConf && hasAm
    }

    private fun normalizeLanguageCode(raw: String): String {
        val code = raw.trim().lowercase().substringBefore('-')
        return if (code == "cn") "zh" else code
    }

    companion object {
        private val curatedCatalog: List<VoskModelDescriptor> = listOf(
            VoskModelDescriptor(
                langCode = "en",
                arabicName = "الإنجليزية",
                modelName = "vosk-model-small-en-us-0.15",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                sizeLabel = "~40 MB"
            ),
            VoskModelDescriptor(
                langCode = "ar",
                arabicName = "العربية",
                modelName = "vosk-model-ar-mgb2-0.4",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-mgb2-0.4.zip",
                sizeLabel = "~318 MB"
            ),
            VoskModelDescriptor(
                langCode = "fr",
                arabicName = "الفرنسية",
                modelName = "vosk-model-small-fr-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
                sizeLabel = "~41 MB"
            ),
            VoskModelDescriptor(
                langCode = "es",
                arabicName = "الإسبانية",
                modelName = "vosk-model-small-es-0.42",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
                sizeLabel = "~39 MB"
            ),
            VoskModelDescriptor(
                langCode = "de",
                arabicName = "الألمانية",
                modelName = "vosk-model-small-de-0.15",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
                sizeLabel = "~45 MB"
            ),
            VoskModelDescriptor(
                langCode = "tr",
                arabicName = "التركية",
                modelName = "vosk-model-small-tr-0.3",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
                sizeLabel = "~35 MB"
            ),
            VoskModelDescriptor(
                langCode = "ru",
                arabicName = "الروسية",
                modelName = "vosk-model-small-ru-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
                sizeLabel = "~45 MB"
            ),
            VoskModelDescriptor(
                langCode = "zh",
                arabicName = "الصينية",
                modelName = "vosk-model-small-cn-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
                sizeLabel = "~42 MB"
            ),
            VoskModelDescriptor(
                langCode = "ja",
                arabicName = "اليابانية",
                modelName = "vosk-model-small-ja-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
                sizeLabel = "~48 MB"
            ),
            VoskModelDescriptor(
                langCode = "ko",
                arabicName = "الكورية",
                modelName = "vosk-model-small-ko-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
                sizeLabel = "~82 MB"
            ),
            VoskModelDescriptor(
                langCode = "it",
                arabicName = "الإيطالية",
                modelName = "vosk-model-small-it-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
                sizeLabel = "~48 MB"
            ),
            VoskModelDescriptor(
                langCode = "pt",
                arabicName = "البرتغالية",
                modelName = "vosk-model-small-pt-0.3",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
                sizeLabel = "~31 MB"
            ),
            VoskModelDescriptor(
                langCode = "hi",
                arabicName = "الهندية",
                modelName = "vosk-model-small-hi-0.22",
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                sizeLabel = "~42 MB"
            )
        )
    }
}

internal fun selectBestCatalogModelForLanguage(
    entries: List<VoskCatalogItem>,
    targetLangCode: String
): VoskCatalogItem? {
    val normalized = targetLangCode.trim().lowercase()
    val valid = entries.filter { item ->
        if (item.obsolete.equals("true", ignoreCase = true)) return@filter false
        if (item.url.isBlank()) return@filter false
        val itemLang = item.lang.trim().lowercase()
        when (normalized) {
            "en" -> itemLang == "en-us" || itemLang == "en"
            "zh" -> itemLang == "cn" || itemLang == "zh" || itemLang == "zh-cn"
            "ar" -> itemLang == "ar" && !item.name.contains("ar-tn", ignoreCase = true)
            else -> itemLang == normalized || itemLang.startsWith("$normalized-")
        }
    }
    if (valid.isEmpty()) return null

    val smallCandidates = valid.filter { it.type.equals("small", ignoreCase = true) }
    if (smallCandidates.isNotEmpty()) {
        return smallCandidates.firstOrNull { it.lang.equals("en-us", ignoreCase = true) }
            ?: smallCandidates.minByOrNull { if (it.size > 0) it.size else Long.MAX_VALUE }
    }

    return valid.minByOrNull { if (it.size > 0) it.size else Long.MAX_VALUE }
}

internal fun stripTopArchiveDirectory(entryName: String): String {
    val clean = entryName.replace('\\', '/').trimStart('/')
    val slashIdx = clean.indexOf('/')
    return if (slashIdx >= 0) clean.substring(slashIdx + 1) else clean
}

internal fun isSafeZipDestination(rootDir: File, candidate: File): Boolean {
    val rootCanonical = rootDir.canonicalPath
    val candCanonical = candidate.canonicalPath
    return candCanonical == rootCanonical || candCanonical.startsWith("$rootCanonical${File.separator}")
}
