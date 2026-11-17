package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IceSapphire
import com.example.ui.theme.IceSapphireGlow
import com.example.ui.theme.MidnightBorderSubtle
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MidnightSurfaceHigh
import com.example.ui.theme.MidnightVoid
import com.example.ui.theme.SilverMetallic
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.SilverPure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAndApkSheet(
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.cloud_status_title),
                style = MaterialTheme.typography.titleLarge,
                color = SilverPure
            )

            InfoCard(
                icon = Icons.Outlined.CloudDone,
                iconTint = EmeraldSuccess,
                title = "الخادم السحابي الدائم (24/7)",
                description = "التطبيق متصل مباشرة بمحرك Firebase AI Logic (Gemini 3.8 Flash) السحابي الذي يعمل طوال الوقت دون انقطاع بمجرد تثبيت التطبيق على هاتفك."
            )

            InfoCard(
                icon = Icons.Outlined.InstallMobile,
                iconTint = IceSapphireGlow,
                title = "تثبيت التطبيق على هاتفك (APK)",
                description = "لتشغيل «بيان v${BuildConfig.VERSION_NAME}» مباشرة على أندرويد بدلاً من محاكي المتصفح: افتح قائمة الإعدادات (Settings) في أعلى منصة AI Studio واختر توليد وتحميل ملف APK ثم ثبّته على هاتفك."
            )

            InfoCard(
                icon = Icons.Outlined.VerifiedUser,
                iconTint = IceSapphire,
                title = "حماية الإصدار للهواتف الحقيقية",
                description = "نسخة الإصدار (Release) مهيأة بحماية App Check reCAPTCHA Enterprise وقاعدة بيانات محلية، لتعمل بكفاءة مستقلة خارج المتصفح."
            )

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
                Text("فهمت", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MidnightSurfaceHigh,
        border = BorderStroke(1.dp, MidnightBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = SilverPure
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SilverMetallic
                )
            }
        }
    }
}
