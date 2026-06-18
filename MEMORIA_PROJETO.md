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

### 2026-06-18 13:14 — v4.3.3 (Simulador Real + Teste de Card Flutuante)
- **Simulador Admin agora dispara overlay REAL:**
  - Ao clicar Boa/Média/Ruim/Aleatória, o OverlayService é iniciado
  - RideData real é enviado via `OverlayService.onRideDetected?.invoke(rideData)`
  - O card de análise aparece flutuante na tela exatamente como aconteceria com corrida real
  - Fluxo end-to-end: dados simulados → RideScorer → OverlayCard real
  - Removido UberStyleCard estático (agora é o overlay real que aparece)
  - SimulationDetailsCard mantido para ver dados internos
- **Botão "Testar Real" no editor de cards:**
  - Gera corrida aleatória e dispara overlay flutuante real
  - Motorista pode testar drag, pinch, posição, tamanho do card
  - Funciona exatamente como GigU no preview de cards
- **Função toRideData()** adicionada ao SimulationResult para conversão
- Versão 4.3.3 (versionCode 51)

### 2026-06-18 14:42 — v4.4.0 (Correções de Bugs + Melhorias de Segurança)
- **Correções de Bugs Críticos:**
  1. Simulação não computa dados reais — campo `isSimulation` no RideData, OverlayService verifica antes de salvar
  2. Crash ao Testar Real no CardTab — try-catch + verificação `canDrawOverlays`
  3. Tela ligada não funciona em tempo real — `keepScreenOnFlow` observado reativamente com `collectAsState`
  4. Overlay auto-iniciar — MainActivity inicia OverlayService automaticamente se habilitado
  5. Crash na Projeção sem dados — mensagem amigável em vez de crash
- **Melhorias:**
  1. ChangePinDialog com validação de PIN atual (3 campos: atual, novo, confirmar)
  2. Tipo de veículo no cadastro (Combustão/Híbrido/Elétrico) + tipo combustível
  3. Ícones por categoria de despesa (IPVA=Receipt, Seguro=Shield, etc.)
  4. Proteção contra engenharia reversa (ProGuard/R8 rules completas)
  5. Cabeçalhos em CardEditorActivity.kt e CardGalleryActivity.kt
  6. Warning do parâmetro `currentPin` corrigido
- Versão 4.4.0 (versionCode 52)
