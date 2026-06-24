# Histórico de Auditorias - NGB AutoRoad

> Este arquivo mantém o registro contínuo de todas as auditorias avançadas realizadas no código, preservando o histórico de análises, simulações e correções sugeridas.

---

## 📅 Auditoria: 24 de Junho de 2026
**Foco:** Lógica Matemática, Propagação de Erros (NaN) e Consistência de Dados (Simulação de 1 Ano)
**Versão do Projeto:** v6.9.7 (estimada)

### 1. Metodologia
Foi desenvolvida uma simulação em Python (`scripts/simulate_1_year_audit.py`) mimetizando as operações matemáticas do Kotlin para avaliar o comportamento do sistema sob carga (equivalente a 1 ano de uso intensivo de um motorista: 4000 corridas). Foram injetadas anomalias como:
- Distâncias zeradas (GPS falho).
- Durações zeradas ou extremamente curtas.
- Valores negativos na corrida (estornos simulados).

### 2. Descobertas e Vulnerabilidades (Cruze de Módulos)

#### 2.1 Propagação Silenciosa de `NaN` no `FinanceDRE.kt` (Crítico)
**Problema:** O cálculo do fator de correção do odômetro usa a função `.average()` em uma coleção filtrada. No Kotlin, se a lista resultante do filtro (`it > 0`) for vazia, `average()` retorna `Double.NaN`. 
**Impacto:** Qualquer número multiplicado por `NaN` vira `NaN`. Se um motorista tiver um mês sem registros válidos de histórico de odômetro, a receita calculada do DRE inteira será corrompida e a UI poderá quebrar.
**Módulo:** `FinanceDREEngine.generateDRE`

#### 2.2 Distorção de Margem de Contribuição (`FinanceDRE.kt`) (Aviso/Alto)
**Problema:** A margem de contribuição percentual não valida adequadamente se a `receitaBruta` é negativa ou zero devido a estornos da plataforma ou falhas do OCR.
**Impacto:** Risco de `Infinity` ou divisão por zero silenciosa, distorcendo relatórios de lucros mensais.

#### 2.3 Problemas de Custo e Prejuízo por Km (`RideScorer.kt`) (Aviso/Médio)
**Problema:** No critério de Lucro/KM, o cálculo normaliza considerando um "Lucro Excelente = 2x o Custo". No entanto, uma corrida com lucro negativo só aplica uma penalidade estática de 5 pontos, em vez de escalar a penalidade proporcionalmente ao tamanho do prejuízo.
**Impacto:** Corridas que dão muito prejuízo não são punidas severamente o suficiente, levando a aceitações equivocadas pelo motorista.

#### 2.4 Race Conditions e Mutabilidade no `KmValidationHistory.kt` (Aviso/Alto)
**Problema:** O histórico de discrepâncias em JSON lê do `SharedPreferences`, altera o array e reescreve no disco sem bloqueio adequado (`synchronized`), diferentemente do `ShiftManager`.
**Impacto:** Se o aplicativo processar duas corridas (ou threads) atualizando o KM ao mesmo tempo, uma sobrescreverá a outra, resultando em perda de histórico do GPS para o motorista.

### 3. Plano de Melhorias e Soluções Propostas

1. **Proteção Anti-NaN (Safe Average):**
   Substituir as chamadas de `.average()` nativas do Kotlin por uma extensão `.safeAverage(fallback: Double)` que garanta o retorno numérico válido em caso de lista vazia.
   *Exemplo no FinanceDRE:*
   ```kotlin
   val avg = historyEntries.map { it.calibrationFactor }.filter { it > 0 }.let { 
       if (it.isNotEmpty()) it.average() else vehicle.odometerCorrectionFactor
   }
   ```

2. **Refatoração do `KmValidationHistory` (Concurrency):**
   Adicionar um lock `private val lock = Any()` assim como no `ShiftManager.kt` ao redor da função `saveDiscrepancy` e `recordValidation`.

3. **Validação Rígida no `FinanceDRE.kt` e `RideScorer.kt`:**
   Adicionar blocos `try/catch` para captura de erros aritméticos, e uso de funções `.coerceIn` ou `.coerceAtLeast(0.0)` em divisores antes de divisões.

4. **Escala de Penalidade de Prejuízo:**
   Melhorar a fórmula do Lucro/KM no `RideScorer.kt` para subtrair pontos de forma não-linear caso o lucro seja negativo (exemplo: prejuízos grandes zeram o Score, forçando o `Safety Modifier`).

### 4. Correções Aplicadas e Validação (24 de Junho de 2026)

Todas as correções propostas no plano de auditoria foram aplicadas e validadas:
- **[FinanceDRE.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/FinanceDRE.kt)**: Correção de `NaN` em `correctionFactor` adicionando verificação segura de lista não-vazia antes de calcular a média (`average()`).
- **[KmValidationHistory.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/KmValidationHistory.kt)**: Adicionado bloqueio sincronizado de exclusão mútua (`private val lock = Any()`) em todos os métodos que manipulam o SharedPreferences e dados JSON (`recordValidation`, `generateMonthlyReport`, `saveDiscrepancy`, `loadDiscrepancies`).
- **[RideScorer.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/RideScorer.kt)**: Substituição da penalidade estática de 5.0 pontos no cálculo de Lucro/KM por uma fórmula dinâmica e não-linear `5.0 + 25.0 * (abs(profitPerKm) / costPerKm)^1.5` limitada a 50.0 pontos, garantindo punições severas para grandes prejuízos operacionais.
- **Validação de Build**: A compilação do projeto com Gradle wrapper foi executada e finalizada com sucesso (`BUILD SUCCESSFUL`), sem erros de sintaxe ou de compilação.

---

