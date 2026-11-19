package com.example.study

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
private const val READ_TIMEOUT_MS = 60_000

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

    suspend fun generateStructuredJson(
        apiKey: String,
        modelName: String,
        turns: List<RestContentTurn>
    ): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            turns = turns,
            temperature = 0.25,
            responseMimeType = "application/json",
            responseModalities = null
        )
        val rawResponse = postJson(
            url = "$GEMINI_REST_BASE/$modelName:generateContent",
            apiKey = apiKey,
            payload = body.toString()
        )
        extractTextFromGeminiRestJson(rawResponse).ifBlank {
            throw PersonalGeminiApiException(200, "أرجع المفتاح الشخصي رداً فارغاً من النموذج $modelName.")
        }
    }

    suspend fun generateText(
        apiKey: String,
        modelName: String,
        turns: List<RestContentTurn>,
        temperature: Double = 0.4
    ): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            turns = turns,
            temperature = temperature,
            responseMimeType = null,
            responseModalities = null
        )
        val rawResponse = postJson(
            url = "$GEMINI_REST_BASE/$modelName:generateContent",
            apiKey = apiKey,
            payload = body.toString()
        )
        extractTextFromGeminiRestJson(rawResponse).ifBlank {
            throw PersonalGeminiApiException(200, "أرجع المفتاح الشخصي رداً نصياً فارغاً.")
        }
    }

    suspend fun streamText(
        apiKey: String,
        modelName: String,
        turns: List<RestContentTurn>,
        temperature: Double = 0.4,
        onChunk: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            turns = turns,
            temperature = temperature,
            responseMimeType = null,
            responseModalities = null
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
                        full.append(chunkText)
                        onChunk(chunkText)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val result = full.toString().trim()
        if (result.isEmpty()) {
            throw PersonalGeminiApiException(200, "انتهى البث بدون استلام نص من النموذج $modelName.")
        }
        result
    }

    suspend fun generateImagePngBytes(
        apiKey: String,
        turns: List<RestContentTurn>
    ): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val candidateModels = listOf(
            "gemini-3.1-flash-image-preview",
            "gemini-2.5-flash-image",
            "gemini-3-pro-image-preview"
        )
        var lastError: Throwable? = null
        for (modelName in candidateModels) {
            try {
                val body = buildRequestBody(
                    turns = turns,
                    temperature = 0.6,
                    responseMimeType = null,
                    responseModalities = listOf("TEXT", "IMAGE")
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
                // If invalid API key (400/401/403), fail immediately instead of trying fallback models
                if (e.httpCode == 400 || e.httpCode == 401 || e.httpCode == 403) {
                    throw e
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: PersonalGeminiApiException(500, "تعذر توليد الصورة عبر المفتاح الشخصي."))
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
        responseModalities: List<String>?
    ): JsonObject = buildJsonObject {
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
            if (responseMimeType != null) {
                put("responseMimeType", responseMimeType)
            }
            if (!responseModalities.isNullOrEmpty()) {
                put("responseModalities", buildJsonArray {
                    responseModalities.forEach { add(JsonPrimitive(it)) }
                })
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
                val text = obj["text"]?.jsonPrimitive?.contentOrNull
                if (!text.isNullOrEmpty()) append(text)
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
        429 -> "تم تجاوز الحصة/الحد المجاني للمفتاح الشخصي (429)"
        else -> "خطأ من خادم Gemini ($httpCode)"
    }
    return if (apiMessage.isNullOrBlank()) prefix else "$prefix: $apiMessage"
}
