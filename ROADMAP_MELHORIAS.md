# Roadmap de Melhorias: NGB AutoRoad Privacy

Este documento estabelece o roteiro completo de evolução do NGB AutoRoad Privacy, organizado por **prioridade de risco e impacto**, com datas estimadas e janelas de implantação para cada item. Os recursos estão divididos em quatro categorias: Críticos (riscos ativos que precisam de ação imediata), Importantes (melhorias de estabilidade e qualidade), Oportunidades (novos recursos que ampliam o diferencial competitivo) e Visão de Longo Prazo (expansões futuras).

---

## Categoria 1 — Críticos: Ação Imediata

Estes itens representam **riscos ativos** que afetam usuários hoje ou afetarão em semanas. Não há justificativa para adiá-los.

### C1. Flag `isAccessibilityTool` no Manifest
**Janela:** Imediatamente — próximo commit (esta semana)
**Versão alvo:** v6.1.2 (hotfix)
**Status:** ✅ Concluído na v6.2.0
**Risco sem ação:** Qualquer motorista que ative o Advanced Protection Mode (AAPM) do Android 16/17 tem o `RideAccessibilityService` **bloqueado automaticamente** pelo sistema. O AutoPilot, a detecção de corridas e o click automático param de funcionar sem aviso.
**Ação:** Adicionar `android:isAccessibilityTool="true"` na declaração do `RideAccessibilityService` no `AndroidManifest.xml`.

---

### C2. Filtros de Status ACCEPTED → COMPLETED no Dashboard e Histórico
**Janela:** Imediatamente — já corrigido na v6.1.1
**Status:** ✅ Concluído na v6.1.1

---

### C3. Overlay Dismiss sem Notificar o Lifecycle
**Janela:** Imediatamente — já corrigido na v6.1.1
**Status:** ✅ Concluído na v6.1.1

---

### C4. Otimização de Memória para Android 17
**Janela:** Julho de 2026 — v6.2.0
**Status:** ✅ Concluído na v6.2.0
**Ações realizadas:**
- `MemoryMonitor.kt` com profiling de RAM a cada 30s.
- Detecção de kills por OOM via `ApplicationExitInfo` (Android 11+).
- Alerta automático em 80% do limite estimado por faixa de RAM.
- `onLowMemory()` implementado no `OverlayService` e `RideAccessibilityService`.

---

### C5. Rota Alternativa ao AccessibilityService (Plano B para AAPM)
**Janela:** Agosto de 2026 — v6.2.0
**Status:** ✅ Concluído na v6.2.0
**Ação realizada:** `NotificationListenerService` promovido a canal primário automático quando `AccessibilityService` é destruído. Hierarquia inteligente sem duplicatas.

---

## Categoria 2 — Importantes: Próximas Versões (Julho 2026–Março 2027)

Estes itens não representam risco imediato, mas impactam diretamente a qualidade e a confiança do motorista no app. O Redesign é o primeiro item desta categoria pois define a interface final — o Tutorial e a Central de Ajuda só fazem sentido após a nova UI estar pronta.

### I1. Redesign Completo — Nova Interface e Arquitetura de Menus
**Janela:** Julho–Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** O Redesign precisa vir primeiro. Criar o tutorial guiado sobre uma interface que ainda vai mudar seria retrabalho — o motorista aprenderia posições e ícones que serão alterados. A nova UI define o ponto de partida correto para o onboarding.

> O conteúdo completo do Redesign está detalhado na seção O1 da Categoria 3 (mantida como referência de especificação). Esta entrada na Categoria 2 indica que a **implementação** foi antecipada para a v6.3.0.

---

### I2. Tutorial Guiado Interativo (Onboarding)
**Janela:** Outubro de 2026 — v7.0.0
**Versão alvo:** v7.0.0
**Dependência:** Requer I1 (Redesign) concluído — o tutorial guia o motorista na interface final.
**Motivação:** Novos motoristas ficam perdidos sem entender como distribuir os 100 pontos de critério, o que é a Zona Neutra do AutoPilot ou como o Ghost Mode funciona. Um tutorial interativo no primeiro acesso reduz o churn e aumenta a conversão para o plano pago.
**Escopo:**
- Tela de boas-vindas com os 3 pilares (Privacidade, AutoPilot, Controle Financeiro).
- Tour interativo por cada aba com tooltips destacando os elementos e posições da nova interface.
- Flag `hasCompletedTutorial` no `PrefsManager` para exibir apenas uma vez.
- Botão "Reprisar Tutorial" nas Configurações.

### I3. Central de Ajuda Integrada
**Janela:** Outubro de 2026 — v7.0.0
**Versão alvo:** v7.0.0
**Dependência:** Requer I1 (Redesign) concluído — os ícones `?` precisam estar nas posições corretas da nova UI.
**Motivação:** Reduzir dúvidas recorrentes sem depender de suporte externo.
**Escopo:** Ícone `?` no topo de cada aba que abre um bottom sheet explicativo. Seção de FAQ dinâmico nas Configurações.

### I4. Mapa de Zonas Funcional
**Janela:** Agosto de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** O motorista desenha áreas de risco no mapa, mas o app não usa esses desenhos no cálculo do score.
**Ação:** Implementar algoritmo Point-in-Polygon (Ray-casting). Aplicar penalidade de -50 pontos no score para corridas com destino em zona de risco.

### I5. Ranking e Relatórios Reais
**Janela:** Agosto de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** A aba de Recursos Avançados exibe dados completamente fictícios (bairros inventados, valores falsos).
**Ações:**
- `RankingTab`: Substituir lista estática por `GROUP BY` no `RideHistoryDao`.
- `ExportTab`: Gerar CSV real a partir do `AppDatabase`.
- `ReportTab`: Conectar gerador de PDF ao `FinanceDatabase`.

### I6. Local Learning Engine com Dados Reais
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** O `LocalLearningEngine` injeta 50 corridas fictícias para treinar o modelo.
**Ação:** Treinar o engine com dados do `RideHistoryDao`. Adicionar sugestão automática de bloqueio de bairros onde o motorista recusa corridas consistentemente.

### I7. Refatoração da ProjectionEngine
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Ações:**
- Filtrar `simulateWhatIf()` estritamente por `status == "COMPLETED"`.
- Remover `coerceAtLeast(1)` que gera projeções infladas.

### I8. Limpeza de Código Morto e Unificação de Deduplicação
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Ações:**
- Remover `VehicleTab` legado (linhas 937-1144 do `FinanceActivity.kt`).
- Centralizar lógica de hash e deduplicação no `RideLifecycleManager`.

---

## Categoria 3 — Oportunidades: Diferencial Competitivo (Outubro 2026–Março 2027)

Estes itens representam o maior potencial de diferenciação do NGB AutoRoad em relação a todos os concorrentes.

### O1. Especificação do Redesign — Referência Técnica
**Implementação:** Antecipada para v6.3.0 (ver I1 na Categoria 2)
**Esta seção serve como documento de especificação detalhada** para o time de desenvolvimento durante a implementação do redesign.
**Contexto:** A v7.0.0 marca a maior evolução do app desde o lançamento, com integração ao Gemini e recursos do Android 17. O redesign acontece em paralelo com a implementação técnica do Android 17, garantindo que a nova UI e os novos recursos sejam lançados juntos como uma experiência coesa.

---

#### Princípios Globais do Redesign

**Ícones com significado imediato:** Todos os ícones do sistema serão substituídos por ícones de mercado que comunicam sua função no primeiro olhar, sem necessidade de ler o rótulo.

| Tela / Recurso | Ícone Atual | Ícone Novo |
|---|---|---|
| INICIO (Dashboard) | Ícone genérico | 🏠 Casa (`Icons.Filled.Home`) |
| Critérios | Ícone atual | 🎚️ Sliders (`Icons.Filled.Tune`) |
| Cards | Ícone atual | 🃏 Cartão (`Icons.Filled.Style`) |
| Financeiro | Ícone atual | 💰 Carteira (`Icons.Filled.AccountBalanceWallet`) |
| Configurações | Ícone atual | ⚙️ Engrenagem (`Icons.Filled.Settings`) |
| AutoPilot | Ícone atual | 🤖 Robô / Piloto (`Icons.Filled.SmartToy`) |
| Ghost Mode | Ícone atual | 👻 Fantasma (`Icons.Filled.VisibilityOff`) |
| Mapa / Zonas | Ícone atual | 📍 Mapa com pin (`Icons.Filled.Map`) |
| Backup | Ícone atual | ☁️ Nuvem com seta (`Icons.Filled.CloudUpload`) |
| Histórico | Ícone atual | 📋 Lista (`Icons.Filled.History`) |
| Turno | Ícone atual | ⏱️ Cronômetro (`Icons.Filled.Timer`) |
| Perfis | Ícone atual | 👤 Pessoa (`Icons.Filled.ManageAccounts`) |

**Tipografia e alinhamento perfeitos:** Todos os textos do sistema serão revisados para alinhamento, espaçamento, justificação e hierarquia visual consistentes, sem exceção. Textos repetidos ou redundantes que expressam a mesma ideia serão unificados ou removidos.

**Acessibilidade de uma mão:** Cada tela deve ser operável com um único toque, sem rolação excessiva. Os recursos mais usados (AutoPilot, Turno, Perfis) devem estar a no máximo 1 toque de distância.

**Integração com Android 17:** O redesign incorpora os novos padrões visuais do Android 17 (Material You dinâmico, App Bubbles, notificações interativas do Gemini).

---

#### Navegação Principal (Barra Inferior)

A ordem das abas será reorganizada para refletir o fluxo natural de uso do motorista, com o INICIO centralizado como ponto de partida. O app sempre iniciará na tela INICIO.

```
┌──────────────────────────────────────────────────────────┐
│  🎚️          🃏          🏠          💰          ⚙️      │
│ CRITÉRIOS   CARDS      INICIO    FINANCEIRO   CONFIG     │
└──────────────────────────────────────────────────────────┘
```

**Mudanças em relação à navegação atual:**
- Ícone do Dashboard substituído por casa (`Home`) com rótulo "INICIO" em português.
- Ordem reorganizada: Critérios | Cards | **INICIO** (centro) | Financeiro | Config.
- App sempre inicia na aba INICIO ao abrir.
- Botões "Controle Financeiro" e "Recursos Avançados" **removidos do topo do Dashboard** — o acesso já existe na barra de navegação inferior.

---

#### Tela INICIO (Dashboard) — Reestruturação

**Itens removidos:**
- Botão "Controle Financeiro" do topo (acesso já disponível na barra inferior).

**Itens adicionados / reorganizados:**

1. **Card de Seleção Rápida de Perfis** — Posicionado logo abaixo do botão "Iniciar Turno". Ao tocar, abre uma janela flutuante (bottom sheet) com a lista de todos os perfis de critérios salvos para seleção e ativação imediata. O card exibe os **3 perfis favoritos** diretamente (sem precisar abrir a janela), com botão de estrela para favoritar/desfavoritar. Perfis favoritos aparecem como chips tocáveis diretamente no card.

2. **Seletor de Perfis na janela flutuante:**
   - Lista completa de perfis salvos (até 5).
   - Ícone de estrela para favoritar (máximo 3 favoritos).
   - Perfil ativo destacado com cor de destaque.
   - Botão "Gerenciar Perfis" que leva à Aba 1 de Critérios.

---

#### Tela CRITÉRIOS — Reestruturação em Abas

A tela de Critérios, atualmente uma lista longa e difícil de navegar, será dividida em **4 abas fixas no topo da tela**, cada uma com foco específico.

---

##### Aba 1 — PAINEL (Dashboard de Critérios)

Esta aba é a "home" dos Critérios. Contém informações importantes e acessos rápidos organizados por prioridade visual.

**Bloco 1 — Perfis de Critérios (destaque superior, primeiro item):**
- Criação e gestão de perfis (criar, editar, excluir, favoritar).
- Perfil ativo em destaque com nome e resumo dos pesos.
- Chips de acesso rápido para os 3 perfis favoritos.

**Bloco 2 — Card IA e Android 17:**
- Card informativo destacado que apresenta a integração com o Gemini e os recursos de IA do Android 17.
- Botão em destaque **"O QUE SOU CAPAZ DE FAZER"** que, ao tocar, abre uma lista com dezenas de recursos do app. Recursos já implementados aparecem normalmente; recursos planejados aparecem com a tag **"Em Breve"**.

**Bloco 3 — Botões Rápidos de Recursos:**
- Toggles para ativar/desativar os principais recursos do sistema de critérios individualmente.
- Botão master **ON / OFF** que liga ou desliga todos os recursos de critérios de uma vez.

**Bloco 4 — Cards Informativos:**
- Recursos mais usados pelo motorista.
- Recursos menos usados (sugestão de simplificação).
- Recursos mais recomendados pelo sistema com base no histórico.
- Outros cards informativos relevantes gerados a partir de dados reais.

---

##### Aba 2 — PESOS E VALORES

Contém os controles de configuração dos critérios de avaliação de corridas.

**Conteúdo:**
- Cards informativos relevantes relacionados a pesos e critérios (ex: "Como o score é calculado?", "Dica: corridas curtas com valor alto têm score maior").
- **Valores Mínimos Desejados** — sliders e campos para definir valor mínimo por km, valor mínimo total, distância máxima de busca, etc.
- **Pesos e Critérios** — distribuição dos 100 pontos entre os critérios (valor, distância, duração, tipo, bairro, etc.).
- **Auto Save** — as alterações são salvas automaticamente ao sair da aba (sem botão "Salvar" explícito, reduzindo a interface).
- **Instruções de uso** — seção colapsável explicando como funciona o cálculo de pesos e como interpretar o score.

---

##### Aba 3 — MAPAS E ZONAS

Contém o editor de zonas de risco e gestão de bairros bloqueados.

**Conteúdo:**
- Cards informativos relevantes relacionados a zonas (ex: "Zonas ativas afetam o score com penalidade de -50 pontos", "Você tem X zonas cadastradas").
- **Editor de Mapa** — mapa interativo para desenhar polígonos de zonas de risco.
- **Lista de Zonas** — lista de zonas cadastradas com toggle para ativar/desativar cada zona individualmente, sem precisar abrir o editor.
- **Bairros Bloqueados** — lista de bairros bloqueados com opção de adicionar/remover.
- **Auto Save** — alterações salvas automaticamente.
- **Instruções de uso** — seção colapsável explicando como as zonas funcionam e afetam o score.

---

##### Aba 4 — AUTOPILOT ⭐

O AutoPilot é o recurso mais diferenciado do app e merece destaque próprio com ícone adequado.

**Ícone:** Substituir pelo ícone `Icons.Filled.SmartToy` (robô) ou `Icons.Filled.DirectionsCar` com indicador de automação — algo que comunique "piloto automático" no primeiro olhar.

**Conteúdo:**
- Cards informativos relevantes (ex: "AutoPilot aceitou X corridas hoje", "Economia de tempo estimada: Y minutos", "Score médio das corridas aceitas automaticamente").
- **Todos os recursos do AutoPilot:**
  - Toggle master ON/OFF.
  - Checkboxes independentes: ☑ Aceitar automaticamente | ☑ Recusar automaticamente.
  - Slider de score mínimo para aceitar.
  - Slider de score máximo para recusar.
  - Zona neutra (faixa entre os dois sliders — motorista decide).
  - Delay humanizado (configuração de tempo de espera antes do click).
  - Filtros geográficos (ativar/desativar por zona).
- **Auto Save** — alterações salvas automaticamente.
- **Instruções de uso** — seção colapsável explicando como o AutoPilot funciona, o que é a zona neutra e como o delay humanizado evita detecção.

---

#### Tela CONFIGURAÇÕES — Reestruturação em Abas

As Configurações também serão divididas em abas para eliminar a lista longa e desorganizada atual.

---

##### Aba APP — Configurações do Aplicativo

Tudo relacionado à aparência e comportamento do app em si.

**Conteúdo:**
- Tema (claro, escuro, automático).
- Idioma (português, inglês — preparação para internacionalização).
- Manter tela ligada durante uso.
- Permissões do app (atalhos para as permissões do sistema: Acessibilidade, Notificações, Sobreposição, Localização).

---

##### Aba SISTEMA — Configurações do Motor Principal

Tudo relacionado às configurações que fazem o sistema principal do app funcionar, organizadas por ordem de prioridade operacional.

**Conteúdo (ordem de prioridade):**
1. **Proteção** — Ghost Mode, configurações de privacidade bancária, AAPM.
2. **Serviços** — Status e controle do AccessibilityService e NotificationListener.
3. **Overlay / Aparência** — Tamanho do card, fonte, posição, transparência.
4. **Botão Flutuante Lateral** — Toggle para ativar/desativar o botão lateral.
   > ⚠️ **Nota técnica:** O botão flutuante lateral é apenas um atalho visual de conveniência. Ao desativá-lo, **o sistema continua funcionando normalmente** — o AutoPilot, a detecção de corridas e o Ghost Mode não são afetados. O motorista pode ativar/desativar o app pelos controles dentro do próprio app ou pela notificação persistente.
5. **Status do Sistema** — Painel de diagnóstico mostrando o estado de todos os serviços ativos.

---

##### Aba ADICIONAIS — Recursos Complementares

Recursos de suporte e extensão do app.

**Conteúdo:**
- **Backup e Restauração** — Exportar e importar configurações e histórico.
- **Histórico** — O histórico de corridas é movido para cá (saindo da barra de navegação principal, liberando espaço para os 5 itens principais).
- Outros recursos complementares futuros (ex: exportação, relatórios).

---

#### Resumo das Mudanças de Navegação

| Tela / Recurso | Localização Atual | Nova Localização |
|---|---|---|
| Dashboard | Aba "Dashboard" | Aba "INICIO" (ícone casa, centro da barra) |
| Critérios | Aba "Critérios" (lista longa) | Aba "CRITÉRIOS" com 4 sub-abas |
| AutoPilot | Seção dentro de Critérios | **Aba 4 de Critérios** (destaque próprio) |
| Perfis | Seção no topo de Critérios | **Aba 1 de Critérios** + card na Home |
| Mapa de Zonas | Activity separada | **Aba 3 de Critérios** |
| Controle Financeiro | Botão no topo do Dashboard | Aba "FINANCEIRO" na barra inferior |
| Recursos Avançados | Botão no topo do Dashboard | Aba "CRITÉRIOS" > Aba 1 > Card IA |
| Histórico | Aba separada na barra inferior | **Aba ADICIONAIS** de Configurações |
| Backup | Card no meio de Configurações | **Aba ADICIONAIS** de Configurações |
| Configurações | Lista plana | 3 sub-abas: APP / SISTEMA / ADICIONAIS |

---

### O2. AppFunctions — Controle por Voz via Gemini
**Janela:** Outubro–Dezembro de 2026 — v7.0.0
**Versão alvo:** v7.0.0
**Contexto:** O Android 17 introduziu o AppFunctions, que permite que apps exponham funcionalidades como "tools" para agentes de IA como o Gemini. **Nenhum concorrente de app para motoristas implementou isso ainda.**

**O que o motorista ganha:** Controle completo do app por voz, sem tirar as mãos do volante.

| Comando de Voz | Ação no App |
|---|---|
| "Gemini, ativa o perfil Noturno" | Troca o perfil de critérios ativo |
| "Quanto ganhei hoje?" | Retorna ganhos do dia |
| "Qual minha projeção essa semana?" | Retorna projeção semanal |
| "Liga o AutoPilot" | Ativa o AutoPilot |
| "Configura pra só aceitar" | Define modo AutoPilot como Aceitar |
| "Inicia meu turno" | Inicia o ShiftManager |
| "Como tá meu turno?" | Retorna resumo do turno atual |
| "Ativa o modo fantasma" | Liga o Ghost Mode |

**Estratégia de implantação:**
- **Agora (preparação):** Criar a classe `AutoRoadAppFunctions`, anotar as funções com `@AppFunction`, testar via ADB.
- **Outubro 2026 (quando Gemini abrir):** Publicar atualização com integração ativa.
- **Marketing:** "O único app de motorista que funciona com o Gemini. Controle por voz, sem tirar as mãos do volante."

---

### O3. NPU On-Device — Inteligência Local Real
**Janela:** Janeiro–Março de 2027 — v7.1.0
**Versão alvo:** v7.1.0
**Contexto:** Com o LiteRT (antigo TensorFlow Lite) e aceleração NPU, é possível rodar modelos de machine learning diretamente no celular, sem enviar dados para nuvem — alinhado com a filosofia "Privacy First" do app.
**Aplicações:**
- Previsão de demanda: modelo treinado com histórico local que prevê horários de pico por região.
- Score adaptativo: ajustar pesos automaticamente com base no padrão real de aceitação/recusa do motorista.
- OCR local: ler valores da tela com modelo local em vez de regex frágil.
**Pré-requisito:** Base de dados real de pelo menos 3 meses de uso (disponível após lançamento comercial).

---

### O4. App Bubbles — Card Flutuante Nativo do Sistema
**Janela:** Março de 2027 — v7.2.0
**Versão alvo:** v7.2.0
**Contexto:** O Android 17 permite que qualquer app seja transformado em uma "bolha flutuante" nativa, sem necessidade da permissão `SYSTEM_ALERT_WINDOW`.
**Vantagem:** Melhor integração com o sistema, sem permissão especial que assusta usuários na instalação.
**Estratégia:** Oferecer como opção nas Configurações ("Usar card flutuante nativo do sistema") enquanto mantém o overlay customizado como padrão para compatibilidade com Android 14/15/16.

---

### O5. Handoff Multi-Device
**Janela:** Segundo semestre de 2027 — v8.0.0
**Versão alvo:** v8.0.0
**Contexto:** O Android 17 introduz o recurso "Continue On" que permite continuar uma tarefa em outro dispositivo.
**Escopo:** Implementar `setHandoffEnabled(true)` nas Activities principais e serializar o estado atual para transferência entre devices.

---

### O6. Complicação de Relógio (Wear OS)
**Janela:** Segundo semestre de 2027 — v8.0.0
**Versão alvo:** v8.0.0
**Escopo:** Criar uma complicação que exibe no relógio: ganho do turno atual, número de corridas aceitas, score da última corrida e status do AutoPilot.

---

## Categoria 4 — Integrações Externas (2027 em Diante)

### E1. Integração com WhatsApp/Telegram
**Janela:** Primeiro semestre de 2027 — v8.1.0
**Motivação:** Segurança do motorista. Ao aceitar uma corrida, o app envia automaticamente a placa, localização atual e destino para um contato de emergência.

### E2. Controle de Manutenção Preditiva
**Janela:** Primeiro semestre de 2027 — v8.1.0
**Motivação:** Rastrear quilometragem total percorrida e emitir alertas para troca de óleo, pastilhas de freio e pneus.

### E3. Dashboard Web (Opt-In)
**Janela:** Segundo semestre de 2027 — v8.2.0
**Motivação:** Para motoristas que desejam ver relatórios no computador. Sincronização segura ponta-a-ponta com backend leve, sempre com opt-in explícito e respeitando a filosofia "Privacy First".

---

## Visão Geral: Linha do Tempo

| Período | Versão | Foco Principal |
|---|---|---|
| **Imediatamente (Jun 2026)** | v6.2.0 ✅ | Flag AAPM, MemoryMonitor, Canal Primário NotificationListener |
| **Julho–Setembro 2026** | v6.3.0 | **Redesign completo** (nova UI, ícones, navegação, Critérios em abas) |
| **Agosto 2026** | v6.3.0 | Mapa de Zonas funcional, Ranking real |
| **Setembro 2026** | v6.3.0 | Learning Engine real, ProjectionEngine, Limpeza de código |
| **Outubro–Dezembro 2026** | v7.0.0 | Tutorial guiado + Central de Ajuda + AppFunctions + Gemini |
| **Janeiro–Março 2027** | v7.1.0 | NPU On-Device, Score adaptativo com ML local |
| **Março 2027** | v7.2.0 | App Bubbles nativo (opcional) |
| **Primeiro semestre 2027** | v8.0.0 | Handoff multi-device, Wear OS |
| **Segundo semestre 2027** | v8.1.0 | WhatsApp/Telegram, Manutenção preditiva |
| **Final 2027** | v8.2.0 | Dashboard Web opt-in |

---

## Plano Comercial: Modelo de Negócio e Precificação

### Contexto de Mercado

O Brasil conta com aproximadamente **1,72 milhão de motoristas de aplicativo** ativos (IBGE, 2024), com crescimento de 35% nos últimos dois anos. A renda líquida média desses trabalhadores gira em torno de **R$ 1.800 a R$ 3.700 mensais**, dependendo da jornada e da cidade. Esse perfil revela um público com renda moderada, altamente sensível a custos fixos recorrentes e que já convive com despesas obrigatórias de combustível, manutenção, seguro e taxa das plataformas (15% a 30% por corrida).

O principal concorrente direto, o **GigU**, cobra:

| Plano GigU | Valor | Equivalente Mensal |
|---|---|---|
| Mensal | R$ 12,90/mês | R$ 12,90 |
| Semestral | R$ 59,90 (10% off) | R$ 9,98 |
| Anual | R$ 99,90 (39% off) | R$ 8,32 |

O GigU possui mais de **1 milhão de downloads** e é o líder atual da categoria. Sua estratégia é baseada em assinatura recorrente, o que gera receita previsível para o negócio, mas cria uma "dor de custo" permanente para o motorista — especialmente nos meses em que ele trabalha menos.

---

### Proposta de Valor Diferenciada: "Pague Uma Vez, Use Para Sempre"

O NGB AutoRoad Privacy se posiciona de forma oposta ao mercado: **sem assinatura, sem mensalidade, sem surpresas na fatura**. A filosofia é simples — o motorista já paga taxa para a Uber, para a 99, para o combustível e para o seguro. O app que deveria ajudá-lo a ganhar mais não pode ser mais uma sangria mensal.

> "O motorista que usa o NGB AutoRoad paga uma única vez e recebe todas as atualizações futuras. Enquanto o concorrente cobra R$ 12,90 todo mês durante anos, o NGB custa o equivalente a **19 meses do GigU** — mas dura para sempre. Em menos de 2 anos, o motorista já economizou. Daí em diante, é lucro puro."

---

### Estrutura de Planos Recomendada

#### Plano Gratuito (Isca de Entrada)
O app é distribuído gratuitamente com funcionalidades básicas desbloqueadas, permitindo que o motorista experimente o valor antes de comprar.

| Recurso | Gratuito | Vitalício |
|---|---|---|
| Card de corrida com score | ✅ (limitado a 3 cards) | ✅ (35 cards) |
| Critérios básicos (distância, valor) | ✅ | ✅ |
| Histórico de corridas (últimas 30) | ✅ | ✅ (ilimitado) |
| Ghost Mode bancário | ✅ | ✅ |
| AutoPilot (aceitar/recusar automático) | ❌ | ✅ |
| Perfis de critérios salvos | ❌ | ✅ (até 5) |
| Controle financeiro completo | ❌ | ✅ |
| Projeção de ganhos e "E se?" | ❌ | ✅ |
| Mapa de zonas de risco | ❌ | ✅ |
| Exportação CSV/PDF | ❌ | ✅ |
| Integração com Gemini (AppFunctions) | ❌ | ✅ |
| Atualizações futuras | ✅ (correções) | ✅ (tudo) |

#### Plano Vitalício — Preço Único
**R$ 249,90** (pagamento único, sem recorrência)

Esse valor foi calculado com base nos seguintes critérios:

- **Ponto de equilíbrio em 20 meses:** O GigU cobra R$ 12,90/mês. Em 20 meses de assinatura, o motorista teria gasto R$ 258,00. Com o NGB, ele paga R$ 249,90 uma vez e nunca mais.
- **Retorno sobre investimento rápido:** Com o AutoPilot recusando apenas 2 corridas ruins por dia (~R$ 5,00/dia de economia), o app se paga em **menos de 50 dias** de uso.
- **Valor percebido premium:** R$ 249,90 posiciona o app como uma ferramenta profissional séria.
- **Sustentabilidade do negócio:** Com 1% do mercado brasileiro (17.200 motoristas), a receita seria de **R$ 4.298.280**.

#### Promoção de Lançamento (Primeiros 90 dias)
**R$ 149,90** — Oferta de fundador para os primeiros 500 compradores (40% de desconto). Cria urgência, gera os primeiros reviews e financia o desenvolvimento da Fase 1 do roadmap.

---

### Canais de Distribuição e Monetização

**Canal Principal — Google Play (In-App Purchase)**
A compra do Plano Vitalício é feita dentro do próprio app via `Google Play Billing`. O Google retém 15%. O desenvolvedor recebe 85% de cada venda.

| Cenário | Vendas/mês | Receita Bruta | Receita Líquida (85%) |
|---|---|---|---|
| Conservador | 100 | R$ 24.990 | R$ 21.242 |
| Realista | 500 | R$ 124.950 | R$ 106.208 |
| Otimista | 2.000 | R$ 499.800 | R$ 424.830 |

**Canal Secundário — Comunidades de Motoristas**
Grupos de WhatsApp e Telegram de motoristas são o principal vetor orgânico de divulgação. Código de desconto de R$ 30,00 para quem indicar pode acelerar o crescimento viral.

**Canal Terciário — YouTube e TikTok**
Parcerias com criadores de conteúdo do nicho de motoristas para demonstrações do app em funcionamento real.

---

### Estratégia de Sustentabilidade a Longo Prazo

**1. Volume e Crescimento Orgânico:** Com 1,72 milhão de motoristas no Brasil e crescimento de 35% ao ano, novos motoristas entram no mercado constantemente.

**2. Versões Futuras com Upgrade Opcional:** Quando funcionalidades de grande porte forem lançadas (ex: Dashboard Web, Integração WhatsApp, IA preditiva), poderá ser oferecido um "Upgrade v2" opcional por um valor simbólico (ex: R$ 49,90) para quem já possui o plano vitalício v1.

**3. Custo Operacional Baixo:** Por ser um app 100% local (sem servidor, sem banco de dados na nuvem), o custo de manutenção é essencialmente zero além do tempo de desenvolvimento.

---

### Comparativo Final: NGB AutoRoad vs. Concorrentes

| Critério | NGB AutoRoad Privacy | GigU | Apps Gratuitos |
|---|---|---|---|
| Modelo de cobrança | Pagamento único | Assinatura mensal | Gratuito (com anúncios) |
| Custo em 12 meses | R$ 249,90 | R$ 154,80 | R$ 0 (mas sem AutoPilot) |
| Custo em 24 meses | R$ 249,90 | R$ 309,60 | R$ 0 |
| Custo em 36 meses | R$ 249,90 | R$ 464,40 | R$ 0 |
| AutoPilot (aceitar/recusar) | ✅ | ❌ | ❌ |
| Ghost Mode bancário | ✅ | ❌ | ❌ |
| Privacidade (100% local) | ✅ | ❌ (dados na nuvem) | ❌ |
| Integração com Gemini | ✅ (v7.0) | ❌ | ❌ |
| Atualizações futuras | ✅ (incluídas) | ✅ (enquanto pagar) | ✅ |
| Perfis de critérios | ✅ (5 perfis) | ❌ | ❌ |
| Controle financeiro | ✅ (completo) | Parcial | Parcial |

---

*Roadmap elaborado com base na auditoria v6.1.0, análise de compatibilidade Android 17 e dados de mercado de junho de 2026. Última atualização: 20/06/2026.*
