package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AppCheckDiagnosticState
import com.example.data.StudyAiTier
import com.example.data.StudyEngineConfig
import com.example.data.StudyMethod
import com.example.data.db.VideoProjectEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.IceSapphireGlow
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightBorderSubtle
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SilverMetallic
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyAiSettingsSheet(
    engineConfig: StudyEngineConfig,
    appCheckState: AppCheckDiagnosticState,
    isCheckingAppCheck: Boolean,
    onSelectTier: (StudyAiTier) -> Unit,
    onSavePersonalKey: (String) -> Unit,
    onTogglePreferPersonalKey: (Boolean) -> Unit,
    onClearPersonalKey: () -> Unit,
    onRunAppCheckCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyDraft by remember(engineConfig.personalApiKey) {
        mutableStateOf(engineConfig.personalApiKey)
    }
    var showKeyPlain by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        contentColor = SilverPure
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "إعدادات المحرك الذكي ومستوى التحليل",
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure,
                fontWeight = FontWeight.Bold
            )

            // Reasoning Tier selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(18.dp))
                    Text("قوة التحليل والاستدلال:", style = MaterialTheme.typography.labelLarge, color = SilverPure)
                }
                StudyAiTier.entries.forEach { tier ->
                    val selected = engineConfig.tier == tier
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) IceSapphire.copy(alpha = 0.2f) else MidnightNavy,
                        border = BorderStroke(1.dp, if (selected) IceSapphireGlow else MidnightBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTier(tier) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tier.arabicLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) SilverPure else SilverMetallic,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tier.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverMuted
                            )
                        }
                    }
                }
            }

            // Personal Gemini API Key Optional Fallback Section
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightNavy,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                            Text(
                                text = "مفتاح Gemini شخصي (اختياري)",
                                style = MaterialTheme.typography.titleSmall,
                                color = SilverPure,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (engineConfig.hasPersonalKey) {
                            Switch(
                                checked = engineConfig.preferPersonalKey,
                                onCheckedChange = onTogglePreferPersonalKey,
                                colors = SwitchDefaults.colors(checkedThumbColor = MidnightVoid, checkedTrackColor = EmeraldSuccess)
                            )
                        }
                    }

                    Text(
                        text = "التطبيق يعمل افتراضياً عبر سحابة Firebase بدون مفتاح. إذا واجهت رفض App Check بسبب التثبيت الجانبي (APK) أو رغبت بتوليد الصور عبر مفتاحك الشخصي من Google AI Studio، أدخله هنا ليُحفظ بأمان على جهازك فقط.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )

                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        singleLine = true,
                        label = { Text("AIzaSy...") },
                        visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKeyPlain = !showKeyPlain }) {
                                Icon(
                                    imageVector = if (showKeyPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "إظهار أو إخفاء المفتاح",
                                    tint = SilverMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SilverPure,
                            unfocusedTextColor = SilverMetallic,
                            focusedBorderColor = IceSapphire,
                            unfocusedBorderColor = MidnightBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personal_gemini_key_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = apiKeyDraft.trim().length >= 16,
                            onClick = { onSavePersonalKey(apiKeyDraft) },
                            colors = ButtonDefaults.buttonColors(containerColor = SilverPure, contentColor = MidnightVoid),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ وتفعيل المفتاح", fontWeight = FontWeight.Bold)
                        }
                        if (engineConfig.hasPersonalKey) {
                            OutlinedButton(
                                onClick = {
                                    apiKeyDraft = ""
                                    onClearPersonalKey()
                                },
                                border = BorderStroke(1.dp, CoralError.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("مسح", color = CoralError)
                            }
                        }
                    }
                }
            }

            // App Check Diagnostic Status
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MidnightNavy,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = IceSapphireGlow)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تشخيص خادم Firebase App Check",
                            style = MaterialTheme.typography.labelMedium,
                            color = SilverPure
                        )
                        val statusDesc = when (appCheckState) {
                            is AppCheckDiagnosticState.Initializing -> "جاري التحضير (${appCheckState})"
                            is AppCheckDiagnosticState.Verified -> "موثّق ونشط (${appCheckState.providerName})"
                            is AppCheckDiagnosticState.AttentionNeeded -> "يحتاج انتباه: ${appCheckState.reason}"
                            is AppCheckDiagnosticState.RateLimited -> "تهدئة مؤقتة: ${appCheckState.reason}"
                        }
                        Text(
                            text = statusDesc,
                            style = MaterialTheme.typography.labelSmall,
                            color = SilverMuted
                        )
                    }
                    IconButton(onClick = onRunAppCheckCheck, enabled = !isCheckingAppCheck) {
                        if (isCheckingAppCheck) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = IceSapphireGlow)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "فحص الاتصال", tint = IceSapphireGlow)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
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
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
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
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
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
