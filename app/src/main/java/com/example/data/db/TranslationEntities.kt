package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "video_projects")
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
    val createdAt: Long = System.currentTimeMillis()
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
