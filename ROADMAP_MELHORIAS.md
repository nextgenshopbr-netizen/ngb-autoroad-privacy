# Roadmap de Melhorias Futuras: NGB AutoRoad Privacy

Este documento estabelece o roteiro de evolução do NGB AutoRoad Privacy, organizado em fases de curto, médio e longo prazo. As melhorias visam consolidar a estabilidade da versão 6.1.1, substituir recursos simulados (mocks) por inteligência real baseada nos dados do motorista, e introduzir um sistema de onboarding para maximizar o engajamento de novos usuários.

---

## Fase 1: Onboarding e Guias (Curto Prazo)
O foco desta fase é reduzir a curva de aprendizado para novos motoristas, explicando de forma interativa como as funcionalidades protegem e otimizam seus ganhos.

### 1. Tutorial Guiado Interativo (Onboarding)
- **Tela de Boas-Vindas:** Apresentação dos pilares do app (Privacidade, AutoPilot, Controle Financeiro).
- **Tour pelas Telas Principais:** Utilização de um componente de overlay (ex: `ShowcaseView` ou tooltips nativos do Jetpack Compose) para destacar botões e explicar funções no primeiro acesso.
  - *Dashboard:* Explicar o card de turno e o resumo de ganhos.
  - *Critérios:* Mostrar como distribuir os 100 pontos e como os perfis funcionam.
  - *AutoPilot:* Explicar o funcionamento da "Zona Neutra" e do delay humanizado.
- **Armazenamento de Estado:** Variável no `PrefsManager` (`hasCompletedTutorial`) para garantir que o tour seja exibido apenas uma vez, com opção de reprisar via aba de Configurações.

### 2. Central de Ajuda Integrada
- **Guia de Bolso:** Ícone de ajuda (`?`) no topo de cada aba que abre um modal ou bottom sheet explicativo sobre a tela atual.
- **FAQ Dinâmico:** Seção nas configurações com respostas para dúvidas comuns (ex: "Por que o AutoPilot não clicou?", "Como o Ghost Mode protege meu banco?").

---

## Fase 2: Substituição de Mocks e Inteligência Real (Médio Prazo)
A versão atual possui uma aba de "Recursos Avançados" (`FeaturesActivity`) que exibe dados falsos (mocks). Esta fase conecta essas telas ao banco de dados real.

### 1. Mapa de Zonas Funcional (ZoneMapActivity)
- **Integração Real:** Conectar os polígonos desenhados no mapa ao `RideScorer`.
- **Lógica:** Implementar algoritmo *Point-in-Polygon* (Ray-casting) para verificar se as coordenadas de origem ou destino da corrida detectada caem dentro das áreas vermelhas desenhadas pelo motorista.
- **Penalidade:** Aplicar redução drástica no score (ex: -50 pontos) para corridas que terminam em zonas de risco.

### 2. Ranking e Relatórios Reais (FeaturesActivity)
- **RankingTab:** Substituir a lista estática de bairros por uma agregação SQL (`GROUP BY`) no `RideHistoryDao`, ordenando os bairros reais onde o motorista mais pegou corridas ou onde obteve o melhor valor por km.
- **ExportTab:** Substituir o gerador de CSV estático por um exportador real que lê o `AppDatabase` e gera planilhas verdadeiras para a contabilidade do motorista.
- **ReportTab:** Conectar o gerador de PDF aos ganhos consolidados do `FinanceDatabase`, criando resumos semanais ou mensais reais.

### 3. Local Learning Engine Verdadeiro
- **Fim dos Padrões Artificiais:** O `LocalLearningEngine` atualmente injeta 50 corridas fictícias. A melhoria consistirá em treiná-lo com o histórico real (`RideHistoryDao`).
- **Sugestões Ativas:** Se o motorista recusa consistentemente corridas para um bairro específico, o app deve sugerir automaticamente a adição desse bairro à lista de bloqueios na aba de Critérios.

---

## Fase 3: Aprimoramento Financeiro e de Lifecycle (Médio Prazo)
O ciclo de vida da corrida foi estabilizado na v6.1.1, mas a camada financeira e de projeção precisa ser ajustada para refletir apenas a realidade.

### 1. Refatoração da ProjectionEngine
- **Projeções Realistas:** O método `simulateWhatIf()` atualmente usa `allRides` (incluindo recusadas e canceladas) para calcular ganhos reais. Deve ser ajustado para filtrar estritamente por `status == "COMPLETED"`.
- **Remoção de Dados Fantasmas:** Remover a lógica que força uma média mínima de 1 corrida/dia (`coerceAtLeast(1)`), o que gera projeções infladas para motoristas que não trabalharam.

### 2. Limpeza de Código Morto (VehicleTab)
- **Remoção do Legado:** O arquivo `FinanceActivity.kt` contém um `VehicleTab` inteiro (linhas 937-1144) que nunca é chamado, pois a navegação utiliza o `VehicleProfilesTab` de `FinanceExtTabs.kt`. Esse código morto deve ser removido para reduzir o tamanho do APK e o tempo de compilação.

### 3. Unificação da Deduplicação de Corridas
- **Problema Atual:** Existem três sistemas de deduplicação independentes (`RideAccessibilityService`, `OverlayService` e `RideNotificationListener`), o que pode gerar conflitos.
- **Solução:** Centralizar a lógica de hash e deduplicação de corridas no `RideLifecycleManager`, que atuará como a única fonte da verdade para o estado da corrida atual.

---

## Fase 4: Integrações Externas e Escalabilidade (Longo Prazo)
Expansão das capacidades do aplicativo para interagir com o ambiente externo e oferecer mais comodidade.

### 1. Integração com WhatsApp/Telegram
- **Mensagem de Segurança Automática:** Ao aceitar uma corrida (status `ACCEPTED`), o app pode enviar automaticamente a placa do carro, localização atual e destino para um contato de emergência via intent do WhatsApp.

### 2. Controle de Manutenção Preditiva
- **Aba de Veículos Aprimorada:** O módulo financeiro atual já possui perfis de veículos. A melhoria consistirá em rastrear a quilometragem total percorrida (somando o `dropoffDistance` das corridas `COMPLETED`) e emitir alertas automáticos para troca de óleo, pastilhas de freio e pneus.

### 3. Dashboard Web para Motoristas
- **Sincronização Nuvem (Opcional):** Para motoristas que desejam ver seus relatórios no computador, implementar uma sincronização segura ponta-a-ponta com um backend leve (ex: Firebase ou Supabase), permitindo visualizar o Dashboard em uma interface web. (Sempre respeitando a filosofia "Privacy First" do app, com opt-in explícito).

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
| Atualizações futuras | ✅ (incluídas) | ✅ (enquanto pagar) | ✅ |
| Perfis de critérios | ✅ (5 perfis) | ❌ | ❌ |
| Controle financeiro | ✅ (completo) | Parcial | Parcial |

---

*Plano comercial elaborado com base em dados de mercado de junho de 2026. Valores e estratégias sujeitos a revisão conforme evolução do produto e do mercado.*
