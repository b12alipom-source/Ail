package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExportFormat
import com.example.data.SubtitleDisplayMode
import com.example.data.db.SubtitleCueEntity
import com.example.export.SubtitleExporter
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightBorderSubtle
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SilverMetallic
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    projectTitle: String,
    cues: List<SubtitleCueEntity>,
    displayMode: SubtitleDisplayMode,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.SRT) }
    val previewContent = remember(cues, selectedFormat, displayMode) {
        SubtitleExporter.buildExportContent(cues, selectedFormat, displayMode)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copyToast = stringResource(R.string.copy_success)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightSurface,
        contentColor = SilverPure
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.export_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = SilverPure
            )
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.chunked(2).forEach { rowFormats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowFormats.forEach { format ->
                            val isSelected = selectedFormat == format
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MidnightSurfaceHigh else MidnightVoid.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) IceSapphire else MidnightBorderSubtle
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_format_${format.name.lowercase()}")
                                    .clickable { selectedFormat = format }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = format.extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) IceSapphire else SilverMuted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = format.arabicLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = SilverPure
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "معاينة المخرجات",
                style = MaterialTheme.typography.labelMedium,
                color = SilverMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MidnightVoid,
                border = BorderStroke(1.dp, MidnightBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 170.dp)
            ) {
                Text(
                    text = previewContent.take(1200),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMetallic,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Bayan Subtitles", previewContent)
                        )
                        Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
                    },
                    border = BorderStroke(1.dp, MidnightBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("copy_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "نسخ النص",
                        tint = SilverPure,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("نسخ", color = SilverPure)
                }

                Button(
                    onClick = {
                        val shareIntent = SubtitleExporter.createShareIntent(
                            context = context,
                            projectTitle = projectTitle,
                            cues = cues,
                            format = selectedFormat,
                            displayMode = displayMode
                        )
                        context.startActivity(shareIntent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SilverPure,
                        contentColor = MidnightVoid
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("share_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "مشاركة الملف",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مشاركة الملف",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
