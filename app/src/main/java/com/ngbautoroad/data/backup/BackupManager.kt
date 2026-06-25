package com.ngbautoroad.data.backup
// ============================================================================
// ARQUIVO: BackupManager.kt
// LOCALIZAÇÃO: data/backup/BackupManager.kt
// RESPONSABILIDADE: Exportação e importação completa de todos os dados do app
// DADOS INCLUÍDOS:
//   - DataStore (ngb_autoroad_prefs) → todas as configurações
//   - Room DB: ngb_autoroad_db → histórico de corridas
//   - Room DB: ngb_finance_db → ganhos, despesas, metas financeiras
//   - SharedPreferences: learning_engine → padrões aprendidos pela IA
//   - SharedPreferences: shift_prefs → estado do turno
//   - SharedPreferences: shift_history → histórico de turnos
// FORMATO: ZIP contendo arquivos JSON + cópias dos DBs SQLite
// VERSÃO: v6.9.4 — Reescrito para robustez e compatibilidade
// ============================================================================

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Gerenciador de backup completo do NGB AutoRoad.
 *
 * Exporta TODOS os dados do app em um arquivo ZIP:
 * - preferences.json (DataStore completo)
 * - shared_prefs_learning_engine.json
 * - shared_prefs_shift_prefs.json
 * - shared_prefs_shift_history.json
 * - ngb_autoroad_db (arquivo SQLite de corridas)
 * - ngb_finance_db (arquivo SQLite financeiro)
 * - backup_metadata.json (versão, data, contagens)
 *
 * Importa restaurando todos os dados acima.
 */
class BackupManager(private val context: Context) {

    companion object {
        private const val TAG = "NGB_BACKUP"
        private const val METADATA_FILE = "backup_metadata.json"
        private const val PREFS_FILE = "preferences.json"
        private const val SP_LEARNING = "shared_prefs_learning_engine.json"
        private const val SP_SHIFT_PREFS = "shared_prefs_shift_prefs.json"
        private const val SP_SHIFT_HISTORY = "shared_prefs_shift_history.json"
        private const val DB_RIDES = "ngb_autoroad_db"
        private const val DB_FINANCE = "ngb_finance_db"
        private const val DS_BINARY = "ngb_autoroad_prefs.preferences_pb"
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    data class BackupMetadata(
        val appVersion: String = "",
        val backupDate: String = "",
        val ridesCount: Int = 0,
        val financeRecords: Int = 0,
        val preferencesCount: Int = 0,
        val description: String = "NGB AutoRoad Full Backup"
    )

    // =========================================================================
    // EXPORTAÇÃO
    // =========================================================================

    /**
     * Exporta todos os dados para um arquivo ZIP no Uri fornecido.
     * @return Resumo do backup (metadados)
     */
    suspend fun exportBackup(outputUri: Uri): BackupMetadata = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando exportação de backup...")

        // 1. Checkpoint WAL para garantir dados no arquivo principal
        checkpointDatabases()

        // 2. Fechar databases para garantir integridade
        closeDatabases()

        val metadata = BackupMetadata(
            appVersion = com.ngbautoroad.BuildConfig.VERSION_NAME,
            backupDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            ridesCount = getRidesCount(),
            financeRecords = getFinanceCount(),
            preferencesCount = getPreferencesCount()
        )

        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // Metadata
                writeJsonToZip(zipOut, METADATA_FILE, json.encodeToString(metadata))

                // DataStore preferences
                val prefsJson = exportDataStoreToJson()
                writeJsonToZip(zipOut, PREFS_FILE, prefsJson)

                // SharedPreferences
                writeJsonToZip(zipOut, SP_LEARNING, exportSharedPrefsToJson("learning_engine"))
                writeJsonToZip(zipOut, SP_SHIFT_PREFS, exportSharedPrefsToJson("shift_prefs"))
                writeJsonToZip(zipOut, SP_SHIFT_HISTORY, exportSharedPrefsToJson("shift_history"))

                // Room databases (cópia binária dos arquivos SQLite — apenas arquivo principal)
                copyDatabaseToZip(zipOut, "ngb_autoroad_db", DB_RIDES)
                copyDatabaseToZip(zipOut, "ngb_finance_db", DB_FINANCE)

                // DataStore binário (garante que as zonas do mapa e estruturas complexas sejam salvas corretamente)
                val dsFile = File(context.filesDir, "datastore/ngb_autoroad_prefs.preferences_pb")
                if (dsFile.exists()) {
                    zipOut.putNextEntry(ZipEntry(DS_BINARY))
                    FileInputStream(dsFile).use { fis -> fis.copyTo(zipOut) }
                    zipOut.closeEntry()
                    Log.d(TAG, "DataStore binário exportado: ${dsFile.length()} bytes")
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo de saída")

        Log.d(TAG, "Backup exportado com sucesso: ${metadata.ridesCount} corridas, ${metadata.financeRecords} registros financeiros")
        metadata
    }

    // =========================================================================
    // IMPORTAÇÃO
    // =========================================================================

    /**
     * Importa todos os dados de um arquivo ZIP no Uri fornecido.
     * @return Metadados do backup importado
     */
    suspend fun importBackup(inputUri: Uri): BackupMetadata = withContext(Dispatchers.IO) {
        Log.d(TAG, "Iniciando importação de backup...")

        // 1. Fechar databases antes de sobrescrever
        closeDatabases()

        var metadata = BackupMetadata()

        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        entryName == METADATA_FILE -> {
                            val content = readZipEntry(zipIn)
                            metadata = json.decodeFromString<BackupMetadata>(content)
                            Log.d(TAG, "Metadata: v${metadata.appVersion}, ${metadata.backupDate}")
                        }
                        entryName == PREFS_FILE -> {
                            val content = readZipEntry(zipIn)
                            importDataStoreFromJson(content)
                        }
                        entryName == SP_LEARNING -> {
                            val content = readZipEntry(zipIn)
                            importSharedPrefsFromJson("learning_engine", content)
                        }
                        entryName == SP_SHIFT_PREFS -> {
                            val content = readZipEntry(zipIn)
                            importSharedPrefsFromJson("shift_prefs", content)
                        }
                        entryName == SP_SHIFT_HISTORY -> {
                            val content = readZipEntry(zipIn)
                            importSharedPrefsFromJson("shift_history", content)
                        }
                        // Database principal
                        entryName == DB_RIDES -> {
                            restoreDatabaseFromZip(zipIn, "ngb_autoroad_db")
                        }
                        entryName == DB_FINANCE -> {
                            restoreDatabaseFromZip(zipIn, "ngb_finance_db")
                        }
                        // WAL files do backup (se existirem)
                        entryName == "$DB_RIDES-wal" -> {
                            restoreWalFromZip(zipIn, "ngb_autoroad_db")
                        }
                        entryName == "$DB_FINANCE-wal" -> {
                            restoreWalFromZip(zipIn, "ngb_finance_db")
                        }
                        // DataStore binário
                        entryName == DS_BINARY -> {
                            val dsFile = File(context.filesDir, "datastore/ngb_autoroad_prefs.preferences_pb")
                            dsFile.parentFile?.mkdirs()
                            FileOutputStream(dsFile).use { fos -> zipIn.copyTo(fos) }
                            Log.d(TAG, "DataStore binário restaurado: ${dsFile.length()} bytes")
                        }
                        // SHM files (ignorar — serão recriados pelo SQLite)
                        entryName.endsWith("-shm") -> {
                            // Skip — SHM é recriado automaticamente
                            Log.d(TAG, "Ignorando $entryName (recriado automaticamente)")
                        }
                        else -> {
                            Log.w(TAG, "Entrada desconhecida no backup: $entryName (ignorada)")
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } ?: throw IOException("Não foi possível abrir o arquivo de entrada")

        // Invalidar singletons para forçar recriação com novos dados
        invalidateDatabaseInstances()

        Log.d(TAG, "Backup importado com sucesso!")
        metadata
    }

    // =========================================================================
    // DATASTORE EXPORT/IMPORT
    // =========================================================================

    private suspend fun exportDataStoreToJson(): String {
        try {
            val prefsManager = com.ngbautoroad.data.prefs.PrefsManager(context)
            val prefs = prefsManager.getAllPreferencesAsMap()
            if (prefs.isEmpty()) {
                Log.w(TAG, "DataStore vazio — exportando mapa vazio")
                return "{}"
            }
            val result = json.encodeToString(prefs)
            Log.d(TAG, "DataStore exportado: ${prefs.size} preferências")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar DataStore: ${e.message}", e)
            return "{}"
        }
    }

    private suspend fun importDataStoreFromJson(jsonContent: String) {
        if (jsonContent.isBlank() || jsonContent == "{}") {
            Log.w(TAG, "Conteúdo de preferências vazio, pulando importação do DataStore")
            return
        }
        try {
            val prefsManager = com.ngbautoroad.data.prefs.PrefsManager(context)
            val rawMap: Map<String, String> = json.decodeFromString(jsonContent)
            val map = migrateLegacyKeys(rawMap)
            prefsManager.restoreAllPreferencesFromMap(map)
            Log.d(TAG, "DataStore restaurado: ${map.size} preferências")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar DataStore: ${e.message}", e)
        }
    }

    /**
     * Migra chaves legadas de backups antigos para o formato atual.
     * - saved_profiles_json → profiles_json (com correção de campos internos)
     */
    private fun migrateLegacyKeys(original: Map<String, String>): Map<String, String> {
        val result = original.toMutableMap()

        // Migrar saved_profiles_json → profiles_json
        if ("saved_profiles_json" in result && "profiles_json" !in result) {
            val legacyValue = result.remove("saved_profiles_json")!!
            // Corrigir nomes de campos antigos dentro do JSON de perfis
            val migrated = legacyValue
                .replace("\"stops\"", "\"intermediateStops\"")
                .replace("\"rating\"", "\"passengerRating\"")
                .replace("\"duration\"", "\"rideDuration\"")
                .replace("\"pickupDist\"", "\"pickupDistance\"")
                .replace("\"dropoffDist\"", "\"dropoffDistance\"")
                .replace("\"maxPickupDist\"", "\"maxPickupDistance\"")
                .replace("\"minRating\"", "\"minPassengerRating\"")
                .replace("\"minDropoffDist\"", "\"minDropoffDistance\"")
            result["profiles_json"] = migrated
            Log.d(TAG, "Migrada chave legada: saved_profiles_json → profiles_json")
        }

        return result
    }

    // =========================================================================
    // SHARED PREFERENCES EXPORT/IMPORT
    // =========================================================================

    private fun exportSharedPrefsToJson(name: String): String {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        if (allEntries.isEmpty()) return "{}"
        val map = mutableMapOf<String, String>()
        for ((key, value) in allEntries) {
            when (value) {
                is String -> map[key] = "S:$value"
                is Int -> map[key] = "I:$value"
                is Long -> map[key] = "L:$value"
                is Float -> map[key] = "F:$value"
                is Boolean -> map[key] = "B:$value"
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    map[key] = "SS:${(value as Set<String>).joinToString("|||")}"
                }
                else -> map[key] = "S:$value"
            }
        }
        return json.encodeToString(map)
    }

    private fun importSharedPrefsFromJson(name: String, jsonContent: String) {
        if (jsonContent.isBlank() || jsonContent == "{}") return
        try {
            val map: Map<String, String> = json.decodeFromString(jsonContent)
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            editor.clear()
            for ((key, typedValue) in map) {
                val colonIndex = typedValue.indexOf(':')
                if (colonIndex < 1) continue
                val type = typedValue.substring(0, colonIndex)
                val value = typedValue.substring(colonIndex + 1)
                when (type) {
                    "S" -> editor.putString(key, value)
                    "I" -> editor.putInt(key, value.toIntOrNull() ?: 0)
                    "L" -> editor.putLong(key, value.toLongOrNull() ?: 0L)
                    "F" -> editor.putFloat(key, value.toFloatOrNull() ?: 0f)
                    "B" -> editor.putBoolean(key, value.toBooleanStrictOrNull() ?: false)
                    "SS" -> editor.putStringSet(key, if (value.isBlank()) emptySet() else value.split("|||").toSet())
                }
            }
            editor.apply()
            Log.d(TAG, "SharedPreferences '$name' restaurado: ${map.size} entradas")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar SharedPreferences '$name': ${e.message}", e)
        }
    }

    // =========================================================================
    // ROOM DATABASE EXPORT/IMPORT (cópia binária)
    // =========================================================================

    private fun copyDatabaseToZip(zipOut: ZipOutputStream, dbName: String, entryName: String) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            Log.w(TAG, "Database $dbName não encontrado, pulando...")
            return
        }

        // Fazer checkpoint do WAL antes de copiar (garante dados no arquivo principal)
        try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            )
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.close()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível fazer checkpoint de $dbName: ${e.message}")
        }

        // Copiar apenas o arquivo principal (após checkpoint, WAL está vazio)
        zipOut.putNextEntry(ZipEntry(entryName))
        FileInputStream(dbFile).use { fis ->
            fis.copyTo(zipOut)
        }
        zipOut.closeEntry()
        Log.d(TAG, "Database $dbName exportado: ${dbFile.length()} bytes")
    }

    private fun restoreDatabaseFromZip(zipIn: ZipInputStream, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Deletar WAL e SHM antigos para evitar conflitos
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()

        FileOutputStream(dbFile).use { fos ->
            zipIn.copyTo(fos)
        }
        Log.d(TAG, "Database $dbName restaurado: ${dbFile.length()} bytes")
    }

    private fun restoreWalFromZip(zipIn: ZipInputStream, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        val walFile = File(dbFile.path + "-wal")
        FileOutputStream(walFile).use { fos ->
            zipIn.copyTo(fos)
        }
        Log.d(TAG, "WAL de $dbName restaurado: ${walFile.length()} bytes")
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Faz checkpoint WAL em todos os databases para garantir que dados estão
     * no arquivo principal antes da cópia.
     */
    private fun checkpointDatabases() {
        listOf("ngb_autoroad_db", "ngb_finance_db").forEach { dbName ->
            try {
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) {
                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    )
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    db.close()
                    Log.d(TAG, "Checkpoint WAL realizado: $dbName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Checkpoint falhou para $dbName: ${e.message}")
            }
        }
    }

    private fun closeDatabases() {
        try {
            com.ngbautoroad.data.db.AppDatabase.closeInstance()
            Log.d(TAG, "AppDatabase fechado")
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar AppDatabase: ${e.message}")
        }
        try {
            com.ngbautoroad.data.db.FinanceDatabase.closeInstance()
            Log.d(TAG, "FinanceDatabase fechado")
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao fechar FinanceDatabase: ${e.message}")
        }
    }

    /**
     * Invalida os singletons dos databases após importação para forçar
     * recriação com os novos arquivos na próxima vez que forem acessados.
     */
    private fun invalidateDatabaseInstances() {
        try {
            com.ngbautoroad.data.db.AppDatabase.closeInstance()
        } catch (_: Exception) {}
        try {
            com.ngbautoroad.data.db.FinanceDatabase.closeInstance()
        } catch (_: Exception) {}
        Log.d(TAG, "Singletons de database invalidados — serão recriados no próximo acesso")
    }

    private fun getRidesCount(): Int {
        return try {
            val dbFile = context.getDatabasePath("ngb_autoroad_db")
            if (dbFile.exists()) {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val cursor = db.rawQuery("SELECT COUNT(*) FROM ride_history", null)
                val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                cursor.close()
                db.close()
                count
            } else 0
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao contar corridas: ${e.message}")
            0
        }
    }

    private fun getFinanceCount(): Int {
        return try {
            val dbFile = context.getDatabasePath("ngb_finance_db")
            if (dbFile.exists()) {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                var count = 0
                // Contar cada tabela individualmente para evitar crash se alguma não existir
                listOf("earnings", "expenses", "financial_goals").forEach { table ->
                    try {
                        val cursor = db.rawQuery("SELECT COUNT(*) FROM $table", null)
                        if (cursor.moveToFirst()) count += cursor.getInt(0)
                        cursor.close()
                    } catch (_: Exception) {
                        // Tabela pode não existir em versões antigas — ignorar
                    }
                }
                db.close()
                count
            } else 0
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao contar registros financeiros: ${e.message}")
            0
        }
    }

    private fun getPreferencesCount(): Int {
        return try {
            val sp1 = context.getSharedPreferences("learning_engine", Context.MODE_PRIVATE).all.size
            val sp2 = context.getSharedPreferences("shift_prefs", Context.MODE_PRIVATE).all.size
            val sp3 = context.getSharedPreferences("shift_history", Context.MODE_PRIVATE).all.size
            sp1 + sp2 + sp3
        } catch (_: Exception) { 0 }
    }

    private fun writeJsonToZip(zipOut: ZipOutputStream, name: String, content: String) {
        zipOut.putNextEntry(ZipEntry(name))
        zipOut.write(content.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
    }

    private fun readZipEntry(zipIn: ZipInputStream): String {
        val baos = ByteArrayOutputStream()
        zipIn.copyTo(baos)
        return baos.toString(Charsets.UTF_8.name())
    }

    /**
     * Gera o nome sugerido para o arquivo de backup.
     */
    fun suggestedFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "NGBAutoRoad_Backup_$dateStr.zip"
    }
}
