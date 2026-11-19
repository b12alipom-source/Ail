package com.example.vault

import android.content.Context
import com.example.R
import com.example.data.BayanBackupSnapshot
import com.example.data.FullBackupProjectBundle
import com.example.data.FullBackupStudyBundle
import com.example.data.TranslationRepository
import com.example.data.db.ProjectFolderEntity
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.SubtitleCueEntity
import com.example.data.db.VoiceTranslationEntity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("سجّل الدخول بحساب Google أولاً للمزامنة السحابية.")
    }

    suspend fun uploadFullSnapshot(
        snapshot: BayanBackupSnapshot,
        localRepository: TranslationRepository
    ): Result<Int> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        var syncedCount = 0
        for (folder in snapshot.folders) {
            uploadFolder(uid, folder).getOrElse { return Result.failure(it) }
            syncedCount++
        }
        for (projectBundle in snapshot.projects) {
            uploadVideoProject(uid, projectBundle).getOrElse { return Result.failure(it) }
            localRepository.markProjectCloudSynced(projectBundle.project.id)
            syncedCount++
        }
        for (studyBundle in snapshot.studyNotebooks) {
            uploadStudyNotebook(uid, studyBundle).getOrElse { return Result.failure(it) }
            localRepository.markStudyCloudSynced(studyBundle.notebook.id)
            syncedCount++
        }
        for (voice in snapshot.voiceTranslations) {
            uploadVoiceTranslation(uid, voice).getOrElse { return Result.failure(it) }
            syncedCount++
        }
        return Result.success(syncedCount)
    }

    suspend fun uploadSingleProject(
        bundle: FullBackupProjectBundle,
        localRepository: TranslationRepository
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return uploadVideoProject(uid, bundle).onSuccess {
            localRepository.markProjectCloudSynced(bundle.project.id)
        }
    }

    suspend fun uploadSingleStudyNotebook(
        bundle: FullBackupStudyBundle,
        localRepository: TranslationRepository
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return uploadStudyNotebook(uid, bundle).onSuccess {
            localRepository.markStudyCloudSynced(bundle.notebook.id)
        }
    }

    suspend fun uploadFolder(folder: ProjectFolderEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return uploadFolder(uid, folder)
    }

    suspend fun uploadVoiceTranslation(entry: VoiceTranslationEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        return uploadVoiceTranslation(uid, entry)
    }

    private suspend fun uploadFolder(uid: String, folder: ProjectFolderEntity): Result<Unit> {
        val docId = sanitizeFirestoreDocId(folder.syncId)
        val path = "users/$uid/folders/$docId"
        return try {
            val docRef = db.collection("users").document(uid)
                .collection("folders").document(docId)
            val updateMap = mapOf(
                "name" to folder.name.trim().ifBlank { "مجلد" }.take(100),
                "accentKey" to folder.accentKey.ifBlank { "sapphire" }.take(32),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val createMap = updateMap + mapOf(
                "syncId" to docId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            createOrUpdatePreservingCreatedAt(docRef, createMap, updateMap)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun uploadVideoProject(
        uid: String,
        bundle: FullBackupProjectBundle
    ): Result<Unit> {
        val proj = bundle.project
        val docId = sanitizeFirestoreDocId(proj.syncId)
        val path = "users/$uid/videoProjects/$docId"
        return try {
            val docRef = db.collection("users").document(uid)
                .collection("videoProjects").document(docId)

            val mutableFields = mutableMapOf<String, Any?>(
                "title" to proj.title.trim().ifBlank { "فيديو مترجم" }.take(200),
                "durationMs" to proj.durationMs.coerceIn(0L, 86_400_000L),
                "sourceLanguageCode" to proj.sourceLanguageCode.ifBlank { "auto" }.take(16),
                "sourceLanguageName" to proj.sourceLanguageName.ifBlank { "تلقائي" }.take(64),
                "targetLanguageCode" to proj.targetLanguageCode.ifBlank { "ar" }.take(16),
                "targetLanguageName" to proj.targetLanguageName.ifBlank { "العربية" }.take(64),
                "summary" to proj.summary.take(2000),
                "cueCount" to bundle.cues.size.coerceIn(0, 50_000),
                "folderSyncId" to bundle.folderSyncId?.takeIf { it.isNotBlank() }?.let { sanitizeFirestoreDocId(it) },
                "hasArchivedVideo" to proj.archivedVideoUri.isNotBlank(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }.mapValues { it.value!! }

            val createMap = mutableFields + mapOf(
                "syncId" to docId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            createOrUpdatePreservingCreatedAt(docRef, createMap, mutableFields)

            // Now upload bounded chunks of up to 120 cues
            val chunks = chunkSubtitleCues(bundle.cues, 120)
            val chunksCollection = docRef.collection("cueChunks")
            chunks.forEachIndexed { index, cueBatch ->
                val chunkDocId = "chunk_$index"
                val chunkRef = chunksCollection.document(chunkDocId)
                val cueMaps = cueBatch.map { cue ->
                    mapOf(
                        "orderIndex" to cue.orderIndex,
                        "startMs" to cue.startMs,
                        "endMs" to cue.endMs,
                        "originalText" to cue.originalText.take(800),
                        "translatedText" to cue.translatedText.take(800)
                    )
                }
                val chunkUpdate = mapOf(
                    "cues" to cueMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val chunkCreate = mapOf(
                    "chunkIndex" to index,
                    "cues" to cueMaps,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                createOrUpdatePreservingCreatedAt(chunkRef, chunkCreate, chunkUpdate)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun uploadStudyNotebook(
        uid: String,
        bundle: FullBackupStudyBundle
    ): Result<Unit> {
        val nb = bundle.notebook
        val docId = sanitizeFirestoreDocId(nb.syncId)
        val path = "users/$uid/studyNotebooks/$docId"
        return try {
            val docRef = db.collection("users").document(uid)
                .collection("studyNotebooks").document(docId)

            // Enforce rule size caps so docs remain well below Firestore 1MB limit
            val attList = bundle.attachments.take(25).map { att ->
                mapOf(
                    "displayName" to att.displayName.take(120),
                    "attachmentType" to att.attachmentType.take(40),
                    "mimeType" to att.mimeType.take(64),
                    "extractedText" to att.extractedText.take(6000)
                )
            }
            val scheduleList = bundle.scheduleItems.take(60).map { item ->
                mapOf(
                    "dayNumber" to item.dayNumber,
                    "dayLabel" to item.dayLabel.take(40),
                    "timeSlot" to item.timeSlot.take(60),
                    "taskTitle" to item.taskTitle.take(200),
                    "taskDetail" to item.taskDetail.take(500),
                    "durationMinutes" to item.durationMinutes,
                    "completed" to item.isCompleted
                )
            }
            val messageList = bundle.messages.takeLast(40).map { msg ->
                mapOf(
                    "userRole" to msg.isUser,
                    "messageText" to msg.messageText.take(2500),
                    "offlineGenerated" to msg.isOfflineGenerated,
                    "attachmentName" to msg.attachmentName.take(120),
                    "createdAtMs" to msg.createdAt
                )
            }

            val mutableFields = mutableMapOf<String, Any?>(
                "title" to nb.title.trim().ifBlank { "مادة دراسية" }.take(200),
                "preferredMethod" to nb.preferredMethod.ifBlank { "FOCUSED_SUMMARY" }.take(40),
                "dailyMinutes" to nb.dailyMinutes.coerceIn(1, 600),
                "totalDays" to nb.totalDays.coerceIn(1, 60),
                "summaryMarkdown" to nb.summaryMarkdown.take(120_000),
                "keyPointsText" to nb.keyPointsText.take(20_000),
                "dossierJson" to nb.dossierJson.take(150_000),
                "mindMapJson" to nb.mindMapJson.take(150_000),
                "lastEngineLabel" to nb.lastEngineLabel.take(120),
                "linkedProjectSyncId" to bundle.linkedProjectSyncId?.takeIf { it.isNotBlank() }?.let { sanitizeFirestoreDocId(it) },
                "attachments" to attList,
                "scheduleItems" to scheduleList,
                "messages" to messageList,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }.mapValues { it.value!! }

            val createMap = mutableFields + mapOf(
                "syncId" to docId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            createOrUpdatePreservingCreatedAt(docRef, createMap, mutableFields)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun uploadVoiceTranslation(
        uid: String,
        entry: VoiceTranslationEntity
    ): Result<Unit> {
        val docId = sanitizeFirestoreDocId(entry.syncId)
        val path = "users/$uid/voiceTranslations/$docId"
        return try {
            val docRef = db.collection("users").document(uid)
                .collection("voiceTranslations").document(docId)
            val mutableFields = mapOf(
                "sourceLanguageCode" to entry.sourceLanguageCode.ifBlank { "auto" }.take(16),
                "sourceLanguageName" to entry.sourceLanguageName.ifBlank { "تلقائي" }.take(64),
                "targetLanguageCode" to entry.targetLanguageCode.ifBlank { "ar" }.take(16),
                "targetLanguageName" to entry.targetLanguageName.ifBlank { "العربية" }.take(64),
                "originalText" to entry.originalText.ifBlank { "-" }.take(8000),
                "translatedText" to entry.translatedText.ifBlank { "-" }.take(8000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val createMap = mutableFields + mapOf(
                "syncId" to docId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            createOrUpdatePreservingCreatedAt(docRef, createMap, mutableFields)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreAllFromCloud(localRepository: TranslationRepository): Result<CloudRestoreSummary> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val userRoot = db.collection("users").document(uid)
        var foldersRestored = 0
        var videosRestored = 0
        var studyRestored = 0
        var voiceRestored = 0

        try {
            // 1. Folders
            val folderSnap = userRoot.collection("folders").get().await()
            for (doc in folderSnap.documents) {
                val createdMs = doc.extractMillis("createdAt")
                val updatedMs = doc.extractMillis("updatedAt", createdMs)
                localRepository.upsertRestoredFolder(
                    syncId = doc.getString("syncId") ?: doc.id,
                    name = doc.getString("name").orEmpty(),
                    accentKey = doc.getString("accentKey") ?: "sapphire",
                    createdAt = createdMs,
                    updatedAt = updatedMs
                )
                foldersRestored++
            }

            // 2. Video Projects & Cue Chunks
            val projSnap = userRoot.collection("videoProjects").get().await()
            for (doc in projSnap.documents) {
                val chunkSnap = doc.reference.collection("cueChunks")
                    .orderBy("chunkIndex", Query.Direction.ASCENDING)
                    .get()
                    .await()

                val allCues = mutableListOf<SubtitleCueEntity>()
                for (chunkDoc in chunkSnap.documents) {
                    @Suppress("UNCHECKED_CAST")
                    val rawList = chunkDoc.get("cues") as? List<Map<String, Any?>> ?: emptyList()
                    rawList.forEach { map ->
                        allCues.add(
                            SubtitleCueEntity(
                                projectId = 0L,
                                orderIndex = (map["orderIndex"] as? Number)?.toInt() ?: allCues.size,
                                startMs = (map["startMs"] as? Number)?.toLong() ?: 0L,
                                endMs = (map["endMs"] as? Number)?.toLong() ?: 0L,
                                originalText = map["originalText"] as? String ?: "",
                                translatedText = map["translatedText"] as? String ?: ""
                            )
                        )
                    }
                }

                val createdMs = doc.extractMillis("createdAt")
                val updatedMs = doc.extractMillis("updatedAt", createdMs)
                localRepository.upsertRestoredVideoProject(
                    syncId = doc.getString("syncId") ?: doc.id,
                    title = doc.getString("title").orEmpty(),
                    videoUri = "",
                    archivedVideoUri = "",
                    durationMs = doc.getLong("durationMs") ?: 0L,
                    sourceLanguageCode = doc.getString("sourceLanguageCode") ?: "auto",
                    sourceLanguageName = doc.getString("sourceLanguageName") ?: "تلقائي",
                    targetLanguageCode = doc.getString("targetLanguageCode") ?: "ar",
                    targetLanguageName = doc.getString("targetLanguageName") ?: "العربية",
                    summary = doc.getString("summary").orEmpty(),
                    createdAt = createdMs,
                    updatedAt = updatedMs,
                    folderSyncId = doc.getString("folderSyncId"),
                    cues = allCues,
                    markCloudAt = System.currentTimeMillis()
                )
                videosRestored++
            }

            // 3. Study Notebooks
            val studySnap = userRoot.collection("studyNotebooks").get().await()
            for (doc in studySnap.documents) {
                val createdMs = doc.extractMillis("createdAt")
                val updatedMs = doc.extractMillis("updatedAt", createdMs)

                @Suppress("UNCHECKED_CAST")
                val rawAtts = doc.get("attachments") as? List<Map<String, Any?>> ?: emptyList()
                val attachments = rawAtts.map { map ->
                    StudyAttachmentEntity(
                        notebookId = 0L,
                        displayName = map["displayName"] as? String ?: "مرفق",
                        attachmentType = map["attachmentType"] as? String ?: "text",
                        mimeType = map["mimeType"] as? String ?: "text/plain",
                        localFilePath = "",
                        extractedText = map["extractedText"] as? String ?: "",
                        createdAt = createdMs
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val rawSched = doc.get("scheduleItems") as? List<Map<String, Any?>> ?: emptyList()
                val scheduleItems = rawSched.map { map ->
                    StudyScheduleItemEntity(
                        notebookId = 0L,
                        dayNumber = (map["dayNumber"] as? Number)?.toInt() ?: 1,
                        dayLabel = map["dayLabel"] as? String ?: "اليوم 1",
                        timeSlot = map["timeSlot"] as? String ?: "",
                        taskTitle = map["taskTitle"] as? String ?: "",
                        taskDetail = map["taskDetail"] as? String ?: "",
                        durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 30,
                        isCompleted = map["completed"] as? Boolean ?: false
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val rawMsgs = doc.get("messages") as? List<Map<String, Any?>> ?: emptyList()
                val messages = rawMsgs.map { map ->
                    StudyMessageEntity(
                        notebookId = 0L,
                        isUser = map["userRole"] as? Boolean ?: true,
                        messageText = map["messageText"] as? String ?: "",
                        isOfflineGenerated = map["offlineGenerated"] as? Boolean ?: false,
                        attachmentPath = "",
                        attachmentName = map["attachmentName"] as? String ?: "",
                        attachmentMimeType = "",
                        createdAt = (map["createdAtMs"] as? Number)?.toLong() ?: createdMs
                    )
                }

                localRepository.upsertRestoredStudyNotebook(
                    syncId = doc.getString("syncId") ?: doc.id,
                    title = doc.getString("title").orEmpty(),
                    preferredMethod = doc.getString("preferredMethod") ?: "FOCUSED_SUMMARY",
                    dailyMinutes = (doc.getLong("dailyMinutes") ?: 60L).toInt(),
                    totalDays = (doc.getLong("totalDays") ?: 5L).toInt(),
                    summaryMarkdown = doc.getString("summaryMarkdown").orEmpty(),
                    keyPointsText = doc.getString("keyPointsText").orEmpty(),
                    dossierJson = doc.getString("dossierJson").orEmpty(),
                    mindMapJson = doc.getString("mindMapJson").orEmpty(),
                    lastEngineLabel = doc.getString("lastEngineLabel").orEmpty(),
                    linkedProjectSyncId = doc.getString("linkedProjectSyncId"),
                    createdAt = createdMs,
                    updatedAt = updatedMs,
                    attachments = attachments,
                    visuals = emptyList(),
                    scheduleItems = scheduleItems,
                    messages = messages,
                    markCloudAt = System.currentTimeMillis()
                )
                studyRestored++
            }

            // 4. Voice Translations
            val voiceSnap = userRoot.collection("voiceTranslations").get().await()
            for (doc in voiceSnap.documents) {
                val createdMs = doc.extractMillis("createdAt")
                localRepository.upsertRestoredVoiceTranslation(
                    syncId = doc.getString("syncId") ?: doc.id,
                    sourceLanguageCode = doc.getString("sourceLanguageCode") ?: "auto",
                    sourceLanguageName = doc.getString("sourceLanguageName") ?: "تلقائي",
                    targetLanguageCode = doc.getString("targetLanguageCode") ?: "ar",
                    targetLanguageName = doc.getString("targetLanguageName") ?: "العربية",
                    originalText = doc.getString("originalText").orEmpty(),
                    translatedText = doc.getString("translatedText").orEmpty(),
                    createdAt = createdMs
                )
                voiceRestored++
            }

            return Result.success(
                CloudRestoreSummary(
                    foldersRestored = foldersRestored,
                    videosRestored = videosRestored,
                    studyNotebooksRestored = studyRestored,
                    voiceEntriesRestored = voiceRestored
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid")
            return Result.failure(e)
        }
    }

    private suspend fun createOrUpdatePreservingCreatedAt(
        docRef: DocumentReference,
        createMap: Map<String, Any>,
        updateMap: Map<String, Any>
    ) {
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.update(updateMap).await()
        } else {
            docRef.set(createMap).await()
        }
    }

    private fun DocumentSnapshot.extractMillis(
        field: String,
        fallback: Long = System.currentTimeMillis()
    ): Long {
        val ts: Timestamp? = getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return ts?.toDate()?.time ?: fallback
    }

    private fun sanitizeFirestoreDocId(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_\\-]+"), "_").take(128)
        return cleaned.ifBlank { UUID.randomUUID().toString() }
    }
}
