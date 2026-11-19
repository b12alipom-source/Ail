package com.example.study

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val GEMINI_REST_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
private const val CONNECT_TIMEOUT_MS = 20_000
private const val READ_TIMEOUT_MS = 120_000

sealed class RestPart {
    data class Text(val text: String) : RestPart()
    data class InlineData(val mimeType: String, val bytes: ByteArray) : RestPart()
}

data class RestContentTurn(
    val role: String,
    val parts: List<RestPart>
)

class PersonalGeminiApiException(
    val httpCode: Int,
    override val message: String
) : Exception(message)

private val restJsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class PersonalGeminiRestClient {

    suspend fun verifyApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            require(cleanKey.length >= 16) { "المفتاح قصير جداً." }
            val conn = (URL("$GEMINI_REST_BASE?pageSize=5").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = 20_000
                setRequestProperty("x-goog-api-key", cleanKey)
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw PersonalGeminiApiException(code, formatRestHttpError(code, body))
                }
                "تم التحقق بنجاح — المفتاح نشط ومتصل بنماذج Gemini 3.1 Pro و Nano Banana Pro."
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun generateStructuredJson(
        apiKey: String,
        modelCandidates: List<String>,
        turns: List<RestContentTurn>,
        systemInstruction: String? = null,
        thinkingLevel: String? = "medium",
        maxOutputTokens: Int = 16384
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val candidates = modelCandidates.ifEmpty { listOf("gemini-3.1-pro-preview", "gemini-3-flash-preview") }
        var lastError: Throwable? = null
        for (modelName in candidates) {
            try {
                val effectiveThinking = normalizeThinkingLevelForModel(modelName, thinkingLevel)
                val body = buildRequestBody(
                    turns = turns,
                    temperature = 0.25,
                    responseMimeType = "application/json",
                    responseModalities = null,
                    systemInstruction = systemInstruction,
                    thinkingLevel = effectiveThinking,
                    maxOutputTokens = maxOutputTokens
                )
                val rawResponse = postJson(
                    url = "$GEMINI_REST_BASE/$modelName:generateContent",
                    apiKey = apiKey,
                    payload = body.toString()
                )
                val extracted = extractTextFromGeminiRestJson(rawResponse).trim()
                if (extracted.isNotBlank()) {
                    return@withContext extracted to modelName
                }
                lastError = PersonalGeminiApiException(200, "أرجع المفتاح الشخصي رداً فارغاً من النموذج $modelName.")
            } catch (e: PersonalGeminiApiException) {
                lastError = e
                if (isFatalKeyError(e.httpCode)) throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: PersonalGeminiApiException(500, "تعذر تنفيذ الطلب عبر المفتاح الشخصي."))
    }

    suspend fun generateText(
        apiKey: String,
        modelCandidates: List<String>,
        turns: List<RestContentTurn>,
        systemInstruction: String? = null,
        temperature: Double = 0.4,
        thinkingLevel: String? = "low",
        maxOutputTokens: Int = 8192
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val candidates = modelCandidates.ifEmpty { listOf("gemini-3-flash-preview", "gemini-3.1-pro-preview") }
        var lastError: Throwable? = null
        for (modelName in candidates) {
            try {
                val effectiveThinking = normalizeThinkingLevelForModel(modelName, thinkingLevel)
                val body = buildRequestBody(
                    turns = turns,
                    temperature = temperature,
                    responseMimeType = null,
                    responseModalities = null,
                    systemInstruction = systemInstruction,
                    thinkingLevel = effectiveThinking,
                    maxOutputTokens = maxOutputTokens
                )
                val rawResponse = postJson(
                    url = "$GEMINI_REST_BASE/$modelName:generateContent",
                    apiKey = apiKey,
                    payload = body.toString()
                )
                val text = extractTextFromGeminiRestJson(rawResponse).trim()
                if (text.isNotBlank()) {
                    return@withContext text to modelName
                }
                lastError = PersonalGeminiApiException(200, "أرجع المفتاح الشخصي رداً نصياً فارغاً.")
            } catch (e: PersonalGeminiApiException) {
                lastError = e
                if (isFatalKeyError(e.httpCode)) throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: PersonalGeminiApiException(500, "تعذر توليد النص عبر المفتاح الشخصي."))
    }

    suspend fun streamText(
        apiKey: String,
        modelCandidates: List<String>,
        turns: List<RestContentTurn>,
        systemInstruction: String? = null,
        temperature: Double = 0.4,
        thinkingLevel: String? = "low",
        maxOutputTokens: Int = 8192,
        onChunk: suspend (String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val candidates = modelCandidates.ifEmpty { listOf("gemini-3.1-pro-preview", "gemini-3-flash-preview") }
        var lastError: Throwable? = null
        for (modelName in candidates) {
            var emittedAnyChunk = false
            try {
                val effectiveThinking = normalizeThinkingLevelForModel(modelName, thinkingLevel)
                val body = buildRequestBody(
                    turns = turns,
                    temperature = temperature,
                    responseMimeType = null,
                    responseModalities = null,
                    systemInstruction = systemInstruction,
                    thinkingLevel = effectiveThinking,
                    maxOutputTokens = maxOutputTokens
                )
                val endpoint = "$GEMINI_REST_BASE/$modelName:streamGenerateContent?alt=sse"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("x-goog-api-key", apiKey.trim())
                }

                val full = StringBuilder()
                try {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(body.toString())
                        writer.flush()
                    }

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        throw PersonalGeminiApiException(code, formatRestHttpError(code, errBody))
                    }

                    BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                        while (true) {
                            yield()
                            val line = reader.readLine() ?: break
                            val dataJson = extractSseDataPayload(line) ?: continue
                            val chunkText = extractTextFromGeminiRestJson(dataJson)
                            if (chunkText.isNotEmpty()) {
                                emittedAnyChunk = true
                                full.append(chunkText)
                                onChunk(chunkText)
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                val result = full.toString().trim()
                if (result.isNotEmpty()) {
                    return@withContext result to modelName
                }
                lastError = PersonalGeminiApiException(200, "انتهى البث بدون استلام نص من النموذج $modelName.")
            } catch (e: PersonalGeminiApiException) {
                lastError = e
                if (emittedAnyChunk || isFatalKeyError(e.httpCode)) throw e
            } catch (e: Exception) {
                lastError = e
                if (emittedAnyChunk) throw e
            }
        }
        throw (lastError ?: PersonalGeminiApiException(500, "تعذر إتمام البث عبر المفتاح الشخصي."))
    }

    suspend fun generateImagePngBytes(
        apiKey: String,
        turns: List<RestContentTurn>,
        preferProModel: Boolean = true
    ): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val candidateModels = if (preferProModel) {
            listOf(
                "gemini-3-pro-image-preview",
                "gemini-3.1-flash-image-preview"
            )
        } else {
            listOf(
                "gemini-3.1-flash-image-preview",
                "gemini-3-pro-image-preview"
            )
        }
        var lastError: Throwable? = null
        for (modelName in candidateModels) {
            try {
                val body = buildRequestBody(
                    turns = turns,
                    temperature = 0.6,
                    responseMimeType = null,
                    responseModalities = listOf("TEXT", "IMAGE"),
                    systemInstruction = null,
                    thinkingLevel = null,
                    maxOutputTokens = null
                )
                val rawResponse = postJson(
                    url = "$GEMINI_REST_BASE/$modelName:generateContent",
                    apiKey = apiKey,
                    payload = body.toString()
                )
                val base64Image = extractBase64ImageFromGeminiRestJson(rawResponse)
                if (!base64Image.isNullOrBlank()) {
                    val decoded = Base64.decode(base64Image, Base64.DEFAULT)
                    if (decoded.isNotEmpty()) {
                        return@withContext decoded to modelName
                    }
                }
            } catch (e: PersonalGeminiApiException) {
                lastError = e
                if (isFatalKeyError(e.httpCode)) throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: PersonalGeminiApiException(500, "تعذر توليد الصورة عبر المفتاح الشخصي."))
    }

    private fun isFatalKeyError(httpCode: Int): Boolean =
        httpCode == 401 || httpCode == 403

    private fun normalizeThinkingLevelForModel(modelName: String, requestedLevel: String?): String? {
        if (requestedLevel.isNullOrBlank()) return null
        // gemini-3.1-pro-preview supports: low, medium, high (no minimal)
        if (modelName.contains("3.1-pro") && requestedLevel == "minimal") {
            return "low"
        }
        return requestedLevel
    }

    private fun postJson(url: String, apiKey: String, payload: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw PersonalGeminiApiException(code, formatRestHttpError(code, responseText))
            }
            return responseText
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(
        turns: List<RestContentTurn>,
        temperature: Double,
        responseMimeType: String?,
        responseModalities: List<String>?,
        systemInstruction: String?,
        thinkingLevel: String?,
        maxOutputTokens: Int?
    ): JsonObject = buildJsonObject {
        if (!systemInstruction.isNullOrBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(
                        buildJsonObject {
                            put("text", systemInstruction)
                        }
                    )
                }
            }
        }
        putJsonArray("contents") {
            turns.forEach { turn ->
                add(
                    buildJsonObject {
                        put("role", turn.role)
                        putJsonArray("parts") {
                            turn.parts.forEach { part ->
                                when (part) {
                                    is RestPart.Text -> add(
                                        buildJsonObject {
                                            put("text", part.text)
                                        }
                                    )
                                    is RestPart.InlineData -> add(
                                        buildJsonObject {
                                            putJsonObject("inlineData") {
                                                put("mimeType", part.mimeType)
                                                put(
                                                    "data",
                                                    Base64.encodeToString(part.bytes, Base64.NO_WRAP)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            if (maxOutputTokens != null) {
                put("maxOutputTokens", maxOutputTokens)
            }
            if (responseMimeType != null) {
                put("responseMimeType", responseMimeType)
            }
            if (!responseModalities.isNullOrEmpty()) {
                put("responseModalities", buildJsonArray {
                    responseModalities.forEach { add(JsonPrimitive(it)) }
                })
            }
            if (!thinkingLevel.isNullOrBlank()) {
                putJsonObject("thinkingConfig") {
                    put("thinkingLevel", thinkingLevel)
                }
            }
        }
    }
}

internal fun extractSseDataPayload(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return null
    val payload = trimmed.removePrefix("data:").trim()
    if (payload.isBlank() || payload == "[DONE]") return null
    return payload
}

internal fun extractTextFromGeminiRestJson(rawJson: String): String {
    if (rawJson.isBlank()) return ""
    return runCatching {
        val root = restJsonParser.parseToJsonElement(rawJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return@runCatching ""
        val first = candidates.firstOrNull()?.jsonObject ?: return@runCatching ""
        val content = first["content"]?.jsonObject ?: return@runCatching ""
        val parts = content["parts"]?.jsonArray ?: return@runCatching ""
        buildString {
            parts.forEach { partEl ->
                val obj = partEl.jsonObject
                // Gemini 3 thinking models may return {"thought": true, "text": "..."}; skip internal chain-of-thought
                val isThought = obj["thought"]?.jsonPrimitive?.booleanOrNull == true
                if (!isThought) {
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrEmpty()) append(text)
                }
            }
        }
    }.getOrDefault("")
}

internal fun extractBase64ImageFromGeminiRestJson(rawJson: String): String? {
    if (rawJson.isBlank()) return null
    return runCatching {
        val root = restJsonParser.parseToJsonElement(rawJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return@runCatching null
        for (cand in candidates) {
            val parts = cand.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray ?: continue
            for (part in parts) {
                val obj = part.jsonObject
                val inlineObj = obj["inlineData"]?.jsonObject ?: obj["inline_data"]?.jsonObject
                val data = inlineObj?.get("data")?.jsonPrimitive?.contentOrNull
                if (!data.isNullOrBlank()) return@runCatching data
            }
        }
        null
    }.getOrNull()
}

internal fun formatRestHttpError(httpCode: Int, errorJson: String): String {
    val apiMessage = runCatching {
        val root = restJsonParser.parseToJsonElement(errorJson).jsonObject
        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    val prefix = when (httpCode) {
        400 -> "طلب غير صالح أو المفتاح الشخصي غير صحيح (400)"
        401, 403 -> "تم رفض المفتاح الشخصي أو الصلاحية غير كافية ($httpCode)"
        404 -> "النموذج غير متاح لهذا المفتاح (404)"
        429 -> "تم تجاوز الحصة/الحد المؤقت للمفتاح الشخصي (429)"
        else -> "خطأ من خادم Gemini ($httpCode)"
    }
    return if (apiMessage.isNullOrBlank()) prefix else "$prefix: $apiMessage"
}
