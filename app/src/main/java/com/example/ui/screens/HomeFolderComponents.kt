package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FolderAccent
import com.example.data.db.ProjectFolderEntity
import com.example.data.db.VideoProjectEntity
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

@Composable
internal fun FolderOrganizerStrip(
    folders: List<ProjectFolderEntity>,
    allProjects: List<VideoProjectEntity>,
    selectedFolderId: Long?,
    onSelectFolder: (Long?) -> Unit,
    onCreateFolderClick: () -> Unit,
    onEditFolderClick: (ProjectFolderEntity) -> Unit,
    onDeleteFolderClick: (ProjectFolderEntity) -> Unit
) {
    val activeFolder = remember(folders, selectedFolderId) {
        folders.firstOrNull { it.id == selectedFolderId }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allSelected = selectedFolderId == null
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = if (allSelected) SilverPure else MidnightSurface,
                border = BorderStroke(1.dp, if (allSelected) SilverPure else MidnightBorderSubtle),
                modifier = Modifier
                    .testTag("folder_chip_all")
                    .clickable { onSelectFolder(null) }
            ) {
                Text(
                    text = "الكل (${allProjects.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (allSelected) MidnightVoid else SilverMetallic,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }

            folders.forEach { folder ->
                val selected = folder.id == selectedFolderId
                val count = allProjects.count { it.folderId == folder.id }
                val accentColor = folderAccentColor(folder.accentKey)

                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (selected) accentColor.copy(alpha = 0.22f) else MidnightSurface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) accentColor else MidnightBorderSubtle
                    ),
                    modifier = Modifier
                        .testTag("folder_chip_${folder.id}")
                        .clickable { onSelectFolder(folder.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(accentColor, CircleShape)
                        )
                        Text(
                            text = "${folder.name} ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) SilverPure else SilverMetallic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, IceSapphire.copy(alpha = 0.5f)),
                modifier = Modifier
                    .testTag("create_folder_button")
                    .clickable { onCreateFolderClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "إنشاء مجلد جديد",
                        tint = IceSapphireGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "+ مجلد جديد",
                        style = MaterialTheme.typography.labelMedium,
                        color = SilverPure
                    )
                }
            }
        }

        if (activeFolder != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MidnightSurfaceHigh,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = folderAccentColor(activeFolder.accentKey),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "مجلد: ${activeFolder.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = SilverPure,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { onEditFolderClick(activeFolder) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "تعديل اسم المجلد",
                                tint = IceSapphireGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteFolderClick(activeFolder) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "حذف المجلد",
                                tint = CoralError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyFolderCard(
    onShowAllClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MidnightSurface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = IceSapphireGlow,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "هذا المجلد فارغ حالياً",
                style = MaterialTheme.typography.titleMedium,
                color = SilverPure
            )
            Text(
                text = "يمكنك ترجمة مقطع جديد هنا مباشرة أو نقل أي مقطع من «الكل» إلى هذا المجلد.",
                style = MaterialTheme.typography.bodySmall,
                color = SilverMuted
            )
            OutlinedButton(
                onClick = onShowAllClick,
                border = BorderStroke(1.dp, MidnightBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("عرض كل المقاطع", color = SilverPure)
            }
        }
    }
}

@Composable
internal fun FolderEditorDialog(
    title: String,
    initialName: String,
    initialAccentKey: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var nameDraft by remember(initialName) { mutableStateOf(initialName) }
    var accentDraft by remember(initialAccentKey) { mutableStateOf(initialAccentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text("اسم المجلد (مثلاً: الجامعة / أفلام / كورسات)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SilverPure,
                        unfocusedTextColor = SilverMetallic,
                        focusedBorderColor = IceSapphire,
                        unfocusedBorderColor = MidnightBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "لون المجلد:",
                    style = MaterialTheme.typography.labelMedium,
                    color = SilverMetallic
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderAccent.entries.forEach { accent ->
                        val selected = accent.key == accentDraft
                        val chipColor = folderAccentColor(accent.key)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) chipColor.copy(alpha = 0.24f) else MidnightNavy,
                            border = BorderStroke(1.dp, if (selected) chipColor else MidnightBorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { accentDraft = accent.key }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(chipColor, CircleShape)
                                )
                                Text(
                                    text = accent.arabicLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) SilverPure else SilverMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(nameDraft, accentDraft) },
                enabled = nameDraft.isNotBlank()
            ) {
                Text(confirmLabel, color = IceSapphireGlow, fontWeight = FontWeight.Bold)
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
internal fun RenameHomeProjectDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text("تسمية المقطع المترجم") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text("اسم المقطع الجديد") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SilverPure,
                    unfocusedTextColor = SilverMetallic,
                    focusedBorderColor = IceSapphire,
                    unfocusedBorderColor = MidnightBorder
                ),
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
internal fun MoveProjectToFolderDialog(
    project: VideoProjectEntity,
    folders: List<ProjectFolderEntity>,
    onDismiss: () -> Unit,
    onSelectFolder: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurfaceHigh,
        titleContentColor = SilverPure,
        title = { Text("نقل «${project.title}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (project.folderId == null) IceSapphire.copy(alpha = 0.2f) else MidnightNavy,
                    border = BorderStroke(
                        1.dp,
                        if (project.folderId == null) IceSapphireGlow else MidnightBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFolder(null) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("السجل العام (بدون مجلد)", color = SilverPure, style = MaterialTheme.typography.bodyMedium)
                        if (project.folderId == null) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = IceSapphireGlow, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (folders.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "لم تنشئ أي مجلدات بعد. اضغط على «+ مجلد جديد» من أعلى القائمة لإنشاء مجلدك الأول.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SilverMuted
                    )
                } else {
                    folders.forEach { folder ->
                        val selected = project.folderId == folder.id
                        val accentColor = folderAccentColor(folder.accentKey)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) accentColor.copy(alpha = 0.22f) else MidnightNavy,
                            border = BorderStroke(1.dp, if (selected) accentColor else MidnightBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFolder(folder.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(accentColor, CircleShape)
                                    )
                                    Text(folder.name, color = SilverPure, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = SilverPure)
            }
        }
    )
}

private fun folderAccentColor(key: String): Color = when (FolderAccent.fromKey(key)) {
    FolderAccent.SAPPHIRE -> IceSapphire
    FolderAccent.EMERALD -> EmeraldSuccess
    FolderAccent.AMBER -> AmberWarning
    FolderAccent.VIOLET -> CoralError
}
