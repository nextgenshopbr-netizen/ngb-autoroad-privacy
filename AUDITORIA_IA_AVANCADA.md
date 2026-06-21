# Auditoria Avançada — Módulo de Inteligência (IA) On-Device
**NGB AutoRoad v6.3.6**

Este documento detalha as vulnerabilidades lógicas encontradas no módulo de inteligência (IA) atual do aplicativo, bem como o projeto arquitetural para uma IA on-device super avançada, leve e sem dependência de nuvem.

---

## 1. Vulnerabilidades Lógicas Identificadas (Módulo a Módulo)

### 1.1 LocalLearningEngine (Aprendizado)
*   **Gargalo de Memória e UI Block**: O `LearningTab` usa `getAll()` para carregar todo o histórico de corridas para a memória. Em dispositivos médios com milhares de corridas, isso causa picos de consumo de memória e travamentos na interface.
*   **Race Condition no Seed**: O `seedFromDatabase` roda assincronamente (`scope.launch`), mas a UI usa um `delay(500)` fixo antes de gerar sugestões. Em celulares lentos, as sugestões são geradas antes do carregamento dos dados, resultando em "Dados insuficientes".
*   **Falta de Decaimento Temporal (Time Decay)**: Padrões antigos têm o mesmo peso que padrões recentes. Bairros que mudaram de perfil ou horários afetados por sazonalidade (ex: férias escolares) distorcem as sugestões atuais.
*   **Detecção de Fadiga Ingênua**: A fadiga é calculada dividindo o array de corridas ao meio, independentemente do tempo real decorrido. Se o motorista trabalha em turnos separados (manhã e noite), a "segunda metade" pode ser um dia totalmente diferente.

### 1.2 AutoPilotEngine (Decisão Automática)
*   **Concorrência Insegura (Race Condition)**: A flag `isProcessing` não é *thread-safe*. Duas corridas recebidas simultaneamente podem burlar o controle e causar ações conflitantes no serviço de acessibilidade.
*   **Ação Pendente "Presa"**: Não há um *timeout* robusto para a ação pendente. Se a tela mudar antes do botão ser clicado, a flag `isProcessing` pode ficar bloqueada permanentemente.
*   **Zona Neutra Estática**: O intervalo neutro (Score 60-74) é fixo. Não se adapta ao perfil do motorista. Motoristas muito exigentes deveriam ter uma zona neutra diferente de motoristas que aceitam a maioria das corridas.
*   **Falta de Feedback Loop**: O motor de decisão não aprende com os resultados. Se o AutoPilot aceita uma corrida e o motorista a cancela em seguida, o sistema não ajusta seus parâmetros.

### 1.3 RideScorer e ProjectionEngine (Matemática e Projeções)
*   **Thresholds Fixos (RideScorer)**: Os valores de normalização (ex: R$0.50 a R$2.50 por km) são fixos no código. Um motorista em uma cidade com tarifas mais baixas sempre terá scores ruins, invalidando a utilidade do AutoPilot.
*   **Normalização Linear Simples**: Não reflete a distribuição real (curva de sino) das corridas. Uma pequena variação no centro da curva deveria impactar o score mais do que variações nos extremos.
*   **Média Simples de 30 Dias (ProjectionEngine)**: Projeções financeiras usam média aritmética dos últimos 30 dias. Não consideram sazonalidade (dias da semana) nem dão peso maior aos dias recentes.

---

## 2. Pesquisa: IA On-Device para Android

Para tornar o aplicativo mais inteligente sem consumir memória excessiva, processador ou depender de nuvem (mantendo a privacidade e funcionando offline), as seguintes abordagens foram avaliadas [1] [2] [3]:

| Tecnologia / Abordagem | Peso no App (APK) | Consumo RAM | Precisão / Adaptabilidade | Adequação ao NGB AutoRoad |
| :--- | :--- | :--- | :--- | :--- |
| **LiteRT (TensorFlow Lite)** | ~2-5 MB (com modelo pequeno) | Médio | Alta (Redes Neurais, Random Forest) | Excelente para modelos tabulares pré-treinados, mas requer pipeline de treinamento externo. |
| **ONNX Runtime Mobile** | ~3-8 MB | Médio-Alto | Alta | Bom, mas focado mais em Deep Learning pesado, excessivo para dados tabulares simples. |
| **Google ML Kit** | ~0 MB (Usa Google Play Services) | Baixo | Limitada a modelos pré-definidos ou customizados via TFLite | Útil apenas se usar o módulo "Custom Models", caindo no mesmo caso do LiteRT. |
| **Heurísticas Estatísticas Avançadas (EWMA, Thompson Sampling)** | **~0 MB (Apenas código Kotlin)** | **Muito Baixo** | **Altíssima (Aprende em tempo real)** | **A ESCOLHA IDEAL**. Permite aprendizado contínuo on-device sem modelos pesados. |

---

## 3. Projeto Arquitetural: IA Local Super Avançada

Para otimizar o NGB AutoRoad para celulares médios a avançados, a solução ideal não é embarcar um modelo de rede neural pesado (como TFLite), mas sim implementar **Algoritmos de Aprendizado Online (Online Learning) e Estatística Adaptativa** diretamente em Kotlin. Isso garante zero aumento no tamanho do APK e processamento instantâneo [4] [5].

### 3.1 Adaptive Scoring via EWMA (Exponentially Weighted Moving Average)
Substituir os limites fixos (`ScoringThresholds`) por limites dinâmicos usando EWMA.
*   **Como funciona**: O sistema mantém uma média móvel dos valores (ex: R$/km) das últimas corridas, dando mais peso (ex: 80%) às corridas mais recentes.
*   **Benefício**: O score se adapta automaticamente à cidade do motorista, ao horário do dia (tarifa dinâmica) e à inflação, sem precisar de configuração manual.

### 3.2 Decisão Automática via Thompson Sampling (Contextual Bandits)
Melhorar o `AutoPilotEngine` para aprender quais tipos de corrida o motorista prefere.
*   **Como funciona**: Em vez de usar apenas a regra "Score > X = Aceita", o sistema usa um algoritmo *Multi-Armed Bandit* (Thompson Sampling). Ele observa o contexto (hora, bairro, R$/km) e a ação do motorista (Aceitou/Recusou).
*   **Benefício**: A IA aprende o "gosto" do motorista. Se o motorista sempre recusa corridas para o bairro "X" à noite, mesmo com score alto, a IA ajusta a probabilidade e passa a auto-recusar ou alertar.

### 3.3 Motor de Sugestões Baseado em Decaimento Temporal (Time Decay)
Corrigir o `LocalLearningEngine` para não tratar todo o histórico igualmente.
*   **Como funciona**: Ao agregar dados de bairros e horários, aplicar um fator de decaimento (ex: `peso = e^(-k * dias_passados)`). Corridas de ontem valem 1.0, corridas de 3 meses atrás valem 0.1.
*   **Benefício**: As sugestões ("Melhores Horários", "Melhores Bairros") refletem a realidade *atual* do mercado, não a média histórica.

### 3.4 Otimização de Banco de Dados (Paginação e Projeção)
*   Substituir `getAll()` por consultas SQL agregadas (`GROUP BY`) ou paginação (`LIMIT/OFFSET`).
*   O banco SQLite (Room) fará o trabalho pesado em C++ nativo, retornando apenas os resultados consolidados para o Kotlin, eliminando os picos de memória (OOM) na interface de IA.

---

## 4. Plano de Ação (Roadmap de Implementação)

Para aplicar estas melhorias sem quebrar o app atual, a implementação deve ser feita em etapas:

1.  **Refatoração de Dados (Segurança e Performance)**
    *   Substituir queries `getAll()` no `LearningTab` e `RankingTab` por queries agregadas no `RideHistoryDao`.
    *   Tornar o `AutoPilotEngine` thread-safe com blocos `synchronized` e timeouts estritos para ações de acessibilidade.
2.  **Implementação do Adaptive Scoring (EWMA)**
    *   Criar classe `AdaptiveScoringEngine`.
    *   Atualizar o `RideScorer` para consumir limites dinâmicos em vez dos estáticos do `ScoringThresholds`.
3.  **Implementação do Time Decay no LocalLearning**
    *   Reescrever a lógica de geração de sugestões para aplicar peso baseado na data da corrida.
4.  **AutoPilot Inteligente (Feedback Loop)**
    *   Adicionar mecanismo para o `RideLifecycleManager` informar ao `AutoPilotEngine` se uma corrida auto-aceitada foi posteriormente cancelada pelo motorista, ajustando os pesos de decisão.

---
### Referências

[1] Google Developers. (2025). LiteRT: Maximum performance, simplified. https://developers.googleblog.com/litert-maximum-performance-simplified/
[2] Microsoft. (n.d.). ONNX Runtime Mobile. https://onnxruntime.ai/docs/tutorials/mobile/
[3] Google Developers. (n.d.). ML Kit Guides. https://developers.google.com/ml-kit/guides
[4] Wikipedia. (n.d.). Multi-armed bandit. https://en.wikipedia.org/wiki/Multi-armed_bandit
[5] Nature. (2024). A novel EWMA-based adaptive control chart for industrial monitoring. https://www.nature.com/articles/s41598-024-83780-y
