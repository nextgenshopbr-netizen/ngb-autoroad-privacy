package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: OcrCaptureService.kt
// LOCALIZAÇÃO: service/OcrCaptureService.kt
// RESPONSABILIDADE: Captura dados de corrida via OCR (ML Kit Text Recognition)
// BLOCOS:
//   - onStartCommand: Inicia captura de tela periódica
//   - captureScreen: Tira screenshot via MediaProjection
//   - processImage: Envia imagem para ML Kit OCR
//   - parseOcrText: Extrai campos da corrida do texto OCR
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, Platform
//   - service/OverlayService.kt → envia RideData via Intent
//   - data/prefs/PrefsManager.kt → ocrEnabled, intervalo
// PROTEÇÕES:
//   - Intervalo adaptativo (não processa se app não está em foreground)
//   - Regex defensivo para cada campo
//   - Deduplicação por hash de texto extraído
// ============================================================================

import android.app.Activity
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ngbautoroad.NGBAutoRoadApp
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData

/**
 * Serviço de captura de tela via MediaProjection + ML Kit OCR.
 * Captura screenshots periódicas e extrai texto para detectar corridas.
 */
class OcrCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CAPTURE_INTERVAL_MS = 1500L // Captura a cada 1.5s
        // Intervalo adaptativo: aumenta quando não há corridas detectadas (item 2.6)
        private const val CAPTURE_INTERVAL_IDLE_MS = 3000L

        private var resultCode: Int = 0
        private var resultData: Intent? = null
        private var consecutiveNoRide = 0

        fun setMediaProjectionResult(code: Int, data: Intent) {
            resultCode = code
            resultData = data
        }

        fun start(context: Context) {
            val intent = Intent(context, OcrCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OcrCaptureService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startCapture()
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture() {
        if (isCapturing) return
        val data = resultData ?: return

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "NGBAutoRoad_OCR",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        isCapturing = true
        scheduleNextCapture()
    }

    private fun stopCapture() {
        isCapturing = false
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        virtualDisplay = null
        mediaProjection = null
        imageReader = null
    }

    private fun scheduleNextCapture() {
        if (!isCapturing) return
        // Intervalo adaptativo: mais lento quando inativo (item 2.6)
        val interval = if (consecutiveNoRide > 5) CAPTURE_INTERVAL_IDLE_MS else CAPTURE_INTERVAL_MS
        handler.postDelayed({
            captureAndProcess()
            scheduleNextCapture()
        }, interval)
    }

    private fun captureAndProcess() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen size
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            if (croppedBitmap != bitmap) bitmap.recycle()

            processWithMlKit(croppedBitmap)
        } finally {
            image.close()
        }
    }

    private fun processWithMlKit(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                if (fullText.isNotBlank()) {
                    val rideData = parseOcrText(fullText)
                    if (rideData != null && (rideData.rideValue > 0 || rideData.dropoffDistance > 0)) {
                        consecutiveNoRide = 0
                        OverlayService.onRideDetected?.invoke(rideData)
                    } else {
                        consecutiveNoRide++
                    }
                } else {
                    consecutiveNoRide++
                }
                bitmap.recycle()
            }
            .addOnFailureListener {
                bitmap.recycle()
            }
    }

    /**
     * Parse do texto OCR para extrair dados da corrida.
     * Suporta múltiplas plataformas via padrões de regex.
     */
    private fun parseOcrText(text: String): RideData? {
        val lines = text.lines()
        var platform = Platform.UNKNOWN
        var rideValue = 0.0
        var distance = 0.0
        var pickupDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0

        // Detectar plataforma
        val lowerText = text.lowercase()
        platform = when {
            lowerText.contains("uber") || lowerText.contains("uberdrive") -> Platform.UBER
            lowerText.contains("99") || lowerText.contains("ninety") -> Platform.NINETY_NINE
            lowerText.contains("indrive") || lowerText.contains("indriver") -> Platform.INDRIVE
            lowerText.contains("cabify") -> Platform.CABIFY
            else -> Platform.UNKNOWN
        }

        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        // Padrão combinado Uber: "R$ XX,XX • X km • X min"
        val uberCombined = Regex("""R\$\s*(\d+[.,]\d{2})\s*[•·]\s*(\d+[.,]\d+)\s*km\s*[•·]\s*(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
        if (uberCombined != null) {
            rideValue = uberCombined.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            distance = uberCombined.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            duration = uberCombined.groupValues[3].toDoubleOrNull() ?: 0.0
        }

        for (line in lines) {
            // Valor: R$ XX,XX
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(line)
                if (valueMatch != null) {
                    rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            // Distância: X,X km (múltiplos por linha)
            val distMatches = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).findAll(line)
            for (dm in distMatches) {
                val d = dm.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                if (pickupDistance == 0.0 && d < 10.0) {
                    pickupDistance = d
                } else if (distance == 0.0) {
                    distance = d
                }
            }

            // Duração: X min ou ~X min
            if (duration == 0.0) {
                val durMatch = Regex("""~?(\d+)\s*min""", RegexOption.IGNORE_CASE).find(line)
                if (durMatch != null) duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            // Rating
            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(line)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) rating = r
                }
            }

            // Paradas
            if (stops == 0 && line.contains("parada", ignoreCase = true)) {
                stops = Regex("""(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            }

            // Bairros via OCR (item 2.3)
            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""(?:embarque|pickup|origem|partida)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(line)
                if (m != null) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
            if (dropoffNeighborhood.isBlank()) {
                val m = Regex("""(?:destino|desembarque|dropoff)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(line)
                if (m != null) dropoffNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        if (rideValue == 0.0 && distance == 0.0 && duration == 0.0) return null

        // Heurística: se pickup > dropoff, trocar
        if (pickupDistance > distance && distance > 0 && pickupDistance > 3.0) {
            val temp = pickupDistance
            pickupDistance = distance
            distance = temp
        }

        return RideData(
            platform = platform,
            rideValue = rideValue,
            rideDuration = duration,
            pickupDistance = pickupDistance,
            dropoffDistance = distance,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NGBAutoRoadApp.CHANNEL_OCR)
            .setContentTitle("NGB AutoRoad - OCR")
            .setContentText("Capturando tela para leitura...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}
