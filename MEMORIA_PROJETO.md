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
