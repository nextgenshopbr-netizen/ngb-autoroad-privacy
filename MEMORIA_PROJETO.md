# MEMÓRIA DO PROJETO — NGBAutoRoad

## Histórico de Alterações

---

### 2026-06-18 08:00 — v4.2.1
- PIN padrão fixo alterado de 147258 para **250696**
- Botão "Alterar PIN" visível no topo do painel admin
- UberStyleCard no simulador (inspirado no GigU): 4 métricas com barras de cor
- Correção: `.start`/`.endInclusive` em vez de `.first`/`.second` para ranges

### 2026-06-18 09:30 — v4.2.2
- Adicionado enum `RideType` (UberX, Comfort, Black, Flash, Green, Prioridade, 99Pop, 99Comfort, inDrive, Cabify)
- Campo `rideType` no `RideData`
- Parser do AccessibilityService detecta tipo de corrida pelo badge
- Simulador admin gera tipos de corrida aleatórios por plataforma
- Card padrão NGBAutoRoad (id=0) adicionado como primeiro da galeria
- Default ao resetar = card padrão (id=0)

### 2026-06-18 10:30 — v4.3.0
- **Card Overlay melhorias:**
  - Bordas dinâmicas por score (verde ≥70, amarelo ≥50, laranja ≥30, vermelho <30)
  - Drag livre com limites de tela
  - Pinch-to-zoom (0.7x a 2.0x) respeitando bordas do celular
  - Acessibilidade A+/A- (10sp a 24sp)
- **Controle Financeiro:**
  - Nova aba "Veículos" — múltiplos veículos, veículo ativo, dados de desgaste
  - Nova aba "Despesas" — despesas individuais com rateio temporal e toggle
  - Nova aba "Projeção" — algoritmo inteligente com filtros diário/semanal/mensal/anual
  - Simulação "E se?" — cenários (todas boas/médias/ruins/mescla ideal)
- DB migração v3→v4 automática

### 2026-06-18 11:30 — v4.3.1
- Galeria de cards reescrita: 35 cards totais (25 melhorados + 10 novos)
- 7 categorias: Completo, Padrão, Financeiro, Localização, Compacto, Minimalista, Temático
- Todos os cards com RIDE_TYPE, bairros, métricas relevantes

### 2026-06-18 12:55 — v4.3.2 (Correções Pós-Simulação Avançada)
- **Simulação avançada executada:** 165 testes, 159 aprovados, 0 falhas
- Correções aplicadas:
  1. Truncamento de bairros longos (TextOverflow.Ellipsis + maxLines=1)
  2. Spacer entre label e valor no OverlayCriteriaRow
  3. Labels traduzidos para PT-BR: "Bairro Embarque", "Bairro Destino", "Duração"
  4. RIDE_TYPE adicionado nos cards minimalistas 22 e 24
  5. Import de TextOverflow adicionado no OverlayCard.kt

---

## Arquitetura Atual

| Módulo | Arquivo | Responsabilidade |
|--------|---------|-----------------|
| Model | RideData.kt | RideData, RideType enum, RideScore |
| Model | CardGallery.kt | 35 cards, CardField enum, categorias |
| Service | RideAccessibilityService.kt | Parser de corridas (Uber, 99, inDrive, Cabify) |
| Service | OverlayService.kt | Overlay flutuante com drag/pinch |
| Service | OverlayCard.kt | Composable do card visual |
| Domain | RideScorer.kt | Cálculo de score |
| Domain | ProjectionEngine.kt | Projeção financeira e simulação "E se?" |
| DB | FinanceDatabase.kt | Room DB com EarningEntity, VehicleProfile, IndividualExpense |
| DB | FinanceExtensions.kt | DAOs para veículos e despesas |
| UI | AdminActivity.kt | Painel admin com simulador |
| UI | FinanceActivity.kt | Controle financeiro (abas) |
| UI | FinanceExtTabs.kt | Abas: Veículos, Despesas, Projeção |
| Prefs | PrefsManager.kt | DataStore com PIN, critérios, thresholds |
