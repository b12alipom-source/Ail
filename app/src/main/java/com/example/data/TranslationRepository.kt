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
import java.util.UUID

data class FullBackupProjectBundle(
    val project: VideoProjectEntity,
    val folderSyncId: String?,
    val cues: List<SubtitleCueEntity>
)

data class FullBackupStudyBundle(
    val notebook: StudyNotebookEntity,
    val linkedProjectSyncId: String?,
    val attachments: List<StudyAttachmentEntity>,
    val visuals: List<StudyVisualEntity>,
    val scheduleItems: List<StudyScheduleItemEntity>,
    val messages: List<StudyMessageEntity>
)

data class BayanBackupSnapshot(
    val folders: List<ProjectFolderEntity>,
    val projects: List<FullBackupProjectBundle>,
    val studyNotebooks: List<FullBackupStudyBundle>,
    val voiceTranslations: List<VoiceTranslationEntity>
)

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
                syncId = UUID.randomUUID().toString(),
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
            syncId = UUID.randomUUID().toString(),
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

    suspend fun updateCueText(cueId: Long, originalText: String, translatedText: String): Long? {
        dao.updateCueText(cueId, originalText, translatedText)
        return dao.getProjectIdForCue(cueId)
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

    suspend fun markProjectVaultSaved(projectId: Long, timestamp: Long = System.currentTimeMillis()) {
        dao.updateProjectVaultSavedAt(projectId, timestamp)
    }

    suspend fun markProjectCloudSynced(projectId: Long, timestamp: Long = System.currentTimeMillis()) {
        dao.updateProjectCloudSyncedAt(projectId, timestamp)
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
                translatedText = result.translatedText,
                syncId = UUID.randomUUID().toString()
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
                updatedAt = now,
                syncId = UUID.randomUUID().toString()
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

    suspend fun markStudyVaultSaved(notebookId: Long, timestamp: Long = System.currentTimeMillis()) {
        dao.updateStudyVaultSavedAt(notebookId, timestamp)
    }

    suspend fun markStudyCloudSynced(notebookId: Long, timestamp: Long = System.currentTimeMillis()) {
        dao.updateStudyCloudSyncedAt(notebookId, timestamp)
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

    suspend fun toggleScheduleItemCompleted(itemId: Long, completed: Boolean): Long? {
        dao.setStudyScheduleItemCompleted(itemId, completed)
        return dao.getNotebookIdForScheduleItem(itemId)
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

    // --- Full Backup Snapshot & Merge Restore ---
    suspend fun getFullBackupSnapshot(): BayanBackupSnapshot {
        val folders = dao.getAllFoldersList().map { folder ->
            if (folder.syncId.isBlank()) {
                val fixed = UUID.randomUUID().toString()
                dao.updateFolderSyncId(folder.id, fixed)
                folder.copy(syncId = fixed)
            } else folder
        }
        val folderSyncById = folders.associate { it.id to it.syncId }

        val rawProjects = dao.getAllProjectsList().map { proj ->
            if (proj.syncId.isBlank()) {
                val fixed = UUID.randomUUID().toString()
                dao.updateProjectSyncId(proj.id, fixed)
                proj.copy(syncId = fixed)
            } else proj
        }
        val projectSyncById = rawProjects.associate { it.id to it.syncId }

        val projectBundles = rawProjects.map { proj ->
            val cues = dao.getCuesForProject(proj.id)
            FullBackupProjectBundle(
                project = proj,
                folderSyncId = proj.folderId?.let { folderSyncById[it] },
                cues = cues
            )
        }

        val studyNotebooks = dao.getAllStudyNotebooksList().map { nb ->
            if (nb.syncId.isBlank()) {
                val fixed = UUID.randomUUID().toString()
                dao.updateStudyNotebookSyncId(nb.id, fixed)
                nb.copy(syncId = fixed)
            } else nb
        }
        val studyBundles = studyNotebooks.map { nb ->
            FullBackupStudyBundle(
                notebook = nb,
                linkedProjectSyncId = nb.linkedProjectId?.let { projectSyncById[it] },
                attachments = dao.getStudyAttachments(nb.id),
                visuals = dao.getStudyVisuals(nb.id),
                scheduleItems = dao.getStudyScheduleList(nb.id),
                messages = dao.getStudyMessages(nb.id)
            )
        }

        val voices = dao.getAllVoiceTranslationsList().map { voice ->
            if (voice.syncId.isBlank()) {
                val fixed = UUID.randomUUID().toString()
                dao.updateVoiceSyncId(voice.id, fixed)
                voice.copy(syncId = fixed)
            } else voice
        }

        return BayanBackupSnapshot(
            folders = folders,
            projects = projectBundles,
            studyNotebooks = studyBundles,
            voiceTranslations = voices
        )
    }

    suspend fun getProjectBackupBundle(projectId: Long): FullBackupProjectBundle? {
        val raw = dao.getProjectById(projectId) ?: return null
        val project = if (raw.syncId.isBlank()) {
            val fixed = UUID.randomUUID().toString()
            dao.updateProjectSyncId(raw.id, fixed)
            raw.copy(syncId = fixed)
        } else raw
        val folderSyncId = project.folderId?.let { fid ->
            val f = dao.getFolderById(fid) ?: return@let null
            if (f.syncId.isBlank()) {
                val fixed = UUID.randomUUID().toString()
                dao.updateFolderSyncId(f.id, fixed)
                fixed
            } else f.syncId
        }
        return FullBackupProjectBundle(
            project = project,
            folderSyncId = folderSyncId,
            cues = dao.getCuesForProject(project.id)
        )
    }

    suspend fun getStudyBackupBundle(notebookId: Long): FullBackupStudyBundle? {
        val raw = dao.getStudyNotebookById(notebookId) ?: return null
        val nb = if (raw.syncId.isBlank()) {
            val fixed = UUID.randomUUID().toString()
            dao.updateStudyNotebookSyncId(raw.id, fixed)
            raw.copy(syncId = fixed)
        } else raw
        val linkedSyncId = nb.linkedProjectId?.let { pid ->
            dao.getProjectById(pid)?.syncId?.takeIf { it.isNotBlank() }
        }
        return FullBackupStudyBundle(
            notebook = nb,
            linkedProjectSyncId = linkedSyncId,
            attachments = dao.getStudyAttachments(nb.id),
            visuals = dao.getStudyVisuals(nb.id),
            scheduleItems = dao.getStudyScheduleList(nb.id),
            messages = dao.getStudyMessages(nb.id)
        )
    }

    suspend fun upsertRestoredFolder(
        syncId: String,
        name: String,
        accentKey: String,
        createdAt: Long,
        updatedAt: Long
    ): Long {
        val cleanSync = syncId.trim().ifBlank { UUID.randomUUID().toString() }
        val existing = dao.getFolderBySyncId(cleanSync)
        return if (existing == null) {
            dao.insertFolder(
                ProjectFolderEntity(
                    name = name.trim().ifBlank { "مجلد مستعاد" },
                    accentKey = accentKey.ifBlank { FolderAccent.SAPPHIRE.key },
                    createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    syncId = cleanSync,
                    updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            )
        } else {
            if (updatedAt > existing.updatedAt) {
                dao.updateFolder(existing.id, name.trim(), accentKey, updatedAt)
            }
            existing.id
        }
    }

    suspend fun upsertRestoredVideoProject(
        syncId: String,
        title: String,
        videoUri: String,
        archivedVideoUri: String,
        durationMs: Long,
        sourceLanguageCode: String,
        sourceLanguageName: String,
        targetLanguageCode: String,
        targetLanguageName: String,
        summary: String,
        createdAt: Long,
        updatedAt: Long,
        folderSyncId: String?,
        cues: List<SubtitleCueEntity>,
        markVaultAt: Long = 0L,
        markCloudAt: Long = 0L
    ): Long {
        val cleanSync = syncId.trim().ifBlank { UUID.randomUUID().toString() }
        val resolvedFolderId = folderSyncId?.takeIf { it.isNotBlank() }?.let { dao.getFolderBySyncId(it)?.id }
        val existing = dao.getProjectBySyncId(cleanSync)
        val sortedCues = cues.sortedBy { it.orderIndex }

        return if (existing == null) {
            val effectivePrimaryUri = videoUri.ifBlank { archivedVideoUri }
            val entity = VideoProjectEntity(
                title = title.trim().ifBlank { "فيديو مستعاد" },
                videoUri = effectivePrimaryUri,
                archivedVideoUri = archivedVideoUri,
                durationMs = durationMs.coerceAtLeast(0L),
                sourceLanguageCode = sourceLanguageCode.ifBlank { "auto" },
                sourceLanguageName = sourceLanguageName.ifBlank { "تلقائي" },
                targetLanguageCode = targetLanguageCode.ifBlank { "ar" },
                targetLanguageName = targetLanguageName.ifBlank { "العربية" },
                summary = summary,
                cueCount = sortedCues.size,
                createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                folderId = resolvedFolderId,
                syncId = cleanSync,
                vaultSavedAt = markVaultAt,
                cloudSyncedAt = markCloudAt
            )
            dao.insertProjectWithCues(entity, sortedCues)
        } else {
            val finalArchived = archivedVideoUri.ifBlank { existing.archivedVideoUri }
            val finalPrimary = existing.videoUri.ifBlank { finalArchived }
            val shouldUpdateMeta = updatedAt > existing.updatedAt ||
                (existing.cueCount == 0 && sortedCues.isNotEmpty()) ||
                (finalArchived.isNotBlank() && existing.archivedVideoUri.isBlank())

            if (shouldUpdateMeta) {
                val merged = existing.copy(
                    title = if (updatedAt > existing.updatedAt) title.trim().ifBlank { existing.title } else existing.title,
                    videoUri = finalPrimary,
                    archivedVideoUri = finalArchived,
                    durationMs = if (durationMs > 0L) durationMs else existing.durationMs,
                    sourceLanguageCode = sourceLanguageCode.ifBlank { existing.sourceLanguageCode },
                    sourceLanguageName = sourceLanguageName.ifBlank { existing.sourceLanguageName },
                    targetLanguageCode = targetLanguageCode.ifBlank { existing.targetLanguageCode },
                    targetLanguageName = targetLanguageName.ifBlank { existing.targetLanguageName },
                    summary = summary.ifBlank { existing.summary },
                    folderId = resolvedFolderId ?: existing.folderId,
                    updatedAt = maxOf(updatedAt, existing.updatedAt),
                    vaultSavedAt = maxOf(markVaultAt, existing.vaultSavedAt),
                    cloudSyncedAt = maxOf(markCloudAt, existing.cloudSyncedAt)
                )
                dao.insertProject(merged)
                if (sortedCues.isNotEmpty() && (updatedAt > existing.updatedAt || existing.cueCount == 0)) {
                    dao.replaceProjectCues(existing.id, sortedCues, maxOf(updatedAt, existing.updatedAt))
                }
            }
            existing.id
        }
    }

    suspend fun upsertRestoredStudyNotebook(
        syncId: String,
        title: String,
        preferredMethod: String,
        dailyMinutes: Int,
        totalDays: Int,
        summaryMarkdown: String,
        keyPointsText: String,
        dossierJson: String,
        mindMapJson: String,
        lastEngineLabel: String,
        linkedProjectSyncId: String?,
        createdAt: Long,
        updatedAt: Long,
        attachments: List<StudyAttachmentEntity>,
        visuals: List<StudyVisualEntity>,
        scheduleItems: List<StudyScheduleItemEntity>,
        messages: List<StudyMessageEntity>,
        markVaultAt: Long = 0L,
        markCloudAt: Long = 0L
    ): Long {
        val cleanSync = syncId.trim().ifBlank { UUID.randomUUID().toString() }
        val linkedProjectId = linkedProjectSyncId?.takeIf { it.isNotBlank() }?.let { dao.getProjectBySyncId(it)?.id }
        val existing = dao.getStudyNotebookBySyncId(cleanSync)

        if (existing == null) {
            val newId = dao.insertStudyNotebook(
                StudyNotebookEntity(
                    title = title.trim().ifBlank { "مادة مستعادة" },
                    preferredMethod = preferredMethod.ifBlank { StudyMethod.FOCUSED_SUMMARY.key },
                    dailyMinutes = dailyMinutes.coerceIn(15, 480),
                    totalDays = totalDays.coerceIn(1, 30),
                    summaryMarkdown = summaryMarkdown,
                    keyPointsText = keyPointsText,
                    dossierJson = dossierJson,
                    mindMapJson = mindMapJson,
                    lastEngineLabel = lastEngineLabel,
                    linkedProjectId = linkedProjectId,
                    createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    syncId = cleanSync,
                    vaultSavedAt = markVaultAt,
                    cloudSyncedAt = markCloudAt
                )
            )
            attachments.forEach { dao.insertStudyAttachment(it.copy(id = 0L, notebookId = newId)) }
            visuals.forEach { dao.insertStudyVisual(it.copy(id = 0L, notebookId = newId)) }
            if (scheduleItems.isNotEmpty()) {
                dao.insertStudyScheduleItems(scheduleItems.map { it.copy(id = 0L, notebookId = newId) })
            }
            messages.forEach { dao.insertStudyMessage(it.copy(id = 0L, notebookId = newId)) }
            return newId
        } else {
            val existingAtts = dao.getStudyAttachments(existing.id)
            val existingVis = dao.getStudyVisuals(existing.id)
            val existingSched = dao.getStudyScheduleList(existing.id)
            val existingMsgs = dao.getStudyMessages(existing.id)

            val shouldOverwriteContent = updatedAt > existing.updatedAt ||
                (existing.summaryMarkdown.isBlank() && summaryMarkdown.isNotBlank())

            if (shouldOverwriteContent) {
                dao.insertStudyNotebook(
                    existing.copy(
                        title = title.trim().ifBlank { existing.title },
                        preferredMethod = preferredMethod.ifBlank { existing.preferredMethod },
                        dailyMinutes = dailyMinutes.coerceIn(15, 480),
                        totalDays = totalDays.coerceIn(1, 30),
                        summaryMarkdown = summaryMarkdown.ifBlank { existing.summaryMarkdown },
                        keyPointsText = keyPointsText.ifBlank { existing.keyPointsText },
                        dossierJson = dossierJson.ifBlank { existing.dossierJson },
                        mindMapJson = mindMapJson.ifBlank { existing.mindMapJson },
                        lastEngineLabel = lastEngineLabel.ifBlank { existing.lastEngineLabel },
                        linkedProjectId = linkedProjectId ?: existing.linkedProjectId,
                        updatedAt = maxOf(updatedAt, existing.updatedAt),
                        vaultSavedAt = maxOf(markVaultAt, existing.vaultSavedAt),
                        cloudSyncedAt = maxOf(markCloudAt, existing.cloudSyncedAt)
                    )
                )
            }

            if (existingAtts.isEmpty() && attachments.isNotEmpty()) {
                attachments.forEach { dao.insertStudyAttachment(it.copy(id = 0L, notebookId = existing.id)) }
            }
            if (existingVis.isEmpty() && visuals.isNotEmpty()) {
                visuals.forEach { dao.insertStudyVisual(it.copy(id = 0L, notebookId = existing.id)) }
            }
            if (existingSched.isEmpty() && scheduleItems.isNotEmpty()) {
                dao.insertStudyScheduleItems(scheduleItems.map { it.copy(id = 0L, notebookId = existing.id) })
            }
            if (existingMsgs.isEmpty() && messages.isNotEmpty()) {
                messages.forEach { dao.insertStudyMessage(it.copy(id = 0L, notebookId = existing.id)) }
            }
            return existing.id
        }
    }

    suspend fun upsertRestoredVoiceTranslation(
        syncId: String,
        sourceLanguageCode: String,
        sourceLanguageName: String,
        targetLanguageCode: String,
        targetLanguageName: String,
        originalText: String,
        translatedText: String,
        createdAt: Long
    ): Long {
        val cleanSync = syncId.trim().ifBlank { UUID.randomUUID().toString() }
        val existing = dao.getVoiceTranslationBySyncId(cleanSync)
        return if (existing == null) {
            dao.insertVoiceTranslation(
                VoiceTranslationEntity(
                    sourceLanguageCode = sourceLanguageCode,
                    sourceLanguageName = sourceLanguageName,
                    targetLanguageCode = targetLanguageCode,
                    targetLanguageName = targetLanguageName,
                    originalText = originalText,
                    translatedText = translatedText,
                    createdAt = createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    syncId = cleanSync
                )
            )
        } else {
            existing.id
        }
    }
}
