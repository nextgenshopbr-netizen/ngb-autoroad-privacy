package com.ngbautoroad.data.model

// ============================================================================
// ARQUIVO: CardGallery.kt
// LOCALIZAÇÃO: data/model/CardGallery.kt
// RESPONSABILIDADE: Galeria de templates de cards + enum de campos
// CLASSES:
//   - CardField (enum): Todos os campos possíveis em um card
//   - EditorField: Campo editável com posição, cor, tamanho
//   - GalleryCard: Template completo com fields, cores, bordas
//   - CardGallery (object): Lista estática de todos os templates disponíveis
// DEPENDENTES:
//   - service/OverlayCard.kt → renderiza GalleryCard
//   - ui/card/CardTab.kt → exibe galeria e preview
//   - ui/editor/CardEditorActivity.kt → edita campos
//   - service/OverlayService.kt → carrega card ativo
// ============================================================================

/**
 * Galeria de cards prontos com 24 modelos.
 * Cada card tem um layout diferente — desde completos com todos os campos
 * até minimalistas com apenas os campos mais relevantes.
 * Suporta favoritos e categorias para facilitar a navegação.
 */
object CardGallery {

    /**
     * Campos disponíveis para exibição no card
     */
    enum class CardField(val label: String, val shortLabel: String) {
        SCORE("Score", "Score"),
        PLATFORM("Plataforma", "Plat."),
        RIDE_TYPE("Tipo de Corrida", "Tipo"),
        RIDE_VALUE("Valor da Corrida", "Valor"),
        VALUE_PER_KM("R$/KM", "R$/km"),
        VALUE_PER_HOUR("R$/Hora", "R$/h"),
        PICKUP_DISTANCE("Dist. Embarque", "Embarq."),
        DROPOFF_DISTANCE("Dist. Destino", "Dest."),
        DURATION("Duração", "Dur."),
        PASSENGER_RATING("Avaliação", "Aval."),
        STOPS("Paradas", "Paradas"),
        PICKUP_NEIGHBORHOOD("Bairro Embarque", "B. Emb."),
        DROPOFF_NEIGHBORHOOD("Bairro Destino", "B. Dest."),
        SCORE_BAR("Barra de Score", "Barra")
    }

    /**
     * Layout de um card da galeria
     */
    data class GalleryCard(
        val id: Int,
        val name: String,
        val description: String,
        val category: CardCategory,
        val fields: List<CardField>,
        val backgroundColor: Long = 0xFF101830,
        val textColor: Long = 0xFFFFFFFF,
        val accentColor: Long = 0xFF4F6BFF,
        val borderColor: Long = 0xFF4F6BFF,
        val borderRadius: Int = 12,
        val fontSize: Int = 14,
        val showBorder: Boolean = true,
        val compactMode: Boolean = false
    )

    enum class CardCategory(val label: String) {
        COMPLETE("Completo"),
        STANDARD("Padrão"),
        COMPACT("Compacto"),
        MINIMAL("Minimalista"),
        THEMED("Temático")
    }

    // =========================================================================
    // CARD PADRÃO NGBAutoRoad (id=0) — Card default do sistema
    // Inspirado no GigU: 4 métricas com barras de cor (verde/vermelho),
    // badge de plataforma+tipo, score circular, fundo branco com borda vermelha.
    // Este card é o que aparece ao resetar configurações e é o primeiro da galeria.
    // =========================================================================
    val DEFAULT_CARD = GalleryCard(
        id = 0, name = "NGBAutoRoad Padrão",
        description = "Card padrão do sistema — 4 métricas com barras de cor, badge de tipo e score",
        category = CardCategory.STANDARD,
        fields = listOf(
            CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
            CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
            CardField.RIDE_VALUE, CardField.PASSENGER_RATING,
            CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
            CardField.DURATION, CardField.SCORE_BAR
        ),
        backgroundColor = 0xFFFFFFFF, // Fundo branco
        textColor = 0xFF1A1A1A,       // Texto preto
        accentColor = 0xFF1A1A1A,     // Accent preto
        borderColor = 0xFFE53935,     // Borda vermelha (como GigU)
        borderRadius = 12,
        fontSize = 14,
        showBorder = true,
        compactMode = false
    )

    val allCards: List<GalleryCard> = listOf(
        // === CARD PADRÃO (primeiro da lista, default ao resetar) ===
        DEFAULT_CARD,

        // === COMPLETOS (todos os campos) ===
        GalleryCard(
            id = 1, name = "Completo Pro",
            description = "Todos os campos com layout profissional",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF0D1B2A, textColor = 0xFFE0E0E0,
            accentColor = 0xFF00BFA5, borderColor = 0xFF00BFA5,
            borderRadius = 16, fontSize = 13
        ),
        GalleryCard(
            id = 2, name = "Completo Dark",
            description = "Todos os campos com tema escuro elegante",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF1A1A2E, textColor = 0xFFEAEAEA,
            accentColor = 0xFFE94560, borderColor = 0xFFE94560,
            borderRadius = 12, fontSize = 13
        ),
        GalleryCard(
            id = 3, name = "Completo Light",
            description = "Todos os campos com tema claro",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFFF5F5F5, textColor = 0xFF212121,
            accentColor = 0xFF1976D2, borderColor = 0xFF1976D2,
            borderRadius = 12, fontSize = 13
        ),
        GalleryCard(
            id = 4, name = "Completo Neon",
            description = "Todos os campos com estilo neon",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF0A0A0A, textColor = 0xFF00FF88,
            accentColor = 0xFF00FF88, borderColor = 0xFF00FF88,
            borderRadius = 8, fontSize = 12
        ),

        // === PADRÃO (campos principais) ===
        GalleryCard(
            id = 5, name = "Padrão Azul",
            description = "Score, valor, R$/km, distâncias e avaliação",
            category = CardCategory.STANDARD,
            fields = listOf(CardField.SCORE, CardField.PLATFORM, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF101830, textColor = 0xFFFFFFFF,
            accentColor = 0xFF4F6BFF, borderColor = 0xFF4F6BFF,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 6, name = "Padrão Verde",
            description = "Score, valor, R$/km, distâncias e avaliação",
            category = CardCategory.STANDARD,
            fields = listOf(CardField.SCORE, CardField.PLATFORM, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF0D2818, textColor = 0xFFE8F5E9,
            accentColor = 0xFF4CAF50, borderColor = 0xFF4CAF50,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 7, name = "Padrão Roxo",
            description = "Score, valor, R$/km, distâncias e avaliação",
            category = CardCategory.STANDARD,
            fields = listOf(CardField.SCORE, CardField.PLATFORM, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF1A0033, textColor = 0xFFE1BEE7,
            accentColor = 0xFFAB47BC, borderColor = 0xFFAB47BC,
            borderRadius = 14, fontSize = 14
        ),
        GalleryCard(
            id = 8, name = "Padrão Laranja",
            description = "Score, valor, R$/km, distâncias e avaliação",
            category = CardCategory.STANDARD,
            fields = listOf(CardField.SCORE, CardField.PLATFORM, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF1A1008, textColor = 0xFFFFF3E0,
            accentColor = 0xFFFF9800, borderColor = 0xFFFF9800,
            borderRadius = 10, fontSize = 14
        ),

        // === COMPACTOS (campos essenciais) ===
        GalleryCard(
            id = 9, name = "Compacto Valor",
            description = "Score + Valor + R$/km + Bairros",
            category = CardCategory.COMPACT,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD),
            backgroundColor = 0xFF1E1E1E, textColor = 0xFFFFFFFF,
            accentColor = 0xFF64B5F6, borderColor = 0xFF64B5F6,
            borderRadius = 8, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 10, name = "Compacto Distância",
            description = "Score + Distâncias + Valor",
            category = CardCategory.COMPACT,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.VALUE_PER_KM),
            backgroundColor = 0xFF212121, textColor = 0xFFE0E0E0,
            accentColor = 0xFFFFAB40, borderColor = 0xFFFFAB40,
            borderRadius = 10, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 11, name = "Compacto Rating",
            description = "Score + Valor + Avaliação + Paradas",
            category = CardCategory.COMPACT,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.PASSENGER_RATING,
                CardField.STOPS, CardField.VALUE_PER_KM),
            backgroundColor = 0xFF263238, textColor = 0xFFCFD8DC,
            accentColor = 0xFF80CBC4, borderColor = 0xFF80CBC4,
            borderRadius = 12, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 12, name = "Compacto Tempo",
            description = "Score + Valor + Duração + R$/hora",
            category = CardCategory.COMPACT,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.DURATION,
                CardField.VALUE_PER_HOUR, CardField.VALUE_PER_KM),
            backgroundColor = 0xFF1B2838, textColor = 0xFFC5CAE9,
            accentColor = 0xFF7C4DFF, borderColor = 0xFF7C4DFF,
            borderRadius = 10, fontSize = 14, compactMode = true
        ),

        // === MINIMALISTAS (2-3 campos) ===
        GalleryCard(
            id = 13, name = "Mini Score",
            description = "Apenas Score grande + cor",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.SCORE_BAR),
            backgroundColor = 0xFF000000, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFFFFF, borderColor = 0x00000000,
            borderRadius = 20, fontSize = 24, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 14, name = "Mini Valor",
            description = "Score + Valor da corrida",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE),
            backgroundColor = 0xFF1A237E, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFEB3B, borderColor = 0x00000000,
            borderRadius = 16, fontSize = 18, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 15, name = "Mini R$/KM",
            description = "Score + R$/KM",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.VALUE_PER_KM),
            backgroundColor = 0xFF004D40, textColor = 0xFFE0F2F1,
            accentColor = 0xFF00E676, borderColor = 0x00000000,
            borderRadius = 16, fontSize = 18, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 16, name = "Mini Bairro",
            description = "Score + Bairro destino",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.DROPOFF_NEIGHBORHOOD),
            backgroundColor = 0xFF311B92, textColor = 0xFFEDE7F6,
            accentColor = 0xFFB388FF, borderColor = 0x00000000,
            borderRadius = 16, fontSize = 16, compactMode = true, showBorder = false
        ),

        // === TEMÁTICOS ===
        GalleryCard(
            id = 17, name = "Uber Style",
            description = "Inspirado no visual Uber",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF000000, textColor = 0xFFFFFFFF,
            accentColor = 0xFF06C167, borderColor = 0xFF06C167,
            borderRadius = 8, fontSize = 14
        ),
        GalleryCard(
            id = 18, name = "99 Style",
            description = "Inspirado no visual 99",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF1A1A1A, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFCA28, borderColor = 0xFFFFCA28,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 19, name = "Cyberpunk",
            description = "Estilo futurista cyberpunk",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.VALUE_PER_HOUR, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.SCORE_BAR),
            backgroundColor = 0xFF0D0221, textColor = 0xFF0ABDC6,
            accentColor = 0xFFEA00D9, borderColor = 0xFFEA00D9,
            borderRadius = 4, fontSize = 13
        ),
        GalleryCard(
            id = 20, name = "Sunset",
            description = "Gradiente quente e acolhedor",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_DISTANCE, CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF2D1B69, textColor = 0xFFFFF8E1,
            accentColor = 0xFFFF6D00, borderColor = 0xFFFF6D00,
            borderRadius = 16, fontSize = 14
        ),
        GalleryCard(
            id = 21, name = "Matrix",
            description = "Estilo terminal hacker",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.DURATION, CardField.STOPS),
            backgroundColor = 0xFF000000, textColor = 0xFF00FF00,
            accentColor = 0xFF00FF00, borderColor = 0xFF003300,
            borderRadius = 0, fontSize = 12, compactMode = true
        ),
        GalleryCard(
            id = 22, name = "Glass",
            description = "Efeito vidro translúcido",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE, CardField.SCORE_BAR),
            backgroundColor = 0x99FFFFFF, textColor = 0xFF1A1A1A,
            accentColor = 0xFF6200EA, borderColor = 0x66FFFFFF,
            borderRadius = 20, fontSize = 14
        ),
        GalleryCard(
            id = 23, name = "Blood Red",
            description = "Estilo vermelho intenso",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR),
            backgroundColor = 0xFF1A0000, textColor = 0xFFFFCDD2,
            accentColor = 0xFFFF1744, borderColor = 0xFFFF1744,
            borderRadius = 8, fontSize = 14
        ),
        GalleryCard(
            id = 24, name = "Ocean Deep",
            description = "Azul oceano profundo",
            category = CardCategory.THEMED,
            fields = listOf(CardField.SCORE, CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.DROPOFF_DISTANCE, CardField.DURATION, CardField.SCORE_BAR),
            backgroundColor = 0xFF001F3F, textColor = 0xFFB3E5FC,
            accentColor = 0xFF00B0FF, borderColor = 0xFF0091EA,
            borderRadius = 14, fontSize = 14
        )
    )

    fun getById(id: Int): GalleryCard? = allCards.find { it.id == id }

    fun getDefault(): GalleryCard = DEFAULT_CARD

    fun getByCategory(category: CardCategory): List<GalleryCard> =
        allCards.filter { it.category == category }

    /**
     * Compatibilidade com código antigo
     */
    val models: List<CardModel> = allCards.map { card ->
        CardModel(
            id = card.id,
            name = card.name,
            backgroundColor = card.backgroundColor,
            textColor = card.textColor,
            accentColor = card.accentColor,
            borderColor = card.borderColor,
            borderRadius = card.borderRadius,
            fontSize = card.fontSize,
            showPlatformIcon = true,
            showScore = true,
            isCustom = false
        )
    }

    fun getModelById(id: Int): CardModel {
        return models.find { it.id == id } ?: models[0]
    }
}
