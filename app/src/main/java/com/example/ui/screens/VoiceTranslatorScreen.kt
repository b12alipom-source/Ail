package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.SupportedLanguages
import com.example.data.TranslationStyle
import com.example.data.db.VoiceTranslationEntity
import com.example.export.SubtitleExporter
import com.example.media.VoiceSpeaker
import com.example.ui.BayanViewModel
import com.example.ui.VoiceUiState
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

@Composable
fun VoiceTranslatorScreen(
    viewModel: BayanViewModel,
    outerPadding: PaddingValues
) {
    val context = LocalContext.current
    val sourceLanguage by viewModel.sourceLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val translationStyle by viewModel.translationStyle.collectAsStateWithLifecycle()
    val voiceUiState by viewModel.voiceUiState.collectAsStateWithLifecycle()
    val voiceHistory by viewModel.voiceHistory.collectAsStateWithLifecycle()

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val voiceSpeaker = remember(context) { VoiceSpeaker(context) }
    DisposableEffect(voiceSpeaker) {
        onDispose { voiceSpeaker.shutdown() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            permissionDeniedOnce = false
            viewModel.startVoiceRecording()
        } else {
            permissionDeniedOnce = true
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
            .padding(outerPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.voice_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = SilverPure
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.voice_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SilverMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = MidnightSurfaceHigh,
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier.clickable {
                            val styles = TranslationStyle.entries
                            val nextIndex = (styles.indexOf(translationStyle) + 1) % styles.size
                            viewModel.setTranslationStyle(styles[nextIndex])
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "نمط الترجمة",
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = translationStyle.arabicLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = SilverPure
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MidnightSurface,
                    border = BorderStroke(1.dp, MidnightBorder),
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VoiceLangChip(
                                label = stringResource(R.string.source_language_label),
                                name = sourceLanguage.arabicName,
                                badge = sourceLanguage.badge,
                                onClick = { showSourcePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = MidnightNavy,
                                border = BorderStroke(1.dp, MidnightBorder),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { viewModel.swapLanguages() }
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
                            VoiceLangChip(
                                label = stringResource(R.string.target_language_label),
                                name = targetLanguage.arabicName,
                                badge = targetLanguage.badge,
                                onClick = { showTargetPicker = true },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!hasMicPermission && permissionDeniedOnce) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MidnightNavy,
                                border = BorderStroke(1.dp, MidnightBorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.voice_permission_needed),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SilverMetallic,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SilverPure,
                                            contentColor = MidnightVoid
                                        )
                                    ) {
                                        Text(
                                            text = stringResource(R.string.voice_grant_permission),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            VoiceStudioControl(
                                state = voiceUiState,
                                onToggleRecord = {
                                    when (voiceUiState) {
                                        is VoiceUiState.Recording -> viewModel.stopAndTranslateVoice()
                                        is VoiceUiState.Translating -> Unit
                                        else -> {
                                            if (hasMicPermission) {
                                                viewModel.startVoiceRecording()
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    }
                                },
                                onCancel = { viewModel.cancelVoiceRecording() }
                            )
                        }
                    }
                }
            }

            if (voiceUiState is VoiceUiState.Error) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CoralError.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = CoralError,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = (voiceUiState as VoiceUiState.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SilverPure,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.dismissVoiceError() }) {
                                Text("حسناً", color = SilverPure, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (voiceHistory.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MidnightSurface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MidnightBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GraphicEq,
                                contentDescription = null,
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = stringResource(R.string.voice_empty_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SilverMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(voiceHistory, key = { it.id }) { item ->
                    VoiceResultCard(
                        entry = item,
                        onSpeak = {
                            voiceSpeaker.speak(item.translatedText, item.targetLanguageCode)
                        },
                        onCopy = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Bayan Voice", item.translatedText)
                            )
                            Toast.makeText(context, context.getString(R.string.copy_success), Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { viewModel.deleteVoiceTranslation(item.id) }
                    )
                }
            }
        }
    }

    if (showSourcePicker) {
        LanguagePickerSheet(
            title = "اختر لغة المتحدث",
            languages = SupportedLanguages.allSourceLanguages,
            selectedCode = sourceLanguage.code,
            onSelect = { viewModel.setSourceLanguage(it) },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerSheet(
            title = "اختر لغة الترجمة الفورية",
            languages = SupportedLanguages.allTargetLanguages,
            selectedCode = targetLanguage.code,
            onSelect = { viewModel.setTargetLanguage(it) },
            onDismiss = { showTargetPicker = false }
        )
    }
}

@Composable
private fun VoiceStudioControl(
    state: VoiceUiState,
    onToggleRecord: () -> Unit,
    onCancel: () -> Unit
) {
    val isRecording = state is VoiceUiState.Recording
    val isTranslating = state is VoiceUiState.Translating
    val amplitude = (state as? VoiceUiState.Recording)?.amplitude ?: 0.08f
    val elapsedMs = (state as? VoiceUiState.Recording)?.elapsedMs ?: 0L

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live amplitude waveform bars
        Row(
            modifier = Modifier.height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val weights = listOf(0.35f, 0.6f, 0.9f, 1.0f, 0.85f, 0.55f, 0.3f)
            weights.forEach { weight ->
                val targetHeight = if (isRecording) (8f + 26f * amplitude * weight) else 6f
                val animatedHeight by animateFloatAsState(
                    targetValue = targetHeight,
                    animationSpec = tween(90),
                    label = "barHeight"
                )
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(animatedHeight.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (isRecording) IceSapphireGlow else MidnightBorder)
                )
            }
        }

        Surface(
            shape = CircleShape,
            color = when {
                isRecording -> CoralError
                isTranslating -> MidnightSurfaceHigh
                else -> SilverPure
            },
            border = BorderStroke(
                width = 2.dp,
                color = if (isRecording) CoralError.copy(alpha = 0.5f) else IceSapphire.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .size(76.dp)
                .testTag("mic_record_button")
                .clickable(enabled = !isTranslating) { onToggleRecord() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    isTranslating -> CircularProgressIndicator(
                        color = IceSapphire,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    isRecording -> Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.voice_tap_to_stop),
                        tint = SilverPure,
                        modifier = Modifier.size(32.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.voice_tap_to_record),
                        tint = MidnightVoid,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        when (state) {
            is VoiceUiState.Recording -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = SubtitleExporter.formatReadableTime(elapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = IceSapphireGlow
                    )
                    Text(
                        text = stringResource(R.string.voice_tap_to_stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = SilverPure
                    )
                }
                TextButton(onClick = onCancel) {
                    Text("إلغاء", color = SilverMuted)
                }
            }
            is VoiceUiState.Translating -> {
                Text(
                    text = stringResource(R.string.voice_translating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IceSapphireGlow
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.voice_tap_to_record),
                    style = MaterialTheme.typography.labelLarge,
                    color = SilverMetallic
                )
            }
        }
    }
}

@Composable
private fun VoiceLangChip(
    label: String,
    name: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MidnightNavy.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SilverMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = IceSapphireGlow
                )
            }
        }
    }
}

@Composable
private fun VoiceResultCard(
    entry: VoiceTranslationEntity,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${entry.sourceLanguageName} ← ${entry.targetLanguageName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = IceSapphireGlow
                )
                Row {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = stringResource(R.string.voice_listen_desc),
                            tint = SilverPure,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "نسخ الترجمة",
                            tint = SilverMetallic,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف",
                            tint = SilverMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = entry.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = SilverPure
            )
            Text(
                text = entry.originalText,
                style = MaterialTheme.typography.bodyMedium,
                color = SilverMuted
            )
        }
    }
}
