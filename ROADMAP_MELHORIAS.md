# Roadmap de Melhorias: NGB AutoRoad Privacy

Este documento estabelece o roteiro completo de evolução do NGB AutoRoad Privacy, organizado por **prioridade de risco e impacto**, com datas estimadas e janelas de implantação para cada item. Os recursos estão divididos em quatro categorias: Críticos (riscos ativos que precisam de ação imediata), Importantes (melhorias de estabilidade e qualidade), Oportunidades (novos recursos que ampliam o diferencial competitivo) e Visão de Longo Prazo (expansões futuras).

---

## Categoria 1 — Críticos: Ação Imediata

Estes itens representam **riscos ativos** que afetam usuários hoje ou afetarão em semanas. Não há justificativa para adiá-los.

### C1. Flag `isAccessibilityTool` no Manifest
**Janela:** Imediatamente — próximo commit (esta semana)
**Versão alvo:** v6.1.2 (hotfix)
**Risco sem ação:** Qualquer motorista que ative o Advanced Protection Mode (AAPM) do Android 16/17 tem o `RideAccessibilityService` **bloqueado automaticamente** pelo sistema. O AutoPilot, a detecção de corridas e o click automático param de funcionar sem aviso.
**Ação:** Adicionar `android:isAccessibilityTool="true"` na declaração do `RideAccessibilityService` no `AndroidManifest.xml`. É uma única linha de código que elimina o risco imediatamente.

```xml
<service android:name=".service.RideAccessibilityService"
    android:isAccessibilityTool="true"
    ... />
```

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
**Versão alvo:** v6.2.0
**Risco sem ação:** O Android 17 (lançado em 16/06/2026) impõe limites rígidos de RAM por app baseados na memória total do dispositivo. Em celulares com 4 GB de RAM (comuns entre motoristas de aplicativo), o app pode ser encerrado abruptamente pelo sistema durante o monitoramento de corridas.
**Ações:**
- Implementar profiling de memória com `Debug.MemoryInfo` para medir consumo real em background.
- Aplicar lazy loading no `CardGallery` (35 cards carregados simultaneamente na memória).
- Monitorar `ApplicationExitInfo` para detectar e registrar kills por excesso de memória.
- Otimizar `ZoneMapActivity` e `FinanceDatabase` para carregamento sob demanda.

---

### C5. Rota Alternativa ao AccessibilityService (Plano B para AAPM)
**Janela:** Agosto de 2026 — v6.2.0
**Versão alvo:** v6.2.0
**Risco sem ação:** Mesmo com a flag `isAccessibilityTool`, o Google pode revisar sua política e reclassificar o app. Ter o `NotificationListenerService` como canal primário de detecção garante que o app continue funcionando mesmo que o AccessibilityService seja revogado.
**Ação:** Migrar a detecção de corridas para o `NotificationListenerService` como canal primário (já existente no app). O AccessibilityService passa a ser canal secundário, responsável apenas pelo parsing avançado de texto na tela e pelo click automático do AutoPilot.

---

## Categoria 2 — Importantes: Próximas Versões (Julho–Setembro 2026)

Estes itens não representam risco imediato, mas impactam diretamente a qualidade e a confiança do motorista no app.

### I1. Tutorial Guiado Interativo (Onboarding)
**Janela:** Julho de 2026 — v6.2.0
**Versão alvo:** v6.2.0
**Motivação:** Novos motoristas que instalam o app ficam perdidos sem entender como distribuir os 100 pontos de critério, o que é a Zona Neutra do AutoPilot ou como o Ghost Mode funciona. Um tutorial interativo no primeiro acesso reduz o churn e aumenta a conversão para o plano pago.
**Escopo:**
- Tela de boas-vindas com os 3 pilares (Privacidade, AutoPilot, Controle Financeiro).
- Tour interativo por cada aba com tooltips destacando os elementos principais.
- Flag `hasCompletedTutorial` no `PrefsManager` para exibir apenas uma vez.
- Botão "Reprisar Tutorial" nas Configurações.

### I2. Central de Ajuda Integrada
**Janela:** Julho de 2026 — v6.2.0
**Versão alvo:** v6.2.0
**Motivação:** Reduzir dúvidas recorrentes ("Por que o AutoPilot não clicou?", "Como o Ghost Mode protege meu banco?") sem depender de suporte externo.
**Escopo:** Ícone `?` no topo de cada aba que abre um bottom sheet explicativo. Seção de FAQ dinâmico nas Configurações.

### I3. Mapa de Zonas Funcional
**Janela:** Agosto de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** O motorista desenha áreas de risco no mapa, mas o app não usa esses desenhos para nada. É um recurso que parece funcionar mas não tem efeito real no score.
**Ação:** Implementar algoritmo Point-in-Polygon (Ray-casting) para verificar se as coordenadas da corrida detectada caem dentro das zonas desenhadas. Aplicar penalidade de -50 pontos no score para corridas com destino em zona de risco.

### I4. Ranking e Relatórios Reais
**Janela:** Agosto de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** A aba de Recursos Avançados exibe dados completamente falsos (bairros fictícios, valores inventados). Isso destrói a credibilidade do app se o motorista perceber.
**Ações:**
- `RankingTab`: Substituir lista estática por `GROUP BY` no `RideHistoryDao`.
- `ExportTab`: Gerar CSV real a partir do `AppDatabase`.
- `ReportTab`: Conectar gerador de PDF ao `FinanceDatabase`.

### I5. Local Learning Engine com Dados Reais
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Motivação:** O `LocalLearningEngine` injeta 50 corridas fictícias para treinar o modelo. Com histórico real, as sugestões passam a refletir o padrão real do motorista.
**Ação:** Treinar o engine com dados do `RideHistoryDao`. Adicionar sugestão automática de bloqueio de bairros onde o motorista recusa corridas consistentemente.

### I6. Refatoração da ProjectionEngine
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Ações:**
- Filtrar `simulateWhatIf()` estritamente por `status == "COMPLETED"`.
- Remover `coerceAtLeast(1)` que gera projeções infladas.

### I7. Limpeza de Código Morto e Unificação de Deduplicação
**Janela:** Setembro de 2026 — v6.3.0
**Versão alvo:** v6.3.0
**Ações:**
- Remover `VehicleTab` legado (linhas 937-1144 do `FinanceActivity.kt`).
- Centralizar lógica de hash e deduplicação no `RideLifecycleManager`.

---

## Categoria 3 — Oportunidades: Diferencial Competitivo (Outubro 2026–Março 2027)

Estes itens não são urgentes, mas representam o maior potencial de diferenciação do NGB AutoRoad em relação a todos os concorrentes.

### O1. AppFunctions — Controle por Voz via Gemini
**Janela:** Outubro–Dezembro de 2026 — v7.0.0
**Versão alvo:** v7.0.0
**Contexto:** O Android 17 introduziu o AppFunctions, que permite que apps exponham funcionalidades como "tools" para agentes de IA como o Gemini. A integração com o Gemini está em private preview (junho 2026) e deve abrir para todos os desenvolvedores no segundo semestre de 2026. **Nenhum concorrente de app para motoristas implementou isso ainda.**

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

### O2. NPU On-Device — Inteligência Local Real
**Janela:** Janeiro–Março de 2027 — v7.1.0
**Versão alvo:** v7.1.0
**Contexto:** O Android 17 exige declaração de `android.hardware.npu` para apps que usam a Neural Processing Unit. Com o LiteRT (antigo TensorFlow Lite) e aceleração NPU, é possível rodar modelos de machine learning diretamente no celular, sem enviar dados para nuvem — alinhado com a filosofia "Privacy First" do app.
**Aplicações:**
- Previsão de demanda: modelo treinado com histórico local que prevê horários de pico por região.
- Score adaptativo: ajustar pesos automaticamente com base no padrão real de aceitação/recusa do motorista.
- OCR local: ler valores da tela com modelo local em vez de regex frágil, aumentando a precisão da detecção.
**Pré-requisito:** Base de dados real de pelo menos 3 meses de uso (disponível após lançamento comercial).

---

### O3. App Bubbles — Card Flutuante Nativo do Sistema
**Janela:** Março de 2027 — v7.2.0
**Versão alvo:** v7.2.0
**Contexto:** O Android 17 permite que qualquer app seja transformado em uma "bolha flutuante" nativa, sem necessidade da permissão `SYSTEM_ALERT_WINDOW`. Isso é exatamente o que o NGB AutoRoad já faz com o `OverlayService`, mas com uma solução customizada que depende de permissão especial.
**Vantagem:** Melhor integração com o sistema, gerenciamento automático de posição e ciclo de vida pelo Android, sem necessidade de permissão especial que assusta usuários na instalação.
**Estratégia:** Oferecer como opção nas Configurações ("Usar card flutuante nativo do sistema") enquanto mantém o overlay customizado como padrão para compatibilidade com Android 14/15/16.

---

### O4. Handoff Multi-Device
**Janela:** Segundo semestre de 2027 — v8.0.0
**Versão alvo:** v8.0.0
**Contexto:** O Android 17 introduz o recurso "Continue On" que permite continuar uma tarefa em outro dispositivo. Para o motorista, isso significa verificar o Dashboard no tablet com tela maior enquanto o celular está no suporte do carro, ou configurar perfis e critérios no computador.
**Escopo:** Implementar `setHandoffEnabled(true)` nas Activities principais e serializar o estado atual para transferência entre devices.

---

### O5. Complicação de Relógio (Wear OS)
**Janela:** Segundo semestre de 2027 — v8.0.0
**Versão alvo:** v8.0.0
**Contexto:** O Android 17 introduz o `MetricStyle` template para Wear OS, suportando métricas de viagens e timers.
**Escopo:** Criar uma complicação que exibe no relógio: ganho do turno atual, número de corridas aceitas, score da última corrida e status do AutoPilot.

---

## Categoria 4 — Integrações Externas (2027 em Diante)

### E1. Integração com WhatsApp/Telegram
**Janela:** Primeiro semestre de 2027 — v8.1.0
**Motivação:** Segurança do motorista. Ao aceitar uma corrida, o app envia automaticamente a placa, localização atual e destino para um contato de emergência.

### E2. Controle de Manutenção Preditiva
**Janela:** Primeiro semestre de 2027 — v8.1.0
**Motivação:** Rastrear quilometragem total percorrida (somando `dropoffDistance` das corridas `COMPLETED`) e emitir alertas para troca de óleo, pastilhas de freio e pneus.

### E3. Dashboard Web (Opt-In)
**Janela:** Segundo semestre de 2027 — v8.2.0
**Motivação:** Para motoristas que desejam ver relatórios no computador. Sincronização segura ponta-a-ponta com backend leve (Firebase ou Supabase), sempre com opt-in explícito e respeitando a filosofia "Privacy First".

---

## Visão Geral: Linha do Tempo

| Período | Versão | Foco Principal |
|---|---|---|
| **Imediatamente (Jun 2026)** | v6.1.2 (hotfix) | Flag `isAccessibilityTool` — 1 linha, risco eliminado |
| **Julho 2026** | v6.2.0 | Tutorial, Central de Ajuda, Otimização de Memória |
| **Agosto 2026** | v6.3.0 | Mapa de Zonas funcional, Ranking real, Rota alternativa AccessibilityService |
| **Setembro 2026** | v6.3.0 | Learning Engine real, ProjectionEngine, Limpeza de código |
| **Outubro–Dezembro 2026** | v7.0.0 | AppFunctions + Integração Gemini (controle por voz) |
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

- **Ponto de equilíbrio em 20 meses:** O GigU cobra R$ 12,90/mês. Em 20 meses de assinatura, o motorista teria gasto R$ 258,00. Com o NGB, ele paga R$ 249,90 uma vez e nunca mais. A partir do 20º mês, cada centavo que ele deixa de pagar ao concorrente é economia real.
- **Retorno sobre investimento rápido:** Com o AutoPilot recusando apenas 2 corridas ruins por dia (economia de ~R$ 5,00/dia em tempo e combustível desperdiçados), o app se paga em **menos de 50 dias** de uso — e continua gerando economia por anos.
- **Valor percebido premium:** R$ 249,90 posiciona o app como uma ferramenta profissional séria, não como um "appzinho barato". Motoristas que investem nesse valor tendem a usar o app com mais disciplina e obter melhores resultados.
- **Sustentabilidade do negócio:** Com 1% do mercado brasileiro (17.200 motoristas), a receita seria de **R$ 4.298.280** — garantindo desenvolvimento contínuo por anos sem depender de assinatura.

#### Promoção de Lançamento (Primeiros 90 dias)
**R$ 149,90** — Oferta de fundador para os primeiros 500 compradores (40% de desconto). Cria urgência, gera os primeiros reviews e financia o desenvolvimento da Fase 1 do roadmap. Após os 500, o preço volta ao valor cheio de R$ 249,90.

---

### Canais de Distribuição e Monetização

**Canal Principal — Google Play (In-App Purchase)**
A compra do Plano Vitalício é feita dentro do próprio app via `Google Play Billing`. O Google retém 15% (taxa reduzida para apps com receita abaixo de US$ 1 milhão/ano). O desenvolvedor recebe 85% de cada venda.

| Cenário | Vendas/mês | Receita Bruta | Receita Líquida (85%) |
|---|---|---|---|
| Conservador | 100 | R$ 24.990 | R$ 21.242 |
| Realista | 500 | R$ 124.950 | R$ 106.208 |
| Otimista | 2.000 | R$ 499.800 | R$ 424.830 |

**Canal Secundário — Comunidades de Motoristas**
Grupos de WhatsApp e Telegram de motoristas são o principal vetor orgânico de divulgação. Uma estratégia de "indique e ganhe" (ex: código de desconto de R$ 30,00 para quem indicar) pode acelerar o crescimento viral sem custo de mídia paga.

**Canal Terciário — YouTube e TikTok**
Parcerias com criadores de conteúdo do nicho de motoristas (canais com 10k-100k seguidores) para demonstrações do app em funcionamento real. O formato "veja quanto eu ganhei a mais usando o NGB" tem alto potencial de conversão nesse público.

---

### Estratégia de Sustentabilidade a Longo Prazo

O modelo vitalício levanta uma questão legítima: como sustentar o desenvolvimento sem receita recorrente? A resposta está em três pilares:

**1. Volume e Crescimento Orgânico:** Com 1,72 milhão de motoristas no Brasil e crescimento de 35% ao ano, novos motoristas entram no mercado constantemente. Cada novo motorista é um potencial comprador do plano vitalício.

**2. Versões Futuras com Upgrade Opcional:** Quando funcionalidades de grande porte forem lançadas (ex: Dashboard Web, Integração WhatsApp, IA preditiva), poderá ser oferecido um "Upgrade v2" opcional por um valor simbólico (ex: R$ 49,90) para quem já possui o plano vitalício v1. Motoristas que compraram na fundação sempre terão prioridade e desconto.

**3. Custo Operacional Baixo:** Por ser um app 100% local (sem servidor, sem banco de dados na nuvem, sem custos de infraestrutura), o custo de manutenção é essencialmente zero além do tempo de desenvolvimento. Isso torna o modelo vitalício viável onde seria inviável para um SaaS tradicional.

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
