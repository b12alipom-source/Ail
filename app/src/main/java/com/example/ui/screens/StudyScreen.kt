package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.StudyDossierData
import com.example.data.StudyMethod
import com.example.data.StudyVisualStyle
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyNotebookEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.StudyVisualEntity
import com.example.study.IngestedAttachment
import com.example.study.parseStudyDossierJson
import com.example.ui.BayanViewModel
import com.example.ui.components.PermanentVaultAndSyncSheet
import com.example.ui.components.ReinstallRecoveryBanner
import com.example.ui.components.findActivityOrNull
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
import java.io.File

private enum class StudyWorkspaceTab(val label: String) {
    SUMMARY("الملخص"),
    VISUALS("الصور والخرائط"),
    SCHEDULE("الجدول"),
    TUTOR("المعلم الذكي")
}

@Composable
fun StudyScreen(
    viewModel: BayanViewModel,
    outerPadding: PaddingValues
) {
    val context = LocalContext.current
    val notebooks by viewModel.studyNotebooks.collectAsStateWithLifecycle()
    val activeNotebookId by viewModel.activeStudyNotebookId.collectAsStateWithLifecycle()
    val attachments by viewModel.activeStudyAttachments.collectAsStateWithLifecycle()
    val visuals by viewModel.activeStudyVisuals.collectAsStateWithLifecycle()
    val scheduleItems by viewModel.activeStudySchedule.collectAsStateWithLifecycle()
    val messages by viewModel.activeStudyMessages.collectAsStateWithLifecycle()
    val busyMessage by viewModel.studyBusyMessage.collectAsStateWithLifecycle()
    val studyError by viewModel.studyError.collectAsStateWithLifecycle()
    val studyErrorRequiresSettings by viewModel.studyErrorRequiresSettings.collectAsStateWithLifecycle()
    val studyNotice by viewModel.studyNotice.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val offlineEngineMode by viewModel.offlineEngineMode.collectAsStateWithLifecycle()
    val engineConfig by viewModel.studyEngineConfig.collectAsStateWithLifecycle()
    val isGeneratingVisual by viewModel.isGeneratingVisual.collectAsStateWithLifecycle()
    val studyVisualError by viewModel.studyVisualError.collectAsStateWithLifecycle()
    val isTutorStreaming by viewModel.isTutorStreaming.collectAsStateWithLifecycle()
    val streamingTutorReply by viewModel.streamingTutorReply.collectAsStateWithLifecycle()
    val pendingChatAttachment by viewModel.pendingChatAttachment.collectAsStateWithLifecycle()
    val appCheckState by viewModel.appCheckStatus.collectAsStateWithLifecycle()
    val isCheckingAppCheck by viewModel.isCheckingAppCheck.collectAsStateWithLifecycle()
    val personalKeyVerifyState by viewModel.personalKeyVerifyState.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultUiState.collectAsStateWithLifecycle()

    LaunchedEffect(notebooks, activeNotebookId) {
        if (activeNotebookId == null && notebooks.isNotEmpty()) {
            viewModel.selectStudyNotebook(notebooks.first().id)
        }
    }

    val activeNotebook = remember(notebooks, activeNotebookId) {
        notebooks.firstOrNull { it.id == activeNotebookId } ?: notebooks.firstOrNull()
    }
    val parsedDossier = remember(activeNotebook?.dossierJson, activeNotebook?.mindMapJson) {
        activeNotebook?.let { nb ->
            parseStudyDossierJson(nb.dossierJson.ifBlank { nb.mindMapJson })
        }
    }

    var activeTab by rememberSaveable { mutableStateOf(StudyWorkspaceTab.SUMMARY) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showAddTextNoteDialog by remember { mutableStateOf(false) }
    var showLinkVideoSheet by remember { mutableStateOf(false) }
    var showEngineSettingsSheet by remember { mutableStateOf(false) }
    var showVaultSyncSheet by remember { mutableStateOf(false) }
    var notebookPendingDelete by remember { mutableStateOf<StudyNotebookEntity?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.attachStudyDocumentUri(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachStudyPhotoUri(uri)
    }

    val chatPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.attachChatMediaUri(uri)
    }

    val chatDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.attachChatMediaUri(uri)
    }

    val vaultSetupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onVaultFolderPicked(uri, runBackupOrRestoreIfEmpty = true)
    }

    val vaultRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.restoreFromDeviceVault(uri)
    }

    val signInAndSyncCloud: () -> Unit = {
        val activityCtx = context.findActivityOrNull() ?: context
        viewModel.signInWithGoogle(activityCtx, autoSyncOrRestore = true)
    }

    val shareImagePath: (String, String) -> Unit = { localPath, title ->
        runCatching {
            val file = File(localPath)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملخص الصورة"))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(MidnightVoid, MidnightNavy, Color(0xFF0D172B))))
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(IceSapphire.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.1f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.1f),
                    radius = size.width * 0.7f
                )
            }
            .padding(outerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Header + Active Engine Chip + Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مساعد الدراسة الذكي",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SilverPure,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (engineConfig.isUsingPersonalKey) EmeraldSuccess.copy(alpha = 0.16f) else MidnightSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (engineConfig.isUsingPersonalKey) EmeraldSuccess.copy(alpha = 0.5f) else MidnightBorderSubtle
                        ),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { showEngineSettingsSheet = true }
                            .testTag("study_engine_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "إعدادات المحرك الذكي",
                                tint = if (engineConfig.isUsingPersonalKey) EmeraldSuccess else IceSapphireGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (offlineEngineMode) "الوضع المحلي أوفلاين (اضغط للتغيير)" else engineConfig.activeBadgeLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverPure
                            )
                        }
                    }
                }

                val isVaultProtected = vaultState.settings.isVaultConfigured || vaultState.signedInUser != null
                IconButton(
                    onClick = { showVaultSyncSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("study_backup_sync_button")
                ) {
                    Icon(
                        imageVector = if (isVaultProtected) Icons.Outlined.CloudDone else Icons.Outlined.Backup,
                        contentDescription = "الحفظ الدائم والمزامنة السحابية",
                        tint = if (isVaultProtected) EmeraldSuccess else IceSapphireGlow,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = SilverPure,
                    modifier = Modifier
                        .testTag("study_new_notebook_button")
                        .clickable { showCreateNotebookDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "مادة جديدة", tint = MidnightVoid, modifier = Modifier.size(18.dp))
                        Text(
                            text = "مادة جديدة",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MidnightVoid
                        )
                    }
                }
            }

            // Notebook Strip
            if (notebooks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notebooks.forEach { nb ->
                        val selected = nb.id == activeNotebook?.id
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (selected) IceSapphire.copy(alpha = 0.24f) else MidnightSurface,
                            border = BorderStroke(1.dp, if (selected) IceSapphireGlow else MidnightBorderSubtle),
                            modifier = Modifier.clickable { viewModel.selectStudyNotebook(nb.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = if (selected) 4.dp else 12.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = nb.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) SilverPure else SilverMetallic,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selected) {
                                    IconButton(
                                        onClick = { notebookPendingDelete = nb },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف المادة", tint = SilverMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = busyMessage != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(color = IceSapphire, trackColor = MidnightBorderSubtle, modifier = Modifier.fillMaxWidth())
                    busyMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = IceSapphireGlow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MidnightSurface)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            studyError?.let { err ->
                Surface(
                    color = CoralError.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, CoralError.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = SilverPure)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (studyErrorRequiresSettings) {
                                OutlinedButton(
                                    onClick = { showEngineSettingsSheet = true },
                                    border = BorderStroke(1.dp, IceSapphireGlow),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إعداد مفتاح شخصي / تشخيص", color = IceSapphireGlow)
                                }
                            }
                            TextButton(onClick = { viewModel.dismissStudyError() }) {
                                Text("إغلاق", color = SilverPure)
                            }
                        }
                    }
                }
            }

            studyNotice?.let { note ->
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(note, style = MaterialTheme.typography.bodySmall, color = SilverPure, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.dismissStudyNotice() }) {
                            Text("حسناً", color = EmeraldSuccess)
                        }
                    }
                }
            }

            if (!showVaultSyncSheet && (vaultState.statusMessage != null || vaultState.errorMessage != null)) {
                val isErr = vaultState.errorMessage != null
                Surface(
                    color = if (isErr) CoralError.copy(alpha = 0.14f) else EmeraldSuccess.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (isErr) CoralError.copy(alpha = 0.45f) else EmeraldSuccess.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vaultState.errorMessage ?: vaultState.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverPure,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissVaultStatus() }) {
                            Text("حسناً", color = if (isErr) CoralError else EmeraldSuccess)
                        }
                    }
                }
            }

            if (activeNotebook == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ReinstallRecoveryBanner(
                        isLocalLibraryEmpty = projects.isEmpty() && notebooks.isEmpty(),
                        vaultState = vaultState,
                        onPickVaultFolderForSetup = { vaultSetupLauncher.launch(null) },
                        onPickVaultFolderForRestore = { vaultRestoreLauncher.launch(null) },
                        onSignInAndRestoreCloud = {
                            if (vaultState.signedInUser != null) {
                                viewModel.restoreFromCloud()
                            } else {
                                signInAndSyncCloud()
                            }
                        },
                        onOpenFullBackupSheet = { showVaultSyncSheet = true },
                        onDismiss = { viewModel.dismissVaultBanner() }
                    )

                    EmptyStudyOnboarding(
                        projectsCount = projects.size,
                        firstProjectTitle = projects.firstOrNull()?.title,
                        onCreateNotebook = { showCreateNotebookDialog = true },
                        onStudyFirstVideo = {
                            projects.firstOrNull()?.let { viewModel.openOrCreateStudyFromProject(it.id) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                ActiveNotebookWorkspace(
                    notebook = activeNotebook,
                    dossier = parsedDossier,
                    attachments = attachments,
                    visuals = visuals,
                    scheduleItems = scheduleItems,
                    messages = messages,
                    isBusy = busyMessage != null,
                    isGeneratingVisual = isGeneratingVisual,
                    studyVisualError = studyVisualError,
                    isTutorStreaming = isTutorStreaming,
                    streamingTutorReply = streamingTutorReply,
                    pendingChatAttachment = pendingChatAttachment,
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it },
                    onOpenBackupSync = { showVaultSyncSheet = true },
                    onPickDocument = { documentPickerLauncher.launch(arrayOf("application/pdf", "text/*")) },
                    onPickPhoto = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onOpenAddTextNote = { showAddTextNoteDialog = true },
                    onOpenLinkVideo = { showLinkVideoSheet = true },
                    onRemoveAttachment = { viewModel.removeStudyAttachment(it) },
                    onGeneratePlan = { method, mins, days ->
                        viewModel.generateStudySummaryAndSchedule(method, mins, days)
                    },
                    onGenerateVisual = { style -> viewModel.generateStudyVisual(style) },
                    onDismissVisualError = { viewModel.dismissStudyVisualError() },
                    onOpenEngineSettings = { showEngineSettingsSheet = true },
                    onSaveVisualToGallery = { visual ->
                        viewModel.saveStudyVisualToGallery(visual) { path ->
                            shareImagePath(path, visual.title)
                        }
                    },
                    onShareVisual = { visual -> shareImagePath(visual.localImagePath, visual.title) },
                    onDeleteVisual = { visual -> viewModel.deleteStudyVisual(visual) },
                    onToggleScheduleTask = { id, completed -> viewModel.toggleStudyScheduleTask(id, completed) },
                    onPickChatPhoto = { chatPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onPickChatDoc = { chatDocPickerLauncher.launch(arrayOf("application/pdf", "text/*", "image/*")) },
                    onClearPendingChatAttachment = { viewModel.clearPendingChatAttachment() },
                    onSendQuestion = { viewModel.sendStudyTutorQuestion(it) },
                    onClearChat = { viewModel.clearStudyChat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (showVaultSyncSheet) {
        PermanentVaultAndSyncSheet(
            vaultState = vaultState,
            onPickVaultFolder = { vaultSetupLauncher.launch(null) },
            onPickFolderAndRestore = { vaultRestoreLauncher.launch(null) },
            onToggleAutoSave = { viewModel.setVaultAutoSave(it) },
            onToggleIncludeVideoCopy = { viewModel.setVaultIncludeVideoCopy(it) },
            onBackupNowToVault = { viewModel.backupAllToDeviceVault() },
            onRestoreNowFromVault = { viewModel.restoreFromDeviceVault() },
            onSignInWithGoogle = signInAndSyncCloud,
            onSignOutGoogle = { viewModel.signOutGoogle(context) },
            onSyncNowToCloud = { viewModel.syncAllToCloud() },
            onRestoreNowFromCloud = { viewModel.restoreFromCloud() },
            onDismissStatus = { viewModel.dismissVaultStatus() },
            onDismiss = { showVaultSyncSheet = false }
        )
    }

    if (showEngineSettingsSheet) {
        StudyAiSettingsSheet(
            engineConfig = engineConfig,
            appCheckState = appCheckState,
            isCheckingAppCheck = isCheckingAppCheck,
            personalKeyVerifyState = personalKeyVerifyState,
            onSelectTier = { viewModel.setStudyAiTier(it) },
            onSavePersonalKey = { viewModel.savePersonalGeminiKey(it) },
            onVerifyPersonalKey = { viewModel.verifyPersonalGeminiKey(it) },
            onTogglePreferPersonalKey = { viewModel.setPreferPersonalGeminiKey(it) },
            onClearPersonalKey = { viewModel.clearPersonalGeminiKey() },
            onRunAppCheckCheck = { viewModel.runAppCheckDiagnostics() },
            onDismiss = { showEngineSettingsSheet = false }
        )
    }

    if (showCreateNotebookDialog) {
        CreateNotebookDialog(
            onDismiss = { showCreateNotebookDialog = false },
            onCreate = { title, method, mins, days ->
                viewModel.createStudyNotebook(title, method, mins, days)
                showCreateNotebookDialog = false
            }
        )
    }

    if (showAddTextNoteDialog) {
        AddTextNoteDialog(
            onDismiss = { showAddTextNoteDialog = false },
            onSave = { title, body ->
                viewModel.attachStudyTextNote(title, body)
                showAddTextNoteDialog = false
            }
        )
    }

    if (showLinkVideoSheet) {
        LinkTranslatedVideoSheet(
            projects = projects,
            onSelectProject = { proj ->
                viewModel.attachTranslatedProjectToStudy(proj.id)
                showLinkVideoSheet = false
            },
            onDismiss = { showLinkVideoSheet = false }
        )
    }

    notebookPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { notebookPendingDelete = null },
            containerColor = MidnightSurfaceHigh,
            titleContentColor = SilverPure,
            textContentColor = SilverMetallic,
            title = { Text("حذف مادة الدراسة؟") },
            text = { Text("سيتم حذف «${pending.title}» ومرفقاتها وصورها وجدولها الدراسي.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudyNotebook(pending.id)
                        notebookPendingDelete = null
                    }
                ) {
                    Text("حذف", color = CoralError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { notebookPendingDelete = null }) {
                    Text("إلغاء", color = SilverPure)
                }
            }
        )
    }
}

@Composable
private fun EmptyStudyOnboarding(
    projectsCount: Int,
    firstProjectTitle: String?,
    onCreateNotebook: () -> Unit,
    onStudyFirstVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MidnightSurfaceHigh,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.School, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(30.dp))
                    }
                }
                Text(
                    text = "نظام الدراسة الذكي المتكامل",
                    style = MaterialTheme.typography.titleLarge,
                    color = SilverPure,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "أرفق ملفات PDF أو صور المحاضرات أو الفيديوهات المترجمة، وسيبني لك «بيان» ملفاً دراسياً شاملاً، وإنفوجرافيك بالصور، وخريطة ذهنية، ومحادثة معلم فورية.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMetallic
                )
                Button(
                    onClick = onCreateNotebook,
                    colors = ButtonDefaults.buttonColors(containerColor = SilverPure, contentColor = MidnightVoid),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_study_notebook_cta")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنشاء مادة دراسية جديدة", fontWeight = FontWeight.Bold)
                }
                if (projectsCount > 0 && firstProjectTitle != null) {
                    TextButton(onClick = onStudyFirstVideo) {
                        Text("أو ابدأ الدراسة من الفيديو: $firstProjectTitle", color = IceSapphireGlow)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveNotebookWorkspace(
    notebook: StudyNotebookEntity,
    dossier: StudyDossierData?,
    attachments: List<StudyAttachmentEntity>,
    visuals: List<StudyVisualEntity>,
    scheduleItems: List<StudyScheduleItemEntity>,
    messages: List<StudyMessageEntity>,
    isBusy: Boolean,
    isGeneratingVisual: Boolean,
    studyVisualError: String?,
    isTutorStreaming: Boolean,
    streamingTutorReply: String,
    pendingChatAttachment: IngestedAttachment?,
    activeTab: StudyWorkspaceTab,
    onSelectTab: (StudyWorkspaceTab) -> Unit,
    onOpenBackupSync: () -> Unit,
    onPickDocument: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenAddTextNote: () -> Unit,
    onOpenLinkVideo: () -> Unit,
    onRemoveAttachment: (StudyAttachmentEntity) -> Unit,
    onGeneratePlan: (StudyMethod, Int, Int) -> Unit,
    onGenerateVisual: (StudyVisualStyle) -> Unit,
    onDismissVisualError: () -> Unit,
    onOpenEngineSettings: () -> Unit,
    onSaveVisualToGallery: (StudyVisualEntity) -> Unit,
    onShareVisual: (StudyVisualEntity) -> Unit,
    onDeleteVisual: (StudyVisualEntity) -> Unit,
    onToggleScheduleTask: (Long, Boolean) -> Unit,
    onPickChatPhoto: () -> Unit,
    onPickChatDoc: () -> Unit,
    onClearPendingChatAttachment: () -> Unit,
    onSendQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMethod by remember(notebook.id, notebook.preferredMethod) {
        mutableStateOf(StudyMethod.fromKey(notebook.preferredMethod))
    }
    var selectedDailyMinutes by remember(notebook.id, notebook.dailyMinutes) {
        mutableIntStateOf(notebook.dailyMinutes)
    }
    var selectedTotalDays by remember(notebook.id, notebook.totalDays) {
        mutableIntStateOf(notebook.totalDays)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Attachments + Method Config Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MidnightSurface,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. مرفقات المادة (${attachments.size})", style = MaterialTheme.typography.titleSmall, color = SilverPure)
                        val isSaved = notebook.vaultSavedAt > 0L || notebook.cloudSyncedAt > 0L
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (isSaved) EmeraldSuccess.copy(alpha = 0.14f) else MidnightNavy,
                            border = BorderStroke(
                                1.dp,
                                if (isSaved) EmeraldSuccess.copy(alpha = 0.45f) else MidnightBorderSubtle
                            ),
                            modifier = Modifier
                                .testTag("study_notebook_backup_pill")
                                .clickable { onOpenBackupSync() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Outlined.FolderSpecial else Icons.Outlined.Backup,
                                    contentDescription = "حالة الحفظ الدائم للمادة",
                                    tint = if (isSaved) EmeraldSuccess else IceSapphireGlow,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isSaved) "محفوظ تلقائياً ✓" else "تفعيل الحفظ الدائم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSaved) EmeraldSuccess else SilverMetallic
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AttachActionChip(Icons.Outlined.PictureAsPdf, "ملف PDF / نص", onPickDocument)
                        AttachActionChip(Icons.Outlined.Image, "صورة درس", onPickPhoto)
                        AttachActionChip(Icons.Outlined.Notes, "ملاحظة نصية", onOpenAddTextNote)
                        AttachActionChip(Icons.Outlined.VideoLibrary, "فيديو مترجم", onOpenLinkVideo)
                    }

                    if (attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachments.forEach { att ->
                                StudyAttachmentChip(attachment = att, onRemove = { onRemoveAttachment(att) })
                            }
                        }
                    }

                    Text("2. طريقة الفهم والجدول المخصص", style = MaterialTheme.typography.titleSmall, color = SilverPure)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudyMethod.entries.forEach { method ->
                            val selected = method == selectedMethod
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = if (selected) IceSapphire.copy(alpha = 0.22f) else MidnightNavy,
                                border = BorderStroke(1.dp, if (selected) IceSapphireGlow else MidnightBorderSubtle),
                                modifier = Modifier.clickable { selectedMethod = method }
                            ) {
                                Text(
                                    text = method.arabicLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) SilverPure else SilverMuted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(30, 60, 90, 120).forEach { mins ->
                            val selected = selectedDailyMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) SilverPure else MidnightNavy,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDailyMinutes = mins }
                            ) {
                                Text(
                                    text = "$mins د/يوم",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MidnightVoid else SilverMetallic,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    maxLines = 1
                                )
                            }
                        }
                        listOf(3, 5, 7).forEach { days ->
                            val selected = selectedTotalDays == days
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) IceSapphire else MidnightNavy,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .clickable { selectedTotalDays = days }
                            ) {
                                Text(
                                    text = "$days أيام",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MidnightVoid else SilverMetallic,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Button(
                        enabled = !isBusy,
                        onClick = { onGeneratePlan(selectedMethod, selectedDailyMinutes, selectedTotalDays) },
                        colors = ButtonDefaults.buttonColors(containerColor = SilverPure, contentColor = MidnightVoid),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_study_plan_button")
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("توليد الملخص المتكامل والجدول", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4 Sub-Tabs (scrollable strip on narrow phones so text never truncates)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudyWorkspaceTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) SilverPure else MidnightSurface,
                        border = BorderStroke(1.dp, if (selected) SilverPure else MidnightBorderSubtle),
                        modifier = Modifier
                            .testTag("study_tab_${tab.name.lowercase()}")
                            .clickable { onSelectTab(tab) }
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MidnightVoid else SilverMetallic,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // Active Tab Body
        item {
            when (activeTab) {
                StudyWorkspaceTab.SUMMARY -> StudySummaryTabContent(
                    notebook = notebook,
                    dossier = dossier,
                    onGenerateClick = { onGeneratePlan(selectedMethod, selectedDailyMinutes, selectedTotalDays) }
                )
                StudyWorkspaceTab.VISUALS -> StudyVisualsAndMindMapTabContent(
                    notebookTitle = notebook.title,
                    dossier = dossier,
                    visuals = visuals,
                    isGeneratingVisual = isGeneratingVisual,
                    visualError = studyVisualError,
                    onGenerateVisual = onGenerateVisual,
                    onDismissVisualError = onDismissVisualError,
                    onOpenEngineSettings = onOpenEngineSettings,
                    onSaveVisualToGallery = onSaveVisualToGallery,
                    onShareVisual = onShareVisual,
                    onDeleteVisual = onDeleteVisual
                )
                StudyWorkspaceTab.SCHEDULE -> StudyScheduleTabContent(
                    scheduleItems = scheduleItems,
                    onToggleCompleted = onToggleScheduleTask,
                    onGenerateClick = { onGeneratePlan(selectedMethod, selectedDailyMinutes, selectedTotalDays) }
                )
                StudyWorkspaceTab.TUTOR -> StudyTutorChatTabContent(
                    messages = messages,
                    isStreaming = isTutorStreaming,
                    streamingReply = streamingTutorReply,
                    pendingAttachment = pendingChatAttachment,
                    onPickChatPhoto = onPickChatPhoto,
                    onPickChatDoc = onPickChatDoc,
                    onClearPendingAttachment = onClearPendingChatAttachment,
                    onSendQuestion = onSendQuestion,
                    onClearChat = onClearChat
                )
            }
        }
    }
}

@Composable
private fun AttachActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MidnightSurfaceHigh,
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = IceSapphireGlow, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = SilverPure)
        }
    }
}
