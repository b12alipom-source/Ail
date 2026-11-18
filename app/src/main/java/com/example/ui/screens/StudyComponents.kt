package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudyAttachmentType
import com.example.data.StudyMethod
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyMessageEntity
import com.example.data.db.StudyNotebookEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.data.db.VideoProjectEntity
import com.example.ui.theme.AmberWarning
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
internal fun StudyAttachmentChip(
    attachment: StudyAttachmentEntity,
    onRemove: () -> Unit
) {
    val type = StudyAttachmentType.fromKey(attachment.attachmentType)
    val icon = when (type) {
        StudyAttachmentType.PDF -> Icons.Outlined.PictureAsPdf
        StudyAttachmentType.IMAGE -> Icons.Outlined.Image
        StudyAttachmentType.TEXT -> Icons.Outlined.Notes
        StudyAttachmentType.VIDEO_TRANSCRIPT -> Icons.Outlined.VideoLibrary
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MidnightNavy,
        border = BorderStroke(1.dp, MidnightBorder)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = type.arabicLabel,
                tint = IceSapphireGlow,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.width(120.dp)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverPure,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = type.arabicLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMuted,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "إزالة المرفق",
                    tint = SilverMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
internal fun StudySummaryTabContent(
    notebook: StudyNotebookEntity,
    onGenerateClick: () -> Unit
) {
    val context = LocalContext.current

    if (notebook.summaryMarkdown.isBlank()) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = IceSapphireGlow,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "لا يوجد ملخص مخصص بعد",
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure
                )
                Text(
                    text = "أرفق ملف PDF أو صورة درس أو فيديو مترجم، ثم اختر أسلوب المذاكرة واضغط على «توليد الملخص والجدول».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMuted
                )
                Button(
                    onClick = onGenerateClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IceSapphire,
                        contentColor = MidnightVoid
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("توليد الملخص الآن", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (notebook.keyPointsText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, IceSapphire.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أهم النقاط والمفاهيم",
                            style = MaterialTheme.typography.titleSmall,
                            color = IceSapphireGlow,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        notebook.title,
                                        "${notebook.keyPointsText}\n\n${notebook.summaryMarkdown}"
                                    )
                                )
                                Toast.makeText(context, "تم نسخ الملخص بالكامل", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "نسخ الملخص",
                                tint = SilverMetallic,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = notebook.keyPointsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverPure
                    )
                }
            }
        }

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
                Text(
                    text = "الملخص التفصيلي (${StudyMethod.fromKey(notebook.preferredMethod).arabicLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    color = SilverPure,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = notebook.summaryMarkdown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMetallic
                )
            }
        }
    }
}

@Composable
internal fun StudyScheduleTabContent(
    scheduleItems: List<StudyScheduleItemEntity>,
    onToggleCompleted: (Long, Boolean) -> Unit,
    onGenerateClick: () -> Unit
) {
    if (scheduleItems.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MidnightSurface,
            border = BorderStroke(1.dp, MidnightBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "لا يوجد جدول دراسي مخصص بعد",
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure
                )
                Text(
                    text = "حدّد مدة المذاكرة اليومية وعدد الأيام ليتم تقسيم دروسك إلى جلسات يومية تفاعلية.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMuted
                )
                Button(
                    onClick = onGenerateClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SilverPure,
                        contentColor = MidnightVoid
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إنشاء الجدول الدراسي", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val completedCount = scheduleItems.count { it.isCompleted }
    val progress = completedCount.toFloat() / scheduleItems.size.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(1.dp, MidnightBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "نسبة إنجاز الجدول",
                        style = MaterialTheme.typography.labelLarge,
                        color = SilverPure
                    )
                    Text(
                        text = "$completedCount / ${scheduleItems.size} جلسة",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = EmeraldSuccess,
                    trackColor = MidnightBorderSubtle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(99.dp))
                )
            }
        }

        scheduleItems.forEach { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (item.isCompleted) MidnightSurface.copy(alpha = 0.6f) else MidnightSurface,
                border = BorderStroke(
                    1.dp,
                    if (item.isCompleted) EmeraldSuccess.copy(alpha = 0.45f) else MidnightBorderSubtle
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCompleted(item.id, !item.isCompleted) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (item.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = if (item.isCompleted) "مكتملة" else "تحديد كمكتملة",
                        tint = if (item.isCompleted) EmeraldSuccess else IceSapphireGlow,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(24.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MidnightSurfaceHigh
                            ) {
                                Text(
                                    text = item.dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IceSapphireGlow,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${item.timeSlot} • ${item.durationMinutes} د",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMuted
                            )
                        }
                        Text(
                            text = item.taskTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (item.isCompleted) SilverMuted else SilverPure,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Text(
                            text = item.taskDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMetallic
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudyTutorChatTabContent(
    messages: List<StudyMessageEntity>,
    isBusy: Boolean,
    onSendQuestion: (String) -> Unit,
    onClearChat: () -> Unit
) {
    var questionDraft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "اسأل المعلم الذكي عن أي نقطة في ملفاتك",
                style = MaterialTheme.typography.labelLarge,
                color = SilverMetallic
            )
            if (messages.isNotEmpty()) {
                TextButton(onClick = onClearChat) {
                    Text("مسح المحادثة", color = SilverMuted)
                }
            }
        }

        if (messages.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurface,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "أمثلة سريعة للبدء:",
                        style = MaterialTheme.typography.labelMedium,
                        color = IceSapphireGlow
                    )
                    listOf(
                        "اشرح لي أهم فكرة في هذه المادة بأسلوب مبسط",
                        "اكتب لي 5 أسئلة امتحان متوقعة مع الحل",
                        "ما أهم المصطلحات التي يجب حفظها؟"
                    ).forEach { promptSuggestion ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MidnightNavy,
                            border = BorderStroke(1.dp, MidnightBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { onSendQuestion(promptSuggestion) }
                        ) {
                            Text(
                                text = promptSuggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverPure,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        } else {
            messages.forEach { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (msg.isUser) 16.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 16.dp
                        ),
                        color = if (msg.isUser) IceSapphire.copy(alpha = 0.22f) else MidnightSurfaceHigh,
                        border = BorderStroke(
                            1.dp,
                            if (msg.isUser) IceSapphireGlow.copy(alpha = 0.55f) else MidnightBorder
                        ),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (msg.isUser) "أنت" else "معلم بيان الذكي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (msg.isUser) IceSapphireGlow else EmeraldSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!msg.isUser && msg.isOfflineGenerated) {
                                    Text(
                                        text = "محلي أوفلاين",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SilverMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.messageText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SilverPure
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = questionDraft,
                onValueChange = { questionDraft = it },
                placeholder = { Text("اكتب سؤالك عن المادة هنا…", color = SilverMuted) },
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
            Surface(
                shape = CircleShape,
                color = if (questionDraft.isNotBlank() && !isBusy) SilverPure else MidnightSurfaceHigh,
                modifier = Modifier.size(48.dp)
            ) {
                IconButton(
                    enabled = questionDraft.isNotBlank() && !isBusy,
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
                        tint = if (questionDraft.isNotBlank() && !isBusy) MidnightVoid else SilverMuted
                    )
                }
            }
        }
    }
}

@Composable
internal fun CreateNotebookDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, method: StudyMethod, dailyMinutes: Int, totalDays: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(StudyMethod.FOCUSED_SUMMARY) }
    var dailyMinutes by remember { mutableIntStateOf(60) }
    var totalDays by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text("إنشاء دفتر / مادة دراسية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("اسم المادة (مثلاً: أحياء / فيزياء / لغة)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SilverPure,
                        unfocusedTextColor = SilverMetallic,
                        focusedBorderColor = IceSapphire,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("طريقة الشرح والتلخيص:", style = MaterialTheme.typography.labelMedium, color = SilverMetallic)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudyMethod.entries.forEach { m ->
                        val selected = m == method
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = if (selected) IceSapphire.copy(alpha = 0.25f) else MidnightNavy,
                            border = BorderStroke(1.dp, if (selected) IceSapphireGlow else MidnightBorderSubtle),
                            modifier = Modifier.clickable { method = m }
                        ) {
                            Text(
                                text = m.arabicLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) SilverPure else SilverMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { mins ->
                        val selected = dailyMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) SilverPure else MidnightNavy,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dailyMinutes = mins }
                        ) {
                            Text(
                                text = "$mins د",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MidnightVoid else SilverMetallic,
                                modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 6.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7, 14).forEach { days ->
                        val selected = totalDays == days
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) SilverPure else MidnightNavy,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { totalDays = days }
                        ) {
                            Text(
                                text = "$days أيام",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MidnightVoid else SilverMetallic,
                                modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.ifBlank { "مادة جديدة" }, method, dailyMinutes, totalDays) }
            ) {
                Text("إنشاء", color = IceSapphireGlow, fontWeight = FontWeight.Bold)
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
internal fun AddTextNoteDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, body: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text("إضافة نص أو ملاحظات مادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("عنوان الملاحظة / الفصل") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SilverPure,
                        unfocusedTextColor = SilverMetallic,
                        focusedBorderColor = IceSapphire,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("الصق أو اكتب محتوى الدرس هنا") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SilverPure,
                        unfocusedTextColor = SilverMetallic,
                        focusedBorderColor = IceSapphire,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body) },
                enabled = body.isNotBlank()
            ) {
                Text("إرفاق", color = IceSapphireGlow, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = SilverMuted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkTranslatedVideoSheet(
    projects: List<VideoProjectEntity>,
    onSelectProject: (VideoProjectEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        contentColor = SilverPure
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ربط مقطع مترجم بالمادة الدراسية",
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure
            )
            if (projects.isEmpty()) {
                Text(
                    text = "لا توجد مقاطع فيديو مترجمة بعد. ترجم مقطعاً من تبويب استوديو الفيديو ثم أرفقه هنا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMuted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projects, key = { it.id }) { proj ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MidnightNavy,
                            border = BorderStroke(1.dp, MidnightBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProject(proj) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VideoLibrary,
                                    contentDescription = null,
                                    tint = IceSapphireGlow
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = proj.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = SilverPure,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${proj.cueCount} سطر • ${proj.targetLanguageName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SilverMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
