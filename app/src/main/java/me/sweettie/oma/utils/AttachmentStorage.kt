package me.sweettie.oma.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AttachmentStorage {
    private const val PREFS_NAME = "attachments_prefs"
    private const val KEY_UPLOADED = "uploaded_files"
    private const val KEY_SELECTED = "selected_files"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Selected files
    fun saveSelectedFiles(selected: Set<String>) {
        prefs.edit { putStringSet(KEY_SELECTED, selected) }
    }

    fun getSelectedFiles(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED, emptySet()) ?: emptySet()
    }

    fun selectFile(uri: String) {
        val selected = getSelectedFiles().toMutableSet()
        selected.add(uri)
        saveSelectedFiles(selected)
    }

    fun deselectFile(id: String) {
        val selected = getSelectedFiles().toMutableSet()
        selected.remove(id)
        saveSelectedFiles(selected)
    }

    fun clearSelectedFiles() {
        prefs.edit { remove(KEY_SELECTED) }
    }

    // Uploaded files (Map serialized as Set of "id=name" strings)

    fun saveUploadedFiles(map: Map<String, String>) {
        val serializedSet = map.map { "${it.key}=${it.value}" }.toSet()
        prefs.edit { putStringSet(KEY_UPLOADED, serializedSet) }
    }

    fun getUploadedFiles(): Map<String, String> {
        val serializedSet = prefs.getStringSet(KEY_UPLOADED, emptySet()) ?: emptySet()
        return serializedSet.mapNotNull {
            val split = it.split("=", limit = 2)
            if (split.size == 2) split[0] to split[1] else null
        }.toMap()
    }

    fun addUploadedFile(id: String, name: String) {
        val current = getUploadedFiles().toMutableMap()
        current[id] = name
        saveUploadedFiles(current)
    }

    fun clearUploadedFiles() {
        prefs.edit { remove(KEY_UPLOADED) }
    }
}