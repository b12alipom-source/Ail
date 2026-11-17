package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.LanguageOption
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightBorderSubtle
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.SilverMetallic
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    title: String,
    languages: List<LanguageOption>,
    selectedCode: String,
    onSelect: (LanguageOption) -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = SilverPure
            )
            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languages, key = { it.code }) { lang ->
                    val isSelected = lang.code.equals(selectedCode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MidnightSurfaceHigh else MidnightSurface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) IceSapphire.copy(alpha = 0.7f) else MidnightBorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lang_option_${lang.code}")
                            .clickable {
                                onSelect(lang)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IceSapphire.copy(alpha = 0.16f) else MidnightBorder.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = lang.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) IceSapphire else SilverMetallic,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.arabicName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = SilverPure
                                )
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SilverMuted
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "مختارة",
                                    tint = IceSapphire,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
