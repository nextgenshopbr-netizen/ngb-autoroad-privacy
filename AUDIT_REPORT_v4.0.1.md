# NGBAutoRoad v4.0.1 — Relatório de Auditoria Lógica

## Resumo Executivo

| Categoria | Quantidade |
|-----------|-----------|
| Testes aprovados | 71 |
| Bugs críticos | 8 |
| Avisos/Melhorias | 14 |
| Total de verificações | 93 |

---

## Bugs Críticos (8) — TODOS serão corrigidos

Todos os 8 bugs são o mesmo padrão: campos numéricos que usam `toDoubleOrNull()` puro do Kotlin, que **não aceita vírgula** como separador decimal. O teclado brasileiro digita `0,80` mas o Kotlin espera `0.80`.

| # | Arquivo | Campo | Impacto |
|---|---------|-------|---------|
| 1 | AddEarningDialog | amount | Ganho salvo como R$0,00 |
| 2 | AddEarningDialog | tips | Gorjeta perdida |
| 3 | AddEarningDialog | bonus | Bônus perdido |
| 4 | AddEarningDialog | distance | Km zerado |
| 5 | AddExpenseDialog | amount | Gasto salvo como R$0,00 |
| 6 | AddExpenseDialog | liters | Litros zerado |
| 7 | AddExpenseDialog | pricePerLiter | Preço/L zerado |
| 8 | AddGoalDialog | targetAmount | Meta com alvo R$0 (impossível atingir) |

**Solução:** Substituir `toDoubleOrNull() ?: 0.0` por extensão `toDoubleLocale()` que faz `replace(",", ".")` antes do parse.

---

## Avisos e Melhorias (14)

### Validações Ausentes (à prova de usuário)

| # | Local | Problema | Solução |
|---|-------|----------|---------|
| 1 | AddEarningDialog | Valor 0 ou negativo aceito | Validar > 0 antes de salvar |
| 2 | AddEarningDialog | Corridas=0 aceito | Se informado, deve ser >= 1 |
| 3 | AddExpenseDialog | Valor 0 aceito | Validar > 0 |
| 4 | AddExpenseDialog | Litros negativos aceitos | Validar >= 0 |
| 5 | AddGoalDialog | Meta com valor 0 | Validar > 0 |
| 6 | VehicleTab | Ano com 5+ dígitos (ex: 20225) | Limitar a 4 dígitos, range 1990-2030 |
| 7 | VehicleTab | Consumo negativo aceito | Validar > 0 |
| 8 | CriteriaTab | Avaliação > 5.0 aceita no input | coerceIn(0.0, 5.0) no campo |

### Lógica de Negócio

| # | Local | Problema | Solução |
|---|-------|----------|---------|
| 9 | RideScorer | inDrive nunca envia rating, motorista penalizado | Pular critério quando rating=0 E plataforma não fornece |
| 10 | ExpenseTab | Recorrência é apenas visual | Implementar WorkManager para gerar gastos automáticos |
| 11 | PreviewDialog | Não respeita campos do card ativo | Iterar galleryCard.fields como o OverlayCard real |

### Informativo

| # | Observação |
|---|-----------|
| 12 | 99 não fornece pickupDistance |
| 13 | inDrive não fornece passengerRating nem intermediateStops |
| 14 | Cabify não fornece pickupDistance nem intermediateStops |

---

## Plano de Correção

Todas as correções serão aplicadas em um único commit (v4.0.2):

1. Criar extensão global `String.toDoubleLocale()` reutilizável
2. Aplicar em TODOS os 8 campos vulneráveis
3. Adicionar validações de input (valor > 0, ano 4 dígitos, etc.)
4. Corrigir RideScorer para pular rating quando plataforma não fornece
5. Adicionar feedback visual (Toast/Snackbar) quando input inválido
6. Corrigir PreviewDialog para respeitar campos do card ativo
