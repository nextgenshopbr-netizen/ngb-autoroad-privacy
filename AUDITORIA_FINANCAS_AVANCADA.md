# Auditoria Avançada — Módulo de Finanças (v6.3.7)

## Resumo Executivo

O módulo de Finanças do NGB AutoRoad possui uma estrutura sólida com 6 abas (Resumo, Ganhos, Despesas, Veículos, Desp. Fixas, Metas), banco Room com migrações, projeção inteligente e simulação "E se?". Porém, a auditoria revelou **8 vulnerabilidades lógicas** que distorcem os números apresentados ao motorista e **6 oportunidades de IA on-device** que podem ser implementadas sem nuvem e sem impacto em performance.

---

## Vulnerabilidades Encontradas

| # | Severidade | Módulo | Problema |
|---|-----------|--------|----------|
| 1 | **CRÍTICA** | FinanceSummaryTab | Lucro Líquido não subtrai despesas fixas/rateadas (IPVA, seguro, parcela) |
| 2 | **ALTA** | GoalsTab | Metas usam ganhos brutos em vez de lucro líquido |
| 3 | **ALTA** | ExpensesTab | Resumo por categoria soma TODAS as despesas sem filtro de período |
| 4 | **ALTA** | Recorrência | Despesas recorrentes cadastradas nunca geram instâncias automáticas |
| 5 | **MÉDIA** | getPeriodRange | Semana começa no domingo (locale pt-BR) em vez de segunda-feira |
| 6 | **MÉDIA** | ProjectionEngine | daysWithData usa dias corridos (sempre ~30) em vez de dias trabalhados |
| 7 | **BAIXA** | EarningsTab | Ganhos auto-importados podem ser editados/deletados manualmente |
| 8 | **BAIXA** | PDF Export | totalExpenses no relatório PDF é sempre zero (Flow nunca coletado) |

---

## Detalhamento das Vulnerabilidades

### 1. Lucro Líquido Inflado (CRÍTICA)

**Arquivo:** `FinanceActivity.kt` linha 182

**Problema:** O cálculo `netProfit = earnings - expenses` considera apenas despesas da tabela `expenses` (gastos avulsos). As despesas individuais rateadas (IPVA, seguro, parcela do carro) da tabela `individual_expenses` são completamente ignoradas.

**Impacto:** Um motorista que ganha R$200/dia mas tem R$2.500/mês em custos fixos (parcela + seguro + IPVA) vê lucro de R$200 quando na realidade é R$117/dia (R$200 - R$83/dia de fixos).

**Solução:** Subtrair `individualExpenseDao.getTotalMonthlyRatedSync()` proporcional ao período selecionado.

---

### 2. Metas Baseadas em Ganho Bruto (ALTA)

**Arquivo:** `FinanceActivity.kt` linhas 1186-1190

**Problema:** O progresso da meta é calculado como `totalEarnings / targetAmount`. Se o motorista define meta de "R$200 de lucro por dia", o sistema mostra 100% quando ele FATUROU R$200, ignorando que gastou R$80 em combustível.

**Solução:** Oferecer opção de meta por "Ganho Bruto" ou "Lucro Líquido" e calcular adequadamente.

---

### 3. Despesas Sem Filtro de Período (ALTA)

**Arquivo:** `FinanceActivity.kt` linha 641

**Problema:** `allExpenses.groupBy { it.category }` soma TODAS as despesas desde o início dos tempos. O resumo "Despesas por Categoria" não respeita nenhum filtro temporal.

**Solução:** Usar `getExpensesByPeriod(startDate, endDate)` com o período selecionado pelo usuário.

---

### 4. Recorrência Não Gera Instâncias (ALTA)

**Arquivo:** `FinanceDatabase.kt` — campos `parentExpenseId`, `isGenerated`, `countGeneratedForDay`

**Problema:** O sistema tem toda a infraestrutura para gerar instâncias de despesas recorrentes (campos no DB, query de verificação de duplicata), mas não existe nenhum Worker/Scheduler que execute a geração diária.

**Impacto:** Motorista cadastra "Alimentação R$30/dia, seg-sex" mas o total de despesas fica zerado nos dias seguintes.

**Solução:** Implementar um `RecurringExpenseWorker` que roda 1x/dia via WorkManager e gera as instâncias pendentes.

---

### 5. Semana Começa no Domingo (MÉDIA)

**Arquivo:** `FinanceActivity.kt` linha 1338

**Problema:** `cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)` — em pt-BR, `firstDayOfWeek` é domingo. Motoristas brasileiros consideram segunda-feira como início da semana de trabalho.

**Solução:** Usar `Calendar.MONDAY` fixo ou permitir configuração pelo usuário.

---

### 6. Projeção Diluída por Dias Não-Trabalhados (MÉDIA)

**Arquivo:** `ProjectionEngine.kt` linhas 59-61

**Problema:** `daysWithData` calcula `(endDate - startDate30) / 86_400_000` que é sempre ~30. Se o motorista trabalhou apenas 15 dos 30 dias, a média diária fica na metade do real.

**Solução:** Contar dias distintos com pelo menos 1 corrida registrada (via query SQL `COUNT(DISTINCT date(timestamp/1000, 'unixepoch'))`).

---

### 7. Ganhos Auto-Importados Editáveis (BAIXA)

**Arquivo:** `FinanceActivity.kt` linha 366

**Problema:** O botão de editar/deletar aparece para todos os ganhos, inclusive os auto-importados. Se o motorista deletar um ganho auto-importado, o `rideHistoryId` perde o vínculo e a corrida pode ser re-importada como duplicata.

**Solução:** Esconder botão de delete para `isAutoImported = true` ou mostrar confirmação especial.

---

### 8. PDF Export com Despesas Zeradas (BAIXA)

**Arquivo:** `FeaturesActivity.kt` ~linha 352

**Problema:** O código inicializa `var total = 0.0` e chama `getExpensesByPeriod()` que retorna um Flow, mas nunca coleta o valor. O PDF sempre mostra despesas = R$0.00.

**Solução:** Usar a versão `suspend` da query ou coletar o Flow com `.first()`.

---

## Arquitetura de IA On-Device Proposta

### Princípios

| Princípio | Implementação |
|-----------|---------------|
| Zero nuvem | Tudo roda local via SQL + Kotlin puro |
| Zero biblioteca extra | Sem TFLite, sem ONNX — apenas aritmética |
| Zero impacto em RAM | Queries SQL agregam no banco (C++ nativo) |
| Zero impacto em bateria | Cálculos sob demanda (não em background) |

### Funcionalidades Inteligentes Propostas

#### F1. DRE Automático (Demonstrativo de Resultados)

Estrutura contábil adaptada para motorista de app:

```
(+) Receita Bruta (ganhos de todas as plataformas)
(-) Custos Variáveis (combustível por km rodado)
(=) Margem de Contribuição
(-) Custos Fixos Rateados (IPVA/12, seguro/12, parcela)
(-) Desgaste do Veículo (pneus, óleo, pastilhas proporcional)
(=) Lucro Operacional
(-) Depreciação (valor do carro / 200.000 km)
(=) Lucro Real
```

**Técnica:** SQL Aggregation puro — uma única query com JOINs.

#### F2. Detecção de Anomalias em Gastos (EWMA + Z-Score)

Detecta gastos fora do padrão por categoria usando o mesmo EWMA do módulo de IA:

- Calcula média móvel exponencial dos últimos 30 dias por categoria
- Se gasto do dia > média + 2σ → alerta: "Combustível 40% acima da média"
- Se gasto semanal < média - 2σ → alerta positivo: "Parabéns! Gastos 30% menores"

**Técnica:** EWMA (α=0.3) + desvio padrão via SQL `AVG()` e `SUM((x-avg)²)`.

#### F3. Break-Even Calculator (Ponto de Equilíbrio)

Calcula quantas corridas/km faltam para cobrir os custos fixos do mês:

```
custos_fixos_restantes = total_fixo_mes - (dias_passados / 30 * total_fixo_mes)
corridas_necessarias = custos_fixos_restantes / lucro_medio_por_corrida
```

**Técnica:** Aritmética simples sobre dados já disponíveis no banco.

#### F4. Projeção com Sazonalidade (Dia da Semana)

Em vez de média simples dos 30 dias, usa fator multiplicador por dia da semana:

- Segunda: 0.8x (dia fraco)
- Sexta/Sábado: 1.3x (dias fortes)
- Domingo: 0.6x (dia fraco)

Fatores calculados automaticamente a partir do histórico real do motorista.

**Técnica:** `GROUP BY strftime('%w', timestamp/1000, 'unixepoch')` no SQL.

#### F5. Alerta de Rentabilidade por Corrida

Ao registrar ganho auto-importado, calcular imediatamente:

```
custo_real = (distancia_total * custo_por_km) + (duracao * custo_por_hora_fixo)
lucro_real = valor_corrida - custo_real
```

Se `lucro_real < 0` → marcar corrida como "prejuízo" e alertar.

**Técnica:** Cálculo inline no momento do auto-import (já existe o hook).

#### F6. Previsão de Manutenção Inteligente

Baseada no odômetro atual + km/dia médio do motorista:

```
km_ate_troca_oleo = (oilChangeKm - (currentOdometer % oilChangeKm))
dias_ate_troca = km_ate_troca_oleo / avg_km_por_dia
```

Gera notificação: "Troca de óleo em ~12 dias (baseado nos seus 95km/dia)"

**Técnica:** Aritmética sobre `VehicleProfileEntity` + média de km dos últimos 7 dias.

---

## Plano de Implementação (Priorizado)

| Fase | Ação | Impacto | Esforço |
|------|------|---------|---------|
| 1 | Corrigir Lucro Líquido (incluir fixos rateados) | CRÍTICO | Baixo |
| 2 | Implementar RecurringExpenseWorker | ALTO | Médio |
| 3 | Corrigir ExpensesTab (filtro de período) | ALTO | Baixo |
| 4 | Corrigir getPeriodRange (segunda-feira) | MÉDIO | Baixo |
| 5 | Corrigir ProjectionEngine (dias trabalhados) | MÉDIO | Baixo |
| 6 | Implementar DRE automático no Resumo | ALTO | Médio |
| 7 | Implementar Break-Even Calculator | MÉDIO | Baixo |
| 8 | Implementar Detecção de Anomalias | MÉDIO | Médio |
| 9 | Implementar Projeção com Sazonalidade | MÉDIO | Médio |
| 10 | Corrigir PDF Export | BAIXO | Baixo |

---

## Conclusão

O módulo de Finanças tem uma base sólida mas apresenta distorções numéricas que podem levar o motorista a decisões financeiras erradas. A vulnerabilidade mais grave é o Lucro Líquido inflado que ignora custos fixos — isso faz o motorista acreditar que está lucrando mais do que realmente está. As soluções propostas são todas implementáveis localmente, sem dependência de nuvem, usando apenas SQL e aritmética Kotlin, mantendo o app leve e rápido em celulares médios.
