package com.example.vault

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "bayan_vault_prefs"
private const val KEY_TREE_URI = "vault_tree_uri"
private const val KEY_FOLDER_LABEL = "vault_folder_label"
private const val KEY_AUTO_SAVE = "auto_save_enabled"
private const val KEY_INCLUDE_VIDEO = "include_video_copy"
private const val KEY_LAST_VAULT_SYNC = "last_vault_sync_at"
private const val KEY_LAST_CLOUD_SYNC = "last_cloud_sync_at"

class VaultPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<VaultSettingsState> = _state.asStateFlow()

    fun currentState(): VaultSettingsState {
        val fresh = loadState()
        _state.value = fresh
        return fresh
    }

    fun saveVaultTreeUri(uri: Uri, folderLabel: String) {
        prefs.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .putString(KEY_FOLDER_LABEL, folderLabel.trim().ifBlank { "مجلد بيان الدائم" })
            .apply()
        _state.value = loadState()
    }

    fun clearVaultTreeUri() {
        prefs.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_FOLDER_LABEL)
            .apply()
        _state.value = loadState()
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE, enabled).apply()
        _state.value = loadState()
    }

    fun setIncludeVideoCopy(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_VIDEO, enabled).apply()
        _state.value = loadState()
    }

    fun markVaultSynced(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_VAULT_SYNC, timestamp).apply()
        _state.value = loadState()
    }

    fun markCloudSynced(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_CLOUD_SYNC, timestamp).apply()
        _state.value = loadState()
    }

    private fun loadState(): VaultSettingsState {
        val rawUri = prefs.getString(KEY_TREE_URI, "")?.trim().orEmpty()
        val hasPerm = if (rawUri.isBlank()) {
            false
        } else {
            runCatching {
                val target = Uri.parse(rawUri)
                context.contentResolver.persistedUriPermissions.any { perm ->
                    perm.uri == target && perm.isReadPermission && perm.isWritePermission
                }
            }.getOrDefault(false)
        }
        return VaultSettingsState(
            vaultTreeUri = rawUri,
            vaultFolderLabel = prefs.getString(KEY_FOLDER_LABEL, "")?.trim().orEmpty(),
            hasValidPersistedPermission = hasPerm,
            autoSaveEnabled = prefs.getBoolean(KEY_AUTO_SAVE, true),
            includeVideoCopy = prefs.getBoolean(KEY_INCLUDE_VIDEO, true),
            lastVaultSyncAt = prefs.getLong(KEY_LAST_VAULT_SYNC, 0L),
            lastCloudSyncAt = prefs.getLong(KEY_LAST_CLOUD_SYNC, 0L)
        )
    }
}
