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
