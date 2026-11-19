package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.SubtitleDisplayMode
import com.example.data.SupportedLanguages
import com.example.data.db.SubtitleCueEntity
import com.example.export.SubtitleExporter
import com.example.ui.BayanViewModel
import com.example.ui.StudioUiState
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.LanguagePickerSheet
import com.example.ui.theme.CoralError
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.IceSapphireGlow
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightBorderSubtle
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SilverMetallic
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure
import kotlinx.coroutines.delay

@Composable
fun StudioScreen(
    projectId: Long,
    viewModel: BayanViewModel,
    onBack: () -> Unit,
    onOpenStudy: () -> Unit = {}
) {
    val studioFlow = remember(projectId) { viewModel.observeStudioState(projectId) }
    val uiState by studioFlow.collectAsStateWithLifecycle()

    var showExportSheet by remember { mutableStateOf(false) }
    var showTargetLangPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var cueToEdit by remember { mutableStateOf<SubtitleCueEntity?>(null) }

    val relinkVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.relinkProjectVideo(projectId, uri)
        }
    }

    Scaffold(
        containerColor = if (isFullscreen) MidnightVoid else MidnightNavy,
        contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing
    ) { innerPadding ->
        when (val state = uiState) {
            StudioUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = IceSapphire)
                }
            }

            is StudioUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = SilverPure
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SilverPure,
                            contentColor = MidnightVoid
                        )
                    ) {
                        Text("العودة للرئيسية")
                    }
                }
            }

            is StudioUiState.Ready -> {
                StudioReadyContent(
                    state = state,
                    isFullscreen = isFullscreen,
                    onFullscreenChange = { isFullscreen = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isFullscreen) Modifier else Modifier.padding(innerPadding)),
                    onBack = onBack,
                    onRenameProject = { showRenameDialog = true },
                    onSendToStudy = {
                        isFullscreen = false
                        viewModel.openOrCreateStudyFromProject(projectId)
                        onOpenStudy()
                    },
                    onOpenExport = { showExportSheet = true },
                    onOpenLanguageSwitch = { showTargetLangPicker = true },
                    onPolishTranslation = { viewModel.polishStudioTranslation(projectId) },
                    onDisplayModeChange = { viewModel.setDisplayMode(it) },
                    onFontScaleAdjust = { viewModel.adjustFontScale(it) },
                    onPlaybackPosition = { viewModel.updatePlaybackPosition(it) },
                    onEditCue = { cueToEdit = it },
                    onRelinkVideo = {
                        relinkVideoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onDismissReTranslateError = { viewModel.dismissReTranslateError() },
                    onDismissReTranslateNotice = { viewModel.dismissReTranslateNotice() }
                )

                if (showExportSheet) {
                    ExportBottomSheet(
                        projectTitle = state.project.title,
                        cues = state.cues,
                        displayMode = state.displayMode,
                        onDismiss = { showExportSheet = false }
                    )
                }

                if (showTargetLangPicker) {
                    LanguagePickerSheet(
                        title = "تبديل لغة الترجمة الفورية",
                        languages = SupportedLanguages.allTargetLanguages,
                        selectedCode = state.project.targetLanguageCode,
                        onSelect = { newLang ->
                            viewModel.changeStudioTargetLanguage(projectId, newLang)
                        },
                        onDismiss = { showTargetLangPicker = false }
                    )
                }

                if (showRenameDialog) {
                    RenameStudioProjectDialog(
                        currentTitle = state.project.title,
                        onDismiss = { showRenameDialog = false },
                        onSave = { newTitle ->
                            viewModel.renameProject(projectId, newTitle)
                            showRenameDialog = false
                        }
                    )
                }

                cueToEdit?.let { editing ->
                    EditCueDialog(
                        cue = editing,
                        onDismiss = { cueToEdit = null },
                        onSave = { newOriginal, newTranslated ->
                            viewModel.updateCueText(editing.id, newOriginal, newTranslated)
                            cueToEdit = null
                        }
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun StudioReadyContent(
    state: StudioUiState.Ready,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onRenameProject: () -> Unit,
    onSendToStudy: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenLanguageSwitch: () -> Unit,
    onPolishTranslation: () -> Unit,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onFontScaleAdjust: (Float) -> Unit,
    onPlaybackPosition: (Long) -> Unit,
    onEditCue: (SubtitleCueEntity) -> Unit,
    onRelinkVideo: () -> Unit,
    onDismissReTranslateError: () -> Unit,
    onDismissReTranslateNotice: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVideoUnavailable by remember(state.project.videoUri, state.project.archivedVideoUri) { mutableStateOf(false) }
    val playbackCandidates = remember(state.project.videoUri, state.project.archivedVideoUri) {
        com.example.vault.selectPlayableUriCandidates(
            state.project.videoUri,
            state.project.archivedVideoUri
        )
    }
    var playbackCandidateIndex by remember(state.project.id, playbackCandidates) { mutableStateOf(0) }
    val activePlaybackUri = playbackCandidates.getOrNull(playbackCandidateIndex)

    BackHandler(enabled = isFullscreen) {
        onFullscreenChange(false)
    }

    DisposableEffect(isFullscreen, context, view) {
        val activity = context.findActivity()
        if (activity != null) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val act = context.findActivity()
            if (act != null && !act.isChangingConfigurations) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(view, isVideoUnavailable, state.isReTranslating) {
        val shouldKeepAwake = !isVideoUnavailable || state.isReTranslating
        view.keepScreenOn = shouldKeepAwake
        onDispose {
            view.keepScreenOn = false
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(activePlaybackUri) {
        if (activePlaybackUri.isNullOrBlank()) {
            isVideoUnavailable = true
            return@LaunchedEffect
        }
        isVideoUnavailable = false
        runCatching {
            val uri = Uri.parse(activePlaybackUri)
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        }.onFailure {
            if (playbackCandidateIndex + 1 < playbackCandidates.size) {
                playbackCandidateIndex++
            } else {
                isVideoUnavailable = true
            }
        }
    }

    DisposableEffect(exoPlayer, playbackCandidates.size) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (playbackCandidateIndex + 1 < playbackCandidates.size) {
                    playbackCandidateIndex++
                } else {
                    isVideoUnavailable = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Millisecond sync loop between ExoPlayer and subtitle overlay / transcript list
    LaunchedEffect(exoPlayer, isVideoUnavailable) {
        if (!isVideoUnavailable) {
            while (true) {
                onPlaybackPosition(exoPlayer.currentPosition)
                delay(100L)
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.activeCueIndex) {
        val index = state.activeCueIndex
        if (index >= 0 && exoPlayer.isPlaying && !isFullscreen) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val isVisible = visibleItems.any { it.index == index }
            if (!isVisible) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val activeCue = state.cues.getOrNull(state.activeCueIndex)

    if (isFullscreen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MidnightVoid)
        ) {
            VideoPlayerCard(
                exoPlayer = exoPlayer,
                isVideoUnavailable = isVideoUnavailable,
                activeCue = activeCue,
                displayMode = state.displayMode,
                fontScale = state.subtitleFontScale,
                isFullscreen = true,
                title = state.project.title,
                onToggleFullscreen = { onFullscreenChange(false) },
                onDisplayModeChange = onDisplayModeChange,
                onFontScaleAdjust = onFontScaleAdjust,
                onRelinkVideo = onRelinkVideo,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Column(modifier = modifier) {
        StudioTopBar(
            title = state.project.title,
            targetLanguageName = state.project.targetLanguageName,
            onBack = onBack,
            onRenameProject = onRenameProject,
            onSendToStudy = onSendToStudy,
            onOpenLanguageSwitch = onOpenLanguageSwitch,
            onOpenExport = onOpenExport
        )

        AnimatedVisibility(visible = state.isReTranslating) {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    color = IceSapphire,
                    trackColor = MidnightBorderSubtle,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.retranslating_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = IceSapphireGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSurface)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        state.reTranslateError?.let { errorMsg ->
            Surface(
                color = CoralError.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverPure,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissReTranslateError) {
                        Text("إغلاق", color = SilverPure)
                    }
                }
            }
        }

        state.reTranslateNotice?.let { noteMsg ->
            Surface(
                color = IceSapphire.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = noteMsg,
                        style = MaterialTheme.typography.labelMedium,
                        color = IceSapphireGlow,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissReTranslateNotice) {
                        Text("حسناً", color = SilverPure)
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth >= 720.dp

            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VideoPlayerCard(
                            exoPlayer = exoPlayer,
                            isVideoUnavailable = isVideoUnavailable,
                            activeCue = activeCue,
                            displayMode = state.displayMode,
                            fontScale = state.subtitleFontScale,
                            isFullscreen = false,
                            title = state.project.title,
                            onToggleFullscreen = { onFullscreenChange(true) },
                            onDisplayModeChange = onDisplayModeChange,
                            onFontScaleAdjust = onFontScaleAdjust,
                            onRelinkVideo = onRelinkVideo
                        )
                        SubtitleControlsToolbar(
                            displayMode = state.displayMode,
                            onDisplayModeChange = onDisplayModeChange,
                            onFontScaleAdjust = onFontScaleAdjust
                        )
                        if (state.project.summary.isNotBlank()) {
                            SummaryPillCard(summary = state.project.summary)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TranscriptSectionHeader(
                            sourceName = state.project.sourceLanguageName,
                            targetName = state.project.targetLanguageName,
                            cueCount = state.cues.size,
                            isBusy = state.isReTranslating,
                            onPolishClick = onPolishTranslation
                        )
                        TranscriptCueList(
                            cues = state.cues,
                            activeCueIndex = state.activeCueIndex,
                            displayMode = state.displayMode,
                            listState = listState,
                            onCueClick = { cue ->
                                exoPlayer.seekTo(cue.startMs)
                                exoPlayer.play()
                                onPlaybackPosition(cue.startMs)
                            },
                            onEditCue = onEditCue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VideoPlayerCard(
                        exoPlayer = exoPlayer,
                        isVideoUnavailable = isVideoUnavailable,
                        activeCue = activeCue,
                        displayMode = state.displayMode,
                        fontScale = state.subtitleFontScale,
                        isFullscreen = false,
                        title = state.project.title,
                        onToggleFullscreen = { onFullscreenChange(true) },
                        onDisplayModeChange = onDisplayModeChange,
                        onFontScaleAdjust = onFontScaleAdjust,
                        onRelinkVideo = onRelinkVideo
                    )

                    SubtitleControlsToolbar(
                        displayMode = state.displayMode,
                        onDisplayModeChange = onDisplayModeChange,
                        onFontScaleAdjust = onFontScaleAdjust
                    )

                    if (state.project.summary.isNotBlank()) {
                        SummaryPillCard(summary = state.project.summary)
                    }

                    TranscriptSectionHeader(
                        sourceName = state.project.sourceLanguageName,
                        targetName = state.project.targetLanguageName,
                        cueCount = state.cues.size,
                        isBusy = state.isReTranslating,
                        onPolishClick = onPolishTranslation
                    )

                    TranscriptCueList(
                        cues = state.cues,
                        activeCueIndex = state.activeCueIndex,
                        displayMode = state.displayMode,
                        listState = listState,
                        onCueClick = { cue ->
                            exoPlayer.seekTo(cue.startMs)
                            exoPlayer.play()
                            onPlaybackPosition(cue.startMs)
                        },
                        onEditCue = onEditCue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    targetLanguageName: String,
    onBack: () -> Unit,
    onRenameProject: () -> Unit,
    onSendToStudy: () -> Unit,
    onOpenLanguageSwitch: () -> Unit,
    onOpenExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .testTag("studio_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.studio_back),
                tint = SilverPure
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onRenameProject() }
                .padding(horizontal = 6.dp, vertical = 10.dp)
                .testTag("studio_rename_project_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "تسمية المقطع",
                tint = SilverMuted,
                modifier = Modifier.size(15.dp)
            )
        }

        IconButton(
            onClick = onSendToStudy,
            modifier = Modifier
                .size(48.dp)
                .testTag("studio_open_study_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = "إرسال لمساعد الدراسة",
                tint = IceSapphireGlow
            )
        }

        Surface(
            shape = RoundedCornerShape(99.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier
                .testTag("studio_language_button")
                .clickable { onOpenLanguageSwitch() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = "تبديل لغة الترجمة",
                    tint = IceSapphireGlow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = targetLanguageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverPure
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onOpenExport,
            modifier = Modifier
                .size(48.dp)
                .testTag("export_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.IosShare,
                contentDescription = stringResource(R.string.studio_export),
                tint = SilverPure
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerCard(
    exoPlayer: ExoPlayer,
    isVideoUnavailable: Boolean,
    activeCue: SubtitleCueEntity?,
    displayMode: SubtitleDisplayMode,
    fontScale: Float,
    isFullscreen: Boolean,
    title: String,
    onToggleFullscreen: () -> Unit,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onFontScaleAdjust: (Float) -> Unit,
    onRelinkVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp)
    val baseModifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    }

    Surface(
        shape = cardShape,
        color = MidnightVoid,
        border = if (isFullscreen) null else BorderStroke(1.dp, MidnightBorder),
        modifier = baseModifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
        ) {
            if (isVideoUnavailable) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = SilverMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.video_unavailable_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverMetallic,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRelinkVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MidnightSurfaceHigh,
                            contentColor = SilverPure
                        ),
                        border = BorderStroke(1.dp, MidnightBorder)
                    ) {
                        Text(stringResource(R.string.relink_video_cta))
                    }
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                controllerAutoShow = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { playerView ->
                            if (playerView.player !== exoPlayer) {
                                playerView.player = exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (activeCue != null) {
                    LiveSubtitleOverlay(
                        cue = activeCue,
                        displayMode = displayMode,
                        fontScale = if (isFullscreen) fontScale * 1.15f else fontScale,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = if (isFullscreen) 32.dp else 14.dp,
                                end = if (isFullscreen) 32.dp else 14.dp,
                                bottom = if (isFullscreen) 78.dp else 52.dp
                            )
                    )
                }
            }

            if (isFullscreen) {
                FullscreenOverlayHud(
                    title = title,
                    displayMode = displayMode,
                    onDisplayModeChange = onDisplayModeChange,
                    onFontScaleAdjust = onFontScaleAdjust,
                    onExitFullscreen = onToggleFullscreen,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else if (!isVideoUnavailable) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightVoid.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, SilverPure.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("studio_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "ملء الشاشة بالعرض",
                            tint = SilverPure
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenOverlayHud(
    title: String,
    displayMode: SubtitleDisplayMode,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onFontScaleAdjust: (Float) -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightVoid.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, SilverPure.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onExitFullscreen,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("studio_exit_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.FullscreenExit,
                    contentDescription = "الخروج من ملء الشاشة",
                    tint = SilverPure
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SilverPure,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            listOf(
                SubtitleDisplayMode.BILINGUAL to "مزدوج",
                SubtitleDisplayMode.TRANSLATED_ONLY to "مترجم",
                SubtitleDisplayMode.ORIGINAL_ONLY to "أصلي"
            ).forEach { (mode, label) ->
                val selected = displayMode == mode
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (selected) IceSapphire.copy(alpha = 0.28f) else MidnightSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, if (selected) IceSapphireGlow else MidnightBorderSubtle),
                    modifier = Modifier.clickable { onDisplayModeChange(mode) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) SilverPure else SilverMetallic,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MidnightSurface.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, MidnightBorderSubtle)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 36.dp)
                            .clickable { onFontScaleAdjust(-0.1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "A-", style = MaterialTheme.typography.labelSmall, color = SilverMetallic)
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 36.dp)
                            .clickable { onFontScaleAdjust(0.1f) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "A+", style = MaterialTheme.typography.labelSmall, color = SilverPure)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSubtitleOverlay(
    cue: SubtitleCueEntity,
    displayMode: SubtitleDisplayMode,
    fontScale: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidnightVoid.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, SilverPure.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (displayMode == SubtitleDisplayMode.BILINGUAL ||
                displayMode == SubtitleDisplayMode.TRANSLATED_ONLY
            ) {
                Text(
                    text = cue.translatedText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (15f * fontScale).sp,
                        lineHeight = (22f * fontScale).sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = SilverPure,
                    textAlign = TextAlign.Center
                )
            }
            if (displayMode == SubtitleDisplayMode.BILINGUAL ||
                displayMode == SubtitleDisplayMode.ORIGINAL_ONLY
            ) {
                Text(
                    text = cue.originalText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (12.5f * fontScale).sp,
                        lineHeight = (18f * fontScale).sp
                    ),
                    color = if (displayMode == SubtitleDisplayMode.ORIGINAL_ONLY) {
                        SilverPure
                    } else {
                        IceSapphireGlow
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SubtitleControlsToolbar(
    displayMode: SubtitleDisplayMode,
    onDisplayModeChange: (SubtitleDisplayMode) -> Unit,
    onFontScaleAdjust: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                SubtitleDisplayMode.BILINGUAL to stringResource(R.string.mode_bilingual),
                SubtitleDisplayMode.TRANSLATED_ONLY to stringResource(R.string.mode_translated),
                SubtitleDisplayMode.ORIGINAL_ONLY to stringResource(R.string.mode_original)
            ).forEach { (mode, label) ->
                val selected = displayMode == mode
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) SilverPure else MidnightSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) SilverPure else MidnightBorderSubtle
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mode_${mode.name.lowercase()}")
                        .clickable { onDisplayModeChange(mode) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MidnightVoid else SilverMetallic,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorderSubtle)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 38.dp)
                        .clickable { onFontScaleAdjust(-0.1f) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A-",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverMetallic
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 38.dp)
                        .clickable { onFontScaleAdjust(0.1f) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A+",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverPure
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPillCard(summary: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.summary_label),
                style = MaterialTheme.typography.labelMedium,
                color = IceSapphireGlow
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = SilverMetallic
            )
        }
    }
}

@Composable
private fun TranscriptSectionHeader(
    sourceName: String,
    targetName: String,
    cueCount: Int,
    isBusy: Boolean,
    onPolishClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.transcript_title),
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure
            )
            Text(
                text = "$sourceName ← $targetName ($cueCount)",
                style = MaterialTheme.typography.labelMedium,
                color = SilverMuted
            )
        }
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(1.dp, IceSapphire.copy(alpha = 0.55f)),
            modifier = Modifier
                .testTag("studio_polish_button")
                .clickable(enabled = !isBusy) { onPolishClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "تحسين ذكي للترجمة",
                    tint = IceSapphireGlow,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "تحسين ذكي",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SilverPure
                )
            }
        }
    }
}

@Composable
private fun TranscriptCueList(
    cues: List<SubtitleCueEntity>,
    activeCueIndex: Int,
    displayMode: SubtitleDisplayMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCueClick: (SubtitleCueEntity) -> Unit,
    onEditCue: (SubtitleCueEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(cues, key = { _, cue -> cue.id }) { index, cue ->
            val isActive = index == activeCueIndex
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isActive) MidnightSurfaceHigh else MidnightSurface,
                border = BorderStroke(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive) IceSapphire else MidnightBorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cue_item_$index")
                    .clickable { onCueClick(cue) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${SubtitleExporter.formatReadableTime(cue.startMs)} → ${SubtitleExporter.formatReadableTime(cue.endMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) IceSapphireGlow else SilverMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (displayMode == SubtitleDisplayMode.BILINGUAL ||
                            displayMode == SubtitleDisplayMode.TRANSLATED_ONLY
                        ) {
                            Text(
                                text = cue.translatedText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = SilverPure
                            )
                        }
                        if (displayMode == SubtitleDisplayMode.BILINGUAL ||
                            displayMode == SubtitleDisplayMode.ORIGINAL_ONLY
                        ) {
                            if (displayMode == SubtitleDisplayMode.BILINGUAL) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = cue.originalText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (displayMode == SubtitleDisplayMode.ORIGINAL_ONLY) {
                                    SilverPure
                                } else {
                                    SilverMuted
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { onEditCue(cue) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_cue_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "تعديل السطر",
                            tint = SilverMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameStudioProjectDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(currentTitle) { mutableStateOf(currentTitle) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = SilverPure,
        unfocusedTextColor = SilverMetallic,
        focusedBorderColor = IceSapphire,
        unfocusedBorderColor = MidnightBorder,
        focusedLabelColor = IceSapphireGlow,
        unfocusedLabelColor = SilverMuted
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text("تسمية المقطع") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text("الاسم الجديد للمقطع") },
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank()
            ) {
                Text("حفظ", color = IceSapphireGlow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = SilverMuted)
            }
        }
    )
}

@Composable
private fun EditCueDialog(
    cue: SubtitleCueEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var originalDraft by remember(cue.id) { mutableStateOf(cue.originalText) }
    var translatedDraft by remember(cue.id) { mutableStateOf(cue.translatedText) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = SilverPure,
        unfocusedTextColor = SilverMetallic,
        focusedBorderColor = IceSapphire,
        unfocusedBorderColor = MidnightBorder,
        focusedLabelColor = IceSapphireGlow,
        unfocusedLabelColor = SilverMuted
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text(stringResource(R.string.edit_cue_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = translatedDraft,
                    onValueChange = { translatedDraft = it },
                    label = { Text("الترجمة") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = originalDraft,
                    onValueChange = { originalDraft = it },
                    label = { Text("النص الأصلي") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(originalDraft, translatedDraft) },
                enabled = translatedDraft.isNotBlank() || originalDraft.isNotBlank()
            ) {
                Text("حفظ", color = IceSapphireGlow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = SilverMuted)
            }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
