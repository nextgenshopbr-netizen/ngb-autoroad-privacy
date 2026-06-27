// ============================================================================
// ARQUIVO: Color.kt
// LOCALIZAÇÃO: ui/theme/Color.kt
// RESPONSABILIDADE: Paleta de cores do app
//   - Cores de score (verde, amarelo, laranja, vermelho)
//   - Cores de plataforma (Uber, 99, inDrive, Cabify)
// DEPENDÊNCIAS: Nenhuma
// ============================================================================
package com.ngbautoroad.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Theme Colors (paleta azul-escuro/índigo do v2.1.7)
val DarkPrimary = Color(0xFF4F6BFF)        // Azul-índigo accent
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF2A3A8F)
val DarkOnPrimaryContainer = Color(0xFFD6DCFF)
val DarkSecondary = Color(0xFF7C8AFF)
val DarkOnSecondary = Color(0xFF1A1A2E)
val DarkBackground = Color(0xFF0F1629)      // Azul-escuro profundo
val DarkOnBackground = Color(0xFFE3E3F0)
val DarkSurface = Color(0xFF1A2040)         // Azul-escuro card
val DarkOnSurface = Color(0xFFE3E3F0)
val DarkSurfaceVariant = Color(0xFF252D50)
val DarkOnSurfaceVariant = Color(0xFFBFC4DC)
val DarkError = Color(0xFFFF6B6B)
val DarkOnError = Color(0xFF1A1A1A)

// Light Theme Colors (corrigido - era o problema do v2.x)
val LightPrimary = Color(0xFF3451DB)       // Azul-índigo forte
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDBE1FF)
val LightOnPrimaryContainer = Color(0xFF001452)
val LightSecondary = Color(0xFF5A6399)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFF8F9FF)    // Branco azulado
val LightOnBackground = Color(0xFF1A1C2E)
val LightSurface = Color(0xFFFFFFFF)       // Branco puro para cards
val LightOnSurface = Color(0xFF1A1C2E)
val LightSurfaceVariant = Color(0xFFE7E8F4)
val LightOnSurfaceVariant = Color(0xFF44465A)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)

// Score Colors (Ajustadas para melhor contraste MD3)
val ScoreGreen = Color(0xFF388E3C)
val ScoreYellow = Color(0xFFFBC02D)
val ScoreOrange = Color(0xFFF57C00)
val ScoreRed = Color(0xFFD32F2F)

// Overlay Card Colors
val OverlayBackground = Color(0xE6101830)  // Fundo semi-transparente escuro
val OverlayBorder = Color(0xFF4F6BFF)
