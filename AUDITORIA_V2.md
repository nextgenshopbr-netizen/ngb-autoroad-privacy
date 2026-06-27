# Relatório de Auditoria Avançada (Fase 1) — NGB AutoRoad Privacy

Este documento detalha os achados da auditoria de qualidade (QA) realizada no código fonte do aplicativo **NGB AutoRoad Privacy**. As falhas foram analisadas sob os critérios de lógica, integridade matemática/financeira, gerenciamento de memória (vazamentos), conformidade com as diretrizes do Android 17 (incluindo segurança de background e consumo de bateria) e consistência entre módulos de inteligência artificial.

As falhas estão classificadas de acordo com as seguintes prioridades:
- **CRÍTICO:** Inviabiliza a funcionalidade, causa vazamentos graves de hardware ou viola severamente as regras do sistema operacional Android.
- **ALTO:** Causa erros operacionais significativos, falhas de lógica em cenários comuns ou inutiliza motores de IA importantes.
- **MÉDIO:** Inconsistências de dados entre módulos, cálculos financeiros imprecisos ou código ineficiente.
- **BAIXO:** Oportunidades de refatoração, redundâncias ou pequenos desvios sem impacto imediato.

---

## 🚨 1. Problemas de Nível: CRÍTICO

### 1.1. Instanciação Redundante e Vazamento de Sensores no `RideLifecycleManager`
- **Módulo afetado:** [RideLifecycleManager.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/service/RideLifecycleManager.kt#L246-L251)
- **Problema:** Nas funções `onRideAccepted()`, `onRideCompleted()` e `validateRideKm()`, o sistema instancia localmente o motor de GPS: `val gps = GpsTrackingEngine(context)`. Cada vez que este construtor é chamado, ele obtém referências aos sensores do sistema (`SensorManager`, `LocationManager`). Como as instâncias são criadas dentro de métodos locais e perdem a referência sem que `stopTracking()` (que limpa os listeners) seja chamado para cada uma delas, os listeners de localização e acelerômetro ficam registrados na memória do sistema.
- **Impacto:** Vazamento massivo de listeners de localização e sensores em background, causando rápido consumo de bateria e risco iminente de ANR (App Not Responding) ou interrupção silenciosa do aplicativo pelo Android por abuso de hardware.
- **Solução proposta:** Tornar o `GpsTrackingEngine` um Singleton gerenciado centralmente ou acoplá-lo diretamente ao ciclo de vida do `RideAccessibilityService` (que é o FGS principal), reutilizando a mesma instância para todas as chamadas do ciclo de vida.

### 1.2. Rastreador GPS do Turno Nunca Inicia (Código Morto de Hardware)
- **Módulo afetado:** [GpsTrackingEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/GpsTrackingEngine.kt#L101-L147)
- **Problema:** As funções `startTracking()` (que registra os listeners no `LocationManager` e acelerômetro) e `stopTracking()` (que remove a inscrição) **nunca são invocadas** em nenhuma parte do aplicativo. O `RideLifecycleManager` apenas chama `gps.startRide()` e `gps.endRide()`, que alteram flags booleanas de estado e salvam SharedPreferences, mas não acionam os listeners de localização reais.
- **Impacto:** O KM real do turno medido via GPS é sempre `0.0`, impossibilitando a validação de subfaturamento da Uber ou o cálculo preciso do DRE baseado em KM real.
- **Solução proposta:** Chamar `startTracking()` ao iniciar o turno em `ShiftManager` e `stopTracking()` ao finalizar o turno.

---

## ⚠️ 2. Problemas de Nível: ALTO

### 2.1. Descarte e Reset de Estado do Odômetro por Falta de Carregamento
- **Módulo afetado:** [GpsTrackingEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/GpsTrackingEngine.kt#L464-L479)
- **Problema:** O método `loadState()` do `GpsTrackingEngine` (que carrega as distâncias e estados persistidos nas SharedPreferences) nunca é chamado no construtor da classe. Como o `RideLifecycleManager` cria uma nova instância de `GpsTrackingEngine` a cada transição de corrida, o estado do GPS começa sempre com valores zerados. Ao chamar `startRide()`, o método `saveState()` é chamado, gravando valores zerados no arquivo de preferências e sobrescrevendo qualquer progresso real acumulado no turno.
- **Impacto:** O progresso do odômetro e os KMs rastreados são apagados e zerados a cada nova corrida aceita.
- **Solução proposta:** Chamar `loadState()` dentro do bloco `init` do `GpsTrackingEngine` para restaurar o estado persistido sempre que a classe for instanciada.

### 2.2. Violação de Diretriz de Background Location (Android 14+ / Android 17)
- **Módulo afetado:** [AndroidManifest.xml](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/AndroidManifest.xml) e [GpsTrackingEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/GpsTrackingEngine.kt#L115-L123)
- **Problema:** O `GpsTrackingEngine` solicita atualizações de localização diretamente no `GPS_PROVIDER` de forma ativa com um intervalo de 3 segundos (`MIN_TIME_MS = 3000L`). No entanto, o `OverlayService` (onde roda o processamento) ou o serviço de acessibilidade correspondente não declara o tipo de serviço de primeiro tempo de localização (`android:foregroundServiceType="location"`) no Manifesto.
- **Impacto:** No Android 14+ e Android 17, o sistema lançará uma `SecurityException` ao tentar registrar o provedor de GPS enquanto o aplicativo estiver em background (ou quando a tela apagar), fazendo o rastreamento falhar silenciosamente (try-catch do `GpsTrackingEngine` apenas engole o erro e faz log).
- **Solução proposta:** Adicionar `android:foregroundServiceType="location"` no serviço persistente adequado no `AndroidManifest.xml` e garantir que o fluxo de permissões exija `ACCESS_BACKGROUND_LOCATION` adequadamente.

### 2.3. Deflação Matemática no Cálculo do Cenário "Mescla Ideal" (Projeções)
- **Módulo afetado:** [ProjectionEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/ProjectionEngine.kt#L231-L240)
- **Problema:** Para projetar os ganhos na "Mescla Ideal", o sistema calcula a média ponderada: `mixedAvgValue = (goodRides.averageOrZero() * 0.7) + (avgRides.averageOrZero() * 0.2) + (badRides.averageOrZero() * 0.1)`. No entanto, se o motorista não tiver feito nenhuma corrida em alguma das categorias nos últimos 30 dias (ex: zero corridas ruins), `averageOrZero()` retorna `0.0`. O peso daquela categoria é simplesmente zerado, sem que os pesos das outras categorias sejam proporcionalmente re-normalizados para somarem 1.0.
- **Impacto:** Se o motorista for muito eficiente e tiver 0 corridas ruins, a projeção de ganhos do cenário "Mescla Ideal" será deflacionada em 10%, exibindo estimativas menores do que a realidade. Se ele tiver apenas corridas médias, a projeção exibirá apenas 20% do valor real (queda de 80%).
- **Solução proposta:** Implementar um cálculo adaptativo que distribua o peso das categorias vazias proporcionalmente entre as categorias que possuem dados de corrida reais.

### 2.4. IA Central de Decisão Consome Tabela de Configuração Obsoleta
- **Módulo afetado:** [AiBrainRepository.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/ai/AiBrainRepository.kt#L71-L75)
- **Problema:** A IA Central de decisões (`AiBrainRepository`) lê os dados do veículo da tabela obsoleta `vehicle_config` (`getConfigSync()`) em vez de obter o veículo ativo na tabela moderna `vehicle_profiles` (`getActiveVehicleSync()`), criada na migração v4.3.0. Como o painel financeiro agora gerencia múltiplos perfis de veículos apenas na nova tabela, a IA lerá dados zerados da tabela obsoleta.
- **Impacto:** A recomendação diária exibida na tela da IA (`Mantenha os ganhos acima de R$ .../km`) calculará o custo por km como `0.0`, sugerindo valores errados ou desatualizados ao motorista.
- **Solução proposta:** Atualizar a query no `AiBrainRepository` para obter o perfil ativo da tabela `vehicle_profiles` via `vehicleProfileDao`.

### 2.5. IA de Volta Vazia Desconectada do Aprendizado Local (Código Morto)
- **Módulo afetado:** [ReturnFactorEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/ReturnFactorEngine.kt#L73) e [RideScorer.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/RideScorer.kt#L350)
- **Problema:** O `ReturnFactorEngine` (motor de aprendizado estatístico off-line de volta vazia por bairro que salva dados em SharedPreferences) nunca é instanciado ou chamado em todo o projeto. Em seu lugar, o `RideScorer` chama a classe estática de fallback `ReturnFactorEngineStatic.calculateReturnPenalty`, que aplica uma fórmula matemática genérica baseada apenas na distância total do trajeto.
- **Impacto:** A inteligência de aprendizado local de volta vazia é código morto. O aplicativo falha em aprender se um bairro específico é bom ou ruim para retornos, aplicando a mesma penalidade genérica para bairros lucrativos e áreas isoladas com a mesma distância.
- **Solução proposta:** Instanciar o `ReturnFactorEngine` e chamá-lo no `RideScorer` para obter o fator real aprendido do bairro de destino.

### 2.6. Falsificação de Dados de Produtividade no Motor de Insights de Fadiga
- **Módulo afetado:** [FatigueInsightEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/FatigueInsightEngine.kt#L423-L438)
- **Problema:** O método `estimateHourlyBreakdown` estima o ganho por hora de cada hora do turno dividindo o total ganho através de uma curva de peso estática e decrescente (fadiga teórica predefinida), em vez de consultar os valores e horários reais das corridas salvas no histórico do turno.
- **Impacto:** Se o motorista trabalhou 10 horas e faturou R$ 200 nas últimas duas horas (por exemplo, na saída de um show), a IA ainda assumirá que as últimas horas foram improdutivas devido à curva de fadiga decrescente estática, gerando um insight falso e incorreto (sugerindo que ele deveria ter parado de rodar).
- **Solução proposta:** Consultar no banco de dados (`RideHistoryDao`) as corridas completadas no período exato do turno para calcular a distribuição de ganho horária real.

### 2.7. Dupla Interceptação e Sobreposição no `RideNotificationListener`
- **Módulo afetado:** [RideNotificationListener.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/service/RideNotificationListener.kt#L206-L215)
- **Problema:** Na lógica de despacho de notificações, quando `isPrimaryChannel` é `false` (modo legado onde as notificações só servem de backup discreto no Ghost Mode) e o Ghost Mode está inativo (`stealthModeActive = false`), a notificação cai no bloco `else` e envia a corrida para o `OverlayService`.
- **Impacto:** Como o `RideAccessibilityService` também está ativo na tela, o mesmo evento de corrida é processado duas vezes. Isso causa dupla exibição de overlays ou sobrescrita de dados de corridas com dados incompletos vindos da notificação, corrompendo o banco de dados.
- **Solução proposta:** No bloco `else`, o Listener de Notificação não deve fazer nada caso o `RideAccessibilityService` esteja ativo e funcional (ou seja, quando o Ghost Mode está inativo).

---

## 🟡 3. Problemas de Nível: MÉDIO

### 3.1. Projeção Financeira Assume Trabalho Ininterrupto (Sem Descanso)
- **Módulo afetado:** [ProjectionEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/ProjectionEngine.kt#L78-L82)
- **Problema:** Ao projetar ganhos e KMs para os períodos solicitados (SEMANA, MES, ANO), o motor multiplica a média diária dos dias trabalhados pelo número total de dias corridos do período (7, 30 e 365, respectivamente).
- **Impacto:** Superestimativa massiva das projeções financeiras. Se o motorista trabalhou apenas 2 dias nos últimos 30 dias com média de R$ 200/dia, a projeção mensal exibirá R$ 6.000, ignorando que o motorista folga 28 dias do mês.
- **Solução proposta:** Calcular a taxa de atividade do motorista (dias trabalhados / dias totais no período histórico de 30 dias) e aplicar essa fração ao multiplicador de projeção.

### 3.2. Erro de Timezone/Fuso Horário no Cálculo de Horários Ótimos da IA
- **Módulo afetado:** [FatigueInsightEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/FatigueInsightEngine.kt#L207)
- **Problema:** Para extrair a hora de início do turno, a IA faz o cálculo: `((shift.startTimeMs % 86_400_000L) / 3_600_000L)`. Esse cálculo extrai a hora correspondente ao meridiano de Greenwich (UTC).
- **Impacto:** O insight de horários mais produtivos do motorista fica deslocado de acordo com o fuso horário local. No Brasil (UTC-3), todos os picos de faturamento são indicados com 3 horas de atraso na tela.
- **Solução proposta:** Usar `Calendar.getInstance().apply { timeInMillis = shift.startTimeMs }.get(Calendar.HOUR_OF_DAY)` para extrair a hora real no fuso do aparelho.

### 3.3. Inconsistência de Desgaste de Veículos Elétricos no Motor de Projeção
- **Módulo afetado:** [ProjectionEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/ProjectionEngine.kt#L337-L343)
- **Problema:** Enquanto o módulo de DRE (`FinanceDRE.kt`) aplica coeficientes especiais para carros elétricos (desgaste de pneus +25%, freios -40%, fluidos -66%, revisões -40%), o `ProjectionEngine` calcula o desgaste chamando diretamente os parâmetros do perfil do veículo de forma linear, ignorando se o tipo do veículo ativo é `ELECTRIC`.
- **Impacto:** Inconsistência financeira. O motorista vê um custo de manutenção estimado na projeção diferente do custo de manutenção real do DRE para veículos elétricos.
- **Solução proposta:** Adaptar as funções de cálculo de desgaste em `ProjectionEngine` para aplicar as mesmas correções de veículos elétricos do DRE.

### 3.4. Margem Prematura no Break-Even do AutoPilot (ProfitAware)
- **Módulo afetado:** [ProfitAwareAutoPilot.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/ProfitAwareAutoPilot.kt#L81-L82)
- **Problema:** A lógica do AutoPilot inteligente calcula o break-even do mês comparando o faturamento bruto (`monthEarnings`) com os custos fixos (`monthlyFixed`).
- **Impacto:** Falso senso de segurança financeira. O faturamento bruto não deduz os custos variáveis (como combustível consumido e desgaste do veículo no mês). O sistema assume que o break-even foi atingido muito antes do real, aumentando a seletividade (`SELECTIVITY_BONUS = 5`) prematuramente e fazendo o motorista perder corridas lucrativas quando ainda está no prejuízo real.
- **Solução proposta:** Subtrair o custo estimado por KM (distância acumulada × custo/km do veículo) do faturamento bruto para obter o lucro líquido real do mês antes de comparar com os custos fixos.

### 3.5. Comparação Inválida de Intervalos de Odômetro (GPS vs Room)
- **Módulo afetado:** [OdometerEngine.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/OdometerEngine.kt#L79-L84)
- **Problema:** A condição para usar a medição do GPS no odômetro exige `gpsKm > kmTracked`. O valor `kmTracked` representa os KMs de corridas desde a última atualização manual do odômetro (acumulado de vários dias/semanas), enquanto `gpsKm` representa apenas os KMs rastreados na sessão do turno atual.
- **Impacto:** Após o primeiro dia do odômetro atualizado, `kmTracked` será sempre muito maior que `gpsKm`, fazendo a estimativa do odômetro ignorar os dados precisos de GPS e reverter sempre para a heurística genérica de rateio de corridas.
- **Solução proposta:** Persistir no banco de dados os KMs acumulados medidos via GPS desde a última atualização do odômetro, permitindo a comparação de períodos equivalentes.

### 3.6. Alerta de Segurança Composta Penaliza Corridas Seguras no `RideScorer`
- **Módulo afetado:** [SafetyScoreModifier.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/domain/SafetyScoreModifier.kt#L191)
- **Problema:** No método estático de penalidade de segurança (`SafetyScoreModifierStatic.calculatePenalty`), o sinalizador de área bloqueada está simplificado como `val isInBlockedArea = blockedNeighborhoods.isNotEmpty()`.
- **Impacto:** Se o motorista tiver qualquer bairro bloqueado configurado no aplicativo (o que ocorre em 99% dos casos de uso para evitar favelas/áreas perigosas), toda corrida realizada à noite com passageiro de nota < 4.5 receberá uma penalidade adicional de +10 pontos, mesmo que seja em um bairro totalmente seguro e fora da lista de bloqueio.
- **Solução proposta:** Substituir por uma verificação real se o bairro de embarque ou desembarque da corrida consta na lista de bairros bloqueados.

---

## 🟢 4. Problemas de Nível: BAIXO

### 4.1. Chamada Assíncrona no Carregamento de Layout do `OverlayService`
- **Módulo afetado:** [OverlayService.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/service/OverlayService.kt#L521-L522)
- **Problema:** Para evitar travar a thread principal (ANR), a restauração da posição do overlay (coordenadas X e Y) foi modificada para carregar de forma assíncrona. Porém, o layout inicial do WindowManager é criado com `savedX = 0` e `savedY = 0` de forma estática antes de carregar o valor real.
- **Impacto:** Pequeno efeito visual desagradável (shimmer/pulo) onde o card aparece momentaneamente no topo esquerdo da tela antes de se mover para a posição correta configurada pelo usuário.
- **Solução proposta:** Carregar as coordenadas das preferências em um fluxo síncrono com cache ou aplicar visibilidade oculta (alpha = 0) ao card até que a posição final seja carregada e configurada no WindowManager.

### 4.2. Registro de Logs do Simulador Usa Contexto Inadequado
- **Módulo afetado:** [OverlayService.kt](file:///c:/Users/ovand/ngb-autoroad-privacy/app/src/main/java/com/ngbautoroad/service/OverlayService.kt#L171)
- **Problema:** No manipulador global de exceções não capturadas do overlay, o `TelemetryLogger` é chamado passando `applicationContext`. Isso é seguro, mas em sub-atividades do simulador é comum passar o contexto da `Activity`, o que pode causar vazamentos de referência curtos se a atividade for destruída durante o processo de escrita assíncrona do log.
- **Solução proposta:** Forçar o uso de `context.applicationContext` em todas as chamadas de inicialização do logger.
