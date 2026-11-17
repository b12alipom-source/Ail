package com.example.data

import com.example.data.db.SubtitleCueEntity
import com.example.data.db.TranslationDao
import com.example.data.db.VideoProjectEntity
import com.example.data.db.VoiceTranslationEntity
import kotlinx.coroutines.flow.Flow

class TranslationRepository(
    private val dao: TranslationDao
) {
    val allProjects: Flow<List<VideoProjectEntity>> = dao.getAllProjects()
    val allVoiceTranslations: Flow<List<VoiceTranslationEntity>> = dao.getAllVoiceTranslations()

    fun observeProject(projectId: Long): Flow<VideoProjectEntity?> =
        dao.observeProjectById(projectId)

    fun observeCues(projectId: Long): Flow<List<SubtitleCueEntity>> =
        dao.observeCuesForProject(projectId)

    suspend fun getProject(projectId: Long): VideoProjectEntity? =
        dao.getProjectById(projectId)

    suspend fun getCues(projectId: Long): List<SubtitleCueEntity> =
        dao.getCuesForProject(projectId)

    suspend fun saveTranslationProject(
        title: String,
        videoUri: String,
        durationMs: Long,
        targetLanguage: LanguageOption,
        result: VideoTranslationResult
    ): Long {
        val project = VideoProjectEntity(
            title = title,
            videoUri = videoUri,
            durationMs = durationMs,
            sourceLanguageCode = result.detectedLanguageCode,
            sourceLanguageName = result.detectedLanguageNameArabic,
            targetLanguageCode = targetLanguage.code,
            targetLanguageName = targetLanguage.arabicName,
            summary = result.summary,
            cueCount = result.segments.size
        )
        val cues = result.segments.mapIndexed { index, segment ->
            SubtitleCueEntity(
                projectId = 0L,
                orderIndex = index,
                startMs = segment.startMs,
                endMs = segment.endMs,
                originalText = segment.originalText,
                translatedText = segment.translatedText
            )
        }
        return dao.insertProjectWithCues(project, cues)
    }

    suspend fun updateCueText(cueId: Long, originalText: String, translatedText: String) {
        dao.updateCueText(cueId, originalText, translatedText)
    }

    suspend fun applyReTranslation(
        projectId: Long,
        targetLanguage: LanguageOption,
        summary: String,
        translationsByCueId: Map<Long, String>
    ) {
        dao.applyReTranslation(
            projectId = projectId,
            targetCode = targetLanguage.code,
            targetName = targetLanguage.arabicName,
            summary = summary,
            translationsByCueId = translationsByCueId
        )
    }

    suspend fun updateProjectVideoUri(projectId: Long, videoUri: String) {
        dao.updateProjectVideoUri(projectId, videoUri)
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }

    suspend fun saveVoiceTranslation(
        targetLanguage: LanguageOption,
        result: VoiceTranslationResult
    ): Long {
        return dao.insertVoiceTranslation(
            VoiceTranslationEntity(
                sourceLanguageCode = result.detectedLanguageCode,
                sourceLanguageName = result.detectedLanguageNameArabic,
                targetLanguageCode = targetLanguage.code,
                targetLanguageName = targetLanguage.arabicName,
                originalText = result.originalText,
                translatedText = result.translatedText
            )
        )
    }

    suspend fun deleteVoiceTranslation(id: Long) {
        dao.deleteVoiceTranslationById(id)
    }
}
