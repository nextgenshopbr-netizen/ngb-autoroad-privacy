package com.ngbautoroad.data.model

/**
 * Galeria de 12 modelos de card pré-definidos
 * Cards 1 e 2 aceitam modelos da galeria
 * Card 3 = Custom exclusivo (não aceita galeria)
 */
object CardGallery {

    val models: List<CardModel> = listOf(
        // 1. Midnight Blue (default)
        CardModel(
            id = 1, name = "Midnight Blue",
            backgroundColor = 0xFF101830,
            textColor = 0xFFE3E3F0,
            accentColor = 0xFF4F6BFF,
            borderColor = 0xFF4F6BFF,
            borderRadius = 12, fontSize = 14
        ),
        // 2. Deep Purple
        CardModel(
            id = 2, name = "Deep Purple",
            backgroundColor = 0xFF1A0A2E,
            textColor = 0xFFE8D5FF,
            accentColor = 0xFF9C27B0,
            borderColor = 0xFF7B1FA2,
            borderRadius = 12, fontSize = 14
        ),
        // 3. Dark Emerald
        CardModel(
            id = 3, name = "Dark Emerald",
            backgroundColor = 0xFF0A1F1A,
            textColor = 0xFFD0F0E0,
            accentColor = 0xFF00C853,
            borderColor = 0xFF2E7D32,
            borderRadius = 12, fontSize = 14
        ),
        // 4. Charcoal Gold
        CardModel(
            id = 4, name = "Charcoal Gold",
            backgroundColor = 0xFF1A1A1A,
            textColor = 0xFFF5F5F5,
            accentColor = 0xFFFFD700,
            borderColor = 0xFFB8860B,
            borderRadius = 8, fontSize = 14
        ),
        // 5. Ocean Depth
        CardModel(
            id = 5, name = "Ocean Depth",
            backgroundColor = 0xFF0D2137,
            textColor = 0xFFB8D4E8,
            accentColor = 0xFF00BCD4,
            borderColor = 0xFF0097A7,
            borderRadius = 16, fontSize = 14
        ),
        // 6. Crimson Night
        CardModel(
            id = 6, name = "Crimson Night",
            backgroundColor = 0xFF1A0A0A,
            textColor = 0xFFFFD5D5,
            accentColor = 0xFFFF5252,
            borderColor = 0xFFD32F2F,
            borderRadius = 12, fontSize = 14
        ),
        // 7. Slate Modern
        CardModel(
            id = 7, name = "Slate Modern",
            backgroundColor = 0xFF2D3748,
            textColor = 0xFFE2E8F0,
            accentColor = 0xFF63B3ED,
            borderColor = 0xFF4299E1,
            borderRadius = 6, fontSize = 13
        ),
        // 8. Neon Cyber
        CardModel(
            id = 8, name = "Neon Cyber",
            backgroundColor = 0xFF0A0A0A,
            textColor = 0xFF00FF88,
            accentColor = 0xFF00FF88,
            borderColor = 0xFF00CC6A,
            borderRadius = 4, fontSize = 13
        ),
        // 9. Warm Sunset
        CardModel(
            id = 9, name = "Warm Sunset",
            backgroundColor = 0xFF2D1B0E,
            textColor = 0xFFFFE0B2,
            accentColor = 0xFFFF6D00,
            borderColor = 0xFFE65100,
            borderRadius = 14, fontSize = 14
        ),
        // 10. Arctic Ice
        CardModel(
            id = 10, name = "Arctic Ice",
            backgroundColor = 0xFF0F1B2D,
            textColor = 0xFFE0F7FA,
            accentColor = 0xFF80DEEA,
            borderColor = 0xFF4DD0E1,
            borderRadius = 10, fontSize = 14
        ),
        // 11. Minimal Dark
        CardModel(
            id = 11, name = "Minimal Dark",
            backgroundColor = 0xFF121212,
            textColor = 0xFFFFFFFF,
            accentColor = 0xFFBB86FC,
            borderColor = 0xFF3700B3,
            borderRadius = 8, fontSize = 14
        ),
        // 12. Forest Night
        CardModel(
            id = 12, name = "Forest Night",
            backgroundColor = 0xFF0B1E0B,
            textColor = 0xFFC8E6C9,
            accentColor = 0xFF66BB6A,
            borderColor = 0xFF388E3C,
            borderRadius = 12, fontSize = 14
        )
    )

    fun getModelById(id: Int): CardModel {
        return models.find { it.id == id } ?: models[0]
    }
}
