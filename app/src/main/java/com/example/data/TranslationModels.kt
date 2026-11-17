package com.example.data

data class LanguageOption(
    val code: String,
    val arabicName: String,
    val nativeName: String,
    val badge: String
)

object SupportedLanguages {
    val autoDetect = LanguageOption(
        code = "auto",
        arabicName = "اكتشاف تلقائي",
        nativeName = "Auto-Detect",
        badge = "AUTO"
    )

    val allTargetLanguages: List<LanguageOption> = listOf(
        LanguageOption("ar", "العربية", "العربية", "AR"),
        LanguageOption("en", "الإنجليزية", "English", "EN"),
        LanguageOption("fr", "الفرنسية", "Français", "FR"),
        LanguageOption("es", "الإسبانية", "Español", "ES"),
        LanguageOption("de", "الألمانية", "Deutsch", "DE"),
        LanguageOption("tr", "التركية", "Türkçe", "TR"),
        LanguageOption("ja", "اليابانية", "日本語", "JA"),
        LanguageOption("ko", "الكورية", "한국어", "KO"),
        LanguageOption("zh", "الصينية", "中文", "ZH"),
        LanguageOption("hi", "الهندية", "हिन्दी", "HI"),
        LanguageOption("it", "الإيطالية", "Italiano", "IT"),
        LanguageOption("pt", "البرتغالية", "Português", "PT"),
        LanguageOption("ru", "الروسية", "Русский", "RU")
    )

    val allSourceLanguages: List<LanguageOption> = listOf(autoDetect) + allTargetLanguages

    fun findByCode(code: String): LanguageOption {
        return allSourceLanguages.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: LanguageOption(code, code.uppercase(), code.uppercase(), code.take(2).uppercase())
    }
}

enum class SubtitleDisplayMode {
    BILINGUAL,
    TRANSLATED_ONLY,
    ORIGINAL_ONLY
}

enum class ExportFormat(val extension: String, val mimeType: String, val arabicLabel: String) {
    SRT("srt", "application/x-subrip", "ملف ترجمة SRT"),
    VTT("vtt", "text/vtt", "ملف ترجمة WebVTT"),
    TXT_TIMESTAMPS("txt", "text/plain", "نص مع الطوابع الزمنية"),
    TXT_CLEAN("txt", "text/plain", "نص مقروء فقط")
}

data class VideoTranslationResult(
    val detectedLanguageCode: String,
    val detectedLanguageNameArabic: String,
    val summary: String,
    val segments: List<GeneratedSegment>
)

data class GeneratedSegment(
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String
)

data class VoiceTranslationResult(
    val detectedLanguageCode: String,
    val detectedLanguageNameArabic: String,
    val originalText: String,
    val translatedText: String
)
