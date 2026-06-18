package com.ngbautoroad.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ReportData(
    val period: String, val totalEarnings: Double, val totalExpenses: Double,
    val totalRides: Int, val totalKm: Double, val totalHours: Double,
    val platformBreakdown: Map<String, Double>, val topNeighborhoods: List<Pair<String, Double>>
)

class ReportGenerator(private val context: Context) {
    fun generatePdf(data: ReportData): File? {
        return try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint().apply { textSize = 14f; isAntiAlias = true }
            val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; isAntiAlias = true }

            var y = 50f
            canvas.drawText("NGBAutoRoad - Relatório Financeiro", 40f, y, titlePaint); y += 30f
            canvas.drawText("Período: ${data.period}", 40f, y, paint); y += 20f
            canvas.drawText("Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())}", 40f, y, paint); y += 40f

            paint.isFakeBoldText = true
            canvas.drawText("RESUMO", 40f, y, paint); y += 25f
            paint.isFakeBoldText = false
            canvas.drawText("Ganhos: R$ ${"%.2f".format(data.totalEarnings)}", 40f, y, paint); y += 20f
            canvas.drawText("Despesas: R$ ${"%.2f".format(data.totalExpenses)}", 40f, y, paint); y += 20f
            canvas.drawText("Lucro: R$ ${"%.2f".format(data.totalEarnings - data.totalExpenses)}", 40f, y, paint); y += 20f
            canvas.drawText("Corridas: ${data.totalRides}", 40f, y, paint); y += 20f
            canvas.drawText("Km rodados: ${"%.1f".format(data.totalKm)}", 40f, y, paint); y += 20f
            canvas.drawText("Horas: ${"%.1f".format(data.totalHours)}", 40f, y, paint); y += 20f
            if (data.totalHours > 0) canvas.drawText("R$/hora: ${"%.2f".format(data.totalEarnings / data.totalHours)}", 40f, y, paint)

            doc.finishPage(page)
            val file = File(context.getExternalFilesDir(null), "relatorio_${System.currentTimeMillis()}.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()
            file
        } catch (_: Exception) { null }
    }
}
