package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVideoTranslator
import com.example.data.GeneratedSegment
import com.example.data.LanguageOption
import com.example.data.SubtitleDisplayMode
import com.example.data.SupportedLanguages
import com.example.data.TranslationRepository
import com.example.data.TranslationStyle
import com.example.data.VideoTranslationResult
import com.example.data.db.AppDatabase
import com.example.data.db.SubtitleCueEntity
import com.example.data.db.VideoProjectEntity
import com.example.data.db.VoiceTranslationEntity
import com.example.media.AudioExtractor
import com.example.media.MediaPreparationException
import com.example.media.VoiceRecorder
import kotlinx.coroutines.CancellationException
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
    data class Recording(val amplitude: Float, val elapsedMs: Long) : VoiceUiState
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
        val reTranslateError: String?
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

    private val voiceRecorder: VoiceRecorder by lazy {
        VoiceRecorder(getApplication())
    }

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

    private val _processingState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    private val _voiceUiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val voiceUiState: StateFlow<VoiceUiState> = _voiceUiState.asStateFlow()

    private val _activeProjectId = MutableStateFlow<Long?>(null)
    private val _currentPositionMs = MutableStateFlow(0L)
    private val _displayMode = MutableStateFlow(SubtitleDisplayMode.BILINGUAL)
    private val _fontScale = MutableStateFlow(1.0f)
    private val _isReTranslating = MutableStateFlow(false)
    private val _reTranslateError = MutableStateFlow<String?>(null)

    private var processingJob: Job? = null
    private var recordingMeterJob: Job? = null
    private var voiceProcessingJob: Job? = null

    fun setSourceLanguage(language: LanguageOption) {
        _sourceLanguage.value = language
    }

    fun setTargetLanguage(language: LanguageOption) {
        _targetLanguage.value = language
    }

    fun setTranslationStyle(style: TranslationStyle) {
        _translationStyle.value = style
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

        processingJob = viewModelScope.launch {
            _processingState.value = ProcessingUiState.ExtractingAudio("مقطع فيديو")
            try {
                val payload = AudioExtractor.prepareChunkedForTranslation(getApplication(), uri)
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
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _processingState.value = ProcessingUiState.Idle
    }

    fun dismissProcessingError() {
        _processingState.value = ProcessingUiState.Idle
    }

    fun startVoiceRecording() {
        if (_voiceUiState.value is VoiceUiState.Recording ||
            _voiceUiState.value is VoiceUiState.Translating
        ) return

        try {
            voiceRecorder.startRecording()
            val startTime = System.currentTimeMillis()
            _voiceUiState.value = VoiceUiState.Recording(amplitude = 0.1f, elapsedMs = 0L)

            recordingMeterJob?.cancel()
            recordingMeterJob = viewModelScope.launch {
                while (isActive) {
                    val amp = voiceRecorder.getNormalizedAmplitude()
                    val elapsed = System.currentTimeMillis() - startTime
                    _voiceUiState.value = VoiceUiState.Recording(amp, elapsed)
                    // Auto-stop at 90s to stay compact
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

    fun stopAndTranslateVoice() {
        if (_voiceUiState.value !is VoiceUiState.Recording) return
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
        _voiceUiState.value = VoiceUiState.Idle
    }

    fun dismissVoiceError() {
        _voiceUiState.value = VoiceUiState.Idle
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

        val projectFlow = repository.observeProject(projectId)
        val cuesFlow = repository.observeCues(projectId)

        return combine(
            projectFlow,
            cuesFlow,
            _currentPositionMs,
            _displayMode,
            _fontScale,
            _isReTranslating,
            _reTranslateError
        ) { values ->
            val project = values[0] as VideoProjectEntity?
            @Suppress("UNCHECKED_CAST")
            val cues = values[1] as List<SubtitleCueEntity>
            val posMs = values[2] as Long
            val mode = values[3] as SubtitleDisplayMode
            val scale = values[4] as Float
            val reTranslating = values[5] as Boolean
            val reTranslateErr = values[6] as String?

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
                    reTranslateError = reTranslateErr
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
        viewModelScope.launch {
            _isReTranslating.value = true
            _reTranslateError.value = null
            try {
                val project = repository.getProject(projectId) ?: return@launch
                if (project.targetLanguageCode == newLanguage.code) {
                    _isReTranslating.value = false
                    return@launch
                }
                val cues = repository.getCues(projectId)
                val (newSummary, translatedMap) = translator.retranslateCues(
                    cues = cues,
                    sourceLanguageName = project.sourceLanguageName,
                    newTargetLanguage = newLanguage,
                    translationStyle = chosenStyle
                )
                repository.applyReTranslation(
                    projectId = projectId,
                    targetLanguage = newLanguage,
                    summary = newSummary,
                    translationsByCueId = translatedMap
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

    override fun onCleared() {
        super.onCleared()
        recordingMeterJob?.cancel()
        voiceRecorder.stopAndCleanupOnly()
    }
}
