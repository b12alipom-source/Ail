package com.example.data

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
        promptInstruction = "Create a crisp, high-yield exam-ready summary highlighting core definitions, key formulas/rules, and high-priority bullet takeaways."
    ),
    STEP_BY_STEP(
        key = "STEP_BY_STEP",
        arabicLabel = "شرح مبسط خطوة بخطوة",
        arabicHint = "أسلوب مدرّس يشرح الفهم من الصفر",
        promptInstruction = "Explain concepts patiently like an expert personal tutor, breaking hard ideas into easy steps with intuitive examples."
    ),
    QA_DRILLS(
        key = "QA_DRILLS",
        arabicLabel = "أسئلة وأجوبة للمراجعة",
        arabicHint = "تدرّب بأسئلة متوقعة وإجاباتها",
        promptInstruction = "Organize the summary into high-value exam-style Questions & Answers covering the most important parts of the attached material."
    ),
    FLASHCARDS(
        key = "FLASHCARDS",
        arabicLabel = "بطاقات حفظ سريعة",
        arabicHint = "مصطلح ← معنى مبسط للحفظ السريع",
        promptInstruction = "Structure the study notes into concise memorization cards (Concept -> Clear Arabic explanation & memory hook)."
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
    val isOfflineFallback: Boolean
)
