package com.ngbautoroad.util

// ============================================================================
// ARQUIVO: Extensions.kt
// LOCALIZAÇÃO: util/Extensions.kt
// RESPONSABILIDADE: Extensões globais reutilizáveis em todo o app
// FUNÇÕES:
//   - String.toDoubleLocale(): Converte string com vírgula BR para Double
//   - String.toDoubleLocaleOrNull(): Versão nullable (retorna null se inválido)
// DEPENDENTES:
//   - ui/finance/FinanceActivity.kt → todos os campos numéricos
//   - ui/criteria/CriteriaTab.kt → ThresholdField
// LÓGICA:
//   - Substitui vírgula por ponto antes de parsear
//   - Remove espaços e caracteres não-numéricos
//   - Retorna 0.0 como fallback seguro
// ============================================================================

/**
 * Extensão para converter String digitada pelo usuário brasileiro em Double.
 * Trata: vírgula como separador decimal, espaços, e valores inválidos.
 * Retorna 0.0 se o valor for inválido.
 *
 * Exemplos:
 *   "14,4" → 14.4
 *   "0,80" → 0.80
 *   "  2400,00  " → 2400.0
 *   "abc" → 0.0
 *   "" → 0.0
 *   "R$15,00" → 0.0 (cifrão não é aceito)
 */
fun String.toDoubleLocale(): Double {
    val cleaned = this.replace(",", ".").trim()
    return cleaned.toDoubleOrNull() ?: 0.0
}

/**
 * Versão que retorna null em vez de 0.0 para campos opcionais.
 */
fun String.toDoubleLocaleOrNull(): Double? {
    val cleaned = this.replace(",", ".").trim()
    return cleaned.toDoubleOrNull()
}

/**
 * Validação: retorna true se o valor é um número positivo válido.
 */
fun String.isValidPositiveNumber(): Boolean {
    val value = this.toDoubleLocaleOrNull()
    return value != null && value > 0.0
}

/**
 * Validação: retorna true se o valor é um inteiro válido no range especificado.
 */
fun String.isValidIntInRange(min: Int, max: Int): Boolean {
    val value = this.trim().toIntOrNull()
    return value != null && value in min..max
}

/**
 * Formata Double para exibição com vírgula (padrão BR).
 */
fun Double.toBrString(decimals: Int = 2): String {
    return "%.${decimals}f".format(this).replace(".", ",")
}
