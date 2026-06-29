package com.ngbautoroad.domain

// ============================================================================
// ARQUIVO: TelemetryLogger.kt
// VERSÃO: v6.9.8
// RESPONSABILIDADE: Sistema de telemetria e coleta de logs persistentes
//   - Grava todos os eventos do sistema em arquivo de texto
//   - Categorias: PARSER, LIFECYCLE, OVERLAY, SHIFT, FINANCE, AUTOPILOT, ERROR, SYSTEM
//   - Rotação automática: max 5MB por arquivo, mantém últimos 3 arquivos
//   - Exportação: gera ZIP com todos os logs para compartilhar
// ACESSO: Singleton via TelemetryLogger.getInstance(context)
// TAGS DE DEBUG: NGB_TELEMETRY
// ============================================================================

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TelemetryLogger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NGB_TELEMETRY"
        private const val LOG_DIR = "telemetry_logs"
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_FILES = 3
        private const val LOG_FILE_PREFIX = "ngb_telemetry_"

        @Volatile
        private var instance: TelemetryLogger? = null

        fun getInstance(context: Context): TelemetryLogger {
            return instance ?: synchronized(this) {
                instance ?: TelemetryLogger(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Categorias de eventos para filtragem
     */
    enum class Category {
        PARSER,     // Detecção e parsing de corridas
        LIFECYCLE,  // Ciclo de vida de corridas (PENDING→ACCEPTED→COMPLETED)
        OVERLAY,    // Exibição/fechamento do overlay
        SHIFT,      // Início/fim de turnos, ganhos
        FINANCE,    // Registros financeiros, auto-import
        AUTOPILOT,  // Decisões do AutoPilot
        ERROR,      // Erros e exceções
        SYSTEM,     // Eventos do sistema (service start/stop, ghost mode, etc.)
        BACKUP,     // Exportação/importação de backup
        USER_ACTION // Ações manuais do usuário (confirmar/deletar corrida)
    }

    /**
     * Níveis de severidade
     */
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private val logDir: File = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private var currentWriter: PrintWriter? = null
    private var currentFile: File? = null
    private val lock = Any()

    // =========================================================================
    // BLOCO: Métodos de log públicos
    // =========================================================================

    /**
     * Log genérico com categoria, nível e mensagem
     */
    fun log(category: Category, level: Level, message: String, extras: Map<String, Any>? = null) {
        val timestamp = dateFormat.format(Date())
        val extrasStr = if (extras != null) " | ${extras.entries.joinToString(", ") { "${it.key}=${it.value}" }}" else ""
        val line = "[$timestamp] [${level.name}] [${category.name}] $message$extrasStr"

        synchronized(lock) {
            try {
                ensureWriter()
                currentWriter?.println(line)
                currentWriter?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao gravar log: ${e.message}")
            }
        }

        // Também logar no Logcat para debug em tempo real
        when (level) {
            Level.DEBUG -> Log.d(TAG, "[$category] $message")
            Level.INFO -> Log.i(TAG, "[$category] $message")
            Level.WARN -> Log.w(TAG, "[$category] $message")
            Level.ERROR -> Log.e(TAG, "[$category] $message")
        }
    }

    // ── Atalhos por categoria ──

    fun parser(message: String, extras: Map<String, Any>? = null) =
        log(Category.PARSER, Level.INFO, message, extras)

    fun parserDebug(message: String, extras: Map<String, Any>? = null) =
        log(Category.PARSER, Level.DEBUG, message, extras)

    fun lifecycle(message: String, extras: Map<String, Any>? = null) =
        log(Category.LIFECYCLE, Level.INFO, message, extras)

    fun overlay(message: String, extras: Map<String, Any>? = null) =
        log(Category.OVERLAY, Level.INFO, message, extras)

    fun shift(message: String, extras: Map<String, Any>? = null) =
        log(Category.SHIFT, Level.INFO, message, extras)

    fun finance(message: String, extras: Map<String, Any>? = null) =
        log(Category.FINANCE, Level.INFO, message, extras)

    fun autopilot(message: String, extras: Map<String, Any>? = null) =
        log(Category.AUTOPILOT, Level.INFO, message, extras)

    fun error(message: String, throwable: Throwable? = null, extras: Map<String, Any>? = null) {
        val fullMessage = if (throwable != null) "$message | Exception: ${throwable.message}" else message
        log(Category.ERROR, Level.ERROR, fullMessage, extras)
    }

    fun system(message: String, extras: Map<String, Any>? = null) =
        log(Category.SYSTEM, Level.INFO, message, extras)

    fun backup(message: String, extras: Map<String, Any>? = null) =
        log(Category.BACKUP, Level.INFO, message, extras)

    fun userAction(message: String, extras: Map<String, Any>? = null) =
        log(Category.USER_ACTION, Level.INFO, message, extras)

    // =========================================================================
    // BLOCO: Log de eventos específicos do parser (dados estruturados)
    // =========================================================================

    /**
     * Registra uma detecção de corrida com todos os dados parseados
     */
    fun logRideDetected(
        platform: String,
        value: Double,
        pickupKm: Double,
        dropoffKm: Double,
        duration: Double,
        rating: Double,
        stops: Int,
        pickupNeighborhood: String,
        dropoffNeighborhood: String,
        valuePerKm: Double,
        valuePerHour: Double,
        hasAcceptButton: Boolean,
        accepted: Boolean,
        hash: Int
    ) {
        parser("CORRIDA DETECTADA", mapOf(
            "platform" to platform,
            "value" to String.format("%.2f", value),
            "pickupKm" to String.format("%.1f", pickupKm),
            "dropoffKm" to String.format("%.1f", dropoffKm),
            "duration" to String.format("%.0f", duration),
            "rating" to String.format("%.2f", rating),
            "stops" to stops,
            "pickupNeighborhood" to pickupNeighborhood,
            "dropoffNeighborhood" to dropoffNeighborhood,
            "valuePerKm" to String.format("%.2f", valuePerKm),
            "valuePerHour" to String.format("%.2f", valuePerHour),
            "hasAcceptButton" to hasAcceptButton,
            "accepted" to accepted,
            "hash" to hash
        ))
    }

    /**
     * Registra uma rejeição pelo parser (com motivo)
     */
    fun logRideRejected(platform: String, reason: String, value: Double = 0.0) {
        parserDebug("CORRIDA REJEITADA: $reason", mapOf(
            "platform" to platform,
            "value" to String.format("%.2f", value),
            "reason" to reason
        ))
    }

    /**
     * Registra duplicata ignorada
     */
    fun logDuplicate(platform: String, value: Double, reason: String) {
        parserDebug("DUPLICATA: $reason", mapOf(
            "platform" to platform,
            "value" to String.format("%.2f", value)
        ))
    }

    /**
     * Registra transição de lifecycle
     */
    fun logLifecycleTransition(rideId: Long, fromPhase: String, toPhase: String, value: Double) {
        lifecycle("TRANSIÇÃO: $fromPhase → $toPhase", mapOf(
            "rideId" to rideId,
            "value" to String.format("%.2f", value),
            "from" to fromPhase,
            "to" to toPhase
        ))
    }

    /**
     * Registra ação do AutoPilot
     */
    fun logAutoPilotDecision(action: String, score: Double, threshold: Double, value: Double) {
        autopilot("DECISÃO: $action", mapOf(
            "score" to String.format("%.1f", score),
            "threshold" to String.format("%.1f", threshold),
            "value" to String.format("%.2f", value),
            "action" to action
        ))
    }

    // =========================================================================
    // BLOCO: Exportação de logs
    // =========================================================================

    /**
     * Exporta todos os logs como ZIP para compartilhamento.
     * @return File do ZIP gerado, ou null se falhar
     */
    fun exportLogsAsZip(): File? {
        return try {
            synchronized(lock) {
                // Flush e fechar writer atual
                currentWriter?.flush()
            }

            val zipFile = File(context.getExternalFilesDir(null), "ngb_telemetry_export_${fileDateFormat.format(Date())}.zip")
            val logFiles = logDir.listFiles()?.filter { it.name.startsWith(LOG_FILE_PREFIX) }?.sortedByDescending { it.lastModified() }

            if (logFiles.isNullOrEmpty()) {
                Log.w(TAG, "Nenhum arquivo de log para exportar")
                return null
            }

            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                for (file in logFiles) {
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }

                // Adicionar metadata
                zip.putNextEntry(ZipEntry("metadata.txt"))
                val metadata = buildString {
                    appendLine("NGB AutoRoad Telemetry Export")
                    appendLine("Data: ${dateFormat.format(Date())}")
                    appendLine("Arquivos: ${logFiles.size}")
                    appendLine("Tamanho total: ${logFiles.sumOf { it.length() } / 1024} KB")
                    appendLine("Versão app: 6.9.8")
                    appendLine("Android: ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                }
                zip.write(metadata.toByteArray())
                zip.closeEntry()
            }

            Log.i(TAG, "Logs exportados: ${zipFile.absolutePath} (${zipFile.length() / 1024} KB)")
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar logs: ${e.message}", e)
            null
        }
    }

    /**
     * Retorna o conteúdo do log atual como String (para preview na UI)
     * @param maxLines Máximo de linhas a retornar (do final do arquivo)
     */
    fun getRecentLogs(maxLines: Int = 100): String {
        return try {
            synchronized(lock) {
                currentWriter?.flush()
            }
            val file = currentFile ?: return "Nenhum log disponível"
            val lines = file.readLines()
            val start = maxOf(0, lines.size - maxLines)
            lines.subList(start, lines.size).joinToString("\n")
        } catch (e: Exception) {
            "Erro ao ler logs: ${e.message}"
        }
    }

    /**
     * Retorna estatísticas dos logs
     */
    fun getStats(): Map<String, Any> {
        val files = logDir.listFiles()?.filter { it.name.startsWith(LOG_FILE_PREFIX) } ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        val lineCount = try {
            currentFile?.readLines()?.size ?: 0
        } catch (_: Exception) { 0 }

        return mapOf(
            "arquivos" to files.size,
            "tamanhoTotal" to "${totalSize / 1024} KB",
            "linhasAtual" to lineCount,
            "diretorio" to logDir.absolutePath
        )
    }

    /**
     * Limpa todos os logs antigos (mantém apenas o arquivo atual)
     */
    fun clearOldLogs() {
        synchronized(lock) {
            val files = logDir.listFiles()?.filter { it.name.startsWith(LOG_FILE_PREFIX) }?.sortedByDescending { it.lastModified() }
            files?.drop(1)?.forEach { it.delete() }
            Log.i(TAG, "Logs antigos limpos. Mantido: ${files?.firstOrNull()?.name}")
        }
    }

    /**
     * Limpa logs mais antigos que N dias
     */
    fun clearOldLogs(daysToKeep: Int) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val files = logDir.listFiles()?.filter { it.name.startsWith(LOG_FILE_PREFIX) } ?: return
            var deleted = 0
            for (file in files) {
                if (file.lastModified() < cutoff && file != currentFile) {
                    file.delete()
                    deleted++
                }
            }
            Log.i(TAG, "Limpos $deleted logs com mais de $daysToKeep dias")
        }
    }

    // =========================================================================
    // BLOCO: Gerenciamento interno de arquivos
    // =========================================================================

    private fun ensureWriter() {
        // Verificar se precisa rotacionar
        val file = currentFile
        if (file != null && file.length() > MAX_FILE_SIZE) {
            rotateFiles()
        }

        if (currentWriter == null || currentFile == null || currentFile?.exists() != true) {
            val newFile = File(logDir, "${LOG_FILE_PREFIX}${fileDateFormat.format(Date())}.log")
            currentFile = newFile
            currentWriter = PrintWriter(FileOutputStream(newFile, true), true)
            Log.d(TAG, "Novo arquivo de log: ${newFile.name}")

            // Gravar header
            currentWriter?.println("═══════════════════════════════════════════════════════════")
            currentWriter?.println("  NGB AutoRoad Telemetry Log")
            currentWriter?.println("  Início: ${dateFormat.format(Date())}")
            currentWriter?.println("  Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            currentWriter?.println("  Android: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
            currentWriter?.println("═══════════════════════════════════════════════════════════")
            currentWriter?.println()
        }
    }

    private fun rotateFiles() {
        try {
            currentWriter?.close()
            currentWriter = null

            // Remover arquivos excedentes
            val files = logDir.listFiles()?.filter { it.name.startsWith(LOG_FILE_PREFIX) }?.sortedByDescending { it.lastModified() }
            files?.drop(MAX_FILES - 1)?.forEach { it.delete() }

            // Próximo ensureWriter() criará novo arquivo
            currentFile = null
            Log.d(TAG, "Rotação de logs executada. Arquivos mantidos: ${MAX_FILES - 1}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro na rotação de logs: ${e.message}")
        }
    }

    /**
     * Libera recursos (chamado no onDestroy do app)
     */
    fun close() {
        synchronized(lock) {
            try {
                currentWriter?.flush()
                currentWriter?.close()
                currentWriter = null
            } catch (_: Exception) {}
        }
    }
}
