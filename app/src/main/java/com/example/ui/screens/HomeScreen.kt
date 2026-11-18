package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AppCheckDiagnosticState
import com.example.data.LanguageOption
import com.example.data.SupportedLanguages
import com.example.data.TranslationStyle
import com.example.data.db.VideoProjectEntity
import com.example.export.SubtitleExporter
import com.example.ui.BayanViewModel
import com.example.ui.ProcessingUiState
import com.example.ui.components.CloudAndApkSheet
import com.example.ui.components.LanguagePickerSheet
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
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

@Composable
fun HomeScreen(
    viewModel: BayanViewModel,
    outerPadding: PaddingValues,
    onOpenProject: (Long) -> Unit
) {
    val context = LocalContext.current
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val sourceLanguage by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val translationStyle by viewModel.translationStyle.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val appCheckStatus by viewModel.appCheckStatus.collectAsStateWithLifecycle()
    val isCheckingAppCheck by viewModel.isCheckingAppCheck.collectAsStateWithLifecycle()
    val offlineEngineMode by viewModel.offlineEngineMode.collectAsStateWithLifecycle()
    val downloadedOfflinePacks by viewModel.downloadedOfflinePacks.collectAsStateWithLifecycle()
    val downloadingPackCode by viewModel.downloadingPackCode.collectAsStateWithLifecycle()
    val packActionError by viewModel.packActionError.collectAsStateWithLifecycle()
    val downloadedVoskModels by viewModel.downloadedVoskModels.collectAsStateWithLifecycle()
    val voskDownloadProgress by viewModel.voskDownloadProgress.collectAsStateWithLifecycle()
    val voskActionError by viewModel.voskActionError.collectAsStateWithLifecycle()
    val view = LocalView.current

    val isActivelyProcessing = processingState is ProcessingUiState.ExtractingAudio ||
        processingState is ProcessingUiState.Translating
    DisposableEffect(view, isActivelyProcessing) {
        view.keepScreenOn = isActivelyProcessing
        onDispose {
            view.keepScreenOn = false
        }
    }

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showCloudInfoSheet by remember { mutableStateOf(false) }
    var projectPendingDelete by remember { mutableStateOf<VideoProjectEntity?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processSelectedVideo(uri, onCompleted = onOpenProject)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val subtitleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAndTranslateSubtitleFile(uri, onCompleted = onOpenProject)
        }
    }

    val launchVideoPicker = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MidnightVoid, MidnightNavy, Color(0xFF0D172B))
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IceSapphire.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.width * 0.72f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.width * 0.72f
                )
            }
            .padding(outerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)
            ) {
                BayanTopHeader(
                    offlineEngineMode = offlineEngineMode,
                    appCheckStatus = appCheckStatus,
                    onCloudBadgeClick = { showCloudInfoSheet = true }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    HeroTranslationStudioCard(
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        translationStyle = translationStyle,
                        onSelectStyle = { viewModel.setTranslationStyle(it) },
                        onSelectSourceClick = { showSourcePicker = true },
                        onSelectTargetClick = { showTargetPicker = true },
                        onSwapLanguages = { viewModel.swapLanguages() },
                        onPickVideoClick = launchVideoPicker,
                        onImportSubtitlesClick = {
                            subtitleImportLauncher.launch(
                                arrayOf("text/*", "application/x-subrip", "application/octet-stream")
                            )
                        }
                    )
                }

                if (processingState is ProcessingUiState.Error) {
                    item {
                        ErrorBannerCard(
                            message = (processingState as ProcessingUiState.Error).message,
                            onOpenCenter = { showCloudInfoSheet = true },
                            onDismiss = { viewModel.dismissProcessingError() }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = null,
                                tint = SilverMetallic,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.recent_translations_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = SilverPure
                            )
                        }
                        if (projects.isNotEmpty()) {
                            Text(
                                text = "${projects.size} مقاطع",
                                style = MaterialTheme.typography.labelMedium,
                                color = SilverMuted
                            )
                        }
                    }
                }

                if (projects.isEmpty()) {
                    item {
                        EmptyHistoryOnboardingCard()
                    }
                } else {
                    items(projects, key = { it.id }) { project ->
                        ProjectHistoryCard(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            onDeleteClick = { projectPendingDelete = project }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = processingState is ProcessingUiState.ExtractingAudio ||
                processingState is ProcessingUiState.Translating,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            ProcessingOverlay(
                state = processingState,
                onCancel = { viewModel.cancelProcessing() }
            )
        }
    }

    if (showCloudInfoSheet) {
        CloudAndApkSheet(
            appCheckStatus = appCheckStatus,
            isCheckingAppCheck = isCheckingAppCheck,
            offlineEngineMode = offlineEngineMode,
            downloadedOfflinePacks = downloadedOfflinePacks,
            downloadingPackCode = downloadingPackCode,
            packActionError = packActionError,
            voskCatalog = viewModel.voskCatalog,
            downloadedVoskModels = downloadedVoskModels,
            voskDownloadProgress = voskDownloadProgress,
            voskActionError = voskActionError,
            securityReport = viewModel.securityReport,
            onToggleOfflineMode = { viewModel.setOfflineEngineMode(it) },
            onRunAppCheckDiagnostics = { viewModel.runAppCheckDiagnostics() },
            onDownloadPack = { viewModel.downloadOfflinePack(it) },
            onDeletePack = { viewModel.deleteOfflinePack(it) },
            onDismissPackError = { viewModel.dismissPackError() },
            onDownloadVoskModel = { viewModel.downloadVoskModel(it) },
            onDeleteVoskModel = { viewModel.deleteVoskModel(it) },
            onDismissVoskError = { viewModel.dismissVoskError() },
            onDismiss = { showCloudInfoSheet = false }
        )
    }

    if (showSourcePicker) {
        LanguagePickerSheet(
            title = "اختر لغة المحتوى الأصلية",
            languages = SupportedLanguages.allSourceLanguages,
            selectedCode = sourceLanguage.code,
            onSelect = { viewModel.setSourceLanguage(it) },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerSheet(
            title = "اختر لغة الترجمة المطلوبة",
            languages = SupportedLanguages.allTargetLanguages,
            selectedCode = targetLanguage.code,
            onSelect = { viewModel.setTargetLanguage(it) },
            onDismiss = { showTargetPicker = false }
        )
    }

    projectPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            containerColor = MidnightSurfaceHigh,
            titleContentColor = SilverPure,
            textContentColor = SilverMetallic,
            title = { Text("حذف المشروع من السجل؟") },
            text = { Text("سيتم حذف «${pending.title}» ونصوصه المترجمة من السجل المحلي نهائياً.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(pending.id)
                        projectPendingDelete = null
                    }
                ) {
                    Text("حذف", color = CoralError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDelete = null }) {
                    Text("إلغاء", color = SilverPure)
                }
            }
        )
    }
}

@Composable
private fun BayanTopHeader(
    offlineEngineMode: Boolean,
    appCheckStatus: AppCheckDiagnosticState,
    onCloudBadgeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = SilverPure
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = SilverMuted
            )
        }
        Surface(
            shape = RoundedCornerShape(99.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier
                .testTag("cloud_status_badge")
                .clickable { onCloudBadgeClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (badgeIcon, badgeColor) = when {
                    offlineEngineMode -> Icons.Outlined.CloudOff to EmeraldSuccess
                    appCheckStatus is AppCheckDiagnosticState.RateLimited -> Icons.Outlined.CloudOff to CoralError
                    appCheckStatus is AppCheckDiagnosticState.AttentionNeeded -> Icons.Outlined.VerifiedUser to IceSapphireGlow
                    else -> Icons.Outlined.CloudDone to EmeraldSuccess
                }
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = stringResource(R.string.cloud_status_title),
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val ver = stringResource(R.string.version_badge)
                Text(
                    text = if (offlineEngineMode) "محلي مستقل • $ver" else stringResource(R.string.cloud_status_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverPure
                )
            }
        }
    }
}

@Composable
private fun HeroTranslationStudioCard(
    sourceLanguage: LanguageOption,
    targetLanguage: LanguageOption,
    translationStyle: TranslationStyle,
    onSelectStyle: (TranslationStyle) -> Unit,
    onSelectSourceClick: () -> Unit,
    onSelectTargetClick: () -> Unit,
    onSwapLanguages: () -> Unit,
    onPickVideoClick: () -> Unit,
    onImportSubtitlesClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightBorder),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MidnightSurfaceHigh.copy(alpha = 0.72f),
                            MidnightSurface
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageSelectorChip(
                    label = stringResource(R.string.source_language_label),
                    language = sourceLanguage,
                    onClick = onSelectSourceClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("source_language_button")
                )

                Surface(
                    shape = CircleShape,
                    color = MidnightNavy,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("swap_languages_button")
                        .clickable { onSwapLanguages() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages_desc),
                            tint = SilverPure,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                LanguageSelectorChip(
                    label = stringResource(R.string.target_language_label),
                    language = targetLanguage,
                    onClick = onSelectTargetClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("target_language_button")
                )
            }

            TranslationStyleSelectorStrip(
                selectedStyle = translationStyle,
                onSelectStyle = onSelectStyle
            )

            Button(
                onClick = onPickVideoClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SilverPure,
                    contentColor = MidnightVoid
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("pick_video_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.VideoFile,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.pick_video_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onImportSubtitlesClick,
                border = BorderStroke(1.dp, MidnightBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_subtitles_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = IceSapphireGlow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.import_subtitles_cta),
                    style = MaterialTheme.typography.labelLarge,
                    color = SilverPure
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ClosedCaption,
                    contentDescription = null,
                    tint = IceSapphire,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.pick_video_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverMuted
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectorChip(
    label: String,
    language: LanguageOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightNavy.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SilverMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = language.arabicName,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MidnightSurfaceHigh
                ) {
                    Text(
                        text = language.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = IceSapphireGlow,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationStyleSelectorStrip(
    selectedStyle: TranslationStyle,
    onSelectStyle: (TranslationStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = IceSapphireGlow,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "نمط الذكاء الاصطناعي: ${selectedStyle.arabicHint}",
                style = MaterialTheme.typography.labelMedium,
                color = SilverMetallic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TranslationStyle.entries.forEach { style ->
                val isSelected = style == selectedStyle
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (isSelected) IceSapphire.copy(alpha = 0.22f) else MidnightNavy.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) IceSapphireGlow else MidnightBorderSubtle
                    ),
                    modifier = Modifier
                        .testTag("style_${style.name.lowercase()}")
                        .clickable { onSelectStyle(style) }
                ) {
                    Text(
                        text = style.arabicLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) SilverPure else SilverMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryOnboardingCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MidnightSurface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = IceSapphireGlow,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_history_title),
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure
            )
            Text(
                text = stringResource(R.string.empty_history_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = SilverMuted
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    stringResource(R.string.step_pick),
                    stringResource(R.string.step_process),
                    stringResource(R.string.step_export)
                ).forEach { step ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightNavy,
                        border = BorderStroke(1.dp, MidnightBorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelMedium,
                            color = SilverMetallic,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectHistoryCard(
    project: VideoProjectEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${project.id}")
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = SilverPure,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${project.sourceLanguageName} ← ${project.targetLanguageName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = IceSapphireGlow
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverMuted
                    )
                    if (project.durationMs > 0L) {
                        Text(
                            text = SubtitleExporter.formatReadableTime(project.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = SilverMuted
                        )
                    }
                    Text(
                        text = "${project.cueCount} سطر",
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverMuted
                    )
                }
                if (project.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = project.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverMetallic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_project_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_project_desc),
                    tint = SilverMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ErrorBannerCard(
    message: String,
    onOpenCenter: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CoralError.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = CoralError,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverPure,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("حسناً", color = SilverPure, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onOpenCenter,
                border = BorderStroke(1.dp, CoralError.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "إدارة نماذج Vosk المحلية أو فحص App Check",
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverPure
                )
            }
        }
    }
}

@Composable
private fun ProcessingOverlay(
    state: ProcessingUiState,
    onCancel: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val stageMessage = when (state) {
        is ProcessingUiState.ExtractingAudio -> stringResource(R.string.processing_extracting)
        is ProcessingUiState.Translating -> {
            if (!state.statusDetail.isNullOrBlank()) {
                state.statusDetail
            } else if (state.totalChunks > 1) {
                "جاري تفريغ وترجمة الجزء ${state.currentChunk} من ${state.totalChunks} إلى ${state.targetLanguageName}…"
            } else {
                "جاري التفريغ والترجمة الفورية إلى ${state.targetLanguageName}…"
            }
        }
        else -> ""
    }

    val fileName = when (state) {
        is ProcessingUiState.ExtractingAudio -> state.fileName
        is ProcessingUiState.Translating -> state.fileName
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightVoid.copy(alpha = 0.85f))
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = IceSapphire,
                        trackColor = MidnightBorderSubtle,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(64.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = SilverPure,
                        modifier = Modifier
                            .size(28.dp)
                            .alpha(pulseAlpha)
                    )
                }

                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = stageMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMetallic
                )

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_processing_button")
                ) {
                    Text(
                        text = stringResource(R.string.processing_cancel),
                        color = SilverMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
