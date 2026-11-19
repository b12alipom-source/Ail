package com.example.study

import android.content.Context
import com.example.data.StudyAiTier
import com.example.data.StudyEngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "bayan_study_ai_prefs"
private const val KEY_PERSONAL_API_KEY = "personal_gemini_api_key"
private const val KEY_PREFER_PERSONAL = "prefer_personal_gemini_key"
private const val KEY_STUDY_AI_TIER = "preferred_study_ai_tier"

class StudyAiPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<StudyEngineConfig> = _config.asStateFlow()

    private fun loadConfig(): StudyEngineConfig {
        val rawKey = prefs.getString(KEY_PERSONAL_API_KEY, "").orEmpty().trim()
        val preferPersonal = prefs.getBoolean(KEY_PREFER_PERSONAL, false)
        val tierKey = prefs.getString(KEY_STUDY_AI_TIER, StudyAiTier.DEEP_REASONING.key).orEmpty()
        return StudyEngineConfig(
            personalApiKey = rawKey,
            preferPersonalKey = preferPersonal && rawKey.isNotBlank(),
            tier = StudyAiTier.fromKey(tierKey)
        )
    }

    fun currentConfig(): StudyEngineConfig = _config.value

    fun savePersonalApiKey(apiKey: String, autoActivate: Boolean = true) {
        val clean = apiKey.trim()
        prefs.edit()
            .putString(KEY_PERSONAL_API_KEY, clean)
            .putBoolean(KEY_PREFER_PERSONAL, autoActivate && clean.isNotBlank())
            .apply()
        _config.value = loadConfig()
    }

    fun clearPersonalApiKey() {
        prefs.edit()
            .remove(KEY_PERSONAL_API_KEY)
            .putBoolean(KEY_PREFER_PERSONAL, false)
            .apply()
        _config.value = loadConfig()
    }

    fun setPreferPersonalKey(prefer: Boolean) {
        val hasKey = _config.value.hasPersonalKey
        prefs.edit()
            .putBoolean(KEY_PREFER_PERSONAL, prefer && hasKey)
            .apply()
        _config.value = loadConfig()
    }

    fun setPreferredTier(tier: StudyAiTier) {
        prefs.edit()
            .putString(KEY_STUDY_AI_TIER, tier.key)
            .apply()
        _config.value = loadConfig()
    }
}
