package com.ngbautoroad.data.prefs

// ============================================================================
// ARQUIVO: PrefsManager.kt
// LOCALIZAÇÃO: data/prefs/PrefsManager.kt
// RESPONSABILIDADE: Persistência de configurações via DataStore Preferences
// DADOS PERSISTIDOS:
//   - CriteriaWeights (8 pesos de critérios)
//   - DriverThresholds (8 valores mínimos/máximos)
//   - Card config (card1ModelId, card2ModelId, activeSlot)
//   - Overlay config (positionX, positionY, size)
//   - Service toggles (serviceActive, ocrEnabled, autoImportEarnings)
//   - Blocked neighborhoods (JSON serializado)
// DEPENDENTES:
//   - Praticamente todos os arquivos do app leem configurações daqui
// PROTEÇÕES:
//   - Todos os flows emitem valor default se chave não existe
//   - Operações de escrita são suspend (não bloqueiam main thread)
// ============================================================================

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ngbautoroad.data.model.CardModel
import com.ngbautoroad.data.model.CriteriaWeights
import com.ngbautoroad.data.model.DriverThresholds
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

    // Driver Thresholds Keys (valores mínimos desejados)
    private val KEY_THRESH_MIN_VALUE_PER_KM = doublePreferencesKey("thresh_min_value_per_km")
    private val KEY_THRESH_MIN_VALUE_PER_HOUR = doublePreferencesKey("thresh_min_value_per_hour")
    private val KEY_THRESH_MIN_RIDE_VALUE = doublePreferencesKey("thresh_min_ride_value")
    private val KEY_THRESH_MAX_PICKUP_DIST = doublePreferencesKey("thresh_max_pickup_distance")
    private val KEY_THRESH_MIN_RATING = doublePreferencesKey("thresh_min_passenger_rating")
    private val KEY_THRESH_MAX_DURATION = doublePreferencesKey("thresh_max_duration")
    private val KEY_THRESH_MAX_STOPS = intPreferencesKey("thresh_max_stops")
    private val KEY_THRESH_MIN_DROPOFF_DIST = doublePreferencesKey("thresh_min_dropoff_distance")

    // Card Selection Keys
    private val KEY_ACTIVE_CARD_SLOT = intPreferencesKey("active_card_slot") // 1, 2, or 3
    private val KEY_CARD1_MODEL_ID = intPreferencesKey("card1_model_id")
    private val KEY_CARD2_MODEL_ID = intPreferencesKey("card2_model_id")
    private val KEY_CARD1_NAME = stringPreferencesKey("card1_name")
    private val KEY_CARD2_NAME = stringPreferencesKey("card2_name")

    // Card 3 Custom Keys
    private val KEY_CARD3_BG_COLOR = longPreferencesKey("card3_bg_color")
    private val KEY_CARD3_TEXT_COLOR = longPreferencesKey("card3_text_color")
    private val KEY_CARD3_ACCENT_COLOR = longPreferencesKey("card3_accent_color")
    private val KEY_CARD3_BORDER_COLOR = longPreferencesKey("card3_border_color")
    private val KEY_CARD3_BORDER_RADIUS = intPreferencesKey("card3_border_radius")
    private val KEY_CARD3_FONT_SIZE = intPreferencesKey("card3_font_size")
    private val KEY_CARD3_LAYOUT_JSON = stringPreferencesKey("card3_layout_json")

    // Overlay Size & Accessibility
    private val KEY_OVERLAY_WIDTH = intPreferencesKey("overlay_width")
    private val KEY_OVERLAY_HEIGHT = intPreferencesKey("overlay_height")
    private val KEY_OVERLAY_FONT_SCALE = floatPreferencesKey("overlay_font_scale")

    // Service State
    private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    private val KEY_OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
    private val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")

    // Blocked Neighborhoods (stored as comma-separated)
    private val KEY_BLOCKED_PICKUP = stringPreferencesKey("blocked_pickup_neighborhoods")
    private val KEY_BLOCKED_DROPOFF = stringPreferencesKey("blocked_dropoff_neighborhoods")

    // Blocked Zones (stored as JSON)
    private val KEY_BLOCKED_ZONES_JSON = stringPreferencesKey("blocked_zones_json")

    // Overlay Position (item 4.2)
    private val KEY_OVERLAY_POS_X = intPreferencesKey("overlay_pos_x")
    private val KEY_OVERLAY_POS_Y = intPreferencesKey("overlay_pos_y")

    // Auto-import earnings (item 3.2)
    private val KEY_AUTO_IMPORT_EARNINGS = booleanPreferencesKey("auto_import_earnings")

    // Overlay Opacity & Bubble (item 4.4)
    private val KEY_OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    private val KEY_BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
    private val KEY_BUBBLE_SIDE = stringPreferencesKey("bubble_side") // "left" or "right"

    // Keep screen on
    private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

    // Gallery favorites
    private val KEY_GALLERY_FAVORITES = stringPreferencesKey("gallery_favorites")

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

    // --- Driver Thresholds (valores mínimos desejados) ---

    val driverThresholdsFlow: Flow<DriverThresholds> = context.dataStore.data.map { prefs ->
        DriverThresholds(
            minValuePerKm = prefs[KEY_THRESH_MIN_VALUE_PER_KM] ?: 0.0,
            minValuePerHour = prefs[KEY_THRESH_MIN_VALUE_PER_HOUR] ?: 0.0,
            minRideValue = prefs[KEY_THRESH_MIN_RIDE_VALUE] ?: 0.0,
            maxPickupDistance = prefs[KEY_THRESH_MAX_PICKUP_DIST] ?: 0.0,
            minPassengerRating = prefs[KEY_THRESH_MIN_RATING] ?: 0.0,
            maxDuration = prefs[KEY_THRESH_MAX_DURATION] ?: 0.0,
            maxStops = prefs[KEY_THRESH_MAX_STOPS] ?: 99,
            minDropoffDistance = prefs[KEY_THRESH_MIN_DROPOFF_DIST] ?: 0.0
        )
    }

    suspend fun saveDriverThresholds(thresholds: DriverThresholds) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THRESH_MIN_VALUE_PER_KM] = thresholds.minValuePerKm
            prefs[KEY_THRESH_MIN_VALUE_PER_HOUR] = thresholds.minValuePerHour
            prefs[KEY_THRESH_MIN_RIDE_VALUE] = thresholds.minRideValue
            prefs[KEY_THRESH_MAX_PICKUP_DIST] = thresholds.maxPickupDistance
            prefs[KEY_THRESH_MIN_RATING] = thresholds.minPassengerRating
            prefs[KEY_THRESH_MAX_DURATION] = thresholds.maxDuration
            prefs[KEY_THRESH_MAX_STOPS] = thresholds.maxStops
            prefs[KEY_THRESH_MIN_DROPOFF_DIST] = thresholds.minDropoffDistance
        }
    }

    // --- Keep Screen On ---

    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_KEEP_SCREEN_ON] ?: false
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_ON] = enabled
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

    val card1NameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD1_NAME] ?: "Card 1"
    }

    val card2NameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD2_NAME] ?: "Card 2"
    }

    suspend fun setCardModelId(slot: Int, modelId: Int) {
        context.dataStore.edit { prefs ->
            when (slot) {
                1 -> prefs[KEY_CARD1_MODEL_ID] = modelId
                2 -> prefs[KEY_CARD2_MODEL_ID] = modelId
            }
        }
    }

    suspend fun setCardName(slot: Int, name: String) {
        context.dataStore.edit { prefs ->
            when (slot) {
                1 -> prefs[KEY_CARD1_NAME] = name
                2 -> prefs[KEY_CARD2_NAME] = name
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

    val card3LayoutJsonFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CARD3_LAYOUT_JSON] ?: ""
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

    suspend fun saveCard3LayoutJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CARD3_LAYOUT_JSON] = json
        }
    }

    // --- Overlay Size & Accessibility ---

    val overlayWidthFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_WIDTH] ?: 320
    }

    val overlayHeightFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_HEIGHT] ?: 180
    }

    val overlayFontScaleFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_FONT_SCALE] ?: 1.0f
    }

    suspend fun saveOverlaySize(width: Int, height: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_WIDTH] = width
            prefs[KEY_OVERLAY_HEIGHT] = height
        }
    }

    suspend fun saveOverlayFontScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_FONT_SCALE] = scale.coerceIn(0.7f, 2.0f)
        }
    }

    // --- Overlay Position (item 4.2) ---

    val overlayPositionXFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_POS_X] ?: 0
    }

    val overlayPositionYFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_POS_Y] ?: 100
    }

    suspend fun saveOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_POS_X] = x
            prefs[KEY_OVERLAY_POS_Y] = y
        }
    }

    // --- Auto-import Earnings (item 3.2) ---

    val autoImportEarningsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_IMPORT_EARNINGS] ?: false
    }

    suspend fun setAutoImportEarnings(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_IMPORT_EARNINGS] = enabled
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

    val protectionEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROTECTION_ENABLED] ?: false
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROTECTION_ENABLED] = enabled
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

    // --- Blocked Zones JSON ---

    val blockedZonesJsonFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_ZONES_JSON] ?: "[]"
    }

    suspend fun saveBlockedZonesJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_ZONES_JSON] = json
        }
    }

    // --- Zone Map Data (mapa interativo) ---

    val zoneMapDataFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLOCKED_ZONES_JSON] ?: ""
    }

    suspend fun saveZoneMapData(json: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOCKED_ZONES_JSON] = json
        }
    }

    // --- Gallery Favorites ---

    val galleryFavoritesFlow: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_GALLERY_FAVORITES] ?: ""
        if (raw.isBlank()) emptySet()
        else raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    suspend fun toggleGalleryFavorite(cardId: Int) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_GALLERY_FAVORITES] ?: ""
            val current = if (raw.isBlank()) mutableSetOf()
            else raw.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()

            if (current.contains(cardId)) current.remove(cardId)
            else current.add(cardId)

            prefs[KEY_GALLERY_FAVORITES] = current.joinToString(",")
        }
    }

    // --- Overlay Opacity & Bubble ---

    val overlayOpacityFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_OPACITY] ?: 1.0f
    }

    suspend fun saveOverlayOpacity(opacity: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_OPACITY] = opacity.coerceIn(0.3f, 1.0f)
        }
    }

    val bubbleEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_ENABLED] ?: true
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_ENABLED] = enabled
        }
    }

    val bubbleSideFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BUBBLE_SIDE] ?: "right"
    }

    suspend fun setBubbleSide(side: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_SIDE] = side
        }
    }

    // --- Admin ---

    private val KEY_ADMIN_PIN = stringPreferencesKey("admin_pin")

    val adminPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ADMIN_PIN] ?: "250696"
    }

    suspend fun saveAdminPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADMIN_PIN] = pin
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    // --- Helpers ---

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


