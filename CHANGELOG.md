# Changelog — NGB AutoRoad

Todas as mudanças notáveis do projeto estão documentadas aqui.

---

## [Não lançado] — 2026-07-01 (PR #1, branch `fix/ride-lifecycle-false-refused-race`)
### Corrigido
- **Corridas concluídas marcadas como RECUSADA** — dois detectores de fim-de-corrida (um específico por fase, um heurístico genérico por palavra solta) competiam pela mesma decisão a cada evento de acessibilidade; o genérico rodava primeiro e podia decretar `REFUSED` em 4-15s mesmo em corridas realmente concluídas na Uber. Corrigido reordenando a prioridade (específico primeiro) e exigindo confirmação dupla (2+ indicadores + 2 leituras consecutivas) antes de decretar RECUSADA/EXPIRADA.
- **Fallback de screenshot+OCR invisível na telemetria** — todo o caminho (`triggerScreenshotFallback`/`processScreenshotWithOcr`) só logava em Logcat, nunca em `TelemetryLogger`. Agora instrumentado — exports de telemetria futuros mostram se o OCR disparou, teve sucesso ou falhou.
- **Gap no acionamento do fallback OCR** — só tentava screenshot quando o evento era `TYPE_WINDOW_STATE_CHANGED`; removido esse filtro, já que a Uber atualiza a oferta via `TYPE_WINDOW_CONTENT_CHANGED` (bottom sheet in-app) sem gerar esse evento.

## [7.0.0] — 2026-06-30
### Adicionado
- **Zonas de Lucro (Heatmaps)** — Nova visualização no mapa destacando áreas de maior rentabilidade baseada no histórico real de ganhos.
- **OCR de Despesas** — Captura automática de valores de recibos e notas fiscais usando ML Kit para agilizar o lançamento financeiro.
- **Segurança SQLCipher** — Criptografia AES-256 do banco de dados Room (App e Finance) com gestão de chaves via Keystore.
- **Glance Home Widget** — Widget interativo para monitorar ganhos do dia e status do serviço sem abrir o app.
- **Quick Settings Tile** — Tile de sistema para controle instantâneo (On/Off) do Overlay Service.
- **Cloud Backup Framework** — Estrutura para backup automático em nuvem privada (Google Drive).

### Melhorado
- **Estabilidade do Overlay** — Migração para `START_STICKY` e gestão proativa de memória no Android 17 para evitar interrupções do sistema.
- **Setup Wizard v2** — Fluxo inicial otimizado com verificação de otimização de bateria e permissões avançadas exigidas pelas APIs mais novas.

## [6.9.16] — 2026-06-30
### Adicionado
- **Injeção de Dependência (Koin)** — Implementação centralizada via `AppModule.kt` e inicialização no `NGBAutoRoadApp`. Refatoração da `MainActivity` para usar `inject()`.
- **Gradle Version Catalog** — Migração de todas as dependências e plugins para `libs.versions.toml`, garantindo consistência e facilidade de atualização.
- **Suporte Edge-to-Edge** — Ativação nativa da experiência de tela cheia conforme diretrizes do Android 15.
- **Configuração de Segurança** — Integração das credenciais de assinatura com `local.properties`, removendo dados sensíveis do `build.gradle.kts`.
- **Compatibilidade 16KB** — Alinhamento nativo de bibliotecas para suporte total a dispositivos com page size de 16KB (Android 15+).

### Alterado
- **Android SDK 37** — Upgrade do `compileSdk` e `targetSdk` para a versão 37.
- **Room Database 2.8.4** — Update para suporte nativo a KSP2 e melhorias de performance.
- **Java 17/21** — Padronização do ambiente de build para as versões mais recentes do Java.

## [6.9.14] — 2026-06-24
### Corrigido
- **Filtros de Tela de Ganhos/Resumos (Uber)** — O parser agora identifica e ignora a tela de ganhos do Uber Driver (`isEarningsScreen`), evitando o registro de falsas corridas no banco de dados.
- **Filtro de Tela de Notificações (`isNotificationShadeContent`)** — Ignora dados capturados da aba de notificações do sistema (Samsung/Android), eliminando falsos positivos.
- **Filtro de Tela de Navegação (`isNavigationScreen`)** — Ignora leituras do parser do Uber quando o motorista está com o GPS de navegação ativo na tela.
- **Deduplicação e Estabilidade do Hash** — Janela de duplicatas estendida e hash do overlay simplificado para considerar apenas `platform`, `rideValue` e `rideType`, ignorando distâncias que mudam dinamicamente.
- **Proteção do Ciclo de Vida do Turno** — O detector de ação do usuário (`UserActionDetector`) agora ignora atualizações de tela de ganhos e da aba de notificações, prevenindo falsas expirações de corrida (`onRefused()`).

## [5.3.2] — 2026-06-19
### Corrigido
- **Barra de título separada acima do card (estilo Windows)** — solução definitiva para sobreposição dos botões A−/A+/✕ com o Score
- Estrutura: Column > [Row(barra título)] + [Box(card conteúdo)]
- Barra usa borderRadius no topo, card usa borderRadius na base
- Funciona em TODOS os modelos de card da galeria

## [5.3.1] — 2026-06-18
### Corrigido
- Botões A-/A+/X posicionados com Box/Alignment.TopEnd
- Score com padding-end para evitar sobreposição (solução parcial)

## [5.3.0] — 2026-06-18
### Alterado
- Botões A-/A+/X reorganizados no canto superior direito

## [5.2.9] — 2026-06-18
### Corrigido
- Fix A+/A- acessibilidade — callback onFontScaleChange conectado no OverlayService
- Limites de escala: 1.0f a 2.5f

## [5.2.8] — 2026-06-18
### Corrigido
- **Fix DEFINITIVO do overlay** — causa raiz em Theme.kt: cast seguro `(view.context as? Activity)?.window`

## [5.2.7] — 2026-06-18
### Corrigido
- Fix ClassCastException ContextThemeWrapper→Activity no OverlayService

## [5.2.6] — 2026-06-18
### Corrigido
- Fix Simulação Admin — applicationContext, retry automático 3s, Toast de confirmação

## [5.2.5] — 2026-06-18
### Corrigido
- Fix crash Cards — ViewModelStoreOwner adicionado ao OverlayService

## [5.2.4] — 2026-06-18
### Corrigido
- Fix crash Compose BOM (NoSuchMethodError KeyframesSpec) — downgrade BOM para 2023.10.01

## [5.2.3] — 2026-06-18
### Adicionado
- Meta obrigatória ao iniciar turno
- Multi-idiomas (PT-BR, ES)
- Logs de debug: NGB_PROJECAO, NGB_TESTAR_CARD, NGB_SIMULACAO
### Alterado
- Unificação Gastos→Despesas
- Campo Min→H:m no financeiro

## [5.2.2] — 2026-06-18
### Adicionado
- Base compilada com mudanças iniciais

## [5.0.0] — 2026-06-18
### Corrigido
- 36 correções em 14 módulos (guards divisão por zero, auto-dismiss, lifecycleScope, etc.)

## [4.5.0] — 2026-06-18
### Adicionado
- 10 novas funcionalidades: Modo Turno, Ranking Bairros, Integração Waze/Maps, Compartilhamento Critérios, Economia Bateria, Alertas Surge, Multi-idioma, Relatório PDF, Export CSV, Aprendizado Local

## [4.4.0] — 2026-06-18
### Corrigido
- 5 bugs críticos (simulação, crash CardTab, tela ligada, overlay auto-iniciar, crash projeção)
### Adicionado
- ChangePinDialog com validação, tipo veículo, ícones despesas, ProGuard rules

## [4.3.3] — 2026-06-18
### Adicionado
- Simulador Admin dispara overlay REAL
- Botão "Testar Real" no editor de cards

## [4.3.2] — 2026-06-18
### Corrigido
- Truncamento bairros longos, spacer entre label/valor, labels PT-BR

## [4.3.1] — 2026-06-18
### Alterado
- Galeria reescrita: 35 cards em 7 categorias

## [4.3.0] — 2026-06-18
### Adicionado
- Bordas dinâmicas por score, drag livre, pinch-to-zoom, acessibilidade A+/A-
- Controle Financeiro: Veículos, Despesas, Projeção, Simulação "E se?"

## [4.2.2] — 2026-06-18
### Adicionado
- Enum RideType, parser detecta tipo de corrida, card padrão NGBAutoRoad

## [4.2.1] — 2026-06-18
### Alterado
- PIN padrão: 250696
- UberStyleCard no simulador
