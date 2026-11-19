package com.example.data

import com.example.data.db.ProjectFolderEntity
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyNotebookEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.StudyVisualEntity
import com.example.data.db.SubtitleCueEntity
import com.example.data.db.TranslationDao
import com.example.data.db.VideoProjectEntity
import com.example.data.db.VoiceTranslationEntity
import kotlinx.coroutines.flow.Flow

class TranslationRepository(
    private val dao: TranslationDao
) {
    val allFolders: Flow<List<ProjectFolderEntity>> = dao.getAllFolders()
    val allProjects: Flow<List<VideoProjectEntity>> = dao.getAllProjects()
    val allVoiceTranslations: Flow<List<VoiceTranslationEntity>> = dao.getAllVoiceTranslations()
    val allStudyNotebooks: Flow<List<StudyNotebookEntity>> = dao.getAllStudyNotebooks()

    // --- Folders ---
    suspend fun createFolder(name: String, accentKey: String = FolderAccent.SAPPHIRE.key): Long {
        val now = System.currentTimeMillis()
        return dao.insertFolder(
            ProjectFolderEntity(
                name = name.trim(),
                accentKey = accentKey,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun renameFolder(folderId: Long, name: String, accentKey: String) {
        dao.updateFolder(folderId, name.trim(), accentKey)
    }

    suspend fun deleteFolder(folderId: Long) {
        dao.deleteFolderSafely(folderId)
    }

    // --- Video Projects ---
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
        result: VideoTranslationResult,
        folderId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val project = VideoProjectEntity(
            title = title,
            videoUri = videoUri,
            durationMs = durationMs,
            sourceLanguageCode = result.detectedLanguageCode,
            sourceLanguageName = result.detectedLanguageNameArabic,
            targetLanguageCode = targetLanguage.code,
            targetLanguageName = targetLanguage.arabicName,
            summary = result.summary,
            cueCount = result.segments.size,
            createdAt = now,
            folderId = folderId,
            updatedAt = now
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

    suspend fun renameProject(projectId: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotEmpty()) {
            dao.renameProject(projectId, clean)
        }
    }

    suspend fun moveProjectToFolder(projectId: Long, folderId: Long?) {
        dao.moveProjectToFolder(projectId, folderId)
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

    suspend fun updateProjectArchivedVideoUri(projectId: Long, archivedUri: String) {
        dao.updateProjectArchivedVideoUri(projectId, archivedUri)
    }

    suspend fun deleteProject(projectId: Long) {
        dao.deleteProjectById(projectId)
    }

    // --- Voice Translations ---
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

    // --- Study Studio ---
    suspend fun createStudyNotebook(
        title: String,
        method: StudyMethod = StudyMethod.FOCUSED_SUMMARY,
        dailyMinutes: Int = 60,
        totalDays: Int = 5,
        linkedProjectId: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertStudyNotebook(
            StudyNotebookEntity(
                title = title.trim(),
                preferredMethod = method.key,
                dailyMinutes = dailyMinutes,
                totalDays = totalDays,
                linkedProjectId = linkedProjectId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun getStudyNotebook(notebookId: Long): StudyNotebookEntity? =
        dao.getStudyNotebookById(notebookId)

    suspend fun updateStudyNotebookConfig(
        notebookId: Long,
        title: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int
    ) {
        dao.updateStudyNotebookConfig(
            notebookId = notebookId,
            title = title.trim(),
            method = method.key,
            dailyMinutes = dailyMinutes.coerceIn(15, 480),
            totalDays = totalDays.coerceIn(1, 30),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteStudyNotebook(notebookId: Long) {
        dao.deleteStudyNotebookById(notebookId)
    }

    fun observeStudyAttachments(notebookId: Long): Flow<List<StudyAttachmentEntity>> =
        dao.observeStudyAttachments(notebookId)

    suspend fun getStudyAttachments(notebookId: Long): List<StudyAttachmentEntity> =
        dao.getStudyAttachments(notebookId)

    suspend fun addStudyAttachment(attachment: StudyAttachmentEntity): Long =
        dao.insertStudyAttachment(attachment)

    suspend fun deleteStudyAttachment(attachmentId: Long) {
        dao.deleteStudyAttachmentById(attachmentId)
    }

    fun observeStudyVisuals(notebookId: Long): Flow<List<StudyVisualEntity>> =
        dao.observeStudyVisuals(notebookId)

    suspend fun getStudyVisuals(notebookId: Long): List<StudyVisualEntity> =
        dao.getStudyVisuals(notebookId)

    suspend fun addStudyVisual(visual: StudyVisualEntity): Long =
        dao.insertStudyVisual(visual)

    suspend fun deleteStudyVisual(visualId: Long) {
        dao.deleteStudyVisualById(visualId)
    }

    fun observeStudySchedule(notebookId: Long): Flow<List<StudyScheduleItemEntity>> =
        dao.observeStudySchedule(notebookId)

    suspend fun saveGeneratedStudyPlan(
        notebookId: Long,
        plan: GeneratedStudyPlanResult
    ) {
        val entities = plan.scheduleTasks.map { task ->
            StudyScheduleItemEntity(
                notebookId = notebookId,
                dayNumber = task.dayNumber,
                dayLabel = task.dayLabel,
                timeSlot = task.timeSlot,
                taskTitle = task.taskTitle,
                taskDetail = task.taskDetail,
                durationMinutes = task.durationMinutes,
                isCompleted = false
            )
        }
        dao.replaceStudySchedule(
            notebookId = notebookId,
            summary = plan.summaryMarkdown,
            keyPoints = plan.keyPointsText,
            dossierJson = plan.dossierJson,
            mindMapJson = plan.mindMapJson,
            engineLabel = plan.engineUsedLabel,
            items = entities,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun toggleScheduleItemCompleted(itemId: Long, completed: Boolean) {
        dao.setStudyScheduleItemCompleted(itemId, completed)
    }

    fun observeStudyMessages(notebookId: Long): Flow<List<StudyMessageEntity>> =
        dao.observeStudyMessages(notebookId)

    suspend fun getStudyMessages(notebookId: Long): List<StudyMessageEntity> =
        dao.getStudyMessages(notebookId)

    suspend fun addStudyMessage(
        notebookId: Long,
        isUser: Boolean,
        text: String,
        isOfflineGenerated: Boolean = false,
        attachmentPath: String = "",
        attachmentName: String = "",
        attachmentMimeType: String = ""
    ): Long {
        return dao.insertStudyMessage(
            StudyMessageEntity(
                notebookId = notebookId,
                isUser = isUser,
                messageText = text.trim(),
                isOfflineGenerated = isOfflineGenerated,
                attachmentPath = attachmentPath,
                attachmentName = attachmentName,
                attachmentMimeType = attachmentMimeType
            )
        )
    }

    suspend fun clearStudyMessages(notebookId: Long) {
        dao.clearStudyMessagesForNotebook(notebookId)
    }
}
