package com.ngbautoroad.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

class DataExporter(private val context: Context) {
    fun exportToCsv(filename: String, headers: List<String>, rows: List<List<String>>): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), "$filename.csv")
            val bom = "\uFEFF"
            file.writeText(bom + headers.joinToString(";") + "\n")
            rows.forEach { row -> file.appendText(row.joinToString(";") { escapeCSV(it) } + "\n") }
            file
        } catch (_: Exception) { null }
    }

    fun shareFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar Dados"))
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(";") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\""
        else value
    }
}
