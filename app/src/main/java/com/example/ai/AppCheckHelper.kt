package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

sealed interface AppCheckDiagnosticState {
    data object Initializing : AppCheckDiagnosticState
    data class Verified(val expiresAtMs: Long, val providerName: String) : AppCheckDiagnosticState
    data class AttentionNeeded(val reason: String, val providerName: String) : AppCheckDiagnosticState
}

sealed interface AppCheckTokenOutcome {
    data class Valid(val token: String, val expiresAtMs: Long) : AppCheckTokenOutcome
    data class Failed(val detail: String) : AppCheckTokenOutcome
}

@Volatile
private var appCheckFactoryInstalled = false

@Volatile
private var installedProviderLabel = if (BuildConfig.DEBUG) "Debug Provider" else "reCAPTCHA Enterprise"

private val appCheckMutex = Mutex()
private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private val _appCheckDiagnosticState =
    MutableStateFlow<AppCheckDiagnosticState>(AppCheckDiagnosticState.Initializing)
val appCheckDiagnosticState: StateFlow<AppCheckDiagnosticState> =
    _appCheckDiagnosticState.asStateFlow()

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.setTokenAutoRefreshEnabled(true)

        if (BuildConfig.DEBUG) {
            installedProviderLabel = "Debug Provider"
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() ensures the debug secret is persisted before provider init
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            if (!appCheckFactoryInstalled || !debugToken.isNullOrEmpty()) {
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheckFactoryInstalled = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }
        } else {
            installedProviderLabel = "reCAPTCHA Enterprise"
            if (appCheckFactoryInstalled) return
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    appCheckFactoryInstalled = true
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    val msg = "recaptcha_site_key resource string is blank."
                    Log.e(TAG, msg)
                    _appCheckDiagnosticState.value = AppCheckDiagnosticState.AttentionNeeded(msg, installedProviderLabel)
                }
            } else {
                val msg = "recaptcha_site_key string resource not found in package."
                Log.e(TAG, msg)
                _appCheckDiagnosticState.value = AppCheckDiagnosticState.AttentionNeeded(msg, installedProviderLabel)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        _appCheckDiagnosticState.value = AppCheckDiagnosticState.AttentionNeeded(
            e.localizedMessage ?: "App Check init error",
            installedProviderLabel
        )
    }
}

fun warmUpAppCheckAsync() {
    warmUpScope.launch {
        verifyOrRefreshAppCheckToken(forceRefresh = false, maxAttempts = 2)
    }
}

suspend fun verifyOrRefreshAppCheckToken(
    forceRefresh: Boolean,
    maxAttempts: Int = 2
): AppCheckTokenOutcome = appCheckMutex.withLock {
    var lastErrorDetail = "تعذر الحصول على رمز تحقق صالح."
    for (attempt in 1..maxAttempts.coerceAtLeast(1)) {
        val shouldForce = forceRefresh || attempt > 1
        val outcome = requestSingleToken(shouldForce)
        if (outcome is AppCheckTokenOutcome.Valid) {
            _appCheckDiagnosticState.value = AppCheckDiagnosticState.Verified(
                expiresAtMs = outcome.expiresAtMs,
                providerName = installedProviderLabel
            )
            return@withLock outcome
        }
        if (outcome is AppCheckTokenOutcome.Failed) {
            lastErrorDetail = outcome.detail
        }
        if (attempt < maxAttempts) {
            delay(1200L * attempt)
        }
    }
    _appCheckDiagnosticState.value = AppCheckDiagnosticState.AttentionNeeded(
        reason = lastErrorDetail,
        providerName = installedProviderLabel
    )
    AppCheckTokenOutcome.Failed(lastErrorDetail)
}

suspend fun refreshAppCheckToken(): Boolean {
    return verifyOrRefreshAppCheckToken(forceRefresh = true, maxAttempts = 2) is AppCheckTokenOutcome.Valid
}

private suspend fun requestSingleToken(forceRefresh: Boolean): AppCheckTokenOutcome =
    suspendCancellableCoroutine { continuation ->
        try {
            FirebaseAppCheck.getInstance()
                .getAppCheckToken(forceRefresh)
                .addOnSuccessListener { result ->
                    val token = result.token
                    val embeddedError = extractErrorFromDummyToken(token)
                    if (token.isBlank() || embeddedError != null) {
                        val detail = embeddedError ?: "Empty App Check token returned."
                        Log.w(TAG, "App Check returned placeholder error token: $detail")
                        if (continuation.isActive) {
                            continuation.resume(AppCheckTokenOutcome.Failed(detail))
                        }
                    } else {
                        Log.d(TAG, "App Check token ready (expires=${result.expireTimeMillis}).")
                        if (continuation.isActive) {
                            continuation.resume(
                                AppCheckTokenOutcome.Valid(token, result.expireTimeMillis)
                            )
                        }
                    }
                }
                .addOnFailureListener { error ->
                    val detail = error.localizedMessage ?: error.javaClass.simpleName
                    Log.w(TAG, "Failed to obtain App Check token: $detail", error)
                    if (continuation.isActive) {
                        continuation.resume(AppCheckTokenOutcome.Failed(detail))
                    }
                }
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            Log.w(TAG, "Exception obtaining App Check token: $detail", e)
            if (continuation.isActive) {
                continuation.resume(AppCheckTokenOutcome.Failed(detail))
            }
        }
    }

internal fun extractErrorFromDummyToken(token: String): String? {
    if (token.isBlank()) return "Empty App Check token"
    val parts = token.split('.')
    // Firebase App Check SDK embeds base64 JSON `{"error":"..."}` when provider fails silently
    val candidates = when (parts.size) {
        1 -> listOf(parts[0])
        3 -> listOf(parts[1], parts[0])
        else -> listOf(token)
    }
    for (encoded in candidates) {
        val decoded = decodeBase64UrlToUtf8(encoded) ?: continue
        val trimmed = decoded.trim()
        if (trimmed.startsWith("{")) {
            val err = runCatching {
                val obj = Json.parseToJsonElement(trimmed) as? JsonObject ?: return@runCatching null
                val elem = obj["error"] ?: return@runCatching null
                (elem as? JsonPrimitive)?.contentOrNull ?: elem.toString()
            }.getOrNull()
            if (!err.isNullOrBlank()) {
                return err
            }
        }
    }
    return null
}

private fun decodeBase64UrlToUtf8(input: String): String? {
    return runCatching {
        val clean = input.replace('-', '+').replace('_', '/').let { s ->
            val mod = s.length % 4
            if (mod == 0) s else s + "=".repeat(4 - mod)
        }
        val table = IntArray(256) { -1 }
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        alphabet.forEachIndexed { i, c -> table[c.code] = i }

        val out = ByteArray((clean.length * 3) / 4)
        var outIdx = 0
        var i = 0
        while (i + 3 < clean.length) {
            val c0 = table[clean[i].code.coerceIn(0, 255)]
            val c1 = table[clean[i + 1].code.coerceIn(0, 255)]
            val c2 = if (clean[i + 2] == '=') 0 else table[clean[i + 2].code.coerceIn(0, 255)]
            val c3 = if (clean[i + 3] == '=') 0 else table[clean[i + 3].code.coerceIn(0, 255)]
            if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) return null
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            out[outIdx++] = ((triple shr 16) and 0xFF).toByte()
            if (clean[i + 2] != '=') out[outIdx++] = ((triple shr 8) and 0xFF).toByte()
            if (clean[i + 3] != '=') out[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        String(out, 0, outIdx, Charsets.UTF_8)
    }.getOrNull()
}
