package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM video_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :projectId")
    fun observeProjectById(projectId: Long): Flow<VideoProjectEntity?>

    @Query("SELECT * FROM video_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): VideoProjectEntity?

    @Query("SELECT * FROM subtitle_cues WHERE projectId = :projectId ORDER BY orderIndex ASC, startMs ASC")
    fun observeCuesForProject(projectId: Long): Flow<List<SubtitleCueEntity>>

    @Query("SELECT * FROM subtitle_cues WHERE projectId = :projectId ORDER BY orderIndex ASC, startMs ASC")
    suspend fun getCuesForProject(projectId: Long): List<SubtitleCueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: VideoProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCues(cues: List<SubtitleCueEntity>)

    @Transaction
    suspend fun insertProjectWithCues(
        project: VideoProjectEntity,
        cues: List<SubtitleCueEntity>
    ): Long {
        val projectId = insertProject(project.copy(cueCount = cues.size))
        val mappedCues = cues.mapIndexed { index, cue ->
            cue.copy(projectId = projectId, orderIndex = index)
        }
        insertCues(mappedCues)
        return projectId
    }

    @Query(
        "UPDATE subtitle_cues SET originalText = :originalText, translatedText = :translatedText WHERE id = :cueId"
    )
    suspend fun updateCueText(cueId: Long, originalText: String, translatedText: String)

    @Query(
        "UPDATE video_projects SET targetLanguageCode = :targetCode, targetLanguageName = :targetName, summary = :summary WHERE id = :projectId"
    )
    suspend fun updateProjectTargetLanguage(
        projectId: Long,
        targetCode: String,
        targetName: String,
        summary: String
    )

    @Query("UPDATE video_projects SET videoUri = :videoUri WHERE id = :projectId")
    suspend fun updateProjectVideoUri(projectId: Long, videoUri: String)

    @Query("UPDATE subtitle_cues SET translatedText = :translatedText WHERE id = :cueId")
    suspend fun updateCueTranslation(cueId: Long, translatedText: String)

    @Transaction
    suspend fun applyReTranslation(
        projectId: Long,
        targetCode: String,
        targetName: String,
        summary: String,
        translationsByCueId: Map<Long, String>
    ) {
        updateProjectTargetLanguage(projectId, targetCode, targetName, summary)
        translationsByCueId.forEach { (cueId, translatedText) ->
            updateCueTranslation(cueId, translatedText)
        }
    }

    @Query("DELETE FROM video_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)

    @Query("SELECT * FROM voice_translations ORDER BY createdAt DESC")
    fun getAllVoiceTranslations(): Flow<List<VoiceTranslationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceTranslation(entry: VoiceTranslationEntity): Long

    @Query("DELETE FROM voice_translations WHERE id = :id")
    suspend fun deleteVoiceTranslationById(id: Long)
}
