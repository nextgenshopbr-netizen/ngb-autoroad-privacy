# Auditoria Avançada de Lógica — NGB AutoRoad v6.3.4

Esta auditoria foi realizada módulo a módulo para identificar vulnerabilidades lógicas, gargalos de concorrência e pontos de melhoria no ecossistema do aplicativo. O relatório detalha os achados e propõe soluções arquiteturais para garantir a estabilidade e precisão do sistema.

## 1. Módulos de Model e Data (Banco de Dados e Entidades)

### 1.1. Inconsistências de Agregação no `RideHistoryDao`
- **Vulnerabilidade:** Várias queries de agregação para o Dashboard (ex: `averageScoreSince`, `countSinceFlow`) **não filtram pelo status da corrida**. Isso significa que corridas `PENDING`, `REFUSED` ou `EXPIRED` estão inflando as médias de score e contagens totais, distorcendo a realidade do motorista.
- **Melhoria:** Adicionar `AND (status = 'COMPLETED' OR status = 'ACCEPTED')` em todas as queries agregadoras, assim como já é feito em `totalEarningsSince`.

### 1.2. Modelagem Financeira Frágil
- **Vulnerabilidade:** O banco de dados financeiro (`FinanceDatabase`) usa `Double` para todos os valores monetários. Isso é suscetível a erros de arredondamento em ponto flutuante, especialmente em cálculos de projeção e rateio. Além disso, não há restrições de chave estrangeira (`@ForeignKey`) entre `EarningEntity` e `RideHistoryEntity`, permitindo inconsistências se o histórico for limpo.
- **Melhoria:** Migrar campos monetários para `Long` (representando centavos) ou usar `BigDecimal`. Implementar chaves estrangeiras com `onDelete = CASCADE` para garantir integridade referencial.

### 1.3. Gestão de Veículos Ativos
- **Vulnerabilidade:** A função `switchActiveVehicle` no `VehicleProfileDao` desativa todos os veículos e ativa um novo, mas a query `getActiveVehicleSync` retorna apenas o primeiro (`LIMIT 1`). Se houver falha na transação, múltiplos veículos podem ficar ativos, causando cálculos imprevisíveis de custos na projeção.
- **Melhoria:** Adicionar uma restrição de unicidade condicional no banco de dados ou reforçar a lógica da transação para garantir que exatamente um veículo esteja ativo.

## 2. Módulos de Domain/Engine (Lógica de Negócios)

### 2.1. Inflação de Corridas na Projeção (`ProjectionEngine`)
- **Vulnerabilidade:** A lógica de simulação `simulateWhatIf` usa `allRides.size * multiplier` para projetar o total de corridas, mas o multiplicador é fracionário para períodos menores que 30 dias (ex: 7.0 / 30.0). O arredondamento `.toInt().coerceAtLeast(1)` pode gerar resultados distorcidos para dias fracos.
- **Melhoria:** Usar projeção estatística baseada em horas ativas em vez de simplesmente ratear o total de corridas do mês, garantindo granularidade mais realista.

### 2.2. Concorrência no Turno (`ShiftManager`)
- **Vulnerabilidade:** O `ShiftManager` lê e grava no `SharedPreferences` de forma não atômica. Se múltiplas corridas forem aceitas rapidamente (ex: auto-aceite simultâneo em dois apps), a leitura/gravação pode sobrescrever valores de `totalEarned`.
- **Melhoria:** Migrar o estado do turno para o Room Database com transações atômicas ou usar `DataStore` com Mutex.

### 2.3. Cache Fixo na IA Local (`LocalLearningEngine`)
- **Vulnerabilidade:** O `LocalLearningEngine` carrega dados do banco uma vez via `seedFromDatabase` e os mantém em memória. Se novas corridas forem concluídas durante a sessão, a IA não as considera até que o app seja reiniciado.
- **Melhoria:** Inscrever a engine no `getAllFlow()` do `RideHistoryDao` para reatividade em tempo real, eliminando a dependência do cache estático.

## 3. Módulos de Service (Ciclo de Vida e Acessibilidade)

### 3.1. Condição de Corrida no `RideLifecycleManager`
- **Vulnerabilidade:** O `RideLifecycleManager` atualiza o banco de dados usando `firstOrNull { it.id == currentRideDbId }` em uma lista puxada do banco (`dao.getAll()`). Isso é extremamente ineficiente e propenso a race conditions, pois lê toda a tabela de histórico em memória a cada transição de fase (ACCEPTED, COMPLETED, etc).
- **Melhoria:** Criar um método `updateStatus(id: Long, status: String)` no DAO para atualização direta no banco de dados via SQL.

### 3.2. Falso-Positivo no `OverlayService`
- **Vulnerabilidade:** A deduplicação no `OverlayService` baseia-se em `platform + rideValue + dropoffDistance`. Corridas curtas idênticas (ex: valor mínimo na mesma plataforma) podem ser ignoradas como duplicatas, mesmo sendo corridas diferentes.
- **Melhoria:** Incluir o timestamp arredondado (ex: janela de 1 minuto) e a distância de embarque no hash de deduplicação.

### 3.3. Conflito de Notificação UNCERTAIN
- **Vulnerabilidade:** O `RideLifecycleManager` cria a notificação de timeout com ID `9001`, mas o `UncertainReceiver` tenta cancelá-la usando o ID `9999`. A notificação permanece na tela mesmo após o motorista responder.
- **Melhoria:** Unificar as constantes de ID de notificação em um arquivo compartilhado (ex: `Constants.kt`).

## 4. Módulos de UI (Dashboard e Apresentação)

### 4.1. Duplicação Lógica no `DashboardTab`
- **Vulnerabilidade:** O `DashboardTab` recalcula médias e somas em memória (`allRides.filter { ... }.sumOf { ... }`) em vez de usar as queries otimizadas do `RideHistoryDao` (ex: `totalEarningsSince`). Isso causa lentidão na UI quando o histórico cresce.
- **Melhoria:** Consumir os Flows agregados diretamente do DAO (ex: `totalEarningsSinceFlow`) para delegar o trabalho pesado ao SQLite.

### 4.2. UI Blocking na Aba de IA
- **Vulnerabilidade:** A `LearningTab` instancia o `LocalLearningEngine` e aplica um `delay(500)` fixo para aguardar o `seedFromDatabase`. Se o banco estiver lento, a UI exibe dados vazios; se for rápido, trava a thread por 500ms desnecessariamente.
- **Melhoria:** Expor o estado de carregamento do `LocalLearningEngine` via `StateFlow` e exibir um shimmer/loading na UI até que os dados estejam prontos.

## 5. Resumo de Prioridades de Correção

1. **CRÍTICA:** Corrigir as queries de leitura completa (`getAll()`) no `RideLifecycleManager` (causará OOM e travamentos com o tempo).
2. **ALTA:** Corrigir o ID da notificação no `UncertainReceiver` (ID 9001 vs 9999).
3. **ALTA:** Aplicar filtro de status (`ACCEPTED/COMPLETED`) nas métricas do Dashboard e nas queries do `RideHistoryDao`.
4. **MÉDIA:** Mover cálculos pesados do `DashboardTab` para o Room Database.
5. **MÉDIA:** Implementar Mutex no `ShiftManager` para evitar perda de ganhos em corridas simultâneas.
