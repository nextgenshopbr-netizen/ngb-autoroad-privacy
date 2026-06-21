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

---

## v4.5.0 — 18/06/2026 15:25

### 10 Novas Funcionalidades (100% Local, sem nuvem/IA paga):

1. **Modo Turno** (ShiftManager.kt) — Iniciar/pausar/encerrar turno com timer, meta de ganho, alerta ao atingir, persistência via SharedPreferences
2. **Ranking de Bairros** (NeighborhoodRanker.kt) — Classificar bairros por R$/km, R$/hora ou total de corridas
3. **Integração Waze/Google Maps** (NavigationHelper.kt) — Deep link com fallback (Waze → Maps → genérico)
4. **Compartilhamento de Critérios** (CriteriaShareManager.kt) — Export/import JSON com validação e FileProvider
5. **Modo Economia de Bateria** (BatteryOptimizer.kt) — Normal/ECO/Ultra-ECO com polling adaptativo
6. **Alertas de Surge/Demanda** (SurgeDetector.kt) — Heurística local baseada em R$/km recente vs baseline
7. **Multi-idioma** — strings.xml PT-BR + ES (espanhol para motoristas na fronteira)
8. **Relatório PDF** (ReportGenerator.kt) — Gerar PDF nativo Android sem dependência de nuvem
9. **Exportar CSV** (DataExporter.kt) — UTF-8 BOM, escape correto, compartilhar via intent
10. **Modo Aprendizado LOCAL** (LocalLearningEngine.kt) — Padrões estatísticos (melhores horários, bairros, fadiga)

### UI:
- FeaturesActivity.kt com 5 abas: Turno, Ranking, IA Local, Relatório, Exportar
- Registrada no AndroidManifest.xml

### Versão: 4.5.0 (versionCode 53)

---

## v5.0.0 — 18/06/2026 16:00
### Correções de Lógica Aplicadas (36 correções em 14 módulos):

**RideScorer.kt:** Guard divisão por zero em 6 funções normalize + escala rating 3.0-5.0

**RideAccessibilityService.kt:** maxDepth=10 no traverseNode + pickupNeighborhood no hash

**OverlayService.kt:** Auto-dismiss timer (30s), remove overlay antes de recriar

**ProjectionEngine.kt:** Guard daysWithData dinâmico + averageOrZero no cenário Mescla

**ShiftManager.kt:** Guard turno duplicado

**LocalLearningEngine.kt:** Persistência SharedPreferences + feedback dados insuficientes

**PrefsManager.kt:** Novo autoDismissSecondsFlow (0-120s)

**NavigationHelper.kt:** FLAG_ACTIVITY_NEW_TASK em todos os intents

**DataExporter.kt:** FLAG_ACTIVITY_NEW_TASK no shareFile

**BatteryOptimizer.kt:** Thresholds ECO=30%, ULTRA_ECO=15%

**MainActivity.kt:** lifecycleScope.launch substitui runBlocking (evita ANR)

**AdminActivity.kt:** Validação PIN mínimo 4 dígitos

**FinanceExtTabs.kt:** Guard installments≥1 + rateio anual (total/12)

**build.gradle.kts:** versionCode=54, versionName="5.0.0"

### Status: BUILD SUCCESSFUL, Push + Tag v5.0.0 no GitHub
### Versão: 5.0.0 (versionCode 54)

---

## v5.2.2 — 18/06/2026 17:00
- Base compilada com mudanças iniciais
- versionCode=25, versionName="5.2.2"

## v5.2.3 — 18/06/2026 18:00
- Unificação Gastos→Despesas no módulo financeiro
- Logs de debug: NGB_PROJECAO, NGB_TESTAR_CARD, NGB_SIMULACAO
- Meta obrigatória ao iniciar turno (dialog no DashboardTab)
- Multi-idiomas: PT-BR (default), ES (values-es/strings.xml)
- Campo Min→H:m no financeiro
- versionCode=26, versionName="5.2.3"

## v5.2.4 — 18/06/2026 18:30
- Fix crash Compose BOM (NoSuchMethodError KeyframesSpec)
- Downgrade BOM de 2024.01.00 para 2023.10.01
- versionCode=27, versionName="5.2.4"

## v5.2.5 — 18/06/2026 19:00
- Fix crash Cards — adicionado ViewModelStoreOwner ao OverlayService
- OverlayService agora implementa: LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner
- versionCode=28, versionName="5.2.5"

## v5.2.6 — 18/06/2026 19:30
- Fix Simulação Admin — usa applicationContext em vez de Activity context
- Retry automático 3s ao falhar
- Toast de confirmação ao simular
- versionCode=29, versionName="5.2.6"

## v5.2.7 — 18/06/2026 20:00
- Fix ClassCastException ContextThemeWrapper→Activity no OverlayService
- versionCode=30, versionName="5.2.7"

## v5.2.8 — 18/06/2026 20:30
- Fix DEFINITIVO do overlay — causa raiz em Theme.kt linha 67
- Corrigido: `(view.context as Activity).window` → `(view.context as? Activity)?.window`
- versionCode=31, versionName="5.2.8"

## v5.2.9 — 18/06/2026 21:00
- Fix A+/A- acessibilidade — conectado callback onFontScaleChange no OverlayService
- Limites de escala: 1.0f-2.5f
- PrefsManager: saveOverlayFontScale com coerceIn
- versionCode=31, versionName="5.2.9"

## v5.3.0 — 18/06/2026 21:30
- Botões A-/A+/X reorganizados (tentativa 1)
- Posicionados no canto superior direito com Row + Alignment.TopEnd
- Problema: ainda sobrepunham o Score
- versionCode=31, versionName="5.3.0"

## v5.3.1 — 18/06/2026 22:00
- Botões A-/A+/X no canto superior direito com Box/Alignment.TopEnd
- Score com padding-end=80.dp para evitar sobreposição
- Problema: padding fixo não funciona bem em todos os cards
- versionCode=32, versionName="5.3.1"

## v5.3.2 — 19/06/2026 00:00
- **SOLUÇÃO DEFINITIVA**: Barra de título SEPARADA acima do card (estilo Windows)
- Estrutura: Column > [Row(barra título com A−/A+/✕)] + [Box(card conteúdo)]
- Barra usa borderRadius no topo, card usa borderRadius na base → visual integrado
- Score não precisa mais de padding extra — sem sobreposição
- Funciona em TODOS os modelos de card da galeria automaticamente
- Fonte negrito com espaçamento generoso nos botões
- versionCode=33, versionName="5.3.2"

---

## Arquitetura Atual (v5.3.2)

| Camada | Arquivo | Responsabilidade |
|--------|---------|-----------------|
| App | NGBAutoRoadApp.kt | Application class |
| Model | RideData.kt | RideData, RideType enum, RideScore, ScoreLevel |
| Model | CardGallery.kt | 35 cards, CardField enum, categorias |
| Model | FinanceModels.kt | Modelos financeiros |
| DB | FinanceDatabase.kt | Room DB (EarningEntity, VehicleProfile, IndividualExpense) |
| DB | FinanceExtensions.kt | DAOs para veículos e despesas |
| DB | RideHistoryEntity.kt | Histórico de corridas |
| Service | RideAccessibilityService.kt | Parser de corridas (Uber, 99, inDrive, Cabify) |
| Service | OverlayService.kt | Overlay flutuante (Lifecycle/ViewModel/SavedState owners) |
| Service | OverlayCard.kt | Composable do card visual + barra de título |
| Service | OcrCaptureService.kt | Captura OCR |
| Service | BubbleService.kt | Bubble flutuante |
| Domain | RideScorer.kt | Cálculo de score multi-critério |
| Domain | ProjectionEngine.kt | Projeção financeira e simulação "E se?" |
| Domain | ShiftManager.kt | Gestão de turnos |
| Domain | LocalLearningEngine.kt | Aprendizado local (padrões estatísticos) |
| Domain | NeighborhoodRanker.kt | Ranking de bairros |
| Domain | NavigationHelper.kt | Integração Waze/Google Maps |
| Domain | CriteriaShareManager.kt | Export/import critérios JSON |
| Domain | BatteryOptimizer.kt | Modo economia de bateria |
| Domain | SurgeDetector.kt | Detecção de surge/demanda |
| Domain | ReportGenerator.kt | Relatório PDF |
| Domain | DataExporter.kt | Exportar CSV |
| UI | MainActivity.kt | Tela principal com abas |
| UI | AdminActivity.kt | Painel admin + simulador |
| UI | FinanceActivity.kt | Controle financeiro |
| UI | FinanceExtTabs.kt | Abas: Projeção, Despesas individuais |
| UI | DashboardTab.kt | Dashboard + turno + meta obrigatória |
| UI | CardTab.kt | Aba de cards |
| UI | CriteriaTab.kt | Configuração de critérios |
| UI | HistoryTab.kt | Histórico |
| UI | SettingsTab.kt | Configurações + seletor idioma |
| UI | CardEditorActivity.kt | Editor de cards |
| UI | CardGalleryActivity.kt | Galeria de cards |
| UI | FeaturesActivity.kt | Features extras (Turno, Ranking, IA, Relatório, Export) |
| UI | ZoneMapActivity.kt | Mapa de zonas |
| Theme | Theme.kt | Tema (fix: safe cast Activity) |
| Theme | Color.kt | Cores do app |
| Theme | Typography.kt | Tipografia |
| Util | Extensions.kt | Extensões Kotlin |
| Prefs | PrefsManager.kt | DataStore (PIN, critérios, idioma, fontScale) |

### Configuração de Build
- **compileSdk**: 34 | **targetSdk**: 34 | **minSdk**: 26
- **Compose BOM**: 2023.10.01 (downgraded para compatibilidade)
- **Kotlin**: 1.9.22 | **KSP** habilitado
- **Gradle**: 8.4 | **JDK**: 17
- **Keystore**: keystore/ngbautoroad-release.jks (incluído no repo)

### Idiomas Suportados
- Português (BR) — default (values/strings.xml)
- Espanhol — values-es/strings.xml
- Inglês — strings hardcoded no código (fallback)

---

## 19/06/2026 — v6.0.0: REESCRITA TOTAL DO ENGINE

### Problema
1. AccessibilityService não detectava corridas da Uber (árvore vazia, throttle alto, Compose não expõe nós)
2. Bancos fechavam ao detectar AccessibilityService ativo

### Pesquisa Realizada
- Engenharia reversa do StopClub (não tem AccessibilityService próprio, usa Macrodroid externo)
- Análise do GigU (concorrente direto, usa AccessibilityService puro, reescreveu engine na v1.2.54)
- Pesquisa sobre accessibilityDataSensitive (Android 15), WRITE_SECURE_SETTINGS, Shizuku
- Análise de como bancos detectam: Settings.Secure, overlay detection, canRetrieveWindowContent

### Solução Implementada: Triple Engine + Ghost Mode

#### Triple Engine (RideAccessibilityService.kt — reescrita total)
- **Camada 1**: getWindows() como fonte PRIMÁRIA + zero throttle + profundidade 50 + hintText
- **Camada 2**: takeScreenshot() + ML Kit OCR (Android 11+, silencioso, sem permissão extra)
- **Camada 3**: RideNotificationListener (novo serviço, backup durante Ghost Mode)

#### Ghost Mode (Stealth Bancário Automático)
- Nível 1: Remove overlay/bubble em <100ms
- Nível 2: Hiberna serviceInfo (packageNames=[placeholder], eventTypes=WINDOW_STATE only)
- Nível 3: UsageStatsManager polling (2s) + timeout 5min
- 30+ bancos brasileiros na lista
- Zero interação do motorista (100% automático)

#### Bug Fix
- Editor de cards: ao fechar overlay de teste, app volta ao foco (antes minimizava)

### Arquivos Modificados
- `RideAccessibilityService.kt` — reescrita total (750+ linhas)
- `RideNotificationListener.kt` — NOVO (Camada 3)
- `OverlayService.kt` — hideOverlay traz app de volta em simulação
- `NGBAutoRoadApp.kt` — canal CHANNEL_GHOST
- `AndroidManifest.xml` — NotificationListener + PACKAGE_USAGE_STATS + VIBRATE
- `accessibility_service_config.xml` — canTakeScreenshot + typeViewScrolled + timeout=0
- `build.gradle.kts` — v6.0.0 (versionCode 44)

### Release
- GitHub: https://github.com/nextgenshopbr-netizen/ngb-autoroad-privacy/releases/tag/v6.0.0
- APK: app-release.apk (42.8 MB)

---

## 19/06/2026 — v6.1.0: AutoPilot + RideLifecycleManager + Bug Fix Ganhos + Perfis

### Problema Resolvido
1. **Bug crítico de ganhos inflados**: OverlayService registrava ganho no momento da detecção, mesmo sem motorista aceitar a corrida
2. Motorista não tinha opção de aceitar/recusar automaticamente baseado no score
3. Não havia como salvar/carregar configurações diferentes (dia vs noite)
4. Não havia rastreamento do ciclo completo da corrida (aceitação → conclusão)

### Solução: Opção C — Híbrido Automático com Confirmação

#### RideLifecycleManager (NOVO)
- Ciclo de vida completo: PENDING → ACCEPTED → COMPLETED/CANCELLED/UNCERTAIN
- Detecta aceitação via textos ("A caminho", "Dirigir até")
- Detecta conclusão via textos ("Viagem concluída", "Avalie o passageiro")
- Detecta cancelamento via textos ("Cancelada", "Cancelled")
- Timeout de 20s para aceitação → EXPIRED se card sumiu
- Timeout de 5min para conclusão → UNCERTAIN → notificação para motorista confirmar
- Registra ganho APENAS quando fase = COMPLETED
- Reversão automática de ganho se corrida cancelada após aceitação

#### AutoPilotEngine (NOVO)
- 4 modos: OFF, ACCEPT_ONLY, REFUSE_ONLY, FULL
- Delay humanizado por faixa de score:
  - Score 90-100: Aceita em 1-2s (corrida excelente)
  - Score 75-89: Aceita em 3-5s (corrida boa)
  - Score 60-74: ZONA NEUTRA — não age
  - Score 40-59: Recusa em 4-6s (corrida ruim)
  - Score 0-39: Recusa em 1-2s (corrida péssima)
- Click automático via AccessibilityService (findAndClickButton)
- Respeita filtros geográficos (bairros/zonas bloqueadas)

#### UncertainReceiver (NOVO)
- BroadcastReceiver para ações da notificação UNCERTAIN
- Botões [Sim, concluída] [Não, cancelada] na notificação

#### Bug Fix: Ganhos
- OverlayService agora salva corrida como PENDING (não registra ganho)
- Ganho só é registrado pelo RideLifecycleManager.onRideCompleted()
- Verificação de duplicatas antes de inserir ganho

#### Sistema de Perfis
- Até 5 perfis salvos (ex: Dia, Noite, Fim de semana)
- Cada perfil guarda: CriteriaWeights, DriverThresholds, modo AutoPilot, scores
- Carregar/excluir perfil com 1 toque
- Serialização JSON via kotlinx.serialization

#### UI (CriteriaTab)
- Seção AutoPilot: seletor de modo (FilterChips), sliders de score, checkbox geo
- Seção Perfis: lista de perfis salvos, botão salvar, dialog com nome
- Card de aviso de zona neutra e aviso de segurança

### Arquivos Modificados (13 arquivos, +1631 linhas)
- `service/RideLifecycleManager.kt` — NOVO (570+ linhas)
- `service/AutoPilotEngine.kt` — NOVO (310+ linhas)
- `service/UncertainReceiver.kt` — NOVO (55 linhas)
- `service/OverlayService.kt` — Bug fix: PENDING + integração lifecycle/autopilot
- `service/RideAccessibilityService.kt` — Instancia lifecycle/autopilot + monitoramento pós-detecção
- `data/model/RideData.kt` — Novos status: PENDING, COMPLETED, UNCERTAIN
- `data/db/RideHistoryEntity.kt` — Queries atualizadas (COMPLETED OR ACCEPTED)
- `data/db/FinanceDatabase.kt` — deleteByRideHistoryId
- `data/prefs/PrefsManager.kt` — Chaves AutoPilot + Perfis
- `ui/criteria/CriteriaTab.kt` — AutoPilotSection + ProfilesSection + SavedProfile
- `NGBAutoRoadApp.kt` — Canal CHANNEL_LIFECYCLE
- `AndroidManifest.xml` — UncertainReceiver
- `build.gradle.kts` — versionCode=45, versionName=6.1.0

### Release
- GitHub: https://github.com/nextgenshopbr-netizen/ngb-autoroad-privacy/releases/tag/v6.1.0

---

## v6.1.1 — Correções da Auditoria + Melhorias UX (19/06/2026)

### Correções da Auditoria:
- **DashboardTab/HistoryTab**: Filtros de ganhos incluem status COMPLETED (antes só ACCEPTED = ganhos zerados)
- **OverlayService.hideOverlay()**: Agora notifica lifecycle via onOverlayDismissed() (corrida não fica presa em PENDING)
- **RideLifecycleManager**: Respeita toggle autoImportEarnings antes de registrar ganho
- **RideLifecycleManager**: Atualiza ShiftManager quando corrida é COMPLETED (turno mostra ganhos reais)
- **UncertainReceiver**: Fallback direto ao banco quando lifecycle não está disponível
- **Ghost Mode**: Consome pendingGhostRide ao desativar (corrida detectada durante banco não se perde)
- **RideNotificationListener**: Expõe instance estática para acesso ao pendingGhostRide

### Melhorias do Usuário:
1. **Perfis no topo** da tela de Critérios (acesso rápido em vez de no final)
2. **Seletor rápido de perfis na Dashboard** (antes de iniciar turno — motorista já seleciona perfil)
3. **AutoPilot multi-seleção**: Checkboxes independentes "Aceitar" e "Recusar" (ambos podem estar ativos)
4. **AutoPilot novos modos**: ACCEPT, REFUSE, BOTH (retrocompatível com legado ACCEPT_ONLY/REFUSE_ONLY/FULL)
5. **Backup & Restauração** movido para último card de Configurações

### Técnico:
- versionCode: 46 | versionName: 6.1.1
- 12 arquivos alterados, +248 -66 linhas
- Build: compilou com sucesso (R8 minificado, 41MB)

---

## v6.3.0 — Redesign + Tutorial + Zonas + Ranking Real (20/06/2026)

### Arquivos criados:
- `ui/finance/FinanceTab.kt` — Aba Financeiro inline na navegação principal
- `ui/tutorial/TutorialOverlay.kt` — Sistema de tutorial guiado com tooltips
- `ui/tutorial/HelpBottomSheet.kt` — Central de Ajuda com FAQ contextual

### Arquivos modificados:
- `ui/MainActivity.kt` — Nova navegação 5 abas (Critérios|Cards|INICIO|Financeiro|Config)
- `ui/dashboard/DashboardTab.kt` — Removidos botões topo, card perfis favoritos
- `ui/criteria/CriteriaTab.kt` — Dividido em 4 sub-abas
- `ui/settings/SettingsTab.kt` — Dividido em 3 sub-abas
- `ui/features/FeaturesActivity.kt` — Dados reais do banco (não mais mocks)
- `service/OverlayService.kt` — Integração zonas do mapa no Score
- `data/prefs/PrefsManager.kt` — Chaves tutorial + favoriteProfiles

### Funcionalidades:
- I1: Redesign completo com ícones Material Design profissionais
- I2: Tutorial guiado interativo no primeiro acesso
- I3: Central de Ajuda com ícone ? e bottom sheet
- I4: Mapa de Zonas funcional (penaliza Score real)
- I5: Ranking e Relatórios com dados reais do banco

---

## v6.3.1 — Fechamento da Categoria 2 do Roadmap (20/06/2026)

### I6 — Learning Engine com Dados Reais
- `seedFromDatabase()`: carrega histórico completo do banco (COMPLETED, ACCEPTED, REFUSED) ao inicializar
- Nova sugestão automática: bairros com 70%+ de recusa são sugeridos para bloqueio
- FeaturesActivity simplificado — sem loops manuais, engine alimentado automaticamente

### I7 — ProjectionEngine Refatorada
- `simulateWhatIf` filtra apenas corridas COMPLETED (não ACCEPTED)
- Removido `coerceAtLeast(1)` em `avgDailyRides` que inflava projeções
- Projeções financeiras agora refletem dados reais sem distorção

### I8 — Limpeza de Código
- Import `RidePattern` desnecessário removido do FeaturesActivity
- Deduplicação mantida por canal (necessário para fallback AAPM Android 17)
- Comentários de versão atualizados

### Status do Roadmap após v6.3.1
- Categoria 1 (Críticos): ✅ 5/5 concluídos
- Categoria 2 (Importantes): ✅ 8/8 concluídos
- Próxima: Categoria 3 — AppFunctions + Gemini (v7.0.0, Out 2026)

---
## v6.3.2 — Correções de Layout e Restauração de Recursos (20/06/2026)

### Arquivos modificados:
- `ui/MainActivity.kt` — Aba "Cards" substituída por "IA" na barra de navegação inferior; aba IA abre FeaturesActivity via Intent
- `ui/dashboard/DashboardTab.kt` — Restaurado card de acesso rápido "Recursos Avançados" (IA) com ícone SmartToy e seta
- `ui/finance/FinanceTab.kt` — Adicionada aba "Projeção" (5ª aba) usando ProjectionTab de FinanceExtTabs.kt; TabRow → ScrollableTabRow com maxLines=1
- `ui/criteria/CriteriaTab.kt` — Botão "Salvar Configuração Atual como Perfil" renomeado para "Novo Perfil"; adicionado botão "Salvar" para atualizar perfil ativo
- `ui/settings/SettingsTab.kt` — Adicionada sub-aba "Cards" (3ª posição) com CardTab; abas agora são App|Sistema|Cards|Adicionais

### Correções aplicadas:
1. **Barra de navegação**: Critérios | IA | Início | Financeiro | Config (Cards movido para Config)
2. **Botão IA na Home**: Card clicável com ícone SmartToy que abre FeaturesActivity
3. **Aba Projeção no Financeiro**: Restaurada usando ProjectionTab existente em FinanceExtTabs.kt
4. **Botões de perfil**: "Salvar" (atualiza perfil ativo) + "Novo Perfil" (cria novo) lado a lado
5. **Textos quebrados**: maxLines=1 nas abas internas, ScrollableTabRow no Financeiro
6. **Cards em Config**: CardTab disponível como sub-aba em Configurações > Cards

### Técnico:
- versionCode: 15 | versionName: 6.3.2
- Build: compilou com sucesso (R8 minificado, 41MB)
- APK: ngb-autoroad-privacy-v6.3.2.apk

---

## v6.3.4 — IA Inline, Finanças CRUD Completo, Android Auto, Overlay Fixes (20/06/2026)

### Arquivos criados:
- `ui/ai/AiTab.kt` — Nova tela inline da aba IA com sub-abas (Turno, Ranking, IA Local, Projeção, Histórico, Relatório, Exportar)
- `res/xml/automotive_app_desc.xml` — Declaração de suporte Android Auto (notification)

### Arquivos modificados:
- `ui/MainActivity.kt` — Ícone IA = AutoAwesome (estrelas), "Financeiro"→"Finanças", AiTab inline (não mais Activity separada), keepScreenOn corrigido, import Intent restaurado
- `ui/finance/FinanceTab.kt` — CRUD completo com todas as abas: Resumo, Ganhos, Despesas, Veículos, Desp. Fixas, Metas (restauradas do FinanceActivity original)
- `ui/settings/SettingsTab.kt` — Sub-aba "Adicionais" renomeada para "Mais", HistoryTab removido (movido para IA), toggle Android Auto adicionado em Sistema
- `ui/criteria/CriteriaTab.kt` — Fallback para nomes vazios de perfis ("Perfil N")
- `ui/dashboard/DashboardTab.kt` — Fallback para nomes vazios de perfis
- `service/OverlayService.kt` — Pinch-to-zoom removido (atrapalhava), resizeOverlay corrigido para preservar altura
- `service/OverlayCard.kt` — Resize handle aumentado para 32dp com ícone ⤡, campos RIDE_TYPE/RIDE_VALUE/VALUE_PER_HOUR adicionados, altura usa customL.cardHeight, fix: ride.rideType.displayName
- `data/prefs/PrefsManager.kt` — androidAutoEnabled adicionado
- `AndroidManifest.xml` — Metadata Android Auto adicionada
- `build.gradle.kts` — versionCode=19, versionName=6.3.4

### Funcionalidades implementadas:
1. **Aba IA inline com dashboard**: Cards de resumo + sub-abas (Turno, Ranking, IA Local, Projeção, Histórico, Relatório, Exportar) — sem Activity separada
2. **Finanças com CRUD completo**: Todas as abas restauradas (Resumo, Ganhos, Despesas, Veículos, Desp. Fixas, Metas)
3. **Projeção Financeira movida para IA**: Removida do Financeiro, adicionada como sub-aba na IA
4. **Histórico de corridas movido para IA**: Removido de Config > Mais, adicionado como sub-aba na IA
5. **Perfis com nomes**: Fallback "Perfil N" para perfis sem nome em CriteriaTab e DashboardTab
6. **keepScreenOn corrigido**: Funciona corretamente na MainActivity
7. **Android Auto**: Toggle em Config > Sistema, metadata no Manifest, automotive_app_desc.xml
8. **Overlay melhorado**: Pinch-to-zoom removido, resize handle 32dp, altura respeita cardHeight, campos mostram valor real

### Técnico:
- versionCode: 19 | versionName: 6.3.4
- Build: compilou com sucesso (R8 minificado, ~43MB)
- APK: ngb-autoroad-privacy-v6.3.4.apk

---

## v6.3.5 — Auditoria de Lógica: Correções de Performance, Concorrência e Precisão (20/06/2026)

### Arquivos modificados:
- `data/db/RideHistoryEntity.kt` — Novas queries: getById, updateStatusById, updateStatusAndValueById; filtro de status em averageScoreSince, countSinceFlow, topPlatformSince
- `service/RideLifecycleManager.kt` — Todos os 7 pontos de getAll().firstOrNull substituídos por updateStatusById (query direta)
- `service/UncertainReceiver.kt` — ID de notificação corrigido (9999→9001); fallback usa updateStatusById
- `service/OverlayService.kt` — Hash de deduplicação melhorado (inclui pickupDistance + pickupNeighborhood)
- `domain/ShiftManager.kt` — Reescrito com synchronized(lock) em todas as operações; addRide faz load+save atômico; commit() síncrono
- `ui/dashboard/DashboardTab.kt` — Médias de score, bestRide e averageValuePerKm filtram apenas COMPLETED/ACCEPTED

### Correções aplicadas:
1. **Performance crítica (RideLifecycleManager)**: Eliminado getAll() que carregava tabela inteira a cada transição de fase → substituído por queries SQL diretas por ID
2. **Bug de notificação (UncertainReceiver)**: Notificação UNCERTAIN ficava presa na tela porque cancelava ID 9999 em vez de 9001
3. **Precisão de métricas (Dashboard + DAO)**: Médias de score e contagens não incluem mais corridas REFUSED/EXPIRED/CANCELLED
4. **Concorrência (ShiftManager)**: Lock sincronizado previne sobrescrita de totalEarned em corridas simultâneas
5. **Falsos positivos (OverlayService)**: Hash de deduplicação agora diferencia corridas curtas com mesmo valor mas embarques diferentes
6. **Integridade de dados (DAO)**: topPlatformSince filtra apenas corridas relevantes

### Técnico:
- versionCode: 20 | versionName: 6.3.5
- Build: compilou com sucesso (R8 minificado, ~56MB debug)
- APK: ngb-autoroad-privacy-v6.3.5.apk
- 6 arquivos alterados, auditoria completa documentada em AUDITORIA_V6.3.4.md

---

## v6.4.0 — Correções de Bugs + Compatibilidade Android 15 (16KB Page Alignment) (21/06/2026)
### Correções de Bugs:
1. **Perfil (DashboardTab)**: Nome salvo agora aparece corretamente no Dashboard — antes mostrava "Perfil 1" mesmo com nome personalizado. Fix no parsing de `profile_X_name` das SharedPreferences.
2. **Overlay altura (OverlayService)**: Altura do overlay agora persiste entre sessões — carrega `overlayHeight` das prefs na criação do serviço e aplica no WindowManager.LayoutParams.
3. **Overlay botões A-/A+ (OverlayCard)**: Botões de acessibilidade restaurados no header do card overlay.

### Compatibilidade Android 15+ (16KB Page Size):
- **Problema**: `libmlkit_google_ocr_pipeline.so` do ML Kit não era alinhada a 16KB, causando aviso "Não é compatível com 16 KB page size" no Android 15+
- **Solução aplicada**:
  1. ML Kit text-recognition atualizado de 16.0.0 para 16.0.1
  2. `jniLibs { useLegacyPackaging = false }` — armazena .so sem compressão no APK
  3. APK re-alinhado com `zipalign -P 16` (build-tools 35.0.0) — garante offset 16KB para todas as bibliotecas nativas
  4. Re-assinado com keystore de produção após realinhamento
- **Verificação**: `zipalign -c -P 16 -v 4` confirma todas as 4 bibliotecas (arm64-v8a, armeabi-v7a, x86, x86_64) com alinhamento OK

### Arquivos modificados:
- `app/build.gradle.kts` — ML Kit 16.0.1, jniLibs useLegacyPackaging=false, versionCode=639
- `app/src/main/java/com/ngbautoroad/ui/dashboard/DashboardTab.kt` — Fix parsing nome de perfil
- `app/src/main/java/com/ngbautoroad/service/OverlayService.kt` — Persistência de altura
- `app/src/main/java/com/ngbautoroad/service/OverlayCard.kt` — Botões A-/A+ restaurados

### Técnico:
- versionCode: 639 | versionName: 6.4.0
- Build: compilou com sucesso (R8 minificado, ~45MB)
- APK: ngb-autoroad-privacy-v6.4.0-release.apk
- Assinado: CN=NGB AutoRoad, O=NextGenShop BR
- 16KB aligned: Verificado com build-tools 35.0.0
- GitHub Release: https://github.com/nextgenshopbr-netizen/ngb-autoroad-privacy/releases/tag/v6.4.0

---

## v6.4.1 — 21/06/2026 03:30

### Proteção por Avaliação do Passageiro (Multiplicador de Penalidade)

**Problema identificado:** Corrida ID=8 do backup com passageiro rating 4.23 recebia score 88 (aceitar), representando risco à segurança do motorista. A penalidade fixa de 9 pts (peso × 0.6) era insuficiente.

**Solução implementada:**

1. **Nova normalizeRating() com duas zonas:**
   - Zona A (4.7-5.0): linear suave → 75 a 100
   - Zona B (< 4.7): curva cúbica agressiva → derruba rapidamente

2. **Multiplicador de penalidade por faixa de rating:**
   - 4.9-5.0: sem penalidade
   - 4.7-4.9: 1.0× (peso × 1.0)
   - 4.5-4.7: 2.5× (peso × 2.5)
   - 4.3-4.5: 3.5× (peso × 3.5)
   - < 4.3: 4.0× (peso × 4.0)

3. **UI: Ícone info na tela de Pesos**
   - Ícone ℹ️ ao lado do slider de "Avaliação do Passageiro"
   - Ao clicar, abre dialog com tabela de multiplicadores
   - Valores calculados em tempo real baseados no peso configurado

**Resultado:** Corrida ID=8 (rating 4.23) → Score cai de 88 para 32 (❌ RECUSAR)

**Arquivos modificados:**
- `domain/RideScorer.kt` — normalizeRating(), getRatingPenaltyMultiplier(), cálculo de penalidade
- `ui/criteria/CriteriaTab.kt` — Row com ícone info + AlertDialog com tabela
- `app/build.gradle.kts` — versionName 6.4.1

---

## v6.5.0 — Odômetro Inteligente + Proteção por Rating (21/06/2026)

### Odômetro Inteligente (4 camadas)
- **Camada 1**: Campo odômetro no cadastro de veículo (VehicleProfileEntity) + OdometerHistoryEntity + Migration v6→v7
- **Camada 1**: Card na Dashboard com dialog inline para atualizar odômetro (sem navegar para outra tela)
- **Camada 2**: OdometerEngine.kt — estimativa automática: `odômetroEstimado = base + (kmRastreado × fatorCorreção)`
- **Camada 3**: Auto-calibração EWMA (alpha=0.3) — sistema aprende o padrão de uso pessoal
- **Camada 4**: DRE e ProjectionEngine usam KM real (fator de correção) em vez de apenas KM de corridas
- **Camada 4**: VehicleProfileCard exibe odômetro, dias desde atualização e fator de correção

### Penalidade de Rating por Multiplicador (RideScorer.kt)
- Zona suave 4.7–5.0: normalização linear 75→100
- Zona rígida <4.7: curva cúbica agressiva
- Multiplicadores de penalidade: 4.5–4.7=2.5×, 4.3–4.5=3.5×, <4.3=4.0×
- Ícone info na CriteriaTab com tabela de multiplicadores
- Caso real: passageiro 4.23★ em corrida excelente → Score 88→32 (RECUSAR)

### Pontos de Ruptura Resolvidos
1. Odômetro nunca atualizado → campo + alerta na Dashboard
2. KM rastreado ≠ KM real → fator de correção (padrão 1.3×)
3. Projeção subestimada → ProjectionEngine usa projKmReal
4. DRE com custos subestimados → totalKm × correctionFactor
5. Sem auto-calibração → EWMA com histórico de atualizações

### Arquivos Modificados
- `data/db/FinanceExtensions.kt` — VehicleProfileEntity (novos campos) + OdometerHistoryEntity + OdometerHistoryDao
- `data/db/FinanceDatabase.kt` — Migration v6→v7, registro de OdometerHistoryEntity
- `domain/OdometerEngine.kt` — NOVO: motor de cálculo do odômetro estimado
- `domain/RideScorer.kt` — normalizeRating com duas zonas + getRatingPenaltyMultiplier
- `domain/FinanceDRE.kt` — totalKm usa fator de correção
- `domain/ProjectionEngine.kt` — projKmReal com fator de correção
- `domain/LocalLearningEngine.kt` — SuggestionType.MAINTENANCE_ALERT
- `ui/dashboard/DashboardTab.kt` — OdometerAlertCard com dialog inline
- `ui/finance/FinanceExtTabs.kt` — Campo odômetro no AddVehicleProfileDialog + VehicleProfileCard
- `ui/criteria/CriteriaTab.kt` — Ícone info com tabela de multiplicadores de rating

---

## v6.6.0 — Inteligência Completa: GPS + Segurança + Manutenção (21/06/2026)

### 12 Rupturas Corrigidas
1. pickupDistance no EarningEntity + DRE (KM morto contabilizado)
2. ReturnFactorEngine — fator de volta vazia por bairro
3. Fadiga por tempo no ShiftManager (4h/6h/8h/10h com alertas)
4. Taxa de cancelamento com alerta visual
5. SafetyScoreModifier multi-fator (horário + bairro + rating cascata)
6. Desgaste diferenciado para veículos elétricos (pneus +25%, freios -40%)
7. MaintenanceReserveEngine — reserva financeira sugerida
8. OdometerEngine integrado com GPS real
9. SmartRoutingEngine — direção de casa no fim do turno
10. ShiftHistoryEntity — histórico de turnos persistido com dados GPS
11. Detecção de padrão da plataforma (teste de corridas ruins)
12. Multi-plataforma awareness

### Novos Engines
- GpsTrackingEngine: odômetro real via GPS + acelerômetro + validação KM da Uber
- GeoEnrichmentEngine: reverse geocoding + dados de trânsito via internet
- SafetyScoreModifier: análise de segurança com efeito cascata multiplicativo
- ReturnFactorEngine: aprendizado de volta vazia por bairro (org.json)
- MaintenanceReserveEngine: cálculo de reserva R$/km para manutenção
- SmartRoutingEngine: direção de casa + padrão plataforma + multi-plataforma
- ShiftHistoryManager: persistência de turnos com dados GPS

### Correções de UI
- Headers redundantes removidos de todas as 5 abas (Início, Critérios, IA, Finanças, Config)

### Banco de Dados
- Migration v7→v8: shift_history (com GPS), pickupDistance no earnings

### Arquivos Novos
- domain/GpsTrackingEngine.kt
- domain/GeoEnrichmentEngine.kt
- domain/SafetyScoreModifier.kt
- domain/ReturnFactorEngine.kt
- domain/MaintenanceReserveEngine.kt
- domain/SmartRoutingEngine.kt
- domain/ShiftHistoryManager.kt

---

## v6.7.0 — Correção de 12 Rupturas (Simulação 1 Ano)
**Data:** 2026-06-21

### Contexto
Simulação avançada de 1 ano com dados aleatórios (365 dias, ~4.500 corridas, uso familiar variável, esquecimento de odômetro) revelou 12 pontos de ruptura cruzando todos os módulos.

### Críticos Corrigidos
1. **Cold Start**: Onboarding obrigatório de odômetro no primeiro uso
2. **DRE Retroativo**: Fator de correção por período (não retroativo)
3. **Safety Floor**: Threshold mínimo 50, score capped quando safety_penalty > 8
4. **KM Validation History**: Persiste discrepâncias Uber vs GPS + relatório mensal (R$6.445/ano de perda detectada)

### Médios Corrigidos
5. **Alerta odômetro menor**: Avisa se valor informado < estimado (possível erro)
6. **Guards divisão por zero**: Já protegido (confirmado na simulação)
7. **Fadiga pré-corrida**: Score reduzido progressivamente após 8h+ de turno
8. **EWMA outlier detection**: Férias/viagens não corrompem o fator (IQR filter)
9. **Seleção de veículo**: vehicleId associado ao turno
10. **Fator max 5.0**: Permite cenários extremos de uso familiar

### Baixos Corrigidos
11. **Fator sazonal**: Família usa mais o carro em férias (jan=1.5×, jul=1.3×)
12. **Filtro Kalman GPS**: Suaviza ruído GPS + ignora micro-movimentos < 3m

### Arquivos Modificados (14)
- OdometerOnboardingDialog.kt (NOVO)
- KmValidationHistory.kt (NOVO)
- OdometerEngine.kt (outlier + sazonal + max 5.0)
- GpsTrackingEngine.kt (Kalman filter)
- RideScorer.kt (safety floor + fadiga)
- FinanceDRE.kt (período-específico)
- ShiftManager.kt (vehicleId)
- DashboardTab.kt (alerta odômetro menor)
- MainActivity.kt (onboarding check)
- PrefsManager.kt (onboarding pref)
- FinanceExtensions.kt (getEntriesInPeriodSync)
- RideData.kt (metadata field)

### Resultado da Simulação Pós-Correção
- Erro máximo de odômetro: 11% (antes 33%)
- Corridas de risco aceitas: 0 (antes 53)
- DRE retroativo: eliminado
- Cold start: impossível (onboarding obrigatório)

### 21/06/2026 - Implementação v6.8.0 e v6.9.0 (Inteligência Proativa e Automação GPS)
- **Setup Wizard e Permissões (v6.8.0)**: Implementado SetupWizardDialog com opções de backup, configuração limpa ou pular. Criado PermissionManager centralizando 9 permissões, incluindo Activity Recognition e GPS Background.
- **ActivityStateDetector (v6.9.0)**: Implementada detecção automática de atividade (Dirigindo, Parado, Caminhando, Correndo) usando Activity Recognition API. O GPS agora alterna automaticamente entre os modos Ativo, Economia e Pausado sem interação do motorista.
- **FatigueInsightEngine (v6.9.0)**: Criado motor de IA que analisa histórico de turnos e gera comparativos reais de ganho/hora (turnos longos vs curtos), horários ótimos e ponto de retorno decrescente. Zero alertas intrusivos; exibe apenas cards informativos no dashboard.
- **MaintenanceReserveAdvisor (v6.9.0)**: Adicionada sugestão automática de taxa de reserva. O sistema detecta se a reserva atual cobrirá a próxima manutenção e sugere ajustes em centavos (R$/km) para evitar déficits.
- **EWMA Cold Start (v6.9.0)**: Ajustado o OdometerEngine para usar alpha=0.5 nos primeiros 30 dias, permitindo que o sistema aprenda o padrão de uso pessoal do motorista 1 a 2 atualizações mais rápido.
- **Simulações de Validação**: Script `sim_v690_validation.py` executado para 1m, 3m, 6m, 1a e 2a. Comprovou acurácia do GPS (>90%), economia de bateria (130h/ano) e zero alertas intrusivos.
- **Release**: APK compilado, alinhado em 16KB, assinado e publicado como release `v6.9.0` no GitHub.

### 21/06/2026 18:15 - Release v6.9.3 (Fix Profile Names + Legacy Backup Migration)
- **Bug Crítico Corrigido — Nomes de Perfil**: Causa raiz identificada: backups gerados usavam chave `saved_profiles_json` mas o app lê de `profiles_json`. Além disso, campos internos de CriteriaWeights e DriverThresholds estavam com nomes errados no JSON de perfis.
- **BackupManager — migrateLegacyKeys()**: Adicionada função que mapeia automaticamente `saved_profiles_json` → `profiles_json` ao importar backups antigos, incluindo correção de campos internos (stops→intermediateStops, rating→passengerRating, duration→rideDuration, pickupDist→pickupDistance, dropoffDist→dropoffDistance, maxPickupDist→maxPickupDistance, minRating→minPassengerRating, minDropoffDist→minDropoffDistance).
- **Correções de Compilação**: Adicionado import `rememberLauncherForActivityResult` no DashboardTab, fix referência `LocalLifecycleOwner` (pacote correto: `compose.ui.platform`), adicionado `@OptIn(ExperimentalMaterial3Api)` no PermissionsStep, adicionada dependência `lifecycle-runtime-compose:2.7.0`.
- **Backup de Teste Regenerado**: Script `generate_backup.py` corrigido com chave `profiles_json`, campos corretos de CriteriaWeights/DriverThresholds, campo `id` e campos `autoPilotMode`/`minAcceptScore`/`maxRefuseScore` em cada perfil. ZIP regenerado com 3.464 corridas, 132 turnos, R$ 48.756 em ganhos.
- **Release**: APK v6.9.3 (versionCode 72) compilado, zipalign 4-byte, assinado v2/v3, publicado no GitHub com backup atualizado.
