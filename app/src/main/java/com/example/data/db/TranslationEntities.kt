package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "project_folders")
data class ProjectFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val accentKey: String = "sapphire",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_projects",
    indices = [Index(value = ["folderId"])]
)
data class VideoProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val summary: String,
    val cueCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null
)

@Entity(
    tableName = "subtitle_cues",
    foreignKeys = [
        ForeignKey(
            entity = VideoProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class SubtitleCueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val orderIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val translatedText: String
)

@Entity(tableName = "voice_translations")
data class VoiceTranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val originalText: String,
    val translatedText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_notebooks")
data class StudyNotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val preferredMethod: String = "FOCUSED_SUMMARY",
    val dailyMinutes: Int = 60,
    val totalDays: Int = 5,
    val summaryMarkdown: String = "",
    val keyPointsText: String = "",
    val dossierJson: String = "",
    val mindMapJson: String = "",
    val lastEngineLabel: String = "",
    val linkedProjectId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_attachments",
    foreignKeys = [
        ForeignKey(
            entity = StudyNotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class StudyAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val displayName: String,
    val attachmentType: String,
    val mimeType: String,
    val localFilePath: String,
    val extractedText: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_visuals",
    foreignKeys = [
        ForeignKey(
            entity = StudyNotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class StudyVisualEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String,
    val styleKey: String,
    val localImagePath: String,
    val promptSummary: String,
    val engineLabel: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_schedule_items",
    foreignKeys = [
        ForeignKey(
            entity = StudyNotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class StudyScheduleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val dayNumber: Int,
    val dayLabel: String,
    val timeSlot: String,
    val taskTitle: String,
    val taskDetail: String,
    val durationMinutes: Int,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "study_messages",
    foreignKeys = [
        ForeignKey(
            entity = StudyNotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["notebookId"])]
)
data class StudyMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val isUser: Boolean,
    val messageText: String,
    val isOfflineGenerated: Boolean = false,
    val attachmentPath: String = "",
    val attachmentName: String = "",
    val attachmentMimeType: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
