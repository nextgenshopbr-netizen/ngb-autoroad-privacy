package com.ngbautoroad.data.model

// ============================================================================
// ARQUIVO: CardGallery.kt
// LOCALIZAÇÃO: data/model/CardGallery.kt
// RESPONSABILIDADE: Galeria de templates de cards + enum de campos
// CLASSES:
//   - CardField (enum): Todos os campos possíveis em um card
//   - GalleryCard: Template completo com fields, cores, bordas
//   - CardGallery (object): Lista estática de todos os templates disponíveis
// DEPENDENTES:
//   - service/OverlayCard.kt → renderiza GalleryCard
//   - ui/card/CardTab.kt → exibe galeria e preview
//   - ui/editor/CardEditorActivity.kt → edita campos
//   - service/OverlayService.kt → carrega card ativo
// ============================================================================

/**
 * Galeria de cards prontos com 34 modelos.
 * Cada card tem um layout diferente — desde completos com todos os campos
 * até minimalistas com apenas os campos mais relevantes.
 * Todos os cards incluem campos relevantes para o motorista avaliar corridas:
 * - Tipo de corrida (UberX, Comfort, Black)
 * - Bairros de embarque e destino
 * - Métricas financeiras (R$/km, R$/hora)
 * - Score com barra de progresso
 * Suporta favoritos e categorias para facilitar a navegação.
 */
object CardGallery {

    /**
     * Campos disponíveis para exibição no card.
     * Cada campo representa uma informação que o motorista pode ver no overlay.
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
        FINANCIAL("Financeiro"),
        LOCATION("Localização"),
        COMPACT("Compacto"),
        MINIMAL("Minimalista"),
        THEMED("Temático")
    }

    // =========================================================================
    // CARD PADRÃO NGBAutoRoad (id=0) — Card default do sistema
    // Inspirado no GigU: 4 métricas com barras de cor (verde/vermelho),
    // badge de plataforma+tipo, score circular, fundo branco com borda dinâmica.
    // Este card é o que aparece ao resetar configurações e é o primeiro da galeria.
    // =========================================================================
    val DEFAULT_CARD = GalleryCard(
        id = 0, name = "NGBAutoRoad Padrão",
        description = "Card padrão — Score, tipo de corrida, 4 métricas com barras, bairros e valor",
        category = CardCategory.STANDARD,
        fields = listOf(
            CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
            CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
            CardField.RIDE_VALUE, CardField.PASSENGER_RATING,
            CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
            CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
            CardField.DURATION, CardField.SCORE_BAR
        ),
        backgroundColor = 0xFFFFFFFF,
        textColor = 0xFF1A1A1A,
        accentColor = 0xFF1A1A1A,
        borderColor = 0xFFE53935, // Borda dinâmica (muda por score)
        borderRadius = 12,
        fontSize = 14,
        showBorder = true,
        compactMode = false
    )

    val allCards: List<GalleryCard> = listOf(
        // =====================================================================
        // CARD PADRÃO (primeiro da lista, default ao resetar)
        // =====================================================================
        DEFAULT_CARD,

        // =====================================================================
        // COMPLETOS (todos os campos — visão total da corrida)
        // =====================================================================
        GalleryCard(
            id = 1, name = "Completo Pro",
            description = "Todos os campos — tipo de corrida, bairros, métricas, avaliação e score",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF0D1B2A, textColor = 0xFFE0E0E0,
            accentColor = 0xFF00BFA5, borderColor = 0xFF00BFA5,
            borderRadius = 14, fontSize = 13
        ),
        GalleryCard(
            id = 2, name = "Completo Escuro",
            description = "Todos os campos com tema escuro — ideal para uso noturno",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF121212, textColor = 0xFFE0E0E0,
            accentColor = 0xFFBB86FC, borderColor = 0xFFBB86FC,
            borderRadius = 12, fontSize = 13
        ),
        GalleryCard(
            id = 3, name = "Completo Claro",
            description = "Todos os campos com tema claro — máxima legibilidade ao sol",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFFF8F9FA, textColor = 0xFF212121,
            accentColor = 0xFF1976D2, borderColor = 0xFF1976D2,
            borderRadius = 12, fontSize = 13
        ),
        GalleryCard(
            id = 4, name = "Completo Alto Contraste",
            description = "Todos os campos — cores de alto contraste para fácil leitura",
            category = CardCategory.COMPLETE,
            fields = CardField.entries.toList(),
            backgroundColor = 0xFF000000, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFD600, borderColor = 0xFFFFD600,
            borderRadius = 10, fontSize = 14
        ),

        // =====================================================================
        // PADRÃO (campos principais para decisão rápida)
        // =====================================================================
        GalleryCard(
            id = 5, name = "Decisão Rápida",
            description = "Score, tipo, valor, R$/km, bairros — tudo para decidir em segundos",
            category = CardCategory.STANDARD,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF101830, textColor = 0xFFFFFFFF,
            accentColor = 0xFF4F6BFF, borderColor = 0xFF4F6BFF,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 6, name = "Motorista Esperto",
            description = "Foco em rentabilidade — R$/km, R$/hora, tipo e distâncias",
            category = CardCategory.STANDARD,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
                CardField.RIDE_VALUE, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_DISTANCE, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF0D2818, textColor = 0xFFE8F5E9,
            accentColor = 0xFF4CAF50, borderColor = 0xFF4CAF50,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 7, name = "Bairro Seguro",
            description = "Prioriza bairros — embarque, destino, avaliação do passageiro e score",
            category = CardCategory.STANDARD,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.PASSENGER_RATING, CardField.RIDE_VALUE,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A0033, textColor = 0xFFE1BEE7,
            accentColor = 0xFFAB47BC, borderColor = 0xFFAB47BC,
            borderRadius = 14, fontSize = 14
        ),
        GalleryCard(
            id = 8, name = "Tempo é Dinheiro",
            description = "Foco em tempo — duração, R$/hora, R$/km e tipo de corrida",
            category = CardCategory.STANDARD,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.DURATION, CardField.VALUE_PER_HOUR,
                CardField.VALUE_PER_KM, CardField.RIDE_VALUE,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A1008, textColor = 0xFFFFF3E0,
            accentColor = 0xFFFF9800, borderColor = 0xFFFF9800,
            borderRadius = 10, fontSize = 14
        ),

        // =====================================================================
        // FINANCEIRO (foco em métricas de rentabilidade)
        // =====================================================================
        GalleryCard(
            id = 9, name = "Lucro Máximo",
            description = "R$/km, R$/hora, valor, tipo — foco total em rentabilidade",
            category = CardCategory.FINANCIAL,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
                CardField.RIDE_VALUE, CardField.DURATION,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1B5E20, textColor = 0xFFE8F5E9,
            accentColor = 0xFF69F0AE, borderColor = 0xFF69F0AE,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 10, name = "Custo-Benefício",
            description = "Distância embarque vs valor — avalia se vale a pena buscar",
            category = CardCategory.FINANCIAL,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.PICKUP_DISTANCE, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.DROPOFF_DISTANCE,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF212121, textColor = 0xFFE0E0E0,
            accentColor = 0xFFFFAB40, borderColor = 0xFFFFAB40,
            borderRadius = 10, fontSize = 14
        ),
        GalleryCard(
            id = 11, name = "Análise Completa",
            description = "Todas as métricas financeiras + avaliação + paradas",
            category = CardCategory.FINANCIAL,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
                CardField.RIDE_VALUE, CardField.PASSENGER_RATING,
                CardField.STOPS, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF263238, textColor = 0xFFCFD8DC,
            accentColor = 0xFF80CBC4, borderColor = 0xFF80CBC4,
            borderRadius = 12, fontSize = 13
        ),
        GalleryCard(
            id = 12, name = "Rendimento/Hora",
            description = "Prioriza R$/hora — ideal para quem quer maximizar ganho por tempo",
            category = CardCategory.FINANCIAL,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.VALUE_PER_HOUR, CardField.DURATION,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1B2838, textColor = 0xFFC5CAE9,
            accentColor = 0xFF7C4DFF, borderColor = 0xFF7C4DFF,
            borderRadius = 10, fontSize = 14
        ),

        // =====================================================================
        // LOCALIZAÇÃO (foco em bairros e distâncias)
        // =====================================================================
        GalleryCard(
            id = 13, name = "Mapa Mental",
            description = "Bairros de embarque e destino + distâncias — saber pra onde vai",
            category = CardCategory.LOCATION,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
                CardField.RIDE_VALUE, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A237E, textColor = 0xFFC5CAE9,
            accentColor = 0xFF448AFF, borderColor = 0xFF448AFF,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 14, name = "Rota Inteligente",
            description = "Distâncias + bairros + duração — planejar a rota antes de aceitar",
            category = CardCategory.LOCATION,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.PICKUP_NEIGHBORHOOD, CardField.PICKUP_DISTANCE,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.DROPOFF_DISTANCE,
                CardField.DURATION, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF004D40, textColor = 0xFFE0F2F1,
            accentColor = 0xFF00E676, borderColor = 0xFF00E676,
            borderRadius = 14, fontSize = 13
        ),
        GalleryCard(
            id = 15, name = "Destino Primeiro",
            description = "Foco no destino — bairro, distância e valor para saber pra onde vai",
            category = CardCategory.LOCATION,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.DROPOFF_DISTANCE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF311B92, textColor = 0xFFEDE7F6,
            accentColor = 0xFFB388FF, borderColor = 0xFFB388FF,
            borderRadius = 14, fontSize = 14
        ),
        GalleryCard(
            id = 16, name = "Embarque Perto",
            description = "Prioriza distância de embarque — aceitar só corridas próximas",
            category = CardCategory.LOCATION,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.PICKUP_DISTANCE, CardField.PICKUP_NEIGHBORHOOD,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF0D47A1, textColor = 0xFFBBDEFB,
            accentColor = 0xFF82B1FF, borderColor = 0xFF82B1FF,
            borderRadius = 12, fontSize = 14
        ),

        // =====================================================================
        // COMPACTOS (campos essenciais em espaço reduzido)
        // =====================================================================
        GalleryCard(
            id = 17, name = "Compacto Essencial",
            description = "Score + Tipo + Valor + R$/km + Bairro destino — mínimo necessário",
            category = CardCategory.COMPACT,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE, CardField.RIDE_VALUE,
                CardField.VALUE_PER_KM, CardField.DROPOFF_NEIGHBORHOOD
            ),
            backgroundColor = 0xFF1E1E1E, textColor = 0xFFFFFFFF,
            accentColor = 0xFF64B5F6, borderColor = 0xFF64B5F6,
            borderRadius = 8, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 18, name = "Compacto Financeiro",
            description = "Score + R$/km + R$/hora + Valor — decisão por rentabilidade",
            category = CardCategory.COMPACT,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.VALUE_PER_KM, CardField.VALUE_PER_HOUR,
                CardField.RIDE_VALUE
            ),
            backgroundColor = 0xFF1B5E20, textColor = 0xFFA5D6A7,
            accentColor = 0xFF76FF03, borderColor = 0xFF76FF03,
            borderRadius = 10, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 19, name = "Compacto Bairros",
            description = "Score + Bairro embarque + Bairro destino + Valor",
            category = CardCategory.COMPACT,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.RIDE_VALUE
            ),
            backgroundColor = 0xFF37474F, textColor = 0xFFECEFF1,
            accentColor = 0xFF80DEEA, borderColor = 0xFF80DEEA,
            borderRadius = 10, fontSize = 14, compactMode = true
        ),
        GalleryCard(
            id = 20, name = "Compacto Distância",
            description = "Score + Distâncias + Valor + Tipo — foco em km",
            category = CardCategory.COMPACT,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.PICKUP_DISTANCE, CardField.DROPOFF_DISTANCE,
                CardField.RIDE_VALUE
            ),
            backgroundColor = 0xFF212121, textColor = 0xFFE0E0E0,
            accentColor = 0xFFFFAB40, borderColor = 0xFFFFAB40,
            borderRadius = 10, fontSize = 14, compactMode = true
        ),

        // =====================================================================
        // MINIMALISTAS (2-4 campos — decisão instantânea)
        // =====================================================================
        GalleryCard(
            id = 21, name = "Mini Score + Tipo",
            description = "Apenas Score grande + tipo de corrida — decisão por instinto",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.RIDE_TYPE, CardField.SCORE_BAR),
            backgroundColor = 0xFF000000, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFFFFF, borderColor = 0x00000000,
            borderRadius = 20, fontSize = 22, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 22, name = "Mini Valor + Destino",
            description = "Valor + Bairro destino — saber quanto e pra onde",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.RIDE_TYPE, CardField.RIDE_VALUE, CardField.DROPOFF_NEIGHBORHOOD),
            backgroundColor = 0xFF1A237E, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFEB3B, borderColor = 0x00000000,
            borderRadius = 16, fontSize = 18, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 23, name = "Mini R$/KM",
            description = "Score + R$/KM — métrica mais importante para rentabilidade",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.VALUE_PER_KM, CardField.RIDE_TYPE),
            backgroundColor = 0xFF004D40, textColor = 0xFFE0F2F1,
            accentColor = 0xFF00E676, borderColor = 0x00000000,
            borderRadius = 16, fontSize = 18, compactMode = true, showBorder = false
        ),
        GalleryCard(
            id = 24, name = "Mini Semáforo",
            description = "Apenas score com barra — verde aceita, vermelho recusa",
            category = CardCategory.MINIMAL,
            fields = listOf(CardField.SCORE, CardField.RIDE_TYPE, CardField.SCORE_BAR),
            backgroundColor = 0xFF1A1A1A, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFFFFF, borderColor = 0x00000000,
            borderRadius = 24, fontSize = 28, compactMode = true, showBorder = false
        ),

        // =====================================================================
        // TEMÁTICOS (visuais diferenciados)
        // =====================================================================
        GalleryCard(
            id = 25, name = "Uber Black",
            description = "Inspirado no visual Uber — preto com verde, tipo e bairros",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF000000, textColor = 0xFFFFFFFF,
            accentColor = 0xFF06C167, borderColor = 0xFF06C167,
            borderRadius = 8, fontSize = 14
        ),
        GalleryCard(
            id = 26, name = "99 Amarelo",
            description = "Inspirado no visual 99 — escuro com amarelo, tipo e métricas",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A1A1A, textColor = 0xFFFFFFFF,
            accentColor = 0xFFFFCA28, borderColor = 0xFFFFCA28,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 27, name = "Cyberpunk",
            description = "Estilo futurista neon — rosa e ciano sobre roxo escuro",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.VALUE_PER_HOUR, CardField.PICKUP_NEIGHBORHOOD,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF0D0221, textColor = 0xFF0ABDC6,
            accentColor = 0xFFEA00D9, borderColor = 0xFFEA00D9,
            borderRadius = 4, fontSize = 13
        ),
        GalleryCard(
            id = 28, name = "Sunset Drive",
            description = "Gradiente quente — laranja sobre roxo, ideal para fim de tarde",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.PASSENGER_RATING, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF2D1B69, textColor = 0xFFFFF8E1,
            accentColor = 0xFFFF6D00, borderColor = 0xFFFF6D00,
            borderRadius = 16, fontSize = 14
        ),
        GalleryCard(
            id = 29, name = "Matrix",
            description = "Estilo terminal hacker — verde sobre preto",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.DURATION, CardField.DROPOFF_NEIGHBORHOOD
            ),
            backgroundColor = 0xFF000000, textColor = 0xFF00FF00,
            accentColor = 0xFF00FF00, borderColor = 0xFF003300,
            borderRadius = 0, fontSize = 12, compactMode = true
        ),
        GalleryCard(
            id = 30, name = "Glass Frost",
            description = "Efeito vidro translúcido — elegante e discreto",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0x99FFFFFF, textColor = 0xFF1A1A1A,
            accentColor = 0xFF6200EA, borderColor = 0x66FFFFFF,
            borderRadius = 20, fontSize = 14
        ),
        GalleryCard(
            id = 31, name = "Ocean Deep",
            description = "Azul oceano profundo — calmo e legível",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.DROPOFF_DISTANCE,
                CardField.DURATION, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF001F3F, textColor = 0xFFB3E5FC,
            accentColor = 0xFF00B0FF, borderColor = 0xFF0091EA,
            borderRadius = 14, fontSize = 14
        ),
        GalleryCard(
            id = 32, name = "Blood Racing",
            description = "Vermelho intenso — para quem gosta de adrenalina",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.VALUE_PER_HOUR, CardField.PICKUP_NEIGHBORHOOD,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A0000, textColor = 0xFFFFCDD2,
            accentColor = 0xFFFF1744, borderColor = 0xFFFF1744,
            borderRadius = 8, fontSize = 14
        ),
        GalleryCard(
            id = 33, name = "Gold Premium",
            description = "Dourado sobre preto — visual premium para motoristas exigentes",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.VALUE_PER_HOUR, CardField.PICKUP_NEIGHBORHOOD,
                CardField.DROPOFF_NEIGHBORHOOD, CardField.PASSENGER_RATING,
                CardField.SCORE_BAR
            ),
            backgroundColor = 0xFF1A1A1A, textColor = 0xFFFFD700,
            accentColor = 0xFFFFD700, borderColor = 0xFFB8860B,
            borderRadius = 12, fontSize = 14
        ),
        GalleryCard(
            id = 34, name = "Arctic Ice",
            description = "Branco gelado com azul — clean e profissional",
            category = CardCategory.THEMED,
            fields = listOf(
                CardField.SCORE, CardField.PLATFORM, CardField.RIDE_TYPE,
                CardField.RIDE_VALUE, CardField.VALUE_PER_KM,
                CardField.PICKUP_NEIGHBORHOOD, CardField.DROPOFF_NEIGHBORHOOD,
                CardField.DURATION, CardField.SCORE_BAR
            ),
            backgroundColor = 0xFFF0F8FF, textColor = 0xFF1A3A5C,
            accentColor = 0xFF0277BD, borderColor = 0xFF0277BD,
            borderRadius = 16, fontSize = 14
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
