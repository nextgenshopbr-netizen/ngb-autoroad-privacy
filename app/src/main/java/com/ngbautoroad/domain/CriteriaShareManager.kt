package com.ngbautoroad.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File

class CriteriaShareManager(private val context: Context) {
    fun exportCriteria(criteria: Map<String, Any>): File {
        val json = JSONObject(criteria)
        json.put("app_version", com.ngbautoroad.BuildConfig.VERSION_NAME)
        json.put("export_timestamp", System.currentTimeMillis())
        val file = File(context.cacheDir, "ngb_criteria_${System.currentTimeMillis()}.json")
        file.writeText(json.toString(2))
        return file
    }

    fun shareCriteria(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Critérios"))
    }

    fun importCriteria(uri: Uri): Map<String, Any>? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val text = input.bufferedReader().readText()
            input.close()
            val json = JSONObject(text)
            if (!json.has("app_version")) return null
            val map = mutableMapOf<String, Any>()
            json.keys().forEach { key -> map[key] = json.get(key) }
            map
        } catch (_: Exception) { null }
    }
}
