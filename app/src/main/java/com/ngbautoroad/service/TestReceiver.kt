package com.ngbautoroad.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.model.RideType
import com.ngbautoroad.data.db.*
import com.ngbautoroad.domain.*
import kotlinx.coroutines.*
import java.util.Calendar

class TestReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NGB_TEST_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command")
        Log.i(TAG, "TestReceiver: broadcast recebido! Comando: $command")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            try {
                when (command) {
                    "start_shift" -> {
                        val goal = intent.getDoubleExtra("goal", 200.0)
                        val vehicleId = intent.getIntExtra("vehicleId", 1)
                        val shiftManager = ShiftManager(context.applicationContext)
                        val state = shiftManager.startShift(goal, vehicleId)
                        Log.i(TAG, "Turno iniciado com sucesso: $state")
                    }
                    "end_shift" -> {
                        val shiftManager = ShiftManager(context.applicationContext)
                        val state = shiftManager.endShift()
                        Log.i(TAG, "Turno encerrado com sucesso: $state")
                    }
                    "accept_ride" -> {
                        withContext(Dispatchers.Main) {
                            val service = RideAccessibilityService.instance
                            val lifecycleManager = service?.lifecycleManager
                            if (lifecycleManager != null) {
                                lifecycleManager.onRideAccepted()
                                Log.i(TAG, "Corrida aceita via comando")
                            } else {
                                Log.e(TAG, "RideLifecycleManager não disponível")
                            }
                        }
                    }
                    "complete_ride" -> {
                        val hasValue = intent.hasExtra("finalValue")
                        val finalValue = if (hasValue) intent.getDoubleExtra("finalValue", 0.0) else null
                        withContext(Dispatchers.Main) {
                            val service = RideAccessibilityService.instance
                            val lifecycleManager = service?.lifecycleManager
                            if (lifecycleManager != null) {
                                lifecycleManager.onRideCompleted(finalValue)
                                Log.i(TAG, "Corrida concluída via comando. Valor real: $finalValue")
                            } else {
                                Log.e(TAG, "RideLifecycleManager não disponível")
                            }
                        }
                    }
                    "cancel_ride" -> {
                        withContext(Dispatchers.Main) {
                            val service = RideAccessibilityService.instance
                            val lifecycleManager = service?.lifecycleManager
                            if (lifecycleManager != null) {
                                lifecycleManager.onRideCancelled()
                                Log.i(TAG, "Corrida cancelada via comando")
                            } else {
                                Log.e(TAG, "RideLifecycleManager não disponível")
                            }
                        }
                    }
                    "setup_vehicle" -> {
                        val brand = intent.getStringExtra("brand") ?: "Fiat"
                        val model = intent.getStringExtra("model") ?: "Argo"
                        val odometer = intent.getIntExtra("odometer", 50000)
                        val costPerKm = intent.getDoubleExtra("costPerKm", 0.35)
                        val fuelPrice = intent.getDoubleExtra("fuelPrice", 5.0)
                        val consumption = intent.getDoubleExtra("consumption", 14.0)

                        val db = FinanceDatabase.getInstance(context.applicationContext)
                        val dao = db.vehicleProfileDao()

                        // Deactivate any existing active vehicle profile
                        dao.deactivateAll()

                        val newVehicle = VehicleProfileEntity(
                            id = 1L,
                            isActive = true,
                            brand = brand,
                            model = model,
                            year = 2024,
                            plate = "ABC-1234",
                            vehicleType = "COMBUSTION",
                            fuelType = "FLEX",
                            averageConsumption = consumption,
                            fuelPrice = fuelPrice,
                            costPerKm = costPerKm,
                            purchaseValue = 70000.0,
                            currentOdometer = odometer,
                            lastOdometerUpdate = System.currentTimeMillis(),
                            odometerCorrectionFactor = 1.0,
                            oilChangeKm = 10000,
                            oilChangeCost = 350.0,
                            tireLifeKm = 40000,
                            tireCost = 1800.0,
                            brakepadLifeKm = 30000,
                            brakepadCost = 500.0,
                            maintenanceIntervalKm = 20000,
                            maintenanceCost = 800.0
                        )
                        try {
                            dao.insert(newVehicle)
                        } catch (e: Exception) {
                            dao.update(newVehicle)
                        }
                        
                        // Force enable auto-import earnings for the simulation
                        val prefsManager = com.ngbautoroad.data.prefs.PrefsManager(context.applicationContext)
                        prefsManager.setAutoImportEarnings(true)

                        Log.i(TAG, "Veículo configurado e ativo: $model com odômetro $odometer, auto-import habilitado")
                    }
                    "update_odometer" -> {
                        val odometer = intent.getIntExtra("odometer", 50500)
                        val db = FinanceDatabase.getInstance(context.applicationContext)
                        val vehicle = db.vehicleProfileDao().getActiveVehicleSync()
                        if (vehicle != null) {
                            val odometerEngine = OdometerEngine(
                                vehicleProfileDao = db.vehicleProfileDao(),
                                odometerHistoryDao = db.odometerHistoryDao(),
                                earningDao = db.earningDao()
                            )
                            odometerEngine.processOdometerUpdate(vehicle, odometer)
                            Log.i(TAG, "Odômetro atualizado para: $odometer")
                        } else {
                            Log.e(TAG, "Nenhum veículo ativo encontrado para atualizar odômetro")
                        }
                    }
                    "register_maintenance" -> {
                        val odometer = intent.getIntExtra("odometer", 51000)
                        val cost = intent.getDoubleExtra("cost", 300.0)
                        val parts = intent.getStringExtra("parts") ?: "Pastilhas de freio"
                        val notes = intent.getStringExtra("notes") ?: "Troca preventiva"

                        val db = FinanceDatabase.getInstance(context.applicationContext)
                        val record = MaintenanceRecordEntity(
                            vehicleId = 1,
                            date = System.currentTimeMillis(),
                            odometer = odometer,
                            totalCost = cost,
                            maintenanceType = "PREVENTIVA",
                            replacedParts = parts,
                            notes = notes
                        )
                        db.maintenanceRecordDao().insert(record)
                        Log.i(TAG, "Registro de manutenção inserido: $parts, custo R$$cost")
                    }
                    "seed_history" -> {
                        // Inserir 32 corridas no histórico para calibração da IA
                        val appDb = AppDatabase.getInstance(context.applicationContext)
                        val financeDb = FinanceDatabase.getInstance(context.applicationContext)
                        appDb.rideHistoryDao().deleteAll()

                        val now = System.currentTimeMillis()
                        val random = java.util.Random()
                        
                        Log.i(TAG, "Semeando 32 corridas no histórico...")
                        for (i in 1..32) {
                            val value = 10.0 + random.nextDouble() * 40.0 // R$ 10 a 50
                            val dropoff = 2.0 + random.nextDouble() * 15.0 // 2 a 17 km
                            val duration = 5.0 + dropoff * 2.0
                            val pickup = 0.5 + random.nextDouble() * 3.0
                            val timestamp = now - (35 - i) * 24 * 60 * 60 * 1000L // corridas distribuídas nos últimos 35 dias

                            val entity = RideHistoryEntity(
                                platform = if (i % 3 == 0) "99" else "Uber",
                                rideValue = value,
                                rideDuration = duration,
                                pickupDistance = pickup,
                                dropoffDistance = dropoff,
                                passengerRating = 4.7 + random.nextDouble() * 0.3,
                                intermediateStops = 0,
                                pickupNeighborhood = "Bairro A",
                                dropoffNeighborhood = "Bairro B",
                                score = 70.0 + random.nextDouble() * 30.0,
                                status = "COMPLETED",
                                timestamp = timestamp
                            )
                            val rideId = appDb.rideHistoryDao().insert(entity)

                            // Também inserir como ganho no FinanceDatabase
                            val earning = EarningEntity(
                                platform = entity.platform,
                                amount = entity.rideValue,
                                distance = entity.dropoffDistance,
                                duration = entity.rideDuration.toInt(),
                                ridesCount = 1,
                                description = "Seed history",
                                period = "DIA",
                                isAutoImported = true,
                                rideHistoryId = rideId,
                                score = entity.score,
                                pickupDistance = entity.pickupDistance,
                                date = timestamp
                            )
                            financeDb.earningDao().insert(earning)
                        }
                        Log.i(TAG, "Semeado com sucesso!")
                    }
                    "trigger_ai" -> {
                        // Chamar LocalLearningEngine.seedFromDatabase
                        val learningEngine = LocalLearningEngine(context.applicationContext)
                        learningEngine.seedFromDatabase(context.applicationContext)
                        Log.i(TAG, "IA calibrada com sucesso!")
                    }
                    "add_expense" -> {
                        val amount = intent.getDoubleExtra("amount", 50.0)
                        val category = intent.getStringExtra("category") ?: "Outros"
                        val isRecurring = intent.getBooleanExtra("isRecurring", false)
                        val db = FinanceDatabase.getInstance(context.applicationContext)
                        val expense = ExpenseEntity(
                            category = category,
                            amount = amount,
                            description = "Despesa simulada",
                            date = System.currentTimeMillis(),
                            isRecurring = isRecurring
                        )
                        db.expenseDao().insert(expense)
                        Log.i(TAG, "Despesa de R$$amount inserida na categoria $category")
                    }
                    else -> {
                        simulateRideOffer(context, intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no processamento do TestReceiver: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun simulateRideOffer(context: Context, intent: Intent) {
        val platformStr = intent.getStringExtra("platform") ?: "Uber"
        val value = intent.getDoubleExtra("value", 15.0)
        val pickup = intent.getDoubleExtra("pickup", 1.5)
        val dropoff = intent.getDoubleExtra("dropoff", 4.5)
        val duration = intent.getDoubleExtra("duration", 12.0)
        val rating = intent.getDoubleExtra("rating", 4.9)
        val stops = intent.getIntExtra("stops", 0)
        val pickupNeigh = intent.getStringExtra("pickupNeighborhood") ?: "Centro"
        val dropoffNeigh = intent.getStringExtra("dropoffNeighborhood") ?: "Jardins"
        val isSimulation = intent.getBooleanExtra("isSimulation", false)

        val platform = when (platformStr.lowercase()) {
            "uber" -> Platform.UBER
            "99" -> Platform.NINETY_NINE
            "indrive" -> Platform.INDRIVE
            else -> Platform.UBER
        }

        val rideType = when (platform) {
            Platform.UBER -> RideType.UBER_X
            Platform.NINETY_NINE -> RideType.NINETY_NINE_POP
            Platform.INDRIVE -> RideType.INDRIVE_STANDARD
            else -> RideType.UNKNOWN
        }

        val rideData = RideData(
            platform = platform,
            rideType = rideType,
            rideValue = value,
            rideDuration = duration,
            pickupDistance = pickup,
            dropoffDistance = dropoff,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeigh,
            dropoffNeighborhood = dropoffNeigh,
            isSimulation = isSimulation
        )

        Log.i(TAG, "Disparando onRideDetected para: $rideData")

        try {
            val serviceIntent = Intent(context, OverlayService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar OverlayService: ${e.message}")
        }

        try {
            val prefsManager = com.ngbautoroad.data.prefs.PrefsManager(context.applicationContext)
            prefsManager.saveAutoPilotMode("BOTH")
            prefsManager.saveAutoPilotMinScore(80)
            prefsManager.saveAutoPilotMaxRefuseScore(60)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar preferências: ${e.message}")
        }
        delay(150)

        var attempts = 0
        while (OverlayService.onRideDetected == null && attempts < 10) {
            delay(200)
            attempts++
        }
        if (OverlayService.onRideDetected != null) {
            Log.i(TAG, "Callback onRideDetected pronto! Invocando...")
            OverlayService.onRideDetected?.invoke(rideData)
        } else {
            Log.e(TAG, "OverlayService não inicializou a tempo.")
        }
    }
}
