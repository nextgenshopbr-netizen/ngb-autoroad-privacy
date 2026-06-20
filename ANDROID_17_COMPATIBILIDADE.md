# Android 17: Análise de Compatibilidade e Oportunidades para o NGB AutoRoad Privacy

O Android 17, lançado em 16 de junho de 2026, representa a maior mudança de paradigma do sistema operacional desde a introdução dos serviços de acessibilidade. O Google está transformando o Android de um "sistema operacional" para um **"sistema de inteligência"**, onde agentes de IA (como o Gemini) podem interagir diretamente com apps instalados, executar workflows complexos e gerenciar recursos em nome do usuário. Este documento analisa cada recurso relevante do Android 17 e seu impacto direto no NGB AutoRoad Privacy.

---

## Impactos Críticos: O Que Precisamos Adequar

### 1. Advanced Protection Mode (AAPM) — Risco de Bloqueio Total do App

O Android 17 introduz o **Advanced Protection Mode**, que bloqueia automaticamente apps que não são classificados como ferramentas de acessibilidade legítimas de usar a Accessibility Services API. Quando o AAPM está ativo, as permissões de acessibilidade são **revogadas automaticamente** para apps que não possuem a flag `isAccessibilityTool="true"`.

Segundo o Google, apenas as seguintes categorias são consideradas ferramentas de acessibilidade: screen readers, sistemas de input por switch, ferramentas de input por voz e programas de acesso Braille. **Automação, monitoramento, assistentes e apps utilitários NÃO são classificados como accessibility tools.**

| Situação | Impacto no NGB AutoRoad |
|---|---|
| Usuário com AAPM desligado | Nenhum impacto, funciona normalmente |
| Usuário com AAPM ligado | `RideAccessibilityService` é **completamente bloqueado** — o app para de funcionar |

**Ação necessária:** Atualmente o AAPM é opt-in e poucos usuários ativam. Porém, a tendência é que se torne padrão em versões futuras. Precisamos preparar uma **rota alternativa** que não dependa exclusivamente do AccessibilityService. A solução mais viável é migrar a detecção de corridas para o `NotificationListenerService` como canal primário (que não é afetado pelo AAPM), mantendo o AccessibilityService apenas como canal secundário para parsing avançado e AutoPilot (click automático).

---

### 2. App Memory Limits — Risco de Encerramento Forçado

O Android 17 introduz **limites rígidos de memória RAM por app** baseados na RAM total do dispositivo. Processos que excedem esses limites são **terminados abruptamente** pelo sistema, sem aviso.

| RAM do Device | Limite Estimado por App |
|---|---|
| 4 GB | ~256 MB |
| 6 GB | ~384 MB |
| 8 GB+ | ~512 MB |

**Impacto no NGB AutoRoad:** O app mantém múltiplos serviços ativos simultaneamente (AccessibilityService, OverlayService, NotificationListener, RideLifecycleManager, AutoPilotEngine). Em dispositivos com 4 GB de RAM (comuns entre motoristas), o consumo combinado pode se aproximar do limite.

**Ação necessária:** Implementar profiling de memória, otimizar o `CardGallery` (35 cards carregados na memória), e considerar lazy loading para componentes pesados como o `ZoneMapActivity` e `FinanceDatabase`. Monitorar `ApplicationExitInfo` para detectar se o app está sendo morto por excesso de memória.

---

### 3. Background Audio Hardening — Notificações Sonoras Silenciadas

O Android 17 impede que apps toquem áudio em background sem um **Foreground Service com While-In-Use (WIU) capabilities**. Isso afeta diretamente as notificações sonoras do `RideLifecycleManager` (notificação UNCERTAIN) e potenciais alertas do AutoPilot.

**Ação necessária:** Garantir que todas as interações de áudio (notificações com som, TTS de alerta) ocorram enquanto o `RideAccessibilityService` (que é um FGS) está ativo e com WIU. Alternativamente, usar o canal de notificação do sistema (que não é afetado pela restrição) em vez de tocar áudio diretamente.

---

### 4. OTP Protection e Restricted Message Access — Ghost Mode Bancário

O Android 17 introduz proteção de OTP para apps não-prioritários: mesmo com permissão de SMS, o app não terá acesso imediato a senhas de uso único. Além disso, mensagens E2E criptografadas ficam restritas.

**Impacto no NGB AutoRoad:** O Ghost Mode bancário usa o `NotificationListenerService` para detectar notificações de apps bancários e ocultar valores. Se o banco enviar OTPs via notificação, o acesso pode ser atrasado. Porém, como o Ghost Mode apenas **oculta** a notificação (não lê o conteúdo do OTP), o impacto deve ser mínimo.

**Ação necessária:** Testar o Ghost Mode em dispositivos Android 17 para confirmar que a ocultação de notificações bancárias continua funcionando. Se necessário, ajustar o timing de interceptação.

---

### 5. Local Network Permission — Nova Permissão Obrigatória

Apps que fazem target Android 17 precisam de permissão explícita para acessar dispositivos na rede local. Isso era opt-in no Android 16 e agora é obrigatório.

**Impacto no NGB AutoRoad:** Atualmente o app não usa rede local para nada. **Nenhuma ação necessária**, a menos que implementemos a funcionalidade de Dashboard Web (Fase 4 do roadmap), que precisaria dessa permissão para sincronização local.

---

## Oportunidades: O Que Podemos Adicionar

### 1. AppFunctions — Expor o NGB AutoRoad para o Gemini

O recurso mais revolucionário do Android 17 é o **AppFunctions**, que permite que apps exponham funcionalidades como "tools" para agentes de IA. O Gemini pode então descobrir e executar essas funções em nome do usuário via linguagem natural.

Isso significa que o motorista poderá dizer ao Gemini coisas como:

> "Gemini, ativa o perfil Noturno no meu app de corridas"
> "Gemini, qual foi meu ganho total hoje?"
> "Gemini, pausa o AutoPilot por 30 minutos"
> "Gemini, mostra minha projeção semanal"

**AppFunctions que podemos expor:**

| Função | Descrição | Comando Natural |
|---|---|---|
| `switchProfile` | Trocar perfil de critérios ativo | "Ativa o perfil Noturno" |
| `getTodayEarnings` | Retornar ganhos do dia | "Quanto ganhei hoje?" |
| `getWeekProjection` | Retornar projeção semanal | "Qual minha projeção essa semana?" |
| `toggleAutoPilot` | Ligar/desligar AutoPilot | "Liga o AutoPilot" |
| `setAutoPilotMode` | Configurar modo (aceitar/recusar/ambos) | "Configura pra só aceitar" |
| `startShift` | Iniciar turno | "Inicia meu turno" |
| `endShift` | Encerrar turno | "Encerra meu turno" |
| `getShiftSummary` | Resumo do turno atual | "Como tá meu turno?" |
| `getRideHistory` | Últimas corridas | "Mostra minhas últimas corridas" |
| `toggleGhostMode` | Ligar/desligar Ghost Mode | "Ativa o modo fantasma" |

**Implementação técnica:**

```kotlin
class AutoRoadAppFunctions(
    private val prefsManager: PrefsManager,
    private val shiftManager: ShiftManager
) {
    /**
     * Switch the active criteria profile.
     * @param context The execution context.
     * @param profileName The name of the profile to activate.
     * @return The activated profile name.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun switchProfile(
        context: AppFunctionContext,
        profileName: String
    ): String { /* ... */ }

    /**
     * Get today's total earnings.
     * @param context The execution context.
     * @return Today's earnings in BRL.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getTodayEarnings(
        context: AppFunctionContext
    ): Double { /* ... */ }
}
```

**Dependência:** Jetpack AppFunctions library (alpha), targetSdk 37.

---

### 2. NPU On-Device AI — Inteligência Local Real

O Android 17 exige que apps declarem `android.hardware.npu` se precisarem acessar a Neural Processing Unit. Com o **LiteRT** (antigo TensorFlow Lite) e aceleração NPU, podemos rodar modelos de machine learning diretamente no celular do motorista.

**Aplicações no NGB AutoRoad:**

| Aplicação | Descrição |
|---|---|
| Previsão de demanda | Modelo treinado com histórico local que prevê horários de pico por região |
| Score adaptativo | Ajustar pesos automaticamente baseado no padrão de aceitação/recusa do motorista |
| Detecção de padrões | Identificar automaticamente que "sexta à noite no centro = corridas boas" |
| OCR local | Ler valores da tela com modelo local em vez de regex frágil |

**Implementação:** Usar LiteRT com modelo quantizado (< 5 MB) treinado com os dados do `RideHistoryDao`. O modelo roda 100% no device, sem enviar dados para nuvem — alinhado com a filosofia "Privacy First".

---

### 3. Handoff (Continue On) — Dashboard Multi-Device

O Android 17 permite que o usuário comece uma tarefa em um device e continue em outro. Para o motorista, isso significa:

- Verificar o Dashboard no **tablet** enquanto o celular está no suporte do carro
- Configurar perfis e critérios no **tablet** com tela maior, e o celular já reflete
- Ver o resumo do turno no **computador** (via Googlebook/ChromeOS)

**Implementação:** Adicionar `setHandoffEnabled(true)` nas Activities principais e implementar `onHandoffActivityDataRequested()` para serializar o estado atual.

---

### 4. App Bubbles — Card Flutuante Nativo

O Android 17 permite que qualquer app seja transformado em uma "bolha flutuante" pelo sistema. Isso é exatamente o que o NGB AutoRoad já faz com o `OverlayService` (overlay flutuante), mas agora o sistema oferece isso nativamente.

**Oportunidade:** Oferecer ao motorista a opção de usar o card de corrida como um **App Bubble nativo** em vez do overlay customizado. Vantagens: melhor integração com o sistema, sem necessidade de permissão SYSTEM_ALERT_WINDOW, e o sistema gerencia a posição e o ciclo de vida.

---

### 5. MetricStyle Template — Complicações de Relógio

O Android 17 introduz o `MetricStyle` template para Wear OS, suportando métricas de saúde, timers e viagens. Podemos criar uma **complicação de relógio** que mostra:

- Ganho do turno atual
- Número de corridas aceitas
- Score da última corrida
- Status do AutoPilot (ativo/inativo)

---

## Plano de Adequação: Prioridades

| Prioridade | Ação | Motivo | Esforço |
|---|---|---|---|
| **P0 (Urgente)** | Preparar rota alternativa ao AccessibilityService | AAPM pode bloquear o app inteiro | Alto |
| **P0 (Urgente)** | Profiling de memória e otimização | App pode ser morto em devices 4 GB | Médio |
| **P1 (Importante)** | Implementar AppFunctions básicas | Integração com Gemini = diferencial competitivo enorme | Médio |
| **P1 (Importante)** | Testar Ghost Mode no Android 17 | Garantir que notificações bancárias continuam sendo ocultadas | Baixo |
| **P2 (Desejável)** | Migrar overlay para App Bubbles (opcional) | Melhor integração com sistema, menos permissões | Médio |
| **P2 (Desejável)** | NPU para score adaptativo | Inteligência real sem nuvem | Alto |
| **P3 (Futuro)** | Handoff multi-device | Dashboard no tablet | Baixo |
| **P3 (Futuro)** | MetricStyle para Wear OS | Complicação de relógio | Baixo |

---

## Resumo Executivo

O Android 17 traz **dois riscos existenciais** para o NGB AutoRoad Privacy:

1. O **Advanced Protection Mode** pode bloquear completamente o AccessibilityService, que é o coração do app (detecção de corridas, parsing, AutoPilot).
2. Os **limites de memória** podem matar o app em dispositivos populares entre motoristas (4 GB RAM).

Ao mesmo tempo, traz **oportunidades transformadoras**:

1. **AppFunctions** permite que o motorista controle o app inteiramente por voz via Gemini — um diferencial que nenhum concorrente oferece.
2. **NPU on-device** viabiliza machine learning local para previsão de demanda e score adaptativo, sem comprometer a privacidade.
3. **App Bubbles** pode substituir o overlay customizado por uma solução nativa do sistema, eliminando a necessidade de permissão SYSTEM_ALERT_WINDOW.

A estratégia recomendada é: **adequar primeiro** (P0), **diferenciar depois** (P1/P2). O motorista que usa o NGB AutoRoad no Android 17 deve ter a mesma experiência de sempre, com a adição de poder dizer "Gemini, ativa meu perfil Noturno" e ter tudo funcionando automaticamente.

---

*Análise baseada na documentação oficial do Android 17 (developer.android.com), blog oficial Android Developers (16/06/2026), e artigos técnicos de segurança. Última atualização: 20/06/2026.*
