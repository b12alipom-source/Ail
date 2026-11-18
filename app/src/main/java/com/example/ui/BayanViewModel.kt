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
import com.example.ai.verifyOrRefreshAppCheckToken
import com.example.data.GeneratedSegment
import com.example.data.LanguageOption
import com.example.data.SubtitleDisplayMode
import com.example.data.SupportedLanguages
import com.example.data.TranslationRepository
import com.example.data.TranslationStyle
import com.example.data.VideoTranslationResult
import com.example.data.VoiceTranslationResult
import com.example.data.db.AppDatabase
import com.example.data.db.SubtitleCueEntity
import com.example.data.db.VideoProjectEntity
import com.example.data.db.VoiceTranslationEntity
import com.example.export.SubtitleParser
import com.example.media.AudioExtractor
import com.example.media.MediaPreparationException
import com.example.media.OfflineSpeechHelper
import com.example.media.VoiceRecorder
import com.example.security.SecurityAuditReport
import com.example.security.SecurityInspector
import com.example.service.TranslationKeepAliveService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
        val totalChunks: Int
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

    private val voiceRecorder: VoiceRecorder by lazy {
        VoiceRecorder(getApplication())
    }

    private val offlineSpeechHelper: OfflineSpeechHelper by lazy {
        OfflineSpeechHelper(getApplication())
    }

    val securityReport: SecurityAuditReport by lazy {
        SecurityInspector.inspectApp(getApplication())
    }

    val appCheckStatus: StateFlow<AppCheckDiagnosticState> = appCheckDiagnosticState

    val projects: StateFlow<List<VideoProjectEntity>> = repository.allProjects
        .stateIn(
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
        }
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
                verifyOrRefreshAppCheckToken(forceRefresh = true, maxAttempts = 2)
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
                status = "جاري استخلاص المسار الصوتي…"
            )
            try {
                val payload = AudioExtractor.prepareChunkedForTranslation(appContext, uri)
                val totalChunks = payload.chunks.size
                val mergedSegments = mutableListOf<GeneratedSegment>()
                var detectedCode = chosenSource.code
                var detectedNameAr = chosenSource.arabicName
                var combinedSummary = ""
                var previousContext = ""

                payload.chunks.forEachIndexed { index, chunk ->
                    _processingState.value = ProcessingUiState.Translating(
                        fileName = payload.title,
                        targetLanguageName = chosenTarget.arabicName,
                        currentChunk = index + 1,
                        totalChunks = totalChunks
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

                    val chunkResult = translator.transcribeAndTranslateChunk(
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
                }

                if (mergedSegments.isEmpty()) {
                    throw IllegalStateException("لم يتم رصد أي كلام منطوق واضح في هذا المقطع.")
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
                    result = mergedResult
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
                    result = result
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
        if (_offlineEngineMode.value) {
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
            if (chosenTarget.code == "ar") SupportedLanguages.findByCode("en") else SupportedLanguages.findByCode("ar")
        } else {
            chosenSource
        }

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

        offlineSpeechHelper.startListening(
            languageCode = effectiveSource.code,
            preferOffline = true,
            onRmsChanged = { rms ->
                val cur = _voiceUiState.value as? VoiceUiState.Recording
                if (cur != null) {
                    _voiceUiState.value = cur.copy(amplitude = rms)
                }
            },
            onResult = { spokenText ->
                recordingMeterJob?.cancel()
                recordingMeterJob = null
                voiceProcessingJob?.cancel()
                voiceProcessingJob = viewModelScope.launch {
                    _voiceUiState.value = VoiceUiState.Translating
                    try {
                        val offlineRes = offlineTranslator.translateText(
                            text = spokenText,
                            sourceLanguage = chosenSource,
                            targetLanguage = chosenTarget
                        )
                        refreshOfflinePacks()
                        repository.saveVoiceTranslation(
                            chosenTarget,
                            VoiceTranslationResult(
                                detectedLanguageCode = offlineRes.detectedLanguageCode,
                                detectedLanguageNameArabic = "${offlineRes.detectedLanguageNameArabic} (محلي)",
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
            },
            onError = { message ->
                recordingMeterJob?.cancel()
                recordingMeterJob = null
                _voiceUiState.value = VoiceUiState.Error(message)
            }
        )
    }

    fun stopAndTranslateVoice() {
        val current = _voiceUiState.value as? VoiceUiState.Recording ?: return
        if (current.isOfflineMode) {
            offlineSpeechHelper.stopListening()
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
        voiceRecorder.stopAndCleanupOnly()
        offlineSpeechHelper.stopAndDestroy()
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

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
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
    }
}
