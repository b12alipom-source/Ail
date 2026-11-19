package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AppCheckDiagnosticState
import com.example.ai.GeminiVideoTranslator
import com.example.ai.OfflineTranslationEngine
import com.example.ai.appCheckDiagnosticState
import com.example.ai.isAppCheckRateLimited
import com.example.ai.verifyOrRefreshAppCheckToken
import com.example.data.FolderAccent
import com.example.data.GeneratedSegment
import com.example.data.LanguageOption
import com.example.data.StudyAiTier
import com.example.data.StudyAttachmentType
import com.example.data.StudyEngineConfig
import com.example.data.StudyMethod
import com.example.data.StudyVisualStyle
import com.example.data.SubtitleDisplayMode
import com.example.data.SupportedLanguages
import com.example.data.TranslationRepository
import com.example.data.TranslationStyle
import com.example.data.VideoTranslationResult
import com.example.data.VoiceTranslationResult
import com.example.data.db.AppDatabase
import com.example.data.db.ProjectFolderEntity
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyNotebookEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.StudyVisualEntity
import com.example.data.db.SubtitleCueEntity
import com.example.data.db.VideoProjectEntity
import com.example.data.db.VoiceTranslationEntity
import com.example.export.SubtitleParser
import com.example.media.AudioExtractor
import com.example.media.LocalVideoFallbackException
import com.example.media.MediaPreparationException
import com.example.media.OfflineSpeechHelper
import com.example.media.OnDeviceVideoTranscriber
import com.example.media.VoiceRecorder
import com.example.media.VoskDownloadProgress
import com.example.media.VoskLiveMicRecognizer
import com.example.media.VoskModelDescriptor
import com.example.media.VoskModelManager
import com.example.security.SecurityAuditReport
import com.example.security.SecurityInspector
import com.example.service.TranslationKeepAliveService
import com.example.study.IngestedAttachment
import com.example.study.StudyAiAssistant
import com.example.study.StudyAiException
import com.example.study.StudyAiPreferences
import com.example.study.StudyAttachmentExtractor
import com.example.study.parseStudyDossierJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BayanViewModel"

sealed interface ProcessingUiState {
    data object Idle : ProcessingUiState
    data class ExtractingAudio(val fileName: String) : ProcessingUiState
    data class Translating(
        val fileName: String,
        val targetLanguageName: String,
        val currentChunk: Int,
        val totalChunks: Int,
        val isOfflineFallback: Boolean = false,
        val statusDetail: String? = null
    ) : ProcessingUiState

    data class Error(val message: String) : ProcessingUiState
}

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data class Recording(val amplitude: Float, val elapsedMs: Long, val isOfflineMode: Boolean = false) : VoiceUiState
    data object Translating : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

sealed interface StudioUiState {
    data object Loading : StudioUiState
    data class Ready(
        val project: VideoProjectEntity,
        val cues: List<SubtitleCueEntity>,
        val activeCueIndex: Int,
        val displayMode: SubtitleDisplayMode,
        val subtitleFontScale: Float,
        val isReTranslating: Boolean,
        val reTranslateError: String?,
        val reTranslateNotice: String?
    ) : StudioUiState

    data class Error(val message: String) : StudioUiState
}

class BayanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TranslationRepository by lazy {
        val dao = AppDatabase.getInstance(getApplication()).translationDao()
        TranslationRepository(dao)
    }

    private val translator: GeminiVideoTranslator by lazy {
        GeminiVideoTranslator()
    }

    private val offlineTranslator: OfflineTranslationEngine by lazy {
        OfflineTranslationEngine()
    }

    private val voskModelManager: VoskModelManager by lazy {
        VoskModelManager(getApplication())
    }

    private val onDeviceVideoTranscriber: OnDeviceVideoTranscriber by lazy {
        OnDeviceVideoTranscriber(getApplication(), offlineTranslator, voskModelManager)
    }

    private val voiceRecorder: VoiceRecorder by lazy {
        VoiceRecorder(getApplication())
    }

    private val offlineSpeechHelper: OfflineSpeechHelper by lazy {
        OfflineSpeechHelper(getApplication())
    }

    private val voskLiveMicRecognizer: VoskLiveMicRecognizer by lazy {
        VoskLiveMicRecognizer()
    }

    private val studyExtractor: StudyAttachmentExtractor by lazy {
        StudyAttachmentExtractor(getApplication())
    }

    private val studyAiPreferences: StudyAiPreferences by lazy {
        StudyAiPreferences(getApplication())
    }

    private val studyAssistant: StudyAiAssistant by lazy {
        StudyAiAssistant()
    }

    private var activeOfflineVoskMic = false

    val securityReport: SecurityAuditReport by lazy {
        SecurityInspector.inspectApp(getApplication())
    }

    val appCheckStatus: StateFlow<AppCheckDiagnosticState> = appCheckDiagnosticState

    val folders: StateFlow<List<ProjectFolderEntity>> = repository.allFolders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId: StateFlow<Long?> = _selectedFolderId.asStateFlow()

    val projects: StateFlow<List<VideoProjectEntity>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val filteredProjects: StateFlow<List<VideoProjectEntity>> = combine(
        projects,
        _selectedFolderId
    ) { all, folderFilter ->
        if (folderFilter == null) all else all.filter { it.folderId == folderFilter }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    val voiceHistory: StateFlow<List<VoiceTranslationEntity>> = repository.allVoiceTranslations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val studyNotebooks: StateFlow<List<StudyNotebookEntity>> = repository.allStudyNotebooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _activeStudyNotebookId = MutableStateFlow<Long?>(null)
    val activeStudyNotebookId: StateFlow<Long?> = _activeStudyNotebookId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStudyAttachments: StateFlow<List<StudyAttachmentEntity>> = _activeStudyNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeStudyAttachments(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStudySchedule: StateFlow<List<StudyScheduleItemEntity>> = _activeStudyNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeStudySchedule(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val studyEngineConfig: StateFlow<StudyEngineConfig>
        get() = studyAiPreferences.config

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStudyMessages: StateFlow<List<StudyMessageEntity>> = _activeStudyNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeStudyMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeStudyVisuals: StateFlow<List<StudyVisualEntity>> = _activeStudyNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeStudyVisuals(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _studyBusyMessage = MutableStateFlow<String?>(null)
    val studyBusyMessage: StateFlow<String?> = _studyBusyMessage.asStateFlow()

    private val _studyError = MutableStateFlow<String?>(null)
    val studyError: StateFlow<String?> = _studyError.asStateFlow()

    private val _studyErrorRequiresSettings = MutableStateFlow(false)
    val studyErrorRequiresSettings: StateFlow<Boolean> = _studyErrorRequiresSettings.asStateFlow()

    private val _studyNotice = MutableStateFlow<String?>(null)
    val studyNotice: StateFlow<String?> = _studyNotice.asStateFlow()

    private val _isGeneratingVisual = MutableStateFlow(false)
    val isGeneratingVisual: StateFlow<Boolean> = _isGeneratingVisual.asStateFlow()

    private val _studyVisualError = MutableStateFlow<String?>(null)
    val studyVisualError: StateFlow<String?> = _studyVisualError.asStateFlow()

    private val _isTutorStreaming = MutableStateFlow(false)
    val isTutorStreaming: StateFlow<Boolean> = _isTutorStreaming.asStateFlow()

    private val _streamingTutorReply = MutableStateFlow("")
    val streamingTutorReply: StateFlow<String> = _streamingTutorReply.asStateFlow()

    private val _pendingChatAttachment = MutableStateFlow<IngestedAttachment?>(null)
    val pendingChatAttachment: StateFlow<IngestedAttachment?> = _pendingChatAttachment.asStateFlow()

    private var visualGenJob: Job? = null
    private var tutorStreamJob: Job? = null

    private val _sourceLanguage = MutableStateFlow(SupportedLanguages.autoDetect)
    val sourceLanguage: StateFlow<LanguageOption> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(SupportedLanguages.allTargetLanguages.first())
    val targetLanguage: StateFlow<LanguageOption> = _targetLanguage.asStateFlow()

    private val _translationStyle = MutableStateFlow(TranslationStyle.BALANCED)
    val translationStyle: StateFlow<TranslationStyle> = _translationStyle.asStateFlow()

    private val _offlineEngineMode = MutableStateFlow(false)
    val offlineEngineMode: StateFlow<Boolean> = _offlineEngineMode.asStateFlow()

    private val _downloadedOfflinePacks = MutableStateFlow<Set<String>>(emptySet())
    val downloadedOfflinePacks: StateFlow<Set<String>> = _downloadedOfflinePacks.asStateFlow()

    private val _downloadingPackCode = MutableStateFlow<String?>(null)
    val downloadingPackCode: StateFlow<String?> = _downloadingPackCode.asStateFlow()

    private val _packActionError = MutableStateFlow<String?>(null)
    val packActionError: StateFlow<String?> = _packActionError.asStateFlow()

    val voskCatalog: List<VoskModelDescriptor>
        get() = voskModelManager.getSupportedCatalog()

    private val _downloadedVoskModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVoskModels: StateFlow<Set<String>> = _downloadedVoskModels.asStateFlow()

    private val _voskDownloadProgress = MutableStateFlow<VoskDownloadProgress?>(null)
    val voskDownloadProgress: StateFlow<VoskDownloadProgress?> = _voskDownloadProgress.asStateFlow()

    private val _voskActionError = MutableStateFlow<String?>(null)
    val voskActionError: StateFlow<String?> = _voskActionError.asStateFlow()

    private val _isCheckingAppCheck = MutableStateFlow(false)
    val isCheckingAppCheck: StateFlow<Boolean> = _isCheckingAppCheck.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    private val _voiceUiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val voiceUiState: StateFlow<VoiceUiState> = _voiceUiState.asStateFlow()

    private val _voiceNotice = MutableStateFlow<String?>(null)
    val voiceNotice: StateFlow<String?> = _voiceNotice.asStateFlow()

    private val _activeProjectId = MutableStateFlow<Long?>(null)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _displayMode = MutableStateFlow(SubtitleDisplayMode.BILINGUAL)
    private val _fontScale = MutableStateFlow(1.0f)
    private val _isReTranslating = MutableStateFlow(false)
    private val _reTranslateError = MutableStateFlow<String?>(null)
    private val _reTranslateNotice = MutableStateFlow<String?>(null)

    private var processingJob: Job? = null
    private var recordingMeterJob: Job? = null
    private var voiceProcessingJob: Job? = null

    init {
        refreshOfflinePacks()
    }

    fun setSourceLanguage(language: LanguageOption) {
        _sourceLanguage.value = language
    }

    fun setTargetLanguage(language: LanguageOption) {
        _targetLanguage.value = language
    }

    fun setTranslationStyle(style: TranslationStyle) {
        _translationStyle.value = style
    }

    fun setOfflineEngineMode(enabled: Boolean) {
        _offlineEngineMode.value = enabled
        _voiceNotice.value = null
    }

    fun refreshOfflinePacks() {
        viewModelScope.launch {
            _downloadedOfflinePacks.value = offlineTranslator.getDownloadedLanguageCodes()
            _downloadedVoskModels.value = voskModelManager.getDownloadedLanguageCodes()
        }
    }

    fun downloadVoskModel(languageCode: String) {
        if (_voskDownloadProgress.value != null) return
        viewModelScope.launch {
            _voskActionError.value = null
            _voskDownloadProgress.value = VoskDownloadProgress(
                langCode = languageCode,
                progressPercent = 0,
                statusLabel = "جاري التحضير للتنزيل…"
            )
            val result = voskModelManager.ensureModelDownloaded(languageCode) { prog ->
                _voskDownloadProgress.value = prog
            }
            result.onFailure { err ->
                _voskActionError.value = err.localizedMessage ?: "تعذر تنزيل نموذج الكلام."
            }
            _voskDownloadProgress.value = null
            _downloadedVoskModels.value = voskModelManager.getDownloadedLanguageCodes()
        }
    }

    fun deleteVoskModel(languageCode: String) {
        if (_voskDownloadProgress.value != null) return
        viewModelScope.launch {
            _voskActionError.value = null
            voskModelManager.deleteModel(languageCode)
            _downloadedVoskModels.value = voskModelManager.getDownloadedLanguageCodes()
        }
    }

    fun dismissVoskError() {
        _voskActionError.value = null
    }

    fun downloadOfflinePack(languageCode: String) {
        if (_downloadingPackCode.value != null) return
        viewModelScope.launch {
            _downloadingPackCode.value = languageCode
            _packActionError.value = null
            val result = offlineTranslator.downloadLanguagePack(languageCode)
            result.onFailure { e ->
                _packActionError.value = e.localizedMessage ?: "تعذر تنزيل حزمة اللغة."
            }
            _downloadedOfflinePacks.value = offlineTranslator.getDownloadedLanguageCodes()
            _downloadingPackCode.value = null
        }
    }

    fun deleteOfflinePack(languageCode: String) {
        if (_downloadingPackCode.value != null) return
        viewModelScope.launch {
            _packActionError.value = null
            offlineTranslator.deleteLanguagePack(languageCode)
            _downloadedOfflinePacks.value = offlineTranslator.getDownloadedLanguageCodes()
        }
    }

    fun dismissPackError() {
        _packActionError.value = null
    }

    fun runAppCheckDiagnostics() {
        if (_isCheckingAppCheck.value) return
        viewModelScope.launch {
            _isCheckingAppCheck.value = true
            try {
                // If already inside a throttle cooldown, verifyOrRefreshAppCheckToken returns immediately without hitting network
                val shouldForce = !isAppCheckRateLimited()
                verifyOrRefreshAppCheckToken(forceRefresh = shouldForce)
            } finally {
                _isCheckingAppCheck.value = false
            }
        }
    }

    fun swapLanguages() {
        val currentSource = _sourceLanguage.value
        val currentTarget = _targetLanguage.value
        if (currentSource.code != SupportedLanguages.autoDetect.code) {
            _sourceLanguage.value = currentTarget
            _targetLanguage.value = currentSource
        } else {
            val fallbackSource = if (currentTarget.code == "ar") {
                SupportedLanguages.findByCode("en")
            } else {
                SupportedLanguages.findByCode("ar")
            }
            _sourceLanguage.value = currentTarget
            _targetLanguage.value = fallbackSource
        }
    }

    fun processSelectedVideo(uri: Uri, onCompleted: (Long) -> Unit) {
        processingJob?.cancel()
        val chosenSource = _sourceLanguage.value
        val chosenTarget = _targetLanguage.value
        val chosenStyle = _translationStyle.value
        val appContext = getApplication<Application>()

        processingJob = viewModelScope.launch {
            _processingState.value = ProcessingUiState.ExtractingAudio("مقطع فيديو")
            TranslationKeepAliveService.updateProgress(
                context = appContext,
                title = "بيان يجهز الفيديو",
                status = "جاري تجهيز المسار الصوتي…"
            )
            try {
                val (initialTitle, initialDurationMs) = withContext(Dispatchers.IO) {
                    AudioExtractor.resolveTitleAndDuration(appContext, uri)
                }

                // If user enabled 100% Offline Mode or App Check is currently in cooldown, jump directly to on-device transcription
                if (_offlineEngineMode.value || isAppCheckRateLimited()) {
                    val reasonLabel = if (_offlineEngineMode.value) {
                        "الوضع المحلي المستقل مفعّل"
                    } else {
                        "التحويل التلقائي للترجمة المحلية (حماية من حظر App Check)"
                    }
                    Log.i(TAG, "Starting video directly with on-device engine: $reasonLabel")
                    val localResult = runOnDeviceVideoPipeline(
                        uri = uri,
                        title = initialTitle,
                        sourceLanguage = chosenSource,
                        targetLanguage = chosenTarget,
                        resumeFromMs = 0L,
                        reasonBanner = reasonLabel
                    )
                    val savedId = repository.saveTranslationProject(
                        title = initialTitle,
                        videoUri = uri.toString(),
                        durationMs = initialDurationMs,
                        targetLanguage = chosenTarget,
                        result = localResult,
                        folderId = _selectedFolderId.value
                    )
                    _processingState.value = ProcessingUiState.Idle
                    onCompleted(savedId)
                    return@launch
                }

                val payload = AudioExtractor.prepareChunkedForTranslation(appContext, uri)
                val totalChunks = payload.chunks.size
                val mergedSegments = mutableListOf<GeneratedSegment>()
                var detectedCode = chosenSource.code
                var detectedNameAr = chosenSource.arabicName
                var combinedSummary = ""
                var previousContext = ""
                var switchedToLocalFallback = false

                for ((index, chunk) in payload.chunks.withIndex()) {
                    _processingState.value = ProcessingUiState.Translating(
                        fileName = payload.title,
                        targetLanguageName = chosenTarget.arabicName,
                        currentChunk = index + 1,
                        totalChunks = totalChunks,
                        isOfflineFallback = false
                    )
                    TranslationKeepAliveService.updateProgress(
                        context = appContext,
                        title = payload.title,
                        status = if (totalChunks > 1) {
                            "جاري ترجمة الجزء ${index + 1} من $totalChunks إلى ${chosenTarget.arabicName}…"
                        } else {
                            "جاري التفريغ والترجمة إلى ${chosenTarget.arabicName}…"
                        },
                        progress = index,
                        max = totalChunks
                    )

                    val chunkOutcome = try {
                        Result.success(
                            translator.transcribeAndTranslateChunk(
                                mediaBytes = chunk.bytes,
                                mimeType = chunk.mimeType,
                                chunkDurationMs = chunk.durationMs,
                                startOffsetMs = chunk.startOffsetMs,
                                sourceLanguage = chosenSource,
                                targetLanguage = chosenTarget,
                                translationStyle = chosenStyle,
                                previousContext = previousContext,
                                allowEmptySegments = totalChunks > 1
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    if (chunkOutcome.isSuccess) {
                        val chunkResult = chunkOutcome.getOrThrow()
                        if (index == 0) {
                            detectedCode = chunkResult.detectedLanguageCode
                            detectedNameAr = chunkResult.detectedLanguageNameArabic
                        }
                        if (chunkResult.summary.isNotBlank()) {
                            combinedSummary = if (combinedSummary.isBlank()) {
                                chunkResult.summary
                            } else {
                                "$combinedSummary ${chunkResult.summary}"
                            }
                        }
                        if (chunkResult.segments.isNotEmpty()) {
                            val trailing = chunkResult.segments.takeLast(2)
                            previousContext = trailing.joinToString("\n") { seg ->
                                "- \"${seg.originalText}\" -> \"${seg.translatedText}\""
                            }
                        }
                        mergedSegments.addAll(chunkResult.segments)
                    } else {
                        val cloudErr = chunkOutcome.exceptionOrNull()!!
                        if (!isCloudFallbackEligible(cloudErr)) {
                            throw cloudErr
                        }
                        Log.w(TAG, "Cloud chunk $index failed; switching automatically to on-device transcription", cloudErr)
                        switchedToLocalFallback = true
                        val resumeMs = mergedSegments.maxOfOrNull { it.endMs } ?: 0L
                        val localPart = runOnDeviceVideoPipeline(
                            uri = uri,
                            title = payload.title,
                            sourceLanguage = chosenSource,
                            targetLanguage = chosenTarget,
                            resumeFromMs = resumeMs,
                            reasonBanner = "تعذر الاتصال السحابي — تم التحويل التلقائي للتفريغ والترجمة محلياً على جهازك"
                        )
                        if (mergedSegments.isEmpty()) {
                            detectedCode = localPart.detectedLanguageCode
                            detectedNameAr = localPart.detectedLanguageNameArabic
                            combinedSummary = localPart.summary
                        } else if (combinedSummary.isBlank()) {
                            combinedSummary = localPart.summary
                        }
                        mergedSegments.addAll(localPart.segments)
                        break
                    }
                }

                if (mergedSegments.isEmpty()) {
                    if (!switchedToLocalFallback) {
                        throw IllegalStateException("لم يتم رصد أي كلام منطوق واضح في هذا المقطع.")
                    } else {
                        throw IllegalStateException("لم يتم رصد كلام واضح في الفيديو. يمكنك استيراد ملف SRT أو المحاولة لاحقاً.")
                    }
                }

                val mergedResult = VideoTranslationResult(
                    detectedLanguageCode = detectedCode,
                    detectedLanguageNameArabic = detectedNameAr,
                    summary = combinedSummary.take(400),
                    segments = mergedSegments.sortedBy { it.startMs }
                )

                val savedProjectId = repository.saveTranslationProject(
                    title = payload.title,
                    videoUri = uri.toString(),
                    durationMs = payload.totalDurationMs,
                    targetLanguage = chosenTarget,
                    result = mergedResult,
                    folderId = _selectedFolderId.value
                )

                _processingState.value = ProcessingUiState.Idle
                onCompleted(savedProjectId)
            } catch (e: CancellationException) {
                _processingState.value = ProcessingUiState.Idle
                throw e
            } catch (e: MediaPreparationException) {
                Log.e(TAG, "Media preparation error", e)
                _processingState.value = ProcessingUiState.Error(
                    e.message ?: "تعذر تجهيز ملف الفيديو."
                )
            } catch (e: LocalVideoFallbackException) {
                Log.e(TAG, "Local video fallback error", e)
                _processingState.value = ProcessingUiState.Error(
                    e.message ?: "تعذر التفريغ الصوتي المحلي لهذا الفيديو."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Video translation failed", e)
                _processingState.value = ProcessingUiState.Error(
                    e.localizedMessage ?: "حدث خطأ أثناء معالجة الفيديو."
                )
            } finally {
                TranslationKeepAliveService.stop(appContext)
            }
        }
    }

    private suspend fun runOnDeviceVideoPipeline(
        uri: Uri,
        title: String,
        sourceLanguage: LanguageOption,
        targetLanguage: LanguageOption,
        resumeFromMs: Long,
        reasonBanner: String
    ): VideoTranslationResult {
        val appContext = getApplication<Application>()
        _processingState.value = ProcessingUiState.Translating(
            fileName = title,
            targetLanguageName = targetLanguage.arabicName,
            currentChunk = 1,
            totalChunks = 1,
            isOfflineFallback = true,
            statusDetail = reasonBanner
        )
        TranslationKeepAliveService.updateProgress(
            context = appContext,
            title = title,
            status = "التفريغ والترجمة المحلية جارية…"
        )
        val result = onDeviceVideoTranscriber.transcribeAndTranslateLocally(
            uri = uri,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            resumeFromMs = resumeFromMs
        ) { stageText, current, total ->
            _processingState.value = ProcessingUiState.Translating(
                fileName = title,
                targetLanguageName = targetLanguage.arabicName,
                currentChunk = current.coerceAtLeast(1),
                totalChunks = total.coerceAtLeast(1),
                isOfflineFallback = true,
                statusDetail = stageText
            )
            TranslationKeepAliveService.updateProgress(
                context = appContext,
                title = title,
                status = stageText,
                progress = current,
                max = total
            )
        }
        refreshOfflinePacks()
        return result
    }

    private fun isCloudFallbackEligible(error: Throwable): Boolean {
        if (error is GeminiVideoTranslator.AppCheckVerificationException ||
            error.cause is GeminiVideoTranslator.AppCheckVerificationException
        ) {
            return true
        }
        val msg = (error.message ?: error.localizedMessage ?: "").lowercase()
        return msg.contains("app check") ||
            msg.contains("appcheck") ||
            msg.contains("too many attempts") ||
            msg.contains("attestation") ||
            msg.contains("التحقق الأمني") ||
            msg.contains("رمز الأمان") ||
            msg.contains("الاتصال بالإنترنت") ||
            msg.contains("مزدحم مؤقتاً") ||
            msg.contains("استغرقت معالجة هذا الجزء وقتاً") ||
            msg.contains("network") ||
            msg.contains("unable to resolve host") ||
            msg.contains("timeout") ||
            msg.contains("403") ||
            msg.contains("429") ||
            msg.contains("503")
    }

    fun importAndTranslateSubtitleFile(uri: Uri, onCompleted: (Long) -> Unit) {
        processingJob?.cancel()
        val chosenSource = _sourceLanguage.value
        val chosenTarget = _targetLanguage.value
        val chosenStyle = _translationStyle.value
        val preferOffline = _offlineEngineMode.value
        val appContext = getApplication<Application>()

        processingJob = viewModelScope.launch {
            val fileTitle = resolveFileName(uri) ?: "ملف ترجمة"
            _processingState.value = ProcessingUiState.Translating(
                fileName = fileTitle,
                targetLanguageName = chosenTarget.arabicName,
                currentChunk = 1,
                totalChunks = 1
            )
            TranslationKeepAliveService.updateProgress(
                context = appContext,
                title = fileTitle,
                status = "جاري قراءة وترجمة ملف الترجمة إلى ${chosenTarget.arabicName}…"
            )
            try {
                val rawText = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("تعذر فتح ملف الترجمة المحدد.")
                }

                val parsedSegments = SubtitleParser.parse(rawText)
                if (parsedSegments.isEmpty()) {
                    throw IllegalStateException("الملف فارغ أو لا يحتوي على توقيت SRT / VTT صالح.")
                }

                val detectedSourceCode = if (chosenSource.code == SupportedLanguages.autoDetect.code) {
                    offlineTranslator.detectLanguageCode(
                        parsedSegments.take(6).joinToString(" ") { it.originalText }
                    )
                } else {
                    chosenSource.code
                }
                val detectedSourceOption = SupportedLanguages.findByCode(detectedSourceCode)

                var translatedSegments: List<GeneratedSegment>? = null
                var summary = "ترجمة ملف ${fileTitle} (${parsedSegments.size} سطراً)."

                if (!preferOffline) {
                    val tempEntities = parsedSegments.mapIndexed { idx, seg ->
                        SubtitleCueEntity(
                            id = idx.toLong() + 1L,
                            projectId = 0L,
                            orderIndex = idx,
                            startMs = seg.startMs,
                            endMs = seg.endMs,
                            originalText = seg.originalText,
                            translatedText = seg.originalText
                        )
                    }
                    runCatching {
                        val (cloudSummary, map) = translator.retranslateCues(
                            cues = tempEntities,
                            sourceLanguageName = detectedSourceOption.arabicName,
                            newTargetLanguage = chosenTarget,
                            translationStyle = chosenStyle
                        )
                        if (cloudSummary.isNotBlank()) summary = cloudSummary
                        translatedSegments = parsedSegments.mapIndexed { idx, seg ->
                            seg.copy(translatedText = map[idx.toLong() + 1L] ?: seg.originalText)
                        }
                    }.onFailure { err ->
                        Log.w(TAG, "Cloud subtitle file translation unavailable, switching to offline ML Kit", err)
                    }
                }

                if (translatedSegments == null) {
                    val translatedLines = offlineTranslator.translateBatch(
                        lines = parsedSegments.map { it.originalText },
                        sourceCode = detectedSourceCode,
                        targetCode = chosenTarget.code
                    )
                    refreshOfflinePacks()
                    summary = "تمت ترجمة الملف محلياً على جهازك (${parsedSegments.size} سطراً) بدون الحاجة لخادم خارجي."
                    translatedSegments = parsedSegments.mapIndexed { idx, seg ->
                        seg.copy(translatedText = translatedLines.getOrElse(idx) { seg.originalText })
                    }
                }

                val finalSegments = translatedSegments!!
                val totalDuration = finalSegments.maxOfOrNull { it.endMs } ?: 0L
                val result = VideoTranslationResult(
                    detectedLanguageCode = detectedSourceCode,
                    detectedLanguageNameArabic = detectedSourceOption.arabicName,
                    summary = summary,
                    segments = finalSegments
                )
                val savedId = repository.saveTranslationProject(
                    title = fileTitle,
                    videoUri = "",
                    durationMs = totalDuration,
                    targetLanguage = chosenTarget,
                    result = result,
                    folderId = _selectedFolderId.value
                )
                _processingState.value = ProcessingUiState.Idle
                onCompleted(savedId)
            } catch (e: CancellationException) {
                _processingState.value = ProcessingUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Subtitle import/translation failed", e)
                _processingState.value = ProcessingUiState.Error(
                    e.localizedMessage ?: "تعذرت ترجمة ملف الترجمة."
                )
            } finally {
                TranslationKeepAliveService.stop(appContext)
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _processingState.value = ProcessingUiState.Idle
        TranslationKeepAliveService.stop(getApplication())
    }

    fun dismissProcessingError() {
        _processingState.value = ProcessingUiState.Idle
    }

    fun startVoiceRecording() {
        if (_voiceUiState.value is VoiceUiState.Recording ||
            _voiceUiState.value is VoiceUiState.Translating
        ) return

        _voiceNotice.value = null
        if (_offlineEngineMode.value || isAppCheckRateLimited()) {
            if (!_offlineEngineMode.value) {
                _voiceNotice.value = "التحقق السحابي في فترة تهدئة — تم التحويل تلقائياً للإملاء والترجمة المحلية."
            }
            startOfflineSpeechDictation()
            return
        }

        try {
            voiceRecorder.startRecording()
            val startTime = System.currentTimeMillis()
            _voiceUiState.value = VoiceUiState.Recording(amplitude = 0.1f, elapsedMs = 0L, isOfflineMode = false)

            recordingMeterJob?.cancel()
            recordingMeterJob = viewModelScope.launch {
                while (isActive) {
                    val amp = voiceRecorder.getNormalizedAmplitude()
                    val elapsed = System.currentTimeMillis() - startTime
                    _voiceUiState.value = VoiceUiState.Recording(amp, elapsed, isOfflineMode = false)
                    if (elapsed >= 90_000L) {
                        stopAndTranslateVoice()
                        break
                    }
                    delay(80L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting voice recording", e)
            voiceRecorder.stopAndCleanupOnly()
            _voiceUiState.value = VoiceUiState.Error(
                e.localizedMessage ?: "تعذر تشغيل الميكروفون."
            )
        }
    }

    fun startOfflineSpeechDictation() {
        if (_voiceUiState.value is VoiceUiState.Recording ||
            _voiceUiState.value is VoiceUiState.Translating
        ) return

        _voiceNotice.value = null
        val chosenSource = _sourceLanguage.value
        val chosenTarget = _targetLanguage.value
        val effectiveSource = if (chosenSource.code == SupportedLanguages.autoDetect.code) {
            val installedVosk = voskModelManager.resolvePreferredInstalledLanguage(chosenTarget.code)
            if (installedVosk != null) {
                SupportedLanguages.findByCode(installedVosk)
            } else if (chosenTarget.code == "ar") {
                SupportedLanguages.findByCode("en")
            } else {
                SupportedLanguages.findByCode("ar")
            }
        } else {
            chosenSource
        }

        val voskModelDir = voskModelManager.getModelDirectoryIfReady(effectiveSource.code)
        val startTime = System.currentTimeMillis()
        _voiceUiState.value = VoiceUiState.Recording(amplitude = 0.15f, elapsedMs = 0L, isOfflineMode = true)
        recordingMeterJob?.cancel()
        recordingMeterJob = viewModelScope.launch {
            while (isActive) {
                val current = _voiceUiState.value as? VoiceUiState.Recording ?: break
                _voiceUiState.value = current.copy(elapsedMs = System.currentTimeMillis() - startTime)
                delay(180L)
            }
        }

        val handleRecognizedText: (String, Boolean) -> Unit = { spokenText, usedVosk ->
            recordingMeterJob?.cancel()
            recordingMeterJob = null
            activeOfflineVoskMic = false
            voiceProcessingJob?.cancel()
            voiceProcessingJob = viewModelScope.launch {
                _voiceUiState.value = VoiceUiState.Translating
                try {
                    val translationSource = if (usedVosk) effectiveSource else chosenSource
                    val offlineRes = offlineTranslator.translateText(
                        text = spokenText,
                        sourceLanguage = translationSource,
                        targetLanguage = chosenTarget
                    )
                    refreshOfflinePacks()
                    val engineSuffix = if (usedVosk) "Vosk محلي" else "محلي"
                    repository.saveVoiceTranslation(
                        chosenTarget,
                        VoiceTranslationResult(
                            detectedLanguageCode = offlineRes.detectedLanguageCode,
                            detectedLanguageNameArabic = "${offlineRes.detectedLanguageNameArabic} ($engineSuffix)",
                            originalText = spokenText,
                            translatedText = offlineRes.translatedText
                        )
                    )
                    _voiceUiState.value = VoiceUiState.Idle
                } catch (e: CancellationException) {
                    _voiceUiState.value = VoiceUiState.Idle
                    throw e
                } catch (e: Exception) {
                    _voiceUiState.value = VoiceUiState.Error(
                        e.localizedMessage ?: "تعذرت الترجمة الصوتية المحلية."
                    )
                }
            }
        }

        if (voskModelDir != null) {
            activeOfflineVoskMic = true
            voskLiveMicRecognizer.startListening(
                scope = viewModelScope,
                modelDir = voskModelDir,
                onRmsChanged = { rms ->
                    val cur = _voiceUiState.value as? VoiceUiState.Recording
                    if (cur != null) {
                        _voiceUiState.value = cur.copy(amplitude = rms)
                    }
                },
                onResult = { spokenText -> handleRecognizedText(spokenText, true) },
                onError = { message ->
                    recordingMeterJob?.cancel()
                    recordingMeterJob = null
                    activeOfflineVoskMic = false
                    _voiceUiState.value = VoiceUiState.Error(message)
                }
            )
            return
        }

        activeOfflineVoskMic = false
        offlineSpeechHelper.startListening(
            languageCode = effectiveSource.code,
            preferOffline = true,
            onRmsChanged = { rms ->
                val cur = _voiceUiState.value as? VoiceUiState.Recording
                if (cur != null) {
                    _voiceUiState.value = cur.copy(amplitude = rms)
                }
            },
            onResult = { spokenText -> handleRecognizedText(spokenText, false) },
            onError = { message ->
                recordingMeterJob?.cancel()
                recordingMeterJob = null
                _voiceUiState.value = VoiceUiState.Error(
                    "$message\nتلميح: ثبّت نموذج Vosk للغة ${effectiveSource.arabicName} من «مركز الاستقلالية والأمان» ليعمل المايك أوفلاين بدون خدمات الجهاز."
                )
            }
        )
    }

    fun stopAndTranslateVoice() {
        val current = _voiceUiState.value as? VoiceUiState.Recording ?: return
        if (current.isOfflineMode) {
            if (activeOfflineVoskMic) {
                voskLiveMicRecognizer.stopListening()
            } else {
                offlineSpeechHelper.stopListening()
            }
            return
        }

        recordingMeterJob?.cancel()
        recordingMeterJob = null

        val chosenSource = _sourceLanguage.value
        val chosenTarget = _targetLanguage.value
        val chosenStyle = _translationStyle.value
        val recentTurnsContext = voiceHistory.value.take(2).reversed().joinToString("\n") { entry ->
            "- \"${entry.originalText}\" -> \"${entry.translatedText}\""
        }

        voiceProcessingJob?.cancel()
        voiceProcessingJob = viewModelScope.launch {
            _voiceUiState.value = VoiceUiState.Translating
            try {
                val audioBytes = voiceRecorder.stopAndReadBytes()
                if (audioBytes.size < 1024) {
                    _voiceUiState.value = VoiceUiState.Error("التسجيل قصير جداً. اضغط وتحدث بوضوح.")
                    return@launch
                }

                val result = translator.translateVoiceClip(
                    audioBytes = audioBytes,
                    mimeType = "audio/mp4",
                    sourceLanguage = chosenSource,
                    targetLanguage = chosenTarget,
                    translationStyle = chosenStyle,
                    conversationContext = recentTurnsContext
                )
                repository.saveVoiceTranslation(chosenTarget, result)
                _voiceUiState.value = VoiceUiState.Idle
            } catch (e: CancellationException) {
                _voiceUiState.value = VoiceUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice translation failed", e)
                _voiceUiState.value = VoiceUiState.Error(
                    e.localizedMessage ?: "تعذرت ترجمة الصوت."
                )
            }
        }
    }

    fun cancelVoiceRecording() {
        recordingMeterJob?.cancel()
        recordingMeterJob = null
        activeOfflineVoskMic = false
        voiceRecorder.stopAndCleanupOnly()
        offlineSpeechHelper.stopAndDestroy()
        voskLiveMicRecognizer.stopAndCancel()
        _voiceUiState.value = VoiceUiState.Idle
    }

    fun translateInstantText(inputText: String) {
        val clean = inputText.trim()
        if (clean.isEmpty()) {
            _voiceUiState.value = VoiceUiState.Error("اكتب أو الصق النص المراد ترجمته أولاً.")
            return
        }
        val chosenSource = _sourceLanguage.value
        val chosenTarget = _targetLanguage.value
        val chosenStyle = _translationStyle.value
        val preferOffline = _offlineEngineMode.value

        _voiceNotice.value = null
        voiceProcessingJob?.cancel()
        voiceProcessingJob = viewModelScope.launch {
            _voiceUiState.value = VoiceUiState.Translating
            try {
                if (preferOffline) {
                    val offlineRes = offlineTranslator.translateText(clean, chosenSource, chosenTarget)
                    refreshOfflinePacks()
                    repository.saveVoiceTranslation(
                        chosenTarget,
                        VoiceTranslationResult(
                            detectedLanguageCode = offlineRes.detectedLanguageCode,
                            detectedLanguageNameArabic = "${offlineRes.detectedLanguageNameArabic} (محلي)",
                            originalText = clean,
                            translatedText = offlineRes.translatedText
                        )
                    )
                } else {
                    val cloudAttempt = runCatching {
                        translator.translateTextDirectly(clean, chosenSource, chosenTarget, chosenStyle)
                    }
                    if (cloudAttempt.isSuccess) {
                        repository.saveVoiceTranslation(chosenTarget, cloudAttempt.getOrThrow())
                    } else {
                        Log.w(TAG, "Cloud text translate unavailable, falling back to offline ML Kit", cloudAttempt.exceptionOrNull())
                        val offlineRes = offlineTranslator.translateText(clean, chosenSource, chosenTarget)
                        refreshOfflinePacks()
                        repository.saveVoiceTranslation(
                            chosenTarget,
                            VoiceTranslationResult(
                                detectedLanguageCode = offlineRes.detectedLanguageCode,
                                detectedLanguageNameArabic = "${offlineRes.detectedLanguageNameArabic} (محلي)",
                                originalText = clean,
                                translatedText = offlineRes.translatedText
                            )
                        )
                        _voiceNotice.value = "تمت الترجمة تلقائياً بالمحرك المحلي المستقل (بدون الاعتماد على السحابة)."
                    }
                }
                _voiceUiState.value = VoiceUiState.Idle
            } catch (e: CancellationException) {
                _voiceUiState.value = VoiceUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Text translation failed", e)
                _voiceUiState.value = VoiceUiState.Error(
                    e.localizedMessage ?: "تعذرت ترجمة النص."
                )
            }
        }
    }

    fun dismissVoiceError() {
        _voiceUiState.value = VoiceUiState.Idle
    }

    fun dismissVoiceNotice() {
        _voiceNotice.value = null
    }

    fun deleteVoiceTranslation(id: Long) {
        viewModelScope.launch {
            repository.deleteVoiceTranslation(id)
        }
    }

    fun selectFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String, accentKey: String = FolderAccent.SAPPHIRE.key) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.createFolder(clean, accentKey)
            _selectedFolderId.value = newId
        }
    }

    fun renameFolder(folderId: Long, newName: String, accentKey: String = FolderAccent.SAPPHIRE.key) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.renameFolder(folderId, clean, accentKey)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
            repository.deleteFolder(folderId)
        }
    }

    fun renameProject(projectId: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.renameProject(projectId, clean)
        }
    }

    fun moveProjectToFolder(projectId: Long, folderId: Long?) {
        viewModelScope.launch {
            repository.moveProjectToFolder(projectId, folderId)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    // --- Study Studio Actions ---
    fun savePersonalGeminiKey(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isEmpty()) return
        studyAiPreferences.savePersonalApiKey(clean, autoActivate = true)
        _studyError.value = null
        _studyErrorRequiresSettings.value = false
        _studyVisualError.value = null
        _studyNotice.value = "تم حفظ وتفعيل مفتاح Gemini الشخصي على جهازك."
    }

    fun clearPersonalGeminiKey() {
        studyAiPreferences.clearPersonalApiKey()
        _studyNotice.value = "تم مسح المفتاح الشخصي والعودة للمسار السحابي الافتراضي."
    }

    fun setPreferPersonalGeminiKey(prefer: Boolean) {
        studyAiPreferences.setPreferPersonalKey(prefer)
        _studyError.value = null
        _studyErrorRequiresSettings.value = false
    }

    fun setStudyAiTier(tier: StudyAiTier) {
        studyAiPreferences.setPreferredTier(tier)
    }

    fun selectStudyNotebook(notebookId: Long?) {
        _activeStudyNotebookId.value = notebookId
        _studyError.value = null
        _studyErrorRequiresSettings.value = false
        _studyVisualError.value = null
        _studyNotice.value = null
        _streamingTutorReply.value = ""
        _pendingChatAttachment.value = null
    }

    fun createStudyNotebook(
        title: String,
        method: StudyMethod = StudyMethod.FOCUSED_SUMMARY,
        dailyMinutes: Int = 60,
        totalDays: Int = 5,
        linkedProjectId: Long? = null
    ) {
        val clean = title.trim().ifBlank { "دفتر دراسة جديد" }
        viewModelScope.launch {
            val newId = repository.createStudyNotebook(
                title = clean,
                method = method,
                dailyMinutes = dailyMinutes,
                totalDays = totalDays,
                linkedProjectId = linkedProjectId
            )
            _activeStudyNotebookId.value = newId
        }
    }

    fun openOrCreateStudyFromProject(projectId: Long) {
        viewModelScope.launch {
            val existing = repository.allStudyNotebooks.first().firstOrNull { it.linkedProjectId == projectId }
            if (existing != null) {
                _activeStudyNotebookId.value = existing.id
                return@launch
            }
            val project = repository.getProject(projectId) ?: return@launch
            val cues = repository.getCues(projectId)
            val newId = repository.createStudyNotebook(
                title = "دراسة: ${project.title}",
                method = StudyMethod.FOCUSED_SUMMARY,
                dailyMinutes = 60,
                totalDays = 4,
                linkedProjectId = projectId
            )
            val transcript = buildString {
                if (project.summary.isNotBlank()) {
                    appendLine("ملخص المقطع: ${project.summary}\n")
                }
                cues.forEach { cue ->
                    appendLine("${cue.translatedText} (${cue.originalText})")
                }
            }.trim()
            repository.addStudyAttachment(
                StudyAttachmentEntity(
                    notebookId = newId,
                    displayName = project.title,
                    attachmentType = StudyAttachmentType.VIDEO_TRANSCRIPT.key,
                    mimeType = "text/plain",
                    localFilePath = "",
                    extractedText = transcript.take(24_000)
                )
            )
            _activeStudyNotebookId.value = newId
        }
    }

    fun updateStudyNotebookConfig(
        notebookId: Long,
        title: String,
        method: StudyMethod,
        dailyMinutes: Int,
        totalDays: Int
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.updateStudyNotebookConfig(notebookId, clean, method, dailyMinutes, totalDays)
        }
    }

    fun deleteStudyNotebook(notebookId: Long) {
        viewModelScope.launch {
            val atts = repository.getStudyAttachments(notebookId)
            atts.forEach { studyExtractor.deleteLocalFileIfExists(it.localFilePath) }
            val visuals = repository.getStudyVisuals(notebookId)
            visuals.forEach { studyExtractor.deleteLocalFileIfExists(it.localImagePath) }
            if (_activeStudyNotebookId.value == notebookId) {
                _activeStudyNotebookId.value = null
            }
            repository.deleteStudyNotebook(notebookId)
        }
    }

    fun attachStudyDocumentUri(uri: Uri) {
        val notebookId = _activeStudyNotebookId.value ?: return
        viewModelScope.launch {
            _studyError.value = null
            _studyBusyMessage.value = "جاري استخلاص وقراءة الملف المرفق…"
            try {
                val ingested = studyExtractor.ingestDocumentOrPdf(uri)
                repository.addStudyAttachment(
                    StudyAttachmentEntity(
                        notebookId = notebookId,
                        displayName = ingested.displayName,
                        attachmentType = ingested.type.key,
                        mimeType = ingested.mimeType,
                        localFilePath = ingested.localFilePath,
                        extractedText = ingested.extractedText
                    )
                )
                _studyNotice.value = "تم إرفاق «${ingested.displayName}» بنجاح."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Attach study document failed", e)
                _studyError.value = e.localizedMessage ?: "تعذر إرفاق الملف المحدد."
            } finally {
                _studyBusyMessage.value = null
            }
        }
    }

    fun attachStudyPhotoUri(uri: Uri) {
        val notebookId = _activeStudyNotebookId.value ?: return
        viewModelScope.launch {
            _studyError.value = null
            _studyBusyMessage.value = "جاري معالجة صورة الدرس وقراءة محتواها…"
            try {
                val ingested = studyExtractor.ingestLecturePhoto(uri)
                repository.addStudyAttachment(
                    StudyAttachmentEntity(
                        notebookId = notebookId,
                        displayName = ingested.displayName,
                        attachmentType = ingested.type.key,
                        mimeType = ingested.mimeType,
                        localFilePath = ingested.localFilePath,
                        extractedText = ingested.extractedText
                    )
                )
                _studyNotice.value = "تمت إضافة صورة الدرس «${ingested.displayName}»."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Attach study photo failed", e)
                _studyError.value = e.localizedMessage ?: "تعذر إرفاق الصورة."
            } finally {
                _studyBusyMessage.value = null
            }
        }
    }

    fun attachStudyTextNote(title: String, noteBody: String) {
        val notebookId = _activeStudyNotebookId.value ?: return
        if (noteBody.isBlank()) {
            _studyError.value = "اكتب نص الملاحظة أو المادة أولاً."
            return
        }
        viewModelScope.launch {
            val ingested = studyExtractor.savePlainNoteAttachment(title, noteBody)
            repository.addStudyAttachment(
                StudyAttachmentEntity(
                    notebookId = notebookId,
                    displayName = ingested.displayName,
                    attachmentType = StudyAttachmentType.TEXT.key,
                    mimeType = ingested.mimeType,
                    localFilePath = ingested.localFilePath,
                    extractedText = ingested.extractedText
                )
            )
            _studyNotice.value = "تم حفظ الملاحظة الدراسية «${ingested.displayName}»."
        }
    }

    fun attachTranslatedProjectToStudy(projectId: Long) {
        val notebookId = _activeStudyNotebookId.value ?: return
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            val cues = repository.getCues(projectId)
            val transcript = buildString {
                if (project.summary.isNotBlank()) {
                    appendLine("ملخص المقطع: ${project.summary}\n")
                }
                cues.forEach { cue ->
                    appendLine("${cue.translatedText} (${cue.originalText})")
                }
            }.trim()
            repository.addStudyAttachment(
                StudyAttachmentEntity(
                    notebookId = notebookId,
                    displayName = project.title,
                    attachmentType = StudyAttachmentType.VIDEO_TRANSCRIPT.key,
                    mimeType = "text/plain",
                    localFilePath = "",
                    extractedText = transcript.take(24_000)
                )
            )
            _studyNotice.value = "تم ربط نص الفيديو «${project.title}» بالمادة."
        }
    }

    fun removeStudyAttachment(attachment: StudyAttachmentEntity) {
        viewModelScope.launch {
            studyExtractor.deleteLocalFileIfExists(attachment.localFilePath)
            repository.deleteStudyAttachment(attachment.id)
        }
    }

    fun generateStudySummaryAndSchedule(
        methodOverride: StudyMethod? = null,
        dailyMinutesOverride: Int? = null,
        totalDaysOverride: Int? = null
    ) {
        val notebookId = _activeStudyNotebookId.value ?: return
        viewModelScope.launch {
            _studyError.value = null
            _studyErrorRequiresSettings.value = false
            _studyNotice.value = null
            _studyBusyMessage.value = "جاري التحليل العميق للمرفقات وبناء الملخص والخريطة الذهنية والجدول…"
            try {
                val notebook = repository.getStudyNotebook(notebookId) ?: return@launch
                val method = methodOverride ?: StudyMethod.fromKey(notebook.preferredMethod)
                val dailyMins = (dailyMinutesOverride ?: notebook.dailyMinutes).coerceIn(15, 480)
                val days = (totalDaysOverride ?: notebook.totalDays).coerceIn(1, 30)

                if (methodOverride != null || dailyMinutesOverride != null || totalDaysOverride != null) {
                    repository.updateStudyNotebookConfig(
                        notebookId = notebookId,
                        title = notebook.title,
                        method = method,
                        dailyMinutes = dailyMins,
                        totalDays = days
                    )
                }

                val attachments = repository.getStudyAttachments(notebookId)
                if (attachments.isEmpty()) {
                    _studyError.value = "أرفق ملف PDF أو صورة أو ملاحظة أو فيديو مترجم أولاً ليقوم المساعد بتحليله."
                    return@launch
                }

                val plan = studyAssistant.generateStudyPlan(
                    config = studyAiPreferences.currentConfig(),
                    notebookTitle = notebook.title,
                    method = method,
                    dailyMinutes = dailyMins,
                    totalDays = days,
                    attachments = attachments,
                    explicitOfflineOnly = _offlineEngineMode.value
                )
                repository.saveGeneratedStudyPlan(notebookId, plan)
                _studyNotice.value = if (plan.isOfflineFallback) {
                    "تم إنشاء الملخص والجدول محلياً على جهازك (وضع أوفلاين المفعّل)."
                } else {
                    "تم إعداد الملخص المتكامل والخريطة الذهنية والجدول (${plan.engineUsedLabel})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: StudyAiException) {
                Log.w(TAG, "Study AI error: ${e.message}", e)
                _studyError.value = e.message
                _studyErrorRequiresSettings.value = e.isAppCheckOrAuthError
            } catch (e: Exception) {
                Log.e(TAG, "Study generation failed", e)
                _studyError.value = e.localizedMessage ?: "تعذر إنشاء الملخص والجدول."
            } finally {
                _studyBusyMessage.value = null
            }
        }
    }

    fun generateStudyVisual(style: StudyVisualStyle) {
        val notebookId = _activeStudyNotebookId.value ?: return
        if (_isGeneratingVisual.value) return
        if (_offlineEngineMode.value) {
            _studyVisualError.value = "توليد ملخصات الصور بالذكاء الاصطناعي يتطلب إيقاف «الوضع المحلي أوفلاين» أو استخدام مفتاح Gemini شخصي."
            return
        }

        visualGenJob?.cancel()
        visualGenJob = viewModelScope.launch {
            _isGeneratingVisual.value = true
            _studyVisualError.value = null
            _studyErrorRequiresSettings.value = false
            try {
                val notebook = repository.getStudyNotebook(notebookId) ?: return@launch
                val attachments = repository.getStudyAttachments(notebookId)
                if (attachments.isEmpty() && notebook.summaryMarkdown.isBlank()) {
                    _studyVisualError.value = "أرفق ملفاً أو ولّد ملخص المادة أولاً ليصمم الذكاء الاصطناعي صورة تلخيص دقيقة لمادتك."
                    return@launch
                }
                val dossier = parseStudyDossierJson(notebook.dossierJson)
                val generated = studyAssistant.generateStudyVisual(
                    config = studyAiPreferences.currentConfig(),
                    notebookTitle = notebook.title,
                    style = style,
                    dossier = dossier,
                    fallbackSummary = notebook.summaryMarkdown,
                    attachments = attachments
                )

                val savedPath = when {
                    generated.bitmap != null -> studyExtractor.saveVisualBitmap(generated.bitmap)
                    generated.rawBytes != null -> studyExtractor.saveVisualBytes(generated.rawBytes)
                    else -> throw IllegalStateException("لم يرجع النموذج بيانات صورة صالحة.")
                }

                repository.addStudyVisual(
                    StudyVisualEntity(
                        notebookId = notebookId,
                        title = generated.title,
                        styleKey = style.key,
                        localImagePath = savedPath,
                        promptSummary = generated.promptSummary,
                        engineLabel = generated.engineLabel
                    )
                )
                _studyNotice.value = "تم توليد «${style.arabicLabel}» وحفظه في معرض المادة."
            } catch (e: CancellationException) {
                throw e
            } catch (e: StudyAiException) {
                _studyVisualError.value = e.message
                _studyErrorRequiresSettings.value = e.isAppCheckOrAuthError
            } catch (e: Exception) {
                Log.e(TAG, "Study visual generation failed", e)
                _studyVisualError.value = e.localizedMessage ?: "تعذر توليد ملخص الصورة."
            } finally {
                _isGeneratingVisual.value = false
            }
        }
    }

    fun saveStudyVisualToGallery(visual: StudyVisualEntity, onShareFallback: (String) -> Unit) {
        viewModelScope.launch {
            val res = studyExtractor.exportVisualToGallery(visual.localImagePath, visual.title)
            res.onSuccess { msg ->
                _studyNotice.value = msg
            }.onFailure { err ->
                if (err is UnsupportedOperationException) {
                    onShareFallback(visual.localImagePath)
                } else {
                    _studyVisualError.value = err.localizedMessage ?: "تعذر حفظ الصورة في المعرض."
                }
            }
        }
    }

    fun deleteStudyVisual(visual: StudyVisualEntity) {
        viewModelScope.launch {
            studyExtractor.deleteLocalFileIfExists(visual.localImagePath)
            repository.deleteStudyVisual(visual.id)
        }
    }

    fun dismissStudyVisualError() {
        _studyVisualError.value = null
    }

    fun attachChatMediaUri(uri: Uri) {
        viewModelScope.launch {
            _studyError.value = null
            _studyBusyMessage.value = "جاري تجهيز المرفق للمحادثة…"
            try {
                val ingested = studyExtractor.ingestChatAttachment(uri)
                _pendingChatAttachment.value = ingested
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat attachment ingest failed", e)
                _studyError.value = e.localizedMessage ?: "تعذر قراءة الملف المرفق."
            } finally {
                _studyBusyMessage.value = null
            }
        }
    }

    fun clearPendingChatAttachment() {
        val current = _pendingChatAttachment.value
        if (current != null) {
            studyExtractor.deleteLocalFileIfExists(current.localFilePath)
            _pendingChatAttachment.value = null
        }
    }

    fun toggleStudyScheduleTask(itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleScheduleItemCompleted(itemId, completed)
        }
    }

    fun sendStudyTutorQuestion(questionText: String) {
        val notebookId = _activeStudyNotebookId.value ?: return
        if (_isTutorStreaming.value) return
        val pendingAtt = _pendingChatAttachment.value
        val cleanQuestion = questionText.trim().ifBlank {
            if (pendingAtt != null) "حلّل هذا المرفق واشرح محتواه أو حلّ المسألة التي فيه خطوة بخطوة." else ""
        }
        if (cleanQuestion.isEmpty()) return

        tutorStreamJob?.cancel()
        tutorStreamJob = viewModelScope.launch {
            _studyError.value = null
            _studyErrorRequiresSettings.value = false
            _isTutorStreaming.value = true
            _streamingTutorReply.value = ""
            _pendingChatAttachment.value = null

            try {
                val notebook = repository.getStudyNotebook(notebookId) ?: return@launch
                val attachments = repository.getStudyAttachments(notebookId)
                val history = repository.getStudyMessages(notebookId)

                repository.addStudyMessage(
                    notebookId = notebookId,
                    isUser = true,
                    text = cleanQuestion,
                    attachmentPath = pendingAtt?.localFilePath.orEmpty(),
                    attachmentName = pendingAtt?.displayName.orEmpty(),
                    attachmentMimeType = pendingAtt?.mimeType.orEmpty()
                )

                val result = studyAssistant.streamStudyTutorReply(
                    config = studyAiPreferences.currentConfig(),
                    notebookTitle = notebook.title,
                    method = StudyMethod.fromKey(notebook.preferredMethod),
                    summary = notebook.summaryMarkdown,
                    dossierJson = notebook.dossierJson,
                    attachments = attachments,
                    history = history,
                    userQuestion = cleanQuestion,
                    inlineAttachment = pendingAtt,
                    explicitOfflineOnly = _offlineEngineMode.value,
                    onChunk = { accumulated ->
                        _streamingTutorReply.value = accumulated
                    }
                )

                repository.addStudyMessage(
                    notebookId = notebookId,
                    isUser = false,
                    text = result.fullText,
                    isOfflineGenerated = result.isOffline
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: StudyAiException) {
                Log.w(TAG, "Study tutor AI error: ${e.message}", e)
                _studyError.value = e.message
                _studyErrorRequiresSettings.value = e.isAppCheckOrAuthError
            } catch (e: Exception) {
                Log.e(TAG, "Study tutor Q&A failed", e)
                _studyError.value = e.localizedMessage ?: "تعذر الرد على سؤالك حالياً."
            } finally {
                _streamingTutorReply.value = ""
                _isTutorStreaming.value = false
            }
        }
    }

    fun clearStudyChat() {
        val notebookId = _activeStudyNotebookId.value ?: return
        tutorStreamJob?.cancel()
        _isTutorStreaming.value = false
        _streamingTutorReply.value = ""
        viewModelScope.launch {
            val msgs = repository.getStudyMessages(notebookId)
            msgs.forEach { msg ->
                if (msg.attachmentPath.isNotBlank()) {
                    studyExtractor.deleteLocalFileIfExists(msg.attachmentPath)
                }
            }
            repository.clearStudyMessages(notebookId)
        }
    }

    fun dismissStudyError() {
        _studyError.value = null
        _studyErrorRequiresSettings.value = false
    }

    fun dismissStudyNotice() {
        _studyNotice.value = null
    }

    fun observeStudioState(projectId: Long): StateFlow<StudioUiState> {
        _activeProjectId.value = projectId
        _currentPositionMs.value = 0L
        _reTranslateError.value = null
        _reTranslateNotice.value = null

        val projectFlow = repository.observeProject(projectId)
        val cuesFlow = repository.observeCues(projectId)

        return combine(
            projectFlow,
            cuesFlow,
            _currentPositionMs,
            _displayMode,
            _fontScale,
            _isReTranslating,
            _reTranslateError,
            _reTranslateNotice
        ) { values ->
            val project = values[0] as VideoProjectEntity?
            @Suppress("UNCHECKED_CAST")
            val cues = values[1] as List<SubtitleCueEntity>
            val posMs = values[2] as Long
            val mode = values[3] as SubtitleDisplayMode
            val scale = values[4] as Float
            val reTranslating = values[5] as Boolean
            val reTranslateErr = values[6] as String?
            val reTranslateNote = values[7] as String?

            if (project == null) {
                StudioUiState.Error("لم يتم العثور على هذا المشروع في السجل المحلي.")
            } else {
                val activeIndex = cues.indexOfFirst { cue ->
                    posMs in cue.startMs..cue.endMs
                }
                StudioUiState.Ready(
                    project = project,
                    cues = cues,
                    activeCueIndex = activeIndex,
                    displayMode = mode,
                    subtitleFontScale = scale,
                    isReTranslating = reTranslating,
                    reTranslateError = reTranslateErr,
                    reTranslateNotice = reTranslateNote
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = StudioUiState.Loading
        )
    }

    fun updatePlaybackPosition(positionMs: Long) {
        _currentPositionMs.value = positionMs
    }

    fun setDisplayMode(mode: SubtitleDisplayMode) {
        _displayMode.value = mode
    }

    fun adjustFontScale(delta: Float) {
        _fontScale.value = (_fontScale.value + delta).coerceIn(0.8f, 1.45f)
    }

    fun updateCueText(cueId: Long, originalText: String, translatedText: String) {
        viewModelScope.launch {
            repository.updateCueText(cueId, originalText.trim(), translatedText.trim())
        }
    }

    fun relinkProjectVideo(projectId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            repository.updateProjectVideoUri(projectId, uri.toString())
        }
    }

    fun changeStudioTargetLanguage(projectId: Long, newLanguage: LanguageOption) {
        if (_isReTranslating.value) return
        val chosenStyle = _translationStyle.value
        val preferOffline = _offlineEngineMode.value
        viewModelScope.launch {
            _isReTranslating.value = true
            _reTranslateError.value = null
            _reTranslateNotice.value = null
            try {
                val project = repository.getProject(projectId) ?: return@launch
                if (project.targetLanguageCode == newLanguage.code) {
                    return@launch
                }
                val cues = repository.getCues(projectId)
                var summary = project.summary
                var translatedMap: Map<Long, String>? = null

                if (!preferOffline) {
                    runCatching {
                        translator.retranslateCues(
                            cues = cues,
                            sourceLanguageName = project.sourceLanguageName,
                            newTargetLanguage = newLanguage,
                            translationStyle = chosenStyle
                        )
                    }.onSuccess { (cloudSummary, map) ->
                        summary = cloudSummary
                        translatedMap = map
                    }.onFailure { err ->
                        Log.w(TAG, "Cloud re-translate failed, falling back to on-device ML Kit", err)
                    }
                }

                if (translatedMap == null) {
                    val translatedLines = offlineTranslator.translateBatch(
                        lines = cues.map { it.originalText },
                        sourceCode = project.sourceLanguageCode,
                        targetCode = newLanguage.code
                    )
                    refreshOfflinePacks()
                    translatedMap = cues.mapIndexed { idx, cue ->
                        cue.id to translatedLines.getOrElse(idx) { cue.translatedText }
                    }.toMap()
                    _reTranslateNotice.value = "تم تبديل اللغة بالمحرك المحلي المستقل (أوفلاين)."
                }

                repository.applyReTranslation(
                    projectId = projectId,
                    targetLanguage = newLanguage,
                    summary = summary,
                    translationsByCueId = translatedMap!!
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Instant re-translation failed", e)
                _reTranslateError.value = e.localizedMessage ?: "تعذرت إعادة الترجمة."
            } finally {
                _isReTranslating.value = false
            }
        }
    }

    fun polishStudioTranslation(projectId: Long) {
        if (_isReTranslating.value) return
        val chosenStyle = _translationStyle.value
        viewModelScope.launch {
            _isReTranslating.value = true
            _reTranslateError.value = null
            _reTranslateNotice.value = null
            try {
                val project = repository.getProject(projectId) ?: return@launch
                val cues = repository.getCues(projectId)
                if (cues.isEmpty()) return@launch
                val targetOption = SupportedLanguages.findByCode(project.targetLanguageCode)
                val (newSummary, polishedMap) = translator.polishCuesWithContext(
                    cues = cues,
                    sourceLanguageName = project.sourceLanguageName,
                    targetLanguage = targetOption,
                    translationStyle = chosenStyle
                )
                repository.applyReTranslation(
                    projectId = projectId,
                    targetLanguage = targetOption,
                    summary = newSummary.ifBlank { project.summary },
                    translationsByCueId = polishedMap
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI polish failed", e)
                _reTranslateError.value = e.localizedMessage ?: "تعذر تحسين الترجمة بالذكاء الاصطناعي."
            } finally {
                _isReTranslating.value = false
            }
        }
    }

    fun dismissReTranslateError() {
        _reTranslateError.value = null
    }

    fun dismissReTranslateNotice() {
        _reTranslateNotice.value = null
    }

    private fun resolveFileName(uri: Uri): String? {
        return runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }

    override fun onCleared() {
        super.onCleared()
        recordingMeterJob?.cancel()
        voiceRecorder.stopAndCleanupOnly()
        offlineSpeechHelper.stopAndDestroy()
        voskLiveMicRecognizer.stopAndCancel()
    }
}
