package com.example.data

import kotlinx.serialization.Serializable

enum class FolderAccent(val key: String, val arabicLabel: String) {
    SAPPHIRE("sapphire", "ياقوتي"),
    EMERALD("emerald", "زمردي"),
    AMBER("amber", "ذهبي"),
    VIOLET("violet", "بنفسجي");

    companion object {
        fun fromKey(key: String): FolderAccent =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SAPPHIRE
    }
}

enum class StudyMethod(
    val key: String,
    val arabicLabel: String,
    val arabicHint: String,
    val promptInstruction: String
) {
    FOCUSED_SUMMARY(
        key = "FOCUSED_SUMMARY",
        arabicLabel = "ملخص مركّز للامتحان",
        arabicHint = "نقاط مهمة، تعريفات، وخلاصة سريعة",
        promptInstruction = "Create a crisp, high-yield exam-ready dossier highlighting core definitions, key formulas/rules, common exam traps, and high-priority takeaways."
    ),
    STEP_BY_STEP(
        key = "STEP_BY_STEP",
        arabicLabel = "شرح مبسط خطوة بخطوة",
        arabicHint = "أسلوب مدرّس يشرح الفهم من الصفر",
        promptInstruction = "Explain concepts patiently like an expert personal tutor, breaking hard ideas into progressive logical steps with intuitive solved examples."
    ),
    QA_DRILLS(
        key = "QA_DRILLS",
        arabicLabel = "أسئلة وأجوبة للمراجعة",
        arabicHint = "تدرّب بأسئلة متوقعة وإجاباتها",
        promptInstruction = "Organize the study dossier around high-value exam-style questions, worked model answers, key terms, and examiner traps."
    ),
    FLASHCARDS(
        key = "FLASHCARDS",
        arabicLabel = "بطاقات حفظ سريعة",
        arabicHint = "مصطلح ← معنى مبسط للحفظ السريع",
        promptInstruction = "Structure the study dossier into crisp memorization concepts, memory hooks, key rules, and fast recall comparisons."
    );

    companion object {
        fun fromKey(key: String): StudyMethod =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FOCUSED_SUMMARY
    }
}

enum class StudyAttachmentType(val key: String, val arabicLabel: String) {
    PDF("PDF", "ملف PDF"),
    IMAGE("IMAGE", "صورة درس"),
    TEXT("TEXT", "ملاحظة / نص"),
    VIDEO_TRANSCRIPT("VIDEO_TRANSCRIPT", "فيديو مترجم");

    companion object {
        fun fromKey(key: String): StudyAttachmentType =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: TEXT
    }
}

enum class StudyAiTier(
    val key: String,
    val firebaseModel: String,
    val firebaseFallbackModel: String,
    val personalRestModel: String,
    val personalRestFallbackModels: List<String>,
    val arabicLabel: String,
    val description: String
) {
    DEEP_REASONING(
        key = "DEEP_REASONING",
        firebaseModel = "gemini-4-argon",
        firebaseFallbackModel = "gemini-3.8-flash",
        personalRestModel = "gemini-3.1-pro-preview",
        personalRestFallbackModels = listOf("gemini-3-flash-preview"),
        arabicLabel = "تحليل عميق (3.1 Pro / Argon)",
        description = "أقوى نماذج Gemini الكاملة للاستدلال العلمي، حل المسائل المعقدة، وتصميم الشروحات والصور الاحترافية"
    ),
    BALANCED(
        key = "BALANCED",
        firebaseModel = "gemini-3.8-flash",
        firebaseFallbackModel = "gemini-4-argon",
        personalRestModel = "gemini-3-flash-preview",
        personalRestFallbackModels = listOf("gemini-3.1-pro-preview"),
        arabicLabel = "سريع وذكي (Gemini 3 Flash)",
        description = "سرعة استجابة فورية مع ذكاء متكامل لفهم ملفات PDF والصور الطويلة والمحادثة"
    );

    fun personalModelChain(): List<String> =
        (listOf(personalRestModel) + personalRestFallbackModels).distinct()

    companion object {
        fun fromKey(key: String): StudyAiTier =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEEP_REASONING
    }
}

enum class StudyVisualStyle(
    val key: String,
    val arabicLabel: String,
    val subtitle: String,
    val artDirection: String
) {
    INFOGRAPHIC(
        key = "INFOGRAPHIC",
        arabicLabel = "إنفوجرافيك شامل",
        subtitle = "لوحة تلخيص متكاملة للمحاور والأفكار",
        artDirection = "Clean educational infographic poster with structured zones, clear icon cards, visual metaphors, deep midnight-blue and glowing cyan/gold scholarly accents, crisp vector illustration style."
    ),
    CONCEPT_MAP(
        key = "CONCEPT_MAP",
        arabicLabel = "خريطة مفاهيم",
        subtitle = "ربط مرئي بين الفروع والمصطلحات",
        artDirection = "Hierarchical visual concept map diagram radiating from a central glowing core node into color-coded curved branches, clean icons, modern textbook diagram aesthetic."
    ),
    PROCESS_FLOW(
        key = "PROCESS_FLOW",
        arabicLabel = "مخطط خطوات وقوانين",
        subtitle = "تسلسل المعادلات والمراحل بالترتيب",
        artDirection = "Step-by-step scientific process flow chart with numbered stages, directional arrows, highlighted formula badges, clean laboratory/academic vector style."
    ),
    COMPARISON_BOARD(
        key = "COMPARISON_BOARD",
        arabicLabel = "لوحة مقارنة",
        subtitle = "الفروق الجوهرية والأنواع جنباً إلى جنب",
        artDirection = "Side-by-side structured educational comparison chart with balanced split columns, contrasting accent colors, clear illustrative icons, high legibility."
    );

    companion object {
        fun fromKey(key: String): StudyVisualStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: INFOGRAPHIC
    }
}

data class StudyEngineConfig(
    val personalApiKey: String = "",
    val preferPersonalKey: Boolean = false,
    val tier: StudyAiTier = StudyAiTier.DEEP_REASONING
) {
    val hasPersonalKey: Boolean get() = personalApiKey.isNotBlank()
    val isUsingPersonalKey: Boolean get() = hasPersonalKey && preferPersonalKey
    val maskedKey: String
        get() = if (personalApiKey.length <= 8) {
            if (personalApiKey.isBlank()) "" else "••••"
        } else {
            "${personalApiKey.take(6)}••••${personalApiKey.takeLast(4)}"
        }

    fun activeBadgeLabel(): String =
        if (isUsingPersonalKey) {
            "مفتاح شخصي • ${tier.arabicLabel}"
        } else {
            "سحابي • ${tier.arabicLabel}"
        }
}

@Serializable
data class StudyDossierSection(
    val heading: String = "",
    val explanation: String = "",
    val keyFormulaOrRule: String = "",
    val example: String = ""
)

@Serializable
data class StudyGlossaryTerm(
    val term: String = "",
    val definition: String = ""
)

@Serializable
data class MindMapBranch(
    val title: String = "",
    val colorTag: String = "sapphire",
    val subtopics: List<String> = emptyList()
)

@Serializable
data class StudyDossierData(
    val overview: String = "",
    val sections: List<StudyDossierSection> = emptyList(),
    val glossary: List<StudyGlossaryTerm> = emptyList(),
    val examTips: List<String> = emptyList(),
    val mindMapRootTitle: String = "",
    val mindMapBranches: List<MindMapBranch> = emptyList()
)

data class GeneratedScheduleTask(
    val dayNumber: Int,
    val dayLabel: String,
    val timeSlot: String,
    val taskTitle: String,
    val taskDetail: String,
    val durationMinutes: Int
)

data class GeneratedStudyPlanResult(
    val summaryMarkdown: String,
    val keyPointsText: String,
    val scheduleTasks: List<GeneratedScheduleTask>,
    val isOfflineFallback: Boolean,
    val dossierJson: String = "",
    val mindMapJson: String = "",
    val engineUsedLabel: String = ""
)
