package com.example.vault

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.BayanBackupSnapshot
import com.example.data.FullBackupProjectBundle
import com.example.data.FullBackupStudyBundle
import com.example.data.SubtitleDisplayMode
import com.example.data.TranslationRepository
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.StudyVisualEntity
import com.example.data.db.SubtitleCueEntity
import com.example.export.SubtitleExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "PermanentDeviceVault"
private const val VAULT_ROOT_FOLDER_NAME = "BayanVault"
private const val DIR_VIDEOS = "videos"
private const val DIR_STUDY = "study"
private const val DIR_VISUALS = "visuals"
private const val DIR_ATTACHMENTS = "attachments"
private const val FILE_FOLDERS_JSON = "folders.json"
private const val FILE_VOICE_JSON = "voice_history.json"
private const val FILE_PROJECT_JSON = "project.json"
private const val FILE_SUBTITLES_SRT = "subtitles.srt"
private const val FILE_SUBTITLES_TXT = "summary_and_subtitles.txt"
private const val FILE_NOTEBOOK_JSON = "notebook.json"
private const val FILE_STUDY_SUMMARY_TXT = "summary.txt"
private const val MAX_ATTACHMENT_COPY_BYTES = 25L * 1024L * 1024L

class PermanentDeviceVaultManager(
    private val context: Context,
    private val repository: TranslationRepository,
    private val preferences: VaultPreferences
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private data class SafChild(
        val docId: String,
        val displayName: String,
        val mimeType: String
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    fun registerPickedVaultFolder(treeUri: Uri): String {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.onFailure { err ->
            Log.w(TAG, "takePersistableUriPermission warning", err)
        }
        val label = resolveTreeFolderLabel(treeUri)
        preferences.saveVaultTreeUri(treeUri, label)
        return label
    }

    suspend fun backupAll(
        includeVideoCopyOverride: Boolean? = null
    ): Result<VaultOperationSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.currentState()
            val treeUri = parseConfiguredTreeUri(prefs)
                ?: throw IllegalStateException("لم يتم اختيار مجلد دائم على الجهاز بعد.")
            val includeVideos = includeVideoCopyOverride ?: prefs.includeVideoCopy

            val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = true)
                ?: throw IllegalStateException("تعذر الوصول إلى المجلد المختار للكتابة.")

            val snapshot = repository.getFullBackupSnapshot()
            writeFoldersManifest(treeUri, rootDocId, snapshot)
            writeVoiceHistoryManifest(treeUri, rootDocId, snapshot)

            var videosSaved = 0
            var videoFilesCopied = 0
            for (bundle in snapshot.projects) {
                val copied = writeProjectToVault(treeUri, rootDocId, bundle, includeVideos)
                videosSaved++
                if (copied) videoFilesCopied++
            }

            var notebooksSaved = 0
            var visualsCopied = 0
            for (study in snapshot.studyNotebooks) {
                val copiedVis = writeStudyNotebookToVault(treeUri, rootDocId, study)
                notebooksSaved++
                visualsCopied += copiedVis
            }

            preferences.markVaultSynced()
            VaultOperationSummary(
                videosSaved = videosSaved,
                videoFilesCopied = videoFilesCopied,
                studyNotebooksSaved = notebooksSaved,
                visualsCopied = visualsCopied,
                voiceEntriesSaved = snapshot.voiceTranslations.size
            )
        }
    }

    suspend fun autoSaveSingleProject(projectId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.currentState()
            if (!prefs.autoSaveEnabled) return@runCatching false
            val treeUri = parseConfiguredTreeUri(prefs) ?: return@runCatching false
            val bundle = repository.getProjectBackupBundle(projectId) ?: return@runCatching false

            val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = true) ?: return@runCatching false
            val snapshot = repository.getFullBackupSnapshot()
            writeFoldersManifest(treeUri, rootDocId, snapshot)
            writeProjectToVault(treeUri, rootDocId, bundle, prefs.includeVideoCopy)
            preferences.markVaultSynced()
            true
        }
    }

    suspend fun autoSaveSingleStudyNotebook(notebookId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.currentState()
            if (!prefs.autoSaveEnabled) return@runCatching false
            val treeUri = parseConfiguredTreeUri(prefs) ?: return@runCatching false
            val bundle = repository.getStudyBackupBundle(notebookId) ?: return@runCatching false

            val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = true) ?: return@runCatching false
            writeStudyNotebookToVault(treeUri, rootDocId, bundle)
            preferences.markVaultSynced()
            true
        }
    }

    suspend fun autoSaveVoiceHistory(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.currentState()
            if (!prefs.autoSaveEnabled) return@runCatching false
            val treeUri = parseConfiguredTreeUri(prefs) ?: return@runCatching false
            val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = true) ?: return@runCatching false
            val snapshot = repository.getFullBackupSnapshot()
            writeVoiceHistoryManifest(treeUri, rootDocId, snapshot)
            preferences.markVaultSynced()
            true
        }
    }

    suspend fun autoSaveFoldersManifest(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = preferences.currentState()
            if (!prefs.autoSaveEnabled) return@runCatching false
            val treeUri = parseConfiguredTreeUri(prefs) ?: return@runCatching false
            val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = true) ?: return@runCatching false
            val snapshot = repository.getFullBackupSnapshot()
            writeFoldersManifest(treeUri, rootDocId, snapshot)
            preferences.markVaultSynced()
            true
        }
    }

    suspend fun restoreFromVault(explicitTreeUri: Uri? = null): Result<VaultRestoreSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val treeUri = explicitTreeUri ?: parseConfiguredTreeUri(preferences.currentState())
                    ?: throw IllegalStateException("اختر مجلد النسخ الدائم على جهازك أولاً للاستعادة.")

                if (explicitTreeUri != null) {
                    registerPickedVaultFolder(explicitTreeUri)
                }

                val rootDocId = resolveVaultRootDocId(treeUri, createIfMissing = false)
                    ?: throw IllegalStateException("المجلد المختار لا يحتوي على نسخة احتياطية لـ BayanVault بعد.")

                var foldersRestored = 0
                var videosRestored = 0
                var videoFilesRelinked = 0
                var studyRestored = 0
                var visualsRestored = 0
                var voiceRestored = 0

                // 1. Folders
                val foldersFile = findChild(treeUri, rootDocId, FILE_FOLDERS_JSON)
                if (foldersFile != null && !foldersFile.isDirectory) {
                    val raw = readTextDocument(treeUri, foldersFile.docId)
                    val manifest = runCatching { json.decodeFromString<VaultFolderManifest>(raw) }.getOrNull()
                    manifest?.folders?.forEach { item ->
                        repository.upsertRestoredFolder(
                            syncId = item.syncId,
                            name = item.name,
                            accentKey = item.accentKey,
                            createdAt = item.createdAt,
                            updatedAt = item.updatedAt
                        )
                        foldersRestored++
                    }
                }

                // 2. Videos
                val videosDir = findChild(treeUri, rootDocId, DIR_VIDEOS)
                if (videosDir != null && videosDir.isDirectory) {
                    val videoSubdirs = listChildren(treeUri, videosDir.docId).filter { it.isDirectory }
                    for (sub in videoSubdirs) {
                        val children = listChildren(treeUri, sub.docId)
                        val projJsonDoc = children.firstOrNull { it.displayName == FILE_PROJECT_JSON && !it.isDirectory }
                            ?: continue
                        val payload = runCatching {
                            json.decodeFromString<VaultVideoProjectPayload>(
                                readTextDocument(treeUri, projJsonDoc.docId)
                            )
                        }.getOrNull() ?: continue

                        val archivedVideoDoc = payload.videoFileName?.let { expectedName ->
                            children.firstOrNull { !it.isDirectory && it.displayName == expectedName }
                        } ?: children.firstOrNull { !it.isDirectory && it.displayName.startsWith("video.") }

                        val archivedUriStr = archivedVideoDoc?.let { doc ->
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.docId).toString()
                        }.orEmpty()
                        if (archivedUriStr.isNotBlank()) videoFilesRelinked++

                        val cues = payload.cues.mapIndexed { idx, cue ->
                            SubtitleCueEntity(
                                projectId = 0L,
                                orderIndex = if (cue.orderIndex >= 0) cue.orderIndex else idx,
                                startMs = cue.startMs,
                                endMs = cue.endMs,
                                originalText = cue.originalText,
                                translatedText = cue.translatedText
                            )
                        }
                        repository.upsertRestoredVideoProject(
                            syncId = payload.syncId,
                            title = payload.title,
                            videoUri = archivedUriStr,
                            archivedVideoUri = archivedUriStr,
                            durationMs = payload.durationMs,
                            sourceLanguageCode = payload.sourceLanguageCode,
                            sourceLanguageName = payload.sourceLanguageName,
                            targetLanguageCode = payload.targetLanguageCode,
                            targetLanguageName = payload.targetLanguageName,
                            summary = payload.summary,
                            createdAt = payload.createdAt,
                            updatedAt = payload.updatedAt,
                            folderSyncId = payload.folderSyncId,
                            cues = cues,
                            markVaultAt = System.currentTimeMillis()
                        )
                        videosRestored++
                    }
                }

                // 3. Study Notebooks (with visuals & attachments restored into app-private storage)
                val studyDir = findChild(treeUri, rootDocId, DIR_STUDY)
                val localAttachmentsRoot = File(context.filesDir, "study-attachments").apply { mkdirs() }
                val localVisualsRoot = File(context.filesDir, "study-visuals").apply { mkdirs() }

                if (studyDir != null && studyDir.isDirectory) {
                    val studySubdirs = listChildren(treeUri, studyDir.docId).filter { it.isDirectory }
                    for (sub in studySubdirs) {
                        val subChildren = listChildren(treeUri, sub.docId)
                        val nbJsonDoc = subChildren.firstOrNull { it.displayName == FILE_NOTEBOOK_JSON && !it.isDirectory }
                            ?: continue
                        val payload = runCatching {
                            json.decodeFromString<VaultStudyNotebookPayload>(
                                readTextDocument(treeUri, nbJsonDoc.docId)
                            )
                        }.getOrNull() ?: continue

                        val visualsSubdir = subChildren.firstOrNull { it.isDirectory && it.displayName == DIR_VISUALS }
                        val visualsChildren = if (visualsSubdir != null) {
                            listChildren(treeUri, visualsSubdir.docId)
                        } else emptyList()

                        val restoredVisuals = payload.visuals.mapNotNull { vis ->
                            val safImg = visualsChildren.firstOrNull {
                                !it.isDirectory && it.displayName == vis.vaultImageFileName
                            }
                            if (safImg == null) return@mapNotNull null
                            val destFile = File(localVisualsRoot, "restored_${UUID.randomUUID()}.png")
                            val copied = copySafDocToLocalFile(treeUri, safImg.docId, destFile)
                            if (!copied) return@mapNotNull null
                            visualsRestored++
                            StudyVisualEntity(
                                notebookId = 0L,
                                title = vis.title,
                                styleKey = vis.styleKey,
                                localImagePath = destFile.absolutePath,
                                promptSummary = vis.promptSummary,
                                engineLabel = vis.engineLabel,
                                createdAt = vis.createdAt
                            )
                        }

                        val attSubdir = subChildren.firstOrNull { it.isDirectory && it.displayName == DIR_ATTACHMENTS }
                        val attChildren = if (attSubdir != null) {
                            listChildren(treeUri, attSubdir.docId)
                        } else emptyList()

                        val restoredAttachments = payload.attachments.map { att ->
                            var restoredLocalPath = ""
                            val relName = att.vaultRelativeFileName
                            if (!relName.isNullOrBlank()) {
                                val safAtt = attChildren.firstOrNull { !it.isDirectory && it.displayName == relName }
                                if (safAtt != null) {
                                    val destFile = File(localAttachmentsRoot, "restored_${UUID.randomUUID()}_${sanitizeVaultSegment(relName, "file")}")
                                    if (copySafDocToLocalFile(treeUri, safAtt.docId, destFile)) {
                                        restoredLocalPath = destFile.absolutePath
                                    }
                                }
                            }
                            StudyAttachmentEntity(
                                notebookId = 0L,
                                displayName = att.displayName,
                                attachmentType = att.attachmentType,
                                mimeType = att.mimeType,
                                localFilePath = restoredLocalPath,
                                extractedText = att.extractedText,
                                createdAt = att.createdAt
                            )
                        }

                        val restoredSchedule = payload.scheduleItems.map { item ->
                            StudyScheduleItemEntity(
                                notebookId = 0L,
                                dayNumber = item.dayNumber,
                                dayLabel = item.dayLabel,
                                timeSlot = item.timeSlot,
                                taskTitle = item.taskTitle,
                                taskDetail = item.taskDetail,
                                durationMinutes = item.durationMinutes,
                                isCompleted = item.completed
                            )
                        }

                        val restoredMessages = payload.messages.map { msg ->
                            StudyMessageEntity(
                                notebookId = 0L,
                                isUser = msg.userRole,
                                messageText = msg.messageText,
                                isOfflineGenerated = msg.offlineGenerated,
                                attachmentPath = "",
                                attachmentName = msg.attachmentName,
                                attachmentMimeType = msg.attachmentMimeType,
                                createdAt = msg.createdAt
                            )
                        }

                        repository.upsertRestoredStudyNotebook(
                            syncId = payload.syncId,
                            title = payload.title,
                            preferredMethod = payload.preferredMethod,
                            dailyMinutes = payload.dailyMinutes,
                            totalDays = payload.totalDays,
                            summaryMarkdown = payload.summaryMarkdown,
                            keyPointsText = payload.keyPointsText,
                            dossierJson = payload.dossierJson,
                            mindMapJson = payload.mindMapJson,
                            lastEngineLabel = payload.lastEngineLabel,
                            linkedProjectSyncId = payload.linkedProjectSyncId,
                            createdAt = payload.createdAt,
                            updatedAt = payload.updatedAt,
                            attachments = restoredAttachments,
                            visuals = restoredVisuals,
                            scheduleItems = restoredSchedule,
                            messages = restoredMessages,
                            markVaultAt = System.currentTimeMillis()
                        )
                        studyRestored++
                    }
                }

                // 4. Voice history
                val voiceFile = findChild(treeUri, rootDocId, FILE_VOICE_JSON)
                if (voiceFile != null && !voiceFile.isDirectory) {
                    val raw = readTextDocument(treeUri, voiceFile.docId)
                    val manifest = runCatching { json.decodeFromString<VaultVoiceHistoryManifest>(raw) }.getOrNull()
                    manifest?.items?.forEach { item ->
                        repository.upsertRestoredVoiceTranslation(
                            syncId = item.syncId,
                            sourceLanguageCode = item.sourceLanguageCode,
                            sourceLanguageName = item.sourceLanguageName,
                            targetLanguageCode = item.targetLanguageCode,
                            targetLanguageName = item.targetLanguageName,
                            originalText = item.originalText,
                            translatedText = item.translatedText,
                            createdAt = item.createdAt
                        )
                        voiceRestored++
                    }
                }

                preferences.markVaultSynced()
                VaultRestoreSummary(
                    foldersRestored = foldersRestored,
                    videosRestored = videosRestored,
                    videoFilesRelinked = videoFilesRelinked,
                    studyNotebooksRestored = studyRestored,
                    visualsRestored = visualsRestored,
                    voiceEntriesRestored = voiceRestored
                )
            }
        }

    /**
     * Zero-permission MediaStore fallback export for completed videos/summaries when the user hasn't
     * selected a SAF Vault folder yet (works on Android 10+ in Movies/Bayan and Downloads/Bayan).
     */
    suspend fun exportVideoSummaryToPublicDownloads(
        title: String,
        summaryText: String,
        srtContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw UnsupportedOperationException("يتطلب التصدير المباشر لمجلد Downloads نظام أندرويد 10 أو أحدث؛ اختر مجلد «BayanVault» بدلاً من ذلك.")
            }
            val safeName = sanitizeVaultSegment(title, "bayan_translation")
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "${safeName}_subtitles.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Bayan")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء ملف في التنزيلات.")
            resolver.openOutputStream(uri)?.use { out ->
                val fullText = buildString {
                    appendLine(title)
                    appendLine("=".repeat(32))
                    if (summaryText.isNotBlank()) {
                        appendLine("الملخص:")
                        appendLine(summaryText)
                        appendLine()
                    }
                    appendLine("الترجمة:")
                    appendLine(srtContent)
                }
                out.write(fullText.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "تم حفظ نسخة نصية دائمة في Downloads/Bayan."
        }
    }

    private suspend fun writeProjectToVault(
        treeUri: Uri,
        rootDocId: String,
        bundle: FullBackupProjectBundle,
        includeVideoFile: Boolean
    ): Boolean {
        val project = bundle.project
        val videosDirId = ensureDirectory(treeUri, rootDocId, DIR_VIDEOS)
        val projectDirName = sanitizeVaultSegment(project.syncId, "proj_${project.id}")
        val projectDirId = ensureDirectory(treeUri, videosDirId, projectDirName)

        var archivedFileName: String? = null
        var videoFileCopiedNow = false

        if (includeVideoFile) {
            val existingChildren = listChildren(treeUri, projectDirId)
            val existingVideoDoc = existingChildren.firstOrNull { !it.isDirectory && it.displayName.startsWith("video.") }
            if (existingVideoDoc != null) {
                archivedFileName = existingVideoDoc.displayName
                val safUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingVideoDoc.docId).toString()
                if (project.archivedVideoUri != safUri) {
                    repository.updateProjectArchivedVideoUri(project.id, safUri)
                }
            } else {
                val candidateUris = selectPlayableUriCandidates(project.videoUri, project.archivedVideoUri)
                for (uriString in candidateUris) {
                    val sourceUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: continue
                    val mime = context.contentResolver.getType(sourceUri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                    val ext = guessVideoExtension(sourceUri, mime)
                    val targetVideoName = "video.$ext"
                    val copiedDocId = copySourceUriToSafFile(treeUri, projectDirId, targetVideoName, mime, sourceUri)
                    if (copiedDocId != null) {
                        archivedFileName = targetVideoName
                        videoFileCopiedNow = true
                        val safUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, copiedDocId).toString()
                        repository.updateProjectArchivedVideoUri(project.id, safUri)
                        break
                    }
                }
            }
        }

        val payload = VaultVideoProjectPayload(
            syncId = project.syncId,
            title = project.title,
            durationMs = project.durationMs,
            sourceLanguageCode = project.sourceLanguageCode,
            sourceLanguageName = project.sourceLanguageName,
            targetLanguageCode = project.targetLanguageCode,
            targetLanguageName = project.targetLanguageName,
            summary = project.summary,
            folderSyncId = bundle.folderSyncId,
            videoFileName = archivedFileName,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt,
            cues = bundle.cues.sortedBy { it.orderIndex }.map { cue ->
                VaultCueItem(
                    orderIndex = cue.orderIndex,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    originalText = cue.originalText,
                    translatedText = cue.translatedText
                )
            }
        )

        writeTextFile(
            treeUri = treeUri,
            parentDocId = projectDirId,
            fileName = FILE_PROJECT_JSON,
            mimeType = "application/json",
            content = json.encodeToString(VaultVideoProjectPayload.serializer(), payload)
        )

        val srtContent = SubtitleExporter.buildExportContent(
            cues = bundle.cues,
            format = com.example.data.ExportFormat.SRT,
            displayMode = SubtitleDisplayMode.BILINGUAL
        )
        writeTextFile(
            treeUri = treeUri,
            parentDocId = projectDirId,
            fileName = FILE_SUBTITLES_SRT,
            mimeType = "application/x-subrip",
            content = srtContent
        )

        val readableSummary = buildString {
            appendLine(project.title)
            appendLine("${project.sourceLanguageName} ← ${project.targetLanguageName}")
            appendLine("=".repeat(36))
            if (project.summary.isNotBlank()) {
                appendLine("\nالملخص:")
                appendLine(project.summary)
            }
            appendLine("\nالترجمة الزمنية:")
            bundle.cues.sortedBy { it.orderIndex }.forEach { cue ->
                appendLine("[${SubtitleExporter.formatReadableTime(cue.startMs)}] ${cue.translatedText}")
                if (cue.originalText.isNotBlank()) {
                    appendLine("   (${cue.originalText})")
                }
            }
        }
        writeTextFile(
            treeUri = treeUri,
            parentDocId = projectDirId,
            fileName = FILE_SUBTITLES_TXT,
            mimeType = "text/plain",
            content = readableSummary
        )

        repository.markProjectVaultSaved(project.id)
        return videoFileCopiedNow
    }

    private suspend fun writeStudyNotebookToVault(
        treeUri: Uri,
        rootDocId: String,
        bundle: FullBackupStudyBundle
    ): Int {
        val nb = bundle.notebook
        val studyDirId = ensureDirectory(treeUri, rootDocId, DIR_STUDY)
        val nbDirName = sanitizeVaultSegment(nb.syncId, "study_${nb.id}")
        val nbDirId = ensureDirectory(treeUri, studyDirId, nbDirName)

        val visualsDirId = ensureDirectory(treeUri, nbDirId, DIR_VISUALS)
        var visualsCopiedCount = 0
        val vaultVisuals = bundle.visuals.mapIndexedNotNull { idx, vis ->
            val srcFile = File(vis.localImagePath)
            if (!srcFile.exists() || !srcFile.isFile || srcFile.length() <= 0L) return@mapIndexedNotNull null
            val targetName = "visual_${idx + 1}_${sanitizeVaultSegment(vis.styleKey, "img")}.png"
            val existing = findChild(treeUri, visualsDirId, targetName)
            if (existing == null) {
                copyLocalFileToSafFile(treeUri, visualsDirId, targetName, "image/png", srcFile)
                visualsCopiedCount++
            }
            VaultStudyVisualItem(
                title = vis.title,
                styleKey = vis.styleKey,
                promptSummary = vis.promptSummary,
                engineLabel = vis.engineLabel,
                vaultImageFileName = targetName,
                createdAt = vis.createdAt
            )
        }

        val attDirId = ensureDirectory(treeUri, nbDirId, DIR_ATTACHMENTS)
        val vaultAttachments = bundle.attachments.mapIndexed { idx, att ->
            var copiedName: String? = null
            if (att.localFilePath.isNotBlank()) {
                val srcFile = File(att.localFilePath)
                if (srcFile.exists() && srcFile.isFile && srcFile.length() in 1L..MAX_ATTACHMENT_COPY_BYTES) {
                    val ext = srcFile.extension.takeIf { it.isNotBlank() } ?: "bin"
                    val candidateName = "att_${idx + 1}_${sanitizeVaultSegment(att.displayName, "doc")}.$ext"
                    if (findChild(treeUri, attDirId, candidateName) == null) {
                        copyLocalFileToSafFile(treeUri, attDirId, candidateName, att.mimeType.ifBlank { "application/octet-stream" }, srcFile)
                    }
                    copiedName = candidateName
                }
            }
            VaultStudyAttachmentItem(
                displayName = att.displayName,
                attachmentType = att.attachmentType,
                mimeType = att.mimeType,
                extractedText = att.extractedText,
                vaultRelativeFileName = copiedName,
                createdAt = att.createdAt
            )
        }

        val payload = VaultStudyNotebookPayload(
            syncId = nb.syncId,
            title = nb.title,
            preferredMethod = nb.preferredMethod,
            dailyMinutes = nb.dailyMinutes,
            totalDays = nb.totalDays,
            summaryMarkdown = nb.summaryMarkdown,
            keyPointsText = nb.keyPointsText,
            dossierJson = nb.dossierJson,
            mindMapJson = nb.mindMapJson,
            lastEngineLabel = nb.lastEngineLabel,
            linkedProjectSyncId = bundle.linkedProjectSyncId,
            createdAt = nb.createdAt,
            updatedAt = nb.updatedAt,
            attachments = vaultAttachments,
            visuals = vaultVisuals,
            scheduleItems = bundle.scheduleItems.map { item ->
                VaultStudyScheduleItem(
                    dayNumber = item.dayNumber,
                    dayLabel = item.dayLabel,
                    timeSlot = item.timeSlot,
                    taskTitle = item.taskTitle,
                    taskDetail = item.taskDetail,
                    durationMinutes = item.durationMinutes,
                    completed = item.isCompleted
                )
            },
            messages = bundle.messages.map { msg ->
                VaultStudyMessageItem(
                    userRole = msg.isUser,
                    messageText = msg.messageText,
                    offlineGenerated = msg.isOfflineGenerated,
                    attachmentName = msg.attachmentName,
                    attachmentMimeType = msg.attachmentMimeType,
                    createdAt = msg.createdAt
                )
            }
        )

        writeTextFile(
            treeUri = treeUri,
            parentDocId = nbDirId,
            fileName = FILE_NOTEBOOK_JSON,
            mimeType = "application/json",
            content = json.encodeToString(VaultStudyNotebookPayload.serializer(), payload)
        )

        val humanText = buildString {
            appendLine(nb.title)
            appendLine("=".repeat(36))
            if (nb.summaryMarkdown.isNotBlank()) {
                appendLine("\nالملخص الشامل:")
                appendLine(nb.summaryMarkdown)
            }
            if (nb.keyPointsText.isNotBlank()) {
                appendLine("\nأهم النقاط:")
                appendLine(nb.keyPointsText)
            }
            if (bundle.scheduleItems.isNotEmpty()) {
                appendLine("\nجدول المذاكرة:")
                bundle.scheduleItems.forEach { task ->
                    val status = if (task.isCompleted) "[✓]" else "[ ]"
                    appendLine("$status ${task.dayLabel} (${task.timeSlot}): ${task.taskTitle} - ${task.taskDetail}")
                }
            }
        }
        writeTextFile(
            treeUri = treeUri,
            parentDocId = nbDirId,
            fileName = FILE_STUDY_SUMMARY_TXT,
            mimeType = "text/plain",
            content = humanText
        )

        repository.markStudyVaultSaved(nb.id)
        return visualsCopiedCount
    }

    private fun writeFoldersManifest(treeUri: Uri, rootDocId: String, snapshot: BayanBackupSnapshot) {
        val manifest = VaultFolderManifest(
            folders = snapshot.folders.map { f ->
                VaultFolderManifestItem(
                    syncId = f.syncId,
                    name = f.name,
                    accentKey = f.accentKey,
                    createdAt = f.createdAt,
                    updatedAt = f.updatedAt
                )
            },
            updatedAt = System.currentTimeMillis()
        )
        writeTextFile(
            treeUri = treeUri,
            parentDocId = rootDocId,
            fileName = FILE_FOLDERS_JSON,
            mimeType = "application/json",
            content = json.encodeToString(VaultFolderManifest.serializer(), manifest)
        )
    }

    private fun writeVoiceHistoryManifest(treeUri: Uri, rootDocId: String, snapshot: BayanBackupSnapshot) {
        val manifest = VaultVoiceHistoryManifest(
            items = snapshot.voiceTranslations.map { v ->
                VaultVoiceTranslationItem(
                    syncId = v.syncId,
                    sourceLanguageCode = v.sourceLanguageCode,
                    sourceLanguageName = v.sourceLanguageName,
                    targetLanguageCode = v.targetLanguageCode,
                    targetLanguageName = v.targetLanguageName,
                    originalText = v.originalText,
                    translatedText = v.translatedText,
                    createdAt = v.createdAt
                )
            },
            updatedAt = System.currentTimeMillis()
        )
        writeTextFile(
            treeUri = treeUri,
            parentDocId = rootDocId,
            fileName = FILE_VOICE_JSON,
            mimeType = "application/json",
            content = json.encodeToString(VaultVoiceHistoryManifest.serializer(), manifest)
        )
    }

    private fun parseConfiguredTreeUri(state: VaultSettingsState): Uri? {
        if (!state.isVaultConfigured) return null
        return runCatching { Uri.parse(state.vaultTreeUri) }.getOrNull()
    }

    private fun resolveTreeFolderLabel(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (docId != null) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (!displayName.isNullOrBlank()) return "$displayName / $VAULT_ROOT_FOLDER_NAME"
        }
        return VAULT_ROOT_FOLDER_NAME
    }

    private fun resolveVaultRootDocId(treeUri: Uri, createIfMissing: Boolean): String? {
        val treeRootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val rootChildren = listChildren(treeUri, treeRootDocId)
        val alreadyVaultDir = rootChildren.any {
            (it.isDirectory && (it.displayName == DIR_VIDEOS || it.displayName == DIR_STUDY)) ||
                (!it.isDirectory && it.displayName == FILE_FOLDERS_JSON)
        }
        if (alreadyVaultDir) return treeRootDocId

        val existingSub = rootChildren.firstOrNull { it.isDirectory && it.displayName == VAULT_ROOT_FOLDER_NAME }
        if (existingSub != null) return existingSub.docId
        if (!createIfMissing) return null
        return ensureDirectory(treeUri, treeRootDocId, VAULT_ROOT_FOLDER_NAME)
    }

    private fun listChildren(treeUri: Uri, parentDocId: String): List<SafChild> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val result = mutableListOf<SafChild>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    result.add(
                        SafChild(
                            docId = cursor.getString(idIdx),
                            displayName = cursor.getString(nameIdx).orEmpty(),
                            mimeType = cursor.getString(mimeIdx).orEmpty()
                        )
                    )
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "listChildren failed for $parentDocId", err)
        }
        return result
    }

    private fun findChild(treeUri: Uri, parentDocId: String, name: String): SafChild? {
        return listChildren(treeUri, parentDocId).firstOrNull { it.displayName == name }
    }

    private fun ensureDirectory(treeUri: Uri, parentDocId: String, dirName: String): String {
        val existing = listChildren(treeUri, parentDocId).firstOrNull {
            it.isDirectory && it.displayName == dirName
        }
        if (existing != null) return existing.docId

        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        ) ?: throw IllegalStateException("تعذر إنشاء المجلد $dirName داخل الذاكرة المختارة.")
        return DocumentsContract.getDocumentId(createdUri)
    }

    private fun writeTextFile(
        treeUri: Uri,
        parentDocId: String,
        fileName: String,
        mimeType: String,
        content: String
    ) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val existing = findChild(treeUri, parentDocId, fileName)
        if (existing != null && !existing.isDirectory) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existing.docId)
            val truncatedOk = runCatching {
                context.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                    out.write(bytes)
                } != null
            }.getOrDefault(false)
            if (truncatedOk) return
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            fileName
        ) ?: throw IllegalStateException("تعذر إنشاء الملف $fileName.")
        context.contentResolver.openOutputStream(createdUri)?.use { out ->
            out.write(bytes)
        } ?: throw IllegalStateException("تعذر الكتابة إلى الملف $fileName.")
    }

    private fun readTextDocument(treeUri: Uri, docId: String): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return context.contentResolver.openInputStream(docUri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }.orEmpty()
    }

    private fun copySourceUriToSafFile(
        treeUri: Uri,
        parentDocId: String,
        targetName: String,
        mimeType: String,
        sourceUri: Uri
    ): String? {
        return runCatching {
            val resolver = context.contentResolver
            val input = resolver.openInputStream(sourceUri) ?: return null
            input.use { stream ->
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                val createdUri = DocumentsContract.createDocument(resolver, parentUri, mimeType, targetName)
                    ?: return null
                resolver.openOutputStream(createdUri)?.use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = stream.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                } ?: return null
                DocumentsContract.getDocumentId(createdUri)
            }
        }.onFailure { err ->
            Log.w(TAG, "copySourceUriToSafFile failed for $targetName", err)
        }.getOrNull()
    }

    private fun copyLocalFileToSafFile(
        treeUri: Uri,
        parentDocId: String,
        targetName: String,
        mimeType: String,
        srcFile: File
    ): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val createdUri = DocumentsContract.createDocument(resolver, parentUri, mimeType, targetName)
                ?: return false
            srcFile.inputStream().use { input ->
                resolver.openOutputStream(createdUri)?.use { out ->
                    input.copyTo(out)
                } ?: return false
            }
            true
        }.getOrDefault(false)
    }

    private fun copySafDocToLocalFile(treeUri: Uri, docId: String, destFile: File): Boolean {
        return runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.openInputStream(docUri)?.use { input ->
                FileOutputStream(destFile).use { out ->
                    input.copyTo(out)
                }
            } ?: return false
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)
    }

    private fun guessVideoExtension(uri: Uri, mimeType: String): String {
        val display = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull().orEmpty()
        val extFromName = display.substringAfterLast('.', "").lowercase()
        if (extFromName in setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v")) {
            return extFromName
        }
        return when {
            mimeType.contains("quicktime") -> "mov"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("x-matroska") -> "mkv"
            else -> "mp4"
        }
    }
}
