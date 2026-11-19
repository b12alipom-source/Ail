package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    // --- Project Folders ---
    @Query("SELECT * FROM project_folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<ProjectFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ProjectFolderEntity): Long

    @Query("UPDATE project_folders SET name = :name, accentKey = :accentKey, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun updateFolder(
        folderId: Long,
        name: String,
        accentKey: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE video_projects SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unfileProjectsInFolder(folderId: Long)

    @Query("DELETE FROM project_folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Transaction
    suspend fun deleteFolderSafely(folderId: Long) {
        unfileProjectsInFolder(folderId)
        deleteFolderById(folderId)
    }

    // --- Video Projects & Cues ---
    @Query("SELECT * FROM video_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :projectId")
    fun observeProjectById(projectId: Long): Flow<VideoProjectEntity?>

    @Query("SELECT * FROM video_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): VideoProjectEntity?

    @Query("UPDATE video_projects SET title = :title, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun renameProject(projectId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_projects SET folderId = :folderId, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun moveProjectToFolder(projectId: Long, folderId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE video_projects SET archivedVideoUri = :archivedVideoUri WHERE id = :projectId")
    suspend fun updateProjectArchivedVideoUri(projectId: Long, archivedVideoUri: String)

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
        "UPDATE video_projects SET targetLanguageCode = :targetCode, targetLanguageName = :targetName, summary = :summary, updatedAt = :updatedAt WHERE id = :projectId"
    )
    suspend fun updateProjectTargetLanguage(
        projectId: Long,
        targetCode: String,
        targetName: String,
        summary: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE video_projects SET videoUri = :videoUri, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateProjectVideoUri(projectId: Long, videoUri: String, updatedAt: Long = System.currentTimeMillis())

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

    // --- Voice Translations ---
    @Query("SELECT * FROM voice_translations ORDER BY createdAt DESC")
    fun getAllVoiceTranslations(): Flow<List<VoiceTranslationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceTranslation(entry: VoiceTranslationEntity): Long

    @Query("DELETE FROM voice_translations WHERE id = :id")
    suspend fun deleteVoiceTranslationById(id: Long)

    // --- Study Notebooks, Attachments, Schedules & Tutor Messages ---
    @Query("SELECT * FROM study_notebooks ORDER BY updatedAt DESC")
    fun getAllStudyNotebooks(): Flow<List<StudyNotebookEntity>>

    @Query("SELECT * FROM study_notebooks WHERE id = :notebookId")
    suspend fun getStudyNotebookById(notebookId: Long): StudyNotebookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyNotebook(notebook: StudyNotebookEntity): Long

    @Query(
        "UPDATE study_notebooks SET title = :title, preferredMethod = :method, dailyMinutes = :dailyMinutes, totalDays = :totalDays, updatedAt = :updatedAt WHERE id = :notebookId"
    )
    suspend fun updateStudyNotebookConfig(
        notebookId: Long,
        title: String,
        method: String,
        dailyMinutes: Int,
        totalDays: Int,
        updatedAt: Long
    )

    @Query(
        "UPDATE study_notebooks SET summaryMarkdown = :summary, keyPointsText = :keyPoints, dossierJson = :dossierJson, mindMapJson = :mindMapJson, lastEngineLabel = :engineLabel, updatedAt = :updatedAt WHERE id = :notebookId"
    )
    suspend fun updateStudyNotebookSummary(
        notebookId: Long,
        summary: String,
        keyPoints: String,
        dossierJson: String,
        mindMapJson: String,
        engineLabel: String,
        updatedAt: Long
    )

    @Query("DELETE FROM study_notebooks WHERE id = :notebookId")
    suspend fun deleteStudyNotebookById(notebookId: Long)

    @Query("SELECT * FROM study_attachments WHERE notebookId = :notebookId ORDER BY createdAt DESC")
    fun observeStudyAttachments(notebookId: Long): Flow<List<StudyAttachmentEntity>>

    @Query("SELECT * FROM study_attachments WHERE notebookId = :notebookId ORDER BY createdAt ASC")
    suspend fun getStudyAttachments(notebookId: Long): List<StudyAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyAttachment(attachment: StudyAttachmentEntity): Long

    @Query("DELETE FROM study_attachments WHERE id = :attachmentId")
    suspend fun deleteStudyAttachmentById(attachmentId: Long)

    @Query("SELECT * FROM study_visuals WHERE notebookId = :notebookId ORDER BY createdAt DESC")
    fun observeStudyVisuals(notebookId: Long): Flow<List<StudyVisualEntity>>

    @Query("SELECT * FROM study_visuals WHERE notebookId = :notebookId ORDER BY createdAt DESC")
    suspend fun getStudyVisuals(notebookId: Long): List<StudyVisualEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyVisual(visual: StudyVisualEntity): Long

    @Query("DELETE FROM study_visuals WHERE id = :visualId")
    suspend fun deleteStudyVisualById(visualId: Long)

    @Query("SELECT * FROM study_schedule_items WHERE notebookId = :notebookId ORDER BY dayNumber ASC, id ASC")
    fun observeStudySchedule(notebookId: Long): Flow<List<StudyScheduleItemEntity>>

    @Query("DELETE FROM study_schedule_items WHERE notebookId = :notebookId")
    suspend fun deleteStudyScheduleForNotebook(notebookId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyScheduleItems(items: List<StudyScheduleItemEntity>)

    @Transaction
    suspend fun replaceStudySchedule(
        notebookId: Long,
        summary: String,
        keyPoints: String,
        dossierJson: String,
        mindMapJson: String,
        engineLabel: String,
        items: List<StudyScheduleItemEntity>,
        updatedAt: Long
    ) {
        updateStudyNotebookSummary(
            notebookId = notebookId,
            summary = summary,
            keyPoints = keyPoints,
            dossierJson = dossierJson,
            mindMapJson = mindMapJson,
            engineLabel = engineLabel,
            updatedAt = updatedAt
        )
        deleteStudyScheduleForNotebook(notebookId)
        insertStudyScheduleItems(items)
    }

    @Query("UPDATE study_schedule_items SET isCompleted = :completed WHERE id = :itemId")
    suspend fun setStudyScheduleItemCompleted(itemId: Long, completed: Boolean)

    @Query("SELECT * FROM study_messages WHERE notebookId = :notebookId ORDER BY createdAt ASC, id ASC")
    fun observeStudyMessages(notebookId: Long): Flow<List<StudyMessageEntity>>

    @Query("SELECT * FROM study_messages WHERE notebookId = :notebookId ORDER BY createdAt ASC, id ASC")
    suspend fun getStudyMessages(notebookId: Long): List<StudyMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyMessage(message: StudyMessageEntity): Long

    @Query("DELETE FROM study_messages WHERE notebookId = :notebookId")
    suspend fun clearStudyMessagesForNotebook(notebookId: Long)
}
