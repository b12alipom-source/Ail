package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.example.vault.SignedInAccountInfo
import com.example.vault.VaultSettingsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VaultBackupUiState(
    val settings: VaultSettingsState = VaultSettingsState(),
    val signedInUser: SignedInAccountInfo? = null,
    val isSigningIn: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val busyLabel: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val bannerDismissed: Boolean = false
)

@Composable
fun ReinstallRecoveryBanner(
    isLocalLibraryEmpty: Boolean,
    vaultState: VaultBackupUiState,
    onPickVaultFolderForSetup: () -> Unit,
    onPickVaultFolderForRestore: () -> Unit,
    onSignInAndRestoreCloud: () -> Unit,
    onOpenFullBackupSheet: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (vaultState.bannerDismissed) return

    // Hide banner once user already has items AND both device vault & cloud sync are set up cleanly
    val fullyProtected = vaultState.settings.isVaultConfigured && vaultState.signedInUser != null
    if (!isLocalLibraryEmpty && fullyProtected) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurfaceHigh,
        border = BorderStroke(
            1.dp,
            if (isLocalLibraryEmpty) IceSapphireGlow.copy(alpha = 0.55f) else EmeraldSuccess.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MidnightNavy,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLocalLibraryEmpty) Icons.Outlined.Restore else Icons.Outlined.Backup,
                            contentDescription = null,
                            tint = if (isLocalLibraryEmpty) IceSapphireGlow else EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLocalLibraryEmpty) {
                            "استعادة فيديوهاتك وملخصاتك بعد إعادة التثبيت"
                        } else {
                            "احفظ أعمالك بشكل دائم حتى لو حُذف التطبيق"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SilverPure
                    )
                    Text(
                        text = if (isLocalLibraryEmpty) {
                            "إذا كنت قد نسخت بياناتك سابقاً، استعدها فوراً من مجلد BayanVault أو حساب Google."
                        } else {
                            "فعّل الحفظ التلقائي إلى مجلد دائم على جهازك (مع نسخة من الفيديو) ومزامنة سحابية."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMetallic
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("لاحقاً", color = SilverMuted)
                }
            }

            if (isLocalLibraryEmpty) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPickVaultFolderForRestore,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SilverPure,
                            contentColor = MidnightVoid
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("banner_restore_vault_button")
                    ) {
                        Icon(Icons.Outlined.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("من مجلد الجهاز", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onSignInAndRestoreCloud,
                        border = BorderStroke(1.dp, IceSapphireGlow),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("banner_restore_cloud_button")
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (vaultState.signedInUser != null) "مزامنة السحابة" else "حساب Google",
                            color = SilverPure,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = if (vaultState.settings.isVaultConfigured) onOpenFullBackupSheet else onPickVaultFolderForSetup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSuccess,
                            contentColor = MidnightVoid
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("banner_setup_vault_button")
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (vaultState.settings.isVaultConfigured) "المجلد الدائم جاهز ✓" else "اختر مجلد الحفظ الدائم",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenFullBackupSheet,
                        border = BorderStroke(1.dp, MidnightBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("banner_open_backup_sheet_button")
                    ) {
                        Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("السحابة", color = SilverPure)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermanentVaultAndSyncSheet(
    vaultState: VaultBackupUiState,
    onPickVaultFolder: () -> Unit,
    onPickFolderAndRestore: () -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onToggleIncludeVideoCopy: (Boolean) -> Unit,
    onBackupNowToVault: () -> Unit,
    onRestoreNowFromVault: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSyncNowToCloud: () -> Unit,
    onRestoreNowFromCloud: () -> Unit,
    onDismissStatus: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = "الحفظ الدائم والمزامنة السحابية",
                style = MaterialTheme.typography.titleLarge,
                color = SilverPure,
                fontWeight = FontWeight.Bold
            )

            PermanentVaultAndSyncSection(
                vaultState = vaultState,
                onPickVaultFolder = onPickVaultFolder,
                onPickFolderAndRestore = onPickFolderAndRestore,
                onToggleAutoSave = onToggleAutoSave,
                onToggleIncludeVideoCopy = onToggleIncludeVideoCopy,
                onBackupNowToVault = onBackupNowToVault,
                onRestoreNowFromVault = onRestoreNowFromVault,
                onSignInWithGoogle = onSignInWithGoogle,
                onSignOutGoogle = onSignOutGoogle,
                onSyncNowToCloud = onSyncNowToCloud,
                onRestoreNowFromCloud = onRestoreNowFromCloud,
                onDismissStatus = onDismissStatus
            )

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

@Composable
fun PermanentVaultAndSyncSection(
    vaultState: VaultBackupUiState,
    onPickVaultFolder: () -> Unit,
    onPickFolderAndRestore: () -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onToggleIncludeVideoCopy: (Boolean) -> Unit,
    onBackupNowToVault: () -> Unit,
    onRestoreNowFromVault: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    onSyncNowToCloud: () -> Unit,
    onRestoreNowFromCloud: () -> Unit,
    onDismissStatus: () -> Unit
) {
    val isAnyBusy = vaultState.isBackingUp || vaultState.isRestoring || vaultState.isSigningIn

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isAnyBusy) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MidnightNavy,
                border = BorderStroke(1.dp, IceSapphireGlow.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = vaultState.busyLabel ?: "جاري تنفيذ العملية…",
                        style = MaterialTheme.typography.labelMedium,
                        color = IceSapphireGlow
                    )
                    LinearProgressIndicator(
                        color = EmeraldSuccess,
                        trackColor = MidnightSurfaceHigh,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        vaultState.errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CoralError.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, CoralError.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismissStatus() }
            ) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverPure,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        vaultState.statusMessage?.let { ok ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismissStatus() }
            ) {
                Text(
                    text = ok,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverPure,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Card A: Permanent Device Folder (BayanVault SAF)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(
                1.dp,
                if (vaultState.settings.isVaultConfigured) EmeraldSuccess.copy(alpha = 0.55f) else MidnightBorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.FolderSpecial,
                        contentDescription = null,
                        tint = if (vaultState.settings.isVaultConfigured) EmeraldSuccess else IceSapphireGlow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مجلد الجهاز الدائم (يبقى بعد حذف التطبيق)",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                        Text(
                            text = if (vaultState.settings.isVaultConfigured) {
                                "المسار النشط: ${vaultState.settings.vaultFolderLabel}"
                            } else {
                                "اختر مجلداً على ذاكرة الهاتف (مثل Documents أو Downloads) لحفظ الفيديوهات والترجمات والملخصات وصور الخرائط."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMetallic
                        )
                    }
                }

                if (vaultState.settings.lastVaultSyncAt > 0L) {
                    Text(
                        text = "آخر نسخ تلقائي للمجلد: ${formatVaultTime(vaultState.settings.lastVaultSyncAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حفظ تلقائي فور الاكتمال", style = MaterialTheme.typography.bodyMedium, color = SilverPure)
                        Text("ينسخ كل فيديو أو ملخص أو خريطة ذهنية تلقائياً فور جاهزيتها.", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    }
                    Switch(
                        checked = vaultState.settings.autoSaveEnabled,
                        onCheckedChange = onToggleAutoSave,
                        colors = SwitchDefaults.colors(checkedThumbColor = MidnightVoid, checkedTrackColor = EmeraldSuccess),
                        modifier = Modifier.testTag("vault_autosave_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تضمين نسخة من ملف الفيديو", style = MaterialTheme.typography.bodyMedium, color = SilverPure)
                        Text("يحفظ ملف الفيديو نفسه داخل المجلد الدائم مع الترجمة SRT والملخص.", style = MaterialTheme.typography.labelSmall, color = SilverMuted)
                    }
                    Switch(
                        checked = vaultState.settings.includeVideoCopy,
                        onCheckedChange = onToggleIncludeVideoCopy,
                        colors = SwitchDefaults.colors(checkedThumbColor = MidnightVoid, checkedTrackColor = EmeraldSuccess),
                        modifier = Modifier.testTag("vault_include_video_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickVaultFolder,
                        enabled = !isAnyBusy,
                        border = BorderStroke(1.dp, MidnightBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pick_vault_folder_button")
                    ) {
                        Text(
                            text = if (vaultState.settings.isVaultConfigured) "تغيير المجلد" else "اختيار المجلد الدائم",
                            color = SilverPure,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onBackupNowToVault,
                        enabled = vaultState.settings.isVaultConfigured && !isAnyBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSuccess,
                            contentColor = MidnightVoid
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("backup_vault_now_button")
                    ) {
                        Text("نسخ الكل الآن", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = if (vaultState.settings.isVaultConfigured) onRestoreNowFromVault else onPickFolderAndRestore,
                    enabled = !isAnyBusy,
                    border = BorderStroke(1.dp, IceSapphireGlow.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("restore_vault_button")
                ) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (vaultState.settings.isVaultConfigured) {
                            "استعادة الفيديوهات والملخصات من المجلد"
                        } else {
                            "تحديد مجلد BayanVault واستعادة بياناتك"
                        },
                        color = SilverPure
                    )
                }
            }
        }

        // Card B: Google Sign-In & Firestore Cloud Sync
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MidnightSurfaceHigh,
            border = BorderStroke(
                1.dp,
                if (vaultState.signedInUser != null) IceSapphireGlow.copy(alpha = 0.55f) else MidnightBorderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (vaultState.signedInUser != null) Icons.Outlined.CloudDone else Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = if (vaultState.signedInUser != null) IceSapphireGlow else SilverMetallic,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المزامنة السحابية عبر حساب Google",
                            style = MaterialTheme.typography.titleMedium,
                            color = SilverPure
                        )
                        Text(
                            text = if (vaultState.signedInUser != null) {
                                "${vaultState.signedInUser.displayName} (${vaultState.signedInUser.email})"
                            } else {
                                "اربط حساب Google لحفظ ترجمات الفيديو وملخصات الدراسة ومحادثات المعلم وجداولك سحابياً عبر أي جهاز."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMetallic
                        )
                    }
                }

                if (vaultState.settings.lastCloudSyncAt > 0L) {
                    Text(
                        text = "آخر مزامنة سحابية: ${formatVaultTime(vaultState.settings.lastCloudSyncAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = IceSapphireGlow
                    )
                }

                if (vaultState.signedInUser == null) {
                    Button(
                        onClick = onSignInWithGoogle,
                        enabled = !isAnyBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SilverPure,
                            contentColor = MidnightVoid
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_signin_button")
                    ) {
                        if (vaultState.isSigningIn) {
                            CircularProgressIndicator(
                                color = MidnightVoid,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري تسجيل الدخول…", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الدخول بحساب Google", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSyncNowToCloud,
                            enabled = !isAnyBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IceSapphire,
                                contentColor = MidnightVoid
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cloud_sync_now_button")
                        ) {
                            Text("رفع للسحابة الآن", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onRestoreNowFromCloud,
                            enabled = !isAnyBusy,
                            border = BorderStroke(1.dp, IceSapphireGlow),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("cloud_restore_now_button")
                        ) {
                            Text("استعادة من السحابة", color = SilverPure, maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = onSignOutGoogle,
                        enabled = !isAnyBusy,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("تسجيل الخروج من الحساب", color = CoralError)
                    }
                }
            }
        }
    }
}

private fun formatVaultTime(epochMs: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }.getOrDefault("")
}

internal tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
