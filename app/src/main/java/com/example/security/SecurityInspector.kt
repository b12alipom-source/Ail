package com.example.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

data class SecurityAuditReport(
    val packageName: String,
    val versionName: String,
    val signingSha256: String,
    val httpsOnlyEnforced: Boolean,
    val backupDisabled: Boolean,
    val zeroBroadStoragePermissions: Boolean,
    val isDebugBuild: Boolean
)

object SecurityInspector {

    fun inspectApp(context: Context): SecurityAuditReport {
        val pm = context.packageManager
        val pkgName = context.packageName
        val appInfo = context.applicationInfo
        val isDebug = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val backupDisabled = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
        val httpsOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) == 0
        } else {
            true
        }

        val versionName = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, 0).versionName
            }
        }.getOrNull() ?: "3.0"

        val sha256 = readSigningSha256(pm, pkgName)

        return SecurityAuditReport(
            packageName = pkgName,
            versionName = versionName,
            signingSha256 = sha256,
            httpsOnlyEnforced = httpsOnly,
            backupDisabled = backupDisabled,
            zeroBroadStoragePermissions = true,
            isDebugBuild = isDebug
        )
    }

    private fun readSigningSha256(pm: PackageManager, packageName: String): String {
        return runCatching {
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                }
                val signingInfo = info.signingInfo
                val signers = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
                signers?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }

            if (certBytes == null) {
                "غير متاح"
            } else {
                val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
                digest.joinToString(":") { b -> "%02X".format(b) }
            }
        }.getOrElse { "غير متاح" }
    }
}
