package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.StudyDossierData
import com.example.data.StudyMethod
import com.example.data.db.StudyAttachmentEntity
import com.example.data.db.StudyNotebookEntity
import com.example.data.db.StudyScheduleItemEntity
import com.example.media.formatStudySummaryForShare
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
        StudyAttachmentType.TEXT -> Icons.AutoMirrored.Outlined.Notes
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
    dossier: StudyDossierData?,
    onGenerateClick: () -> Unit
) {
    val context = LocalContext.current

    if (notebook.summaryMarkdown.isBlank() && dossier == null) {
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
                    text = "أرفق ملف PDF أو صورة درس أو فيديو مترجم، ثم اختر أسلوب المذاكرة واضغط على «توليد الملخص المتكامل والجدول».",
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
                    Text("تحليل وتوليد الآن", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header strip with Engine badge, Share and Copy All buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = notebook.lastEngineLabel.ifBlank { StudyMethod.fromKey(notebook.preferredMethod).arabicLabel },
                style = MaterialTheme.typography.labelMedium,
                color = IceSapphireGlow,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val exportText = formatStudySummaryForShare(
                            title = notebook.title,
                            methodLabel = StudyMethod.fromKey(notebook.preferredMethod).arabicLabel,
                            keyPoints = notebook.keyPointsText,
                            summaryMarkdown = notebook.summaryMarkdown,
                            dossier = dossier
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, notebook.title)
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملخص"))
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("share_study_summary_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "مشاركة وتصدير الملخص",
                        tint = IceSapphireGlow,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val exportText = formatStudySummaryForShare(
                            title = notebook.title,
                            methodLabel = StudyMethod.fromKey(notebook.preferredMethod).arabicLabel,
                            keyPoints = notebook.keyPointsText,
                            summaryMarkdown = notebook.summaryMarkdown,
                            dossier = dossier
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(notebook.title, exportText))
                        Toast.makeText(context, "تم نسخ الملخص بالكامل", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "نسخ الملخص بالكامل",
                        tint = SilverMetallic,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Rich Overview
        if (dossier != null && dossier.overview.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, IceSapphire.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "النظرة الشاملة للمادة",
                        style = MaterialTheme.typography.titleSmall,
                        color = IceSapphireGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dossier.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverPure
                    )
                }
            }
        }

        // Key Takeaways
        if (notebook.keyPointsText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "أهم النقاط المركّزة",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = notebook.keyPointsText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilverPure
                    )
                }
            }
        }

        // Structured Dossier Sections (when available)
        if (dossier != null && dossier.sections.isNotEmpty()) {
            dossier.sections.forEachIndexed { idx, section ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MidnightSurface,
                    border = BorderStroke(1.dp, MidnightBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. ${section.heading}",
                            style = MaterialTheme.typography.titleSmall,
                            color = SilverPure,
                            fontWeight = FontWeight.Bold
                        )
                        if (section.explanation.isNotBlank()) {
                            Text(
                                text = section.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SilverMetallic
                            )
                        }
                        if (section.keyFormulaOrRule.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = IceSapphire.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, IceSapphireGlow.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "القاعدة / القانون الجوهري:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IceSapphireGlow,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = section.keyFormulaOrRule,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverPure
                                    )
                                }
                            }
                        }
                        if (section.example.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MidnightNavy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "مثال تطبيقي:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = section.example,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverMetallic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Glossary Terms
            if (dossier.glossary.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MidnightSurface,
                    border = BorderStroke(1.dp, MidnightBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(18.dp))
                            Text(
                                text = "قاموس المصطلحات",
                                style = MaterialTheme.typography.titleSmall,
                                color = SilverPure,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        dossier.glossary.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MidnightNavy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.term,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = IceSapphireGlow,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = item.definition,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverMetallic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Exam Tips
            if (dossier.examTips.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AmberWarning.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                            Text(
                                text = "أسرار الامتحان وتنبيهات مهمة",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        dossier.examTips.forEach { tip ->
                            Text(
                                text = "• $tip",
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverPure
                            )
                        }
                    }
                }
            }
        } else if (notebook.summaryMarkdown.isNotBlank()) {
            // Backward-compatible markdown fallback for older notebooks
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
