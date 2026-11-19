package com.example.vault

import com.example.data.db.SubtitleCueEntity
import kotlinx.serialization.Serializable

@Serializable
data class VaultFolderManifestItem(
    val syncId: String,
    val name: String,
    val accentKey: String = "sapphire",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class VaultFolderManifest(
    val folders: List<VaultFolderManifestItem> = emptyList(),
    val updatedAt: Long = 0L
)

@Serializable
data class VaultCueItem(
    val orderIndex: Int = 0,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val originalText: String = "",
    val translatedText: String = ""
)

@Serializable
data class VaultVideoProjectPayload(
    val syncId: String,
    val title: String,
    val durationMs: Long = 0L,
    val sourceLanguageCode: String = "auto",
    val sourceLanguageName: String = "تلقائي",
    val targetLanguageCode: String = "ar",
    val targetLanguageName: String = "العربية",
    val summary: String = "",
    val folderSyncId: String? = null,
    val videoFileName: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val cues: List<VaultCueItem> = emptyList()
)

@Serializable
data class VaultStudyAttachmentItem(
    val displayName: String,
    val attachmentType: String,
    val mimeType: String,
    val extractedText: String = "",
    val vaultRelativeFileName: String? = null,
    val createdAt: Long = 0L
)

@Serializable
data class VaultStudyVisualItem(
    val title: String,
    val styleKey: String,
    val promptSummary: String = "",
    val engineLabel: String = "",
    val vaultImageFileName: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class VaultStudyScheduleItem(
    val dayNumber: Int = 1,
    val dayLabel: String = "",
    val timeSlot: String = "",
    val taskTitle: String = "",
    val taskDetail: String = "",
    val durationMinutes: Int = 30,
    val completed: Boolean = false
)

@Serializable
data class VaultStudyMessageItem(
    val userRole: Boolean = true,
    val messageText: String = "",
    val offlineGenerated: Boolean = false,
    val attachmentName: String = "",
    val attachmentMimeType: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class VaultStudyNotebookPayload(
    val syncId: String,
    val title: String,
    val preferredMethod: String = "FOCUSED_SUMMARY",
    val dailyMinutes: Int = 60,
    val totalDays: Int = 5,
    val summaryMarkdown: String = "",
    val keyPointsText: String = "",
    val dossierJson: String = "",
    val mindMapJson: String = "",
    val lastEngineLabel: String = "",
    val linkedProjectSyncId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val attachments: List<VaultStudyAttachmentItem> = emptyList(),
    val visuals: List<VaultStudyVisualItem> = emptyList(),
    val scheduleItems: List<VaultStudyScheduleItem> = emptyList(),
    val messages: List<VaultStudyMessageItem> = emptyList()
)

@Serializable
data class VaultVoiceTranslationItem(
    val syncId: String,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val originalText: String,
    val translatedText: String,
    val createdAt: Long = 0L
)

@Serializable
data class VaultVoiceHistoryManifest(
    val items: List<VaultVoiceTranslationItem> = emptyList(),
    val updatedAt: Long = 0L
)

data class VaultSettingsState(
    val vaultTreeUri: String = "",
    val vaultFolderLabel: String = "",
    val hasValidPersistedPermission: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val includeVideoCopy: Boolean = true,
    val lastVaultSyncAt: Long = 0L,
    val lastCloudSyncAt: Long = 0L
) {
    val isVaultConfigured: Boolean
        get() = vaultTreeUri.isNotBlank() && hasValidPersistedPermission
}

data class VaultOperationSummary(
    val videosSaved: Int = 0,
    val videoFilesCopied: Int = 0,
    val studyNotebooksSaved: Int = 0,
    val visualsCopied: Int = 0,
    val voiceEntriesSaved: Int = 0
)

data class VaultRestoreSummary(
    val foldersRestored: Int = 0,
    val videosRestored: Int = 0,
    val videoFilesRelinked: Int = 0,
    val studyNotebooksRestored: Int = 0,
    val visualsRestored: Int = 0,
    val voiceEntriesRestored: Int = 0
) {
    val totalRestoredItems: Int
        get() = foldersRestored + videosRestored + studyNotebooksRestored + voiceEntriesRestored
}

data class SignedInAccountInfo(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?
)

data class CloudRestoreSummary(
    val foldersRestored: Int = 0,
    val videosRestored: Int = 0,
    val studyNotebooksRestored: Int = 0,
    val voiceEntriesRestored: Int = 0
) {
    val totalRestored: Int
        get() = foldersRestored + videosRestored + studyNotebooksRestored + voiceEntriesRestored
}

/**
 * Pure helper utilities tested in JVM standalone unit tests.
 */
internal fun chunkSubtitleCues(
    cues: List<SubtitleCueEntity>,
    chunkSize: Int = 120
): List<List<SubtitleCueEntity>> {
    val safeSize = chunkSize.coerceIn(1, 120)
    if (cues.isEmpty()) return emptyList()
    return cues.sortedBy { it.orderIndex }.chunked(safeSize)
}

internal fun sanitizeVaultSegment(raw: String, fallback: String = "item"): String {
    val cleaned = raw.trim()
        .replace(Regex("[^a-zA-Z0-9_\\-]+"), "_")
        .trim('_')
        .take(96)
    return cleaned.ifBlank { fallback }
}

internal fun selectPlayableUriCandidates(primaryUri: String, archivedUri: String): List<String> {
    return listOf(primaryUri.trim(), archivedUri.trim())
        .filter { it.isNotBlank() }
        .distinct()
}
