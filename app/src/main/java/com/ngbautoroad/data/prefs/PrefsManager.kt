package com.ngbautoroad.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ngbautoroad.data.model.CardModel
import com.ngbautoroad.data.model.CriteriaWeights
import com.ngbautoroad.domain.ScoringThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ngb_autoroad_prefs")

class PrefsManager(private val context: Context) {

    // Criteria Weights Keys
    private val KEY_WEIGHT_VALUE_PER_KM = intPreferencesKey("weight_value_per_km")
    private val KEY_WEIGHT_VALUE_PER_HOUR = intPreferencesKey("weight_value_per_hour")
    private val KEY_WEIGHT_STOPS = intPreferencesKey("weight_intermediate_stops")
    private val KEY_WEIGHT_RATING = intPreferencesKey("weight_passenger_rating")
    private val KEY_WEIGHT_RIDE_VALUE = intPreferencesKey("weight_ride_value")
    private val KEY_WEIGHT_DURATION = intPreferencesKey("weight_duration")
    private val KEY_WEIGHT_PICKUP_DIST = intPreferencesKey("weight_pickup_distance")
    private val KEY_WEIGHT_DROPOFF_DIST = intPreferencesKey("weight_dropoff_distance")

    // Card Selection Keys
    private val KEY_ACTIVE_CARD_SLOT = intPreferencesKey("active_card_slot") // 1, 2, or 3
    private val KEY_CARD1_MODEL_ID = intPreferencesKey("card1_model_id")
    private val KEY_CARD2_MODEL_ID = intPreferencesKey("card2_model_id")

    // Card 3 Custom Keys
    private val KEY_CARD3_BG_COLOR = longPreferencesKey("card3_bg_color")
    private val KEY_CARD3_TEXT_COLOR = longPreferencesKey("card3_text_color")
    private val KEY_CARD3_ACCENT_COLOR = longPreferencesKey("card3_accent_color")
    private val KEY_CARD3_BORDER_COLOR = longPreferencesKey("card3_border_color")
    private val KEY_CARD3_BORDER_RADIUS = intPreferencesKey("card3_border_radius")
    private val KEY_CARD3_FONT_SIZE = intPreferencesKey("card3_font_size")

    // Service State
    private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    private val KEY_OCR_ENABLED = booleanPreferencesKey("ocr_enabled")

    // Blocked Neighborhoods (stored as comma-separated JSON)
    private val KEY_BLOCKED_PICKUP = stringPreferencesKey("blocked_pickup_neighborhoods")
    private val KEY_BLOCKED_DROPOFF = stringPreferencesKey("blocked_dropoff_neighborhoods")

    // --- Criteria Weights ---

    val criteriaWeightsFlow: Flow<CriteriaWeights> = context.dataStore.data.map { prefs ->
        CriteriaWeights(
            valuePerKm = prefs[KEY_WEIGHT_VALUE_PER_KM] ?: 30,
            valuePerHour = prefs[KEY_WEIGHT_VALUE_PER_HOUR] ?: 30,
            intermediateStops = prefs[KEY_WEIGHT_STOPS] ?: 25,
            passengerRating = prefs[KEY_WEIGHT_RATING] ?: 15,
            rideValue = prefs[KEY_WEIGHT_RIDE_VALUE] ?: 0,
            rideDuration = prefs[KEY_WEIGHT_DURATION] ?: 0,
            pickupDistance = prefs[KEY_WEIGHT_PICKUP_DIST] ?: 0,
            dropoffDistance = prefs[KEY_WEIGHT_DROPOFF_DIST] ?: 0
        )
    }

    suspend fun saveCriteriaWeights(weights: CriteriaWeights) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WEIGHT_VALUE_PER_KM] = weights.valuePerKm
            prefs[KEY_WEIGHT_VALUE_PER_HOUR] = weights.valuePerHour
            prefs[KEY_WEIGHT_STOPS] = weights.intermediateStops
            prefs[KEY_WEIGHT_RATING] = weights.passengerRating
            prefs[KEY_WEIGHT_RIDE_VALUE] = weights.rideValue
            prefs[KEY_WEIGHT_DURATION] = weights.rideDuration
            prefs[KEY_WEIGHT_PICKUP_DIST] = weights.pickupDistance
            prefs[KEY_WEIGHT_DROPOFF_DIST] = weights.dropoffDistance
        }
    }

    // --- Card Selection ---

    val activeCardSlotFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_CARD_SLOT] ?: 1
    }

    suspend fun setActiveCardSlot(slot: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_CARD_SLOT] = slot.coerceIn(1, 3)
        }
    }

    val card1ModelIdFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD1_MODEL_ID] ?: 1
    }

    val card2ModelIdFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD2_MODEL_ID] ?: 2
    }

    suspend fun setCardModelId(slot: Int, modelId: Int) {
        context.dataStore.edit { prefs ->
            when (slot) {
                1 -> prefs[KEY_CARD1_MODEL_ID] = modelId
                2 -> prefs[KEY_CARD2_MODEL_ID] = modelId
            }
        }
    }

    // --- Card 3 Custom ---

    val card3CustomFlow: Flow<CardModel> = context.dataStore.data.map { prefs ->
        CardModel(
            id = 0,
            name = "Custom",
            backgroundColor = prefs[KEY_CARD3_BG_COLOR] ?: 0xFF101830,
            textColor = prefs[KEY_CARD3_TEXT_COLOR] ?: 0xFFFFFFFF,
            accentColor = prefs[KEY_CARD3_ACCENT_COLOR] ?: 0xFF4F6BFF,
            borderColor = prefs[KEY_CARD3_BORDER_COLOR] ?: 0xFF4F6BFF,
            borderRadius = prefs[KEY_CARD3_BORDER_RADIUS] ?: 12,
            fontSize = prefs[KEY_CARD3_FONT_SIZE] ?: 14,
            isCustom = true
        )
    }

    suspend fun saveCard3Custom(card: CardModel) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CARD3_BG_COLOR] = card.backgroundColor
            prefs[KEY_CARD3_TEXT_COLOR] = card.textColor
            prefs[KEY_CARD3_ACCENT_COLOR] = card.accentColor
            prefs[KEY_CARD3_BORDER_COLOR] = card.borderColor
            prefs[KEY_CARD3_BORDER_RADIUS] = card.borderRadius
            prefs[KEY_CARD3_FONT_SIZE] = card.fontSize
        }
    }

    // --- Service State ---

    val serviceEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVICE_ENABLED] ?: false
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVICE_ENABLED] = enabled
        }
    }

    val ocrEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_OCR_ENABLED] ?: true
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OCR_ENABLED] = enabled
        }
    }

    // --- Blocked Neighborhoods ---

    val blockedPickupFlow: Flow<List<Pair<String, Int>>> = context.dataStore.data.map { prefs ->
        parseNeighborhoods(prefs[KEY_BLOCKED_PICKUP] ?: "")
    }

    val blockedDropoffFlow: Flow<List<Pair<String, Int>>> = context.dataStore.data.map { prefs ->
        parseNeighborhoods(prefs[KEY_BLOCKED_DROPOFF] ?: "")
    }

    suspend fun saveBlockedPickup(neighborhoods: List<Pair<String, Int>>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_PICKUP] = serializeNeighborhoods(neighborhoods)
        }
    }

    suspend fun saveBlockedDropoff(neighborhoods: List<Pair<String, Int>>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_DROPOFF] = serializeNeighborhoods(neighborhoods)
        }
    }

    private fun parseNeighborhoods(raw: String): List<Pair<String, Int>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                parts[0] to (parts[1].toIntOrNull() ?: 20)
            } else null
        }
    }

    private fun serializeNeighborhoods(list: List<Pair<String, Int>>): String {
        return list.joinToString(";") { "${it.first}:${it.second}" }
    }
}
