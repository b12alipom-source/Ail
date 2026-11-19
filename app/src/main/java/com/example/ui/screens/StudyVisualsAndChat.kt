package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.FolderAccent
import com.example.data.MindMapBranch
import com.example.data.StudyAttachmentType
import com.example.data.StudyDossierData
import com.example.data.StudyVisualStyle
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyVisualEntity
import com.example.study.IngestedAttachment
import com.example.ui.theme.AmberWarning
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

@Composable
internal fun StudyVisualsAndMindMapTabContent(
    notebookTitle: String,
    dossier: StudyDossierData?,
    visuals: List<StudyVisualEntity>,
    isGeneratingVisual: Boolean,
    visualError: String?,
    onGenerateVisual: (StudyVisualStyle) -> Unit,
    onDismissVisualError: () -> Unit,
    onOpenEngineSettings: () -> Unit,
    onSaveVisualToGallery: (StudyVisualEntity) -> Unit,
    onShareVisual: (StudyVisualEntity) -> Unit,
    onDeleteVisual: (StudyVisualEntity) -> Unit
) {
    var selectedStyle by remember { mutableStateOf(StudyVisualStyle.INFOGRAPHIC) }
    var zoomedVisual by remember { mutableStateOf<StudyVisualEntity?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. AI Visual Infographics Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = IceSapphireGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "توليد ملخصات صور بالذكاء الاصطناعي",
                        style = MaterialTheme.typography.titleSmall,
                        color = SilverPure,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = selectedStyle.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudyVisualStyle.entries.forEach { style ->
                        val selected = style == selectedStyle
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (selected) IceSapphire.copy(alpha = 0.24f) else MidnightNavy,
                            border = BorderStroke(
                                1.dp,
                                if (selected) IceSapphireGlow else MidnightBorderSubtle
                            ),
                            modifier = Modifier.clickable { selectedStyle = style }
                        ) {
                            Text(
                                text = style.arabicLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) SilverPure else SilverMuted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Button(
                    enabled = !isGeneratingVisual,
                    onClick = { onGenerateVisual(selectedStyle) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IceSapphire,
                        contentColor = MidnightVoid
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_study_visual_button")
                ) {
                    if (isGeneratingVisual) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MidnightVoid
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري تصميم الصورة (30–60 ث)…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إنشاء «${selectedStyle.arabicLabel}»", fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(visible = isGeneratingVisual) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            color = IceSapphireGlow,
                            trackColor = MidnightBorderSubtle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(99.dp))
                        )
                        Text(
                            text = "يقوم نموذج Gemini لتوليد الصور الآن برسم المخطط التعليمي لمادة «$notebookTitle»…",
                            style = MaterialTheme.typography.labelSmall,
                            color = IceSapphireGlow
                        )
                    }
                }

                visualError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CoralError.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CoralError.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverPure
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onOpenEngineSettings,
                                    border = BorderStroke(1.dp, IceSapphireGlow),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = IceSapphireGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إعدادات المحرك / المفتاح", color = IceSapphireGlow)
                                }
                                TextButton(onClick = onDismissVisualError) {
                                    Text("إغلاق", color = SilverMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Generated Visual Gallery
        if (visuals.isNotEmpty()) {
            visuals.forEach { visual ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MidnightSurfaceHigh,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = visual.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = SilverPure,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (visual.engineLabel.isNotBlank()) {
                                    Text(
                                        text = visual.engineLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IceSapphireGlow
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = { zoomedVisual = visual },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.ZoomIn, contentDescription = "تكبير الصورة", tint = SilverPure)
                                }
                                IconButton(
                                    onClick = { onSaveVisualToGallery(visual) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = "حفظ في المعرض", tint = EmeraldSuccess)
                                }
                                IconButton(
                                    onClick = { onShareVisual(visual) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = "مشاركة الصورة", tint = IceSapphireGlow)
                                }
                                IconButton(
                                    onClick = { onDeleteVisual(visual) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الصورة", tint = CoralError)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AsyncImage(
                            model = File(visual.localImagePath),
                            contentDescription = visual.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { zoomedVisual = visual }
                        )
                    }
                }
            }
        }

        // 2. Interactive Arabic Mind Map Tree
        MindMapTreeSection(
            rootTitle = dossier?.mindMapRootTitle?.ifBlank { notebookTitle } ?: notebookTitle,
            branches = dossier?.mindMapBranches.orEmpty()
        )
    }

    zoomedVisual?.let { visual ->
        FullscreenVisualZoomDialog(
            imagePath = visual.localImagePath,
            title = visual.title,
            onSaveToGallery = { onSaveVisualToGallery(visual) },
            onShare = { onShareVisual(visual) },
            onDismiss = { zoomedVisual = null }
        )
    }
}

@Composable
private fun MindMapTreeSection(
    rootTitle: String,
    branches: List<MindMapBranch>
) {
    val expandedMap = remember(branches) { mutableStateMapOf<Int, Boolean>() }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurface,
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "الخريطة الذهنية التفاعلية (نص عربي دقيق)",
                    style = MaterialTheme.typography.titleSmall,
                    color = SilverPure,
                    fontWeight = FontWeight.Bold
                )
            }

            if (branches.isEmpty()) {
                Text(
                    text = "اضغط «توليد الملخص المتكامل والجدول» من الأعلى لبناء خريطة ذهنية تفاعلية تربط كافة مفاهيم المادة بوضوح.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMuted
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IceSapphire.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, IceSapphireGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = rootTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SilverPure,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                branches.forEachIndexed { idx, branch ->
                    val expanded = expandedMap[idx] ?: true
                    val accentColor = when (FolderAccent.fromKey(branch.colorTag)) {
                        FolderAccent.EMERALD -> EmeraldSuccess
                        FolderAccent.AMBER -> AmberWarning
                        FolderAccent.VIOLET -> Color(0xFFB794F4)
                        FolderAccent.SAPPHIRE -> IceSapphireGlow
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MidnightNavy,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedMap[idx] = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                    )
                                    Text(
                                        text = branch.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SilverPure,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = if (expanded) "طي الفرع" else "توسيع الفرع",
                                    tint = accentColor
                                )
                            }

                            AnimatedVisibility(visible = expanded && branch.subtopics.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp, start = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    branch.subtopics.forEach { sub ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MidnightSurfaceHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "• $sub",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SilverMetallic,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenVisualZoomDialog(
    imagePath: String,
    title: String,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "إغلاق", tint = SilverPure)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                IconButton(onClick = onSaveToGallery, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = "حفظ في المعرض", tint = EmeraldSuccess)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Share, contentDescription = "مشاركة", tint = IceSapphireGlow)
                }
            }
        }
    }
}

@Composable
internal fun StudyTutorChatTabContent(
    messages: List<StudyMessageEntity>,
    isStreaming: Boolean,
    streamingReply: String,
    pendingAttachment: IngestedAttachment?,
    onPickChatPhoto: () -> Unit,
    onPickChatDoc: () -> Unit,
    onClearPendingAttachment: () -> Unit,
    onSendQuestion: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    var questionDraft by remember { mutableStateOf("") }
    var zoomedAttachmentPath by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المعلم الذكي (بث فوري + تحليل صور المسائل)",
                style = MaterialTheme.typography.labelLarge,
                color = SilverMetallic
            )
            if (messages.isNotEmpty()) {
                TextButton(onClick = onClearChat) {
                    Text("مسح المحادثة", color = SilverMuted)
                }
            }
        }

        // Quick-Action Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "اشرح لي أهم محور خطوة بخطوة",
                "استخرج أهم القوانين والتعريفات",
                "اكتب لي 4 أسئلة امتحان متوقعة بالحل",
                "اختبر فهمي الآن بسؤالين"
            ).forEach { quickPrompt ->
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MidnightNavy,
                    border = BorderStroke(1.dp, MidnightBorderSubtle),
                    modifier = Modifier.clickable(enabled = !isStreaming) { onSendQuestion(quickPrompt) }
                ) {
                    Text(
                        text = quickPrompt,
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverPure,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (messages.isEmpty() && !isStreaming) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurface,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "أرسل سؤالك أو صوّر أي مسألة مباشرة",
                        style = MaterialTheme.typography.titleSmall,
                        color = IceSapphireGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "يمكنك إرفاق صورة معادلة أو صفحة كتاب من زر الصورة أدناه، وسيقرأها المعلم الذكي ويشرح الحل فورياً أثناء الكتابة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )
                }
            }
        } else {
            messages.forEach { msg ->
                TutorMessageBubble(
                    isUser = msg.isUser,
                    text = msg.messageText,
                    attachmentPath = msg.attachmentPath,
                    attachmentName = msg.attachmentName,
                    attachmentMimeType = msg.attachmentMimeType,
                    subtitleBadge = if (!msg.isUser && msg.isOfflineGenerated) "محلي أوفلاين" else null,
                    isStreaming = false,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("bayan_tutor", msg.messageText))
                        Toast.makeText(context, "تم نسخ الإجابة", Toast.LENGTH_SHORT).show()
                    },
                    onImageClick = { path -> zoomedAttachmentPath = path }
                )
            }

            if (isStreaming) {
                TutorMessageBubble(
                    isUser = false,
                    text = streamingReply.ifBlank { "المعلم الذكي يحلل ويكتب الإجابة…" },
                    attachmentPath = "",
                    attachmentName = "",
                    attachmentMimeType = "",
                    subtitleBadge = "بث مباشر…",
                    isStreaming = true,
                    onCopy = {},
                    onImageClick = {}
                )
            }
        }

        // Pending inline attachment chip above composer
        AnimatedVisibility(visible = pendingAttachment != null) {
            pendingAttachment?.let { att ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightSurfaceHigh,
                    border = BorderStroke(1.dp, IceSapphireGlow.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (att.type == StudyAttachmentType.IMAGE) {
                            AsyncImage(
                                model = File(att.localFilePath),
                                contentDescription = att.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = att.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = SilverPure,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "سيُرسل مع سؤالك القادم",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                        IconButton(onClick = onClearPendingAttachment, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "إزالة المرفق", tint = SilverMuted)
                        }
                    }
                }
            }
        }

        // Chat Input Bar with Photo & File Pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                enabled = !isStreaming,
                onClick = onPickChatPhoto,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_attach_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = "إرفاق صورة مسألة",
                    tint = IceSapphireGlow
                )
            }
            IconButton(
                enabled = !isStreaming,
                onClick = onPickChatDoc,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_attach_doc_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = "إرفاق PDF أو ملف",
                    tint = SilverMetallic
                )
            }
            OutlinedTextField(
                value = questionDraft,
                onValueChange = { questionDraft = it },
                placeholder = { Text("اسأل أو أرفق صورة مسألة…", color = SilverMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SilverPure,
                    unfocusedTextColor = SilverMetallic,
                    focusedBorderColor = IceSapphire,
                    unfocusedBorderColor = MidnightBorder
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("study_chat_input")
            )
            val canSend = (questionDraft.isNotBlank() || pendingAttachment != null) && !isStreaming
            Surface(
                shape = CircleShape,
                color = if (canSend) SilverPure else MidnightSurfaceHigh,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    enabled = canSend,
                    onClick = {
                        val toSend = questionDraft
                        questionDraft = ""
                        onSendQuestion(toSend)
                    },
                    modifier = Modifier.testTag("study_chat_send")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال السؤال",
                        tint = if (canSend) MidnightVoid else SilverMuted
                    )
                }
            }
        }
    }

    zoomedAttachmentPath?.let { path ->
        FullscreenVisualZoomDialog(
            imagePath = path,
            title = "المرفق",
            onSaveToGallery = {},
            onShare = {},
            onDismiss = { zoomedAttachmentPath = null }
        )
    }
}

@Composable
private fun TutorMessageBubble(
    isUser: Boolean,
    text: String,
    attachmentPath: String,
    attachmentName: String,
    attachmentMimeType: String,
    subtitleBadge: String?,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) IceSapphire.copy(alpha = 0.22f) else MidnightSurfaceHigh,
            border = BorderStroke(
                1.dp,
                if (isUser) IceSapphireGlow.copy(alpha = 0.55f) else MidnightBorder
            ),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "أنت" else "معلم بيان الذكي",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) IceSapphireGlow else EmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (subtitleBadge != null) {
                            Text(
                                text = subtitleBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isStreaming) IceSapphireGlow else SilverMuted
                            )
                        }
                        if (!isUser && !isStreaming) {
                            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "نسخ الإجابة",
                                    tint = SilverMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (attachmentPath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val isImg = attachmentMimeType.startsWith("image/")
                    if (isImg) {
                        AsyncImage(
                            model = File(attachmentPath),
                            contentDescription = attachmentName.ifBlank { "صورة مرفقة" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onImageClick(attachmentPath) }
                        )
                    } else if (attachmentName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MidnightNavy
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(16.dp))
                                Text(attachmentName, style = MaterialTheme.typography.labelSmall, color = SilverPure)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverPure
                )
            }
        }
    }
}
