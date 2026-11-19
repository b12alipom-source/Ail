package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AppCheckDiagnosticState
import kotlinx.coroutines.delay
import com.example.data.SupportedLanguages
import com.example.media.InternalStorageStats
import com.example.media.VoskDownloadProgress
import com.example.media.VoskModelDescriptor
import com.example.media.formatByteSizeArabic
import com.example.security.SecurityAuditReport
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CloudAndApkSheet(
    appCheckStatus: AppCheckDiagnosticState,
    isCheckingAppCheck: Boolean,
    offlineEngineMode: Boolean,
    downloadedOfflinePacks: Set<String>,
    downloadingPackCode: String?,
    packActionError: String?,
    voskCatalog: List<VoskModelDescriptor>,
    downloadedVoskModels: Set<String>,
    voskDownloadProgress: VoskDownloadProgress?,
    voskActionError: String?,
    securityReport: SecurityAuditReport,
    storageStats: InternalStorageStats,
    storageNotice: String?,
    onClearTempCache: () -> Unit,
    onDismissStorageNotice: () -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onRunAppCheckDiagnostics: () -> Unit,
    onDownloadPack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onDismissPackError: () -> Unit,
    onDownloadVoskModel: (String) -> Unit,
    onDeleteVoskModel: (String) -> Unit,
    onDismissVoskError: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        contentColor = SilverPure
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.cloud_status_title),
                style = MaterialTheme.typography.titleLarge,
                color = SilverPure
            )

            // 0. Internal App Storage & Cache Summary
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "التخزين الداخلي للتطبيق",
                                style = MaterialTheme.typography.titleMedium,
                                color = SilverPure
                            )
                        }
                        Text(
                            text = formatByteSizeArabic(storageStats.totalBytes),
                            style = MaterialTheme.typography.labelLarge,
                            color = IceSapphireGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "تحفظ الفيديوهات المترجمة وملخصاتك ومرفقات الدراسة محلياً داخل التطبيق فقط دون أي مجلدات خارجية أو حسابات سحابية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMetallic
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightNavy,
                        border = BorderStroke(1.dp, MidnightBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StorageStatRow(
                                label = "نسخ الفيديو المحفوظة (${storageStats.savedVideosCount})",
                                value = formatByteSizeArabic(storageStats.savedVideosBytes)
                            )
                            StorageStatRow(
                                label = "ملفات الدراسة والصور",
                                value = formatByteSizeArabic(storageStats.studyAssetsBytes)
                            )
                            StorageStatRow(
                                label = "ملفات الكاش المؤقتة",
                                value = formatByteSizeArabic(storageStats.tempCacheBytes)
                            )
                        }
                    }

                    storageNotice?.let { note ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldSuccess.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissStorageNotice() }
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onClearTempCache,
                        border = BorderStroke(1.dp, MidnightBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_temp_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = IceSapphireGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تنظيف الكاش المؤقت", color = SilverPure)
                    }
                }
            }

            // 1. Standalone Offline Engine Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(
                    1.dp,
                    if (offlineEngineMode) EmeraldSuccess.copy(alpha = 0.6f) else MidnightBorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (offlineEngineMode) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone,
                        contentDescription = null,
                        tint = if (offlineEngineMode) EmeraldSuccess else IceSapphireGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الوضع المحلي المستقل (أوفلاين 100%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                        Text(
                            text = "يعمل مباشرة على جهازك لترجمة النصوص والصوت وملفات SRT بدون أي اتصال أو App Check.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMetallic
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = offlineEngineMode,
                        onCheckedChange = onToggleOfflineMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MidnightVoid,
                            checkedTrackColor = EmeraldSuccess
                        ),
                        modifier = Modifier.testTag("offline_mode_switch")
                    )
                }
            }

            // 2. Live App Check & Cloud Engine Diagnostics
            var cooldownRemainingSec by remember(appCheckStatus) {
                mutableIntStateOf(
                    if (appCheckStatus is AppCheckDiagnosticState.RateLimited) {
                        ((appCheckStatus.cooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
                    } else 0
                )
            }
            LaunchedEffect(appCheckStatus) {
                if (appCheckStatus is AppCheckDiagnosticState.RateLimited) {
                    while (true) {
                        val rem = ((appCheckStatus.cooldownUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
                        cooldownRemainingSec = rem
                        if (rem <= 0) break
                        delay(1000L)
                    }
                } else {
                    cooldownRemainingSec = 0
                }
            }

            val (statusIcon, statusTint, statusTitle, statusDesc) = when (appCheckStatus) {
                is AppCheckDiagnosticState.Verified -> QuadInfo(
                    Icons.Outlined.VerifiedUser,
                    EmeraldSuccess,
                    "التحقق السحابي جاهز (${appCheckStatus.providerName})",
                    "رمز الأمان نشط ومتصل بخوادم Firebase AI Logic. الفيديوهات الطويلة والترجمة الأدبية تعمل بأقصى دقة."
                )

                is AppCheckDiagnosticState.Initializing -> QuadInfo(
                    Icons.Outlined.CloudDone,
                    IceSapphireGlow,
                    "جاري تجهيز التحقق السحابي (App Check)",
                    "يمكنك تشغيل فحص التحقق الآن أو الاعتماد على التحويل التلقائي للترجمة المحلية."
                )

                is AppCheckDiagnosticState.RateLimited -> QuadInfo(
                    Icons.Outlined.WarningAmber,
                    CoralError,
                    if (cooldownRemainingSec > 0) {
                        "تهدئة حماية من الحظر (${cooldownRemainingSec} ث)"
                    } else {
                        "انتهت فترة التهدئة (${appCheckStatus.providerName})"
                    },
                    if (cooldownRemainingSec > 0) {
                        "تم إيقاف طلبات App Check مؤقتاً لمنع تكرار «Too many attempts». أثناء ذلك يعمل التحويل التلقائي المحلي للفيديوهات والنصوص بشكل طبيعي."
                    } else {
                        "يمكنك إعادة فحص التحقق الآن أو الاستمرار بالوضع المحلي الأوفلاين."
                    }
                )

                is AppCheckDiagnosticState.AttentionNeeded -> QuadInfo(
                    Icons.Outlined.WarningAmber,
                    CoralError,
                    "تعذر التحقق السحابي (${appCheckStatus.providerName})",
                    "السبب التقني: ${appCheckStatus.reason}\nسيتم التحويل تلقائياً للتفريغ والترجمة المحلية عند اختيار فيديو، أو يمكنك تفعيل الوضع المحلي بالكامل."
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = SilverPure
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = statusDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = SilverMetallic
                            )
                        }
                    }

                    val inCooldown = appCheckStatus is AppCheckDiagnosticState.RateLimited && cooldownRemainingSec > 0
                    OutlinedButton(
                        onClick = onRunAppCheckDiagnostics,
                        enabled = !isCheckingAppCheck && !inCooldown,
                        border = BorderStroke(1.dp, MidnightBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("appcheck_retest_button")
                    ) {
                        if (isCheckingAppCheck) {
                            CircularProgressIndicator(
                                color = IceSapphireGlow,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري فحص التحقق…", color = SilverPure)
                        } else if (inCooldown) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = CoralError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("انتظر ${cooldownRemainingSec} ث لإعادة الفحص بأمان", color = SilverMetallic)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فحص تحقق App Check الآن", color = SilverPure)
                        }
                    }
                }
            }

            // 3. Embedded Vosk Offline Speech Recognition Models
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نماذج الكلام الأوفلاين للفيديو والمايك (Vosk)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                    }
                    Text(
                        text = "محرك Vosk مدمج داخل التطبيق لتفريغ كلام الفيديو والمايك أوفلاين بدون خدمات النظام. ثبّت لغة الفيديو مرة واحدة وسيعمل دائماً حتى بدون إنترنت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMetallic
                    )

                    voskDownloadProgress?.let { prog ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MidnightNavy,
                            border = BorderStroke(1.dp, IceSapphire.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = prog.statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IceSapphireGlow
                                )
                                val pct = prog.progressPercent
                                if (pct != null && pct in 1..99) {
                                    LinearProgressIndicator(
                                        progress = { pct / 100f },
                                        color = EmeraldSuccess,
                                        trackColor = MidnightSurfaceHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        color = IceSapphireGlow,
                                        trackColor = MidnightSurfaceHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    voskActionError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CoralError.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissVoskError() }
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverPure,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voskCatalog.take(8).forEach { model ->
                            val isInstalled = downloadedVoskModels.contains(model.langCode)
                            val isBusyThis = voskDownloadProgress?.langCode == model.langCode
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = if (isInstalled) EmeraldSuccess.copy(alpha = 0.18f) else MidnightNavy,
                                border = BorderStroke(
                                    1.dp,
                                    if (isInstalled) EmeraldSuccess else MidnightBorderSubtle
                                ),
                                modifier = Modifier
                                    .testTag("vosk_pack_${model.langCode}")
                                    .clickable(enabled = voskDownloadProgress == null) {
                                        if (isInstalled) {
                                            onDeleteVoskModel(model.langCode)
                                        } else {
                                            onDownloadVoskModel(model.langCode)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    when {
                                        isBusyThis -> CircularProgressIndicator(
                                            color = IceSapphireGlow,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        isInstalled -> Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "حذف نموذج الكلام",
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Outlined.DownloadForOffline,
                                            contentDescription = "تنزيل نموذج الكلام",
                                            tint = IceSapphireGlow,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isInstalled) {
                                            "${model.arabicName} ✓"
                                        } else {
                                            "${model.arabicName} (${model.sizeLabel})"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isInstalled) SilverPure else SilverMetallic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Offline Text Translation Language Packs Manager (ML Kit)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.DownloadForOffline,
                            contentDescription = null,
                            tint = IceSapphireGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "حزم اللغات المحلية (للعمل بدون إنترنت)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                    }
                    Text(
                        text = "نزّل اللغات التي تستخدمها مرة واحدة لتعمل الترجمة النصية وملفات SRT والإملاء دون أي اتصال مستقبلاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMetallic
                    )

                    packActionError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CoralError.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissPackError() }
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverPure,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SupportedLanguages.allTargetLanguages.take(8).forEach { lang ->
                            val isDownloaded = downloadedOfflinePacks.contains(lang.code)
                            val isDownloading = downloadingPackCode == lang.code
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = if (isDownloaded) EmeraldSuccess.copy(alpha = 0.18f) else MidnightNavy,
                                border = BorderStroke(
                                    1.dp,
                                    if (isDownloaded) EmeraldSuccess else MidnightBorderSubtle
                                ),
                                modifier = Modifier.clickable(enabled = downloadingPackCode == null) {
                                    if (isDownloaded) {
                                        onDeletePack(lang.code)
                                    } else {
                                        onDownloadPack(lang.code)
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    when {
                                        isDownloading -> CircularProgressIndicator(
                                            color = IceSapphireGlow,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        isDownloaded -> Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "حذف الحزمة",
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Outlined.DownloadForOffline,
                                            contentDescription = "تنزيل الحزمة",
                                            tint = SilverMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isDownloaded) "${lang.arabicName} ✓" else lang.arabicName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isDownloaded) SilverPure else SilverMetallic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Security & Installation Trust Verification Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "درع الأمان وسلامة التثبيت (${securityReport.versionName}v)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                    }

                    SecurityBadgeRow(
                        label = "اتصال مشفر حصراً (HTTPS Only)",
                        ok = securityReport.httpsOnlyEnforced
                    )
                    SecurityBadgeRow(
                        label = "بدون أذونات تخزين أو وصول خاص",
                        ok = securityReport.zeroBroadStoragePermissions
                    )
                    SecurityBadgeRow(
                        label = "تعطيل النسخ الاحتياطي غير المشفر",
                        ok = securityReport.backupDisabled
                    )

                    Text(
                        text = "ملاحظة التثبيت: عند تثبيت أي APK من خارج متجر Play يعرض أندرويد تنبيه Play Protect تلقائياً بسبب التثبيت الجانبي. طابق بصمة SHA-256 أدناه مع صفحة الإصدارات وتثبيت التوقيع الموحد في GitHub يضمن سلامة تطبيقك وعدم اعتباره خطراً:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMetallic
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MidnightNavy,
                        border = BorderStroke(1.dp, MidnightBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SHA-256: ${securityReport.signingSha256}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverMetallic,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Bayan SHA-256", securityReport.signingSha256))
                                    Toast.makeText(context, "تم نسخ بصمة الشهادة", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "نسخ SHA-256",
                                    tint = SilverPure,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SilverPure,
                    contentColor = MidnightVoid
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class QuadInfo(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val desc: String
)

@Composable
private fun SecurityBadgeRow(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = if (ok) EmeraldSuccess else CoralError,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SilverPure
        )
    }
}

@Composable
private fun StorageStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SilverMetallic
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = SilverPure,
            fontWeight = FontWeight.SemiBold
        )
    }
}
