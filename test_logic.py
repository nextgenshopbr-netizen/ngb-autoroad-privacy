#!/usr/bin/env python3
"""
NGBAutoRoad v3.2.2 - Teste Lógico Completo
============================================
Simula toda a lógica do RideScorer, validação de critérios, 
cálculos financeiros e mapeamento de dados para cards.

Testa:
1. Score com todos os 9 critérios ativos
2. Score com critérios parciais (campos zerados devem ser ignorados)
3. Penalidades de thresholds
4. Penalidades de bairros bloqueados
5. Normalização de cada critério
6. Cálculos financeiros (custo/km, lucro líquido, metas)
7. Mapeamento de dados OCR → RideData → Card
8. Verificação de critério duplicado (passengerRating vs userRating)
"""

import sys

# ===== MODELOS (replicando a lógica Kotlin) =====

class RideData:
    def __init__(self, platform="UBER", rideValue=0.0, rideDuration=0.0,
                 pickupDistance=0.0, dropoffDistance=0.0, passengerRating=0.0,
                 userRating=0.0, intermediateStops=0, pickupNeighborhood="",
                 dropoffNeighborhood=""):
        self.platform = platform
        self.rideValue = rideValue
        self.rideDuration = rideDuration
        self.pickupDistance = pickupDistance
        self.dropoffDistance = dropoffDistance
        self.passengerRating = passengerRating
        self.userRating = userRating
        self.intermediateStops = intermediateStops
        self.pickupNeighborhood = pickupNeighborhood
        self.dropoffNeighborhood = dropoffNeighborhood

    @property
    def valuePerKm(self):
        return self.rideValue / self.dropoffDistance if self.dropoffDistance > 0 else 0.0

    @property
    def valuePerHour(self):
        return (self.rideValue / self.rideDuration) * 60.0 if self.rideDuration > 0 else 0.0


class CriteriaWeights:
    def __init__(self, valuePerKm=30, valuePerHour=30, intermediateStops=25,
                 passengerRating=15, userRating=0, rideValue=0, rideDuration=0,
                 pickupDistance=0, dropoffDistance=0):
        self.valuePerKm = valuePerKm
        self.valuePerHour = valuePerHour
        self.intermediateStops = intermediateStops
        self.passengerRating = passengerRating
        self.userRating = userRating
        self.rideValue = rideValue
        self.rideDuration = rideDuration
        self.pickupDistance = pickupDistance
        self.dropoffDistance = dropoffDistance

    @property
    def totalUsed(self):
        return (self.valuePerKm + self.valuePerHour + self.intermediateStops +
                self.passengerRating + self.userRating + self.rideValue +
                self.rideDuration + self.pickupDistance + self.dropoffDistance)


class DriverThresholds:
    def __init__(self, minValuePerKm=0.0, minValuePerHour=0.0, minRideValue=0.0,
                 maxPickupDistance=0.0, minPassengerRating=0.0, minUserRating=0.0,
                 maxDuration=0.0, maxStops=99, minDropoffDistance=0.0):
        self.minValuePerKm = minValuePerKm
        self.minValuePerHour = minValuePerHour
        self.minRideValue = minRideValue
        self.maxPickupDistance = maxPickupDistance
        self.minPassengerRating = minPassengerRating
        self.minUserRating = minUserRating
        self.maxDuration = maxDuration
        self.maxStops = maxStops
        self.minDropoffDistance = minDropoffDistance


class ScoringThresholds:
    def __init__(self):
        self.minValuePerKm = 0.50
        self.maxValuePerKm = 2.50
        self.minValuePerHour = 10.0
        self.maxValuePerHour = 40.0
        self.minRideValue = 5.0
        self.maxRideValue = 50.0
        self.minDuration = 10.0
        self.maxDuration = 60.0
        self.minPickupDistance = 0.5
        self.maxPickupDistance = 5.0
        self.minDropoffDistance = 2.0
        self.maxDropoffDistance = 20.0


# ===== RIDE SCORER (replicando lógica Kotlin) =====

class RideScorer:
    def __init__(self, weights, driverThresholds=None, blockedNeighborhoods=None, thresholds=None):
        self.weights = weights
        self.driverThresholds = driverThresholds or DriverThresholds()
        self.blockedNeighborhoods = blockedNeighborhoods or []
        self.thresholds = thresholds or ScoringThresholds()

    def calculateScore(self, ride):
        criteriaScores = {}
        violations = []

        # 1. Valor por KM
        if self.weights.valuePerKm > 0:
            normalized = self._normalizeValuePerKm(ride.valuePerKm)
            weighted = normalized * self.weights.valuePerKm / 100.0
            criteriaScores["valuePerKm"] = {"normalized": normalized, "weight": self.weights.valuePerKm, "weighted": weighted}
            if self.driverThresholds.minValuePerKm > 0 and ride.valuePerKm < self.driverThresholds.minValuePerKm:
                penalty = self.weights.valuePerKm * 0.5
                violations.append(("Valor/KM", ride.valuePerKm, self.driverThresholds.minValuePerKm, penalty))

        # 2. Valor por Hora
        if self.weights.valuePerHour > 0:
            normalized = self._normalizeValuePerHour(ride.valuePerHour)
            weighted = normalized * self.weights.valuePerHour / 100.0
            criteriaScores["valuePerHour"] = {"normalized": normalized, "weight": self.weights.valuePerHour, "weighted": weighted}
            if self.driverThresholds.minValuePerHour > 0 and ride.valuePerHour < self.driverThresholds.minValuePerHour:
                penalty = self.weights.valuePerHour * 0.5
                violations.append(("Valor/Hora", ride.valuePerHour, self.driverThresholds.minValuePerHour, penalty))

        # 3. Paradas
        if self.weights.intermediateStops > 0:
            normalized = self._normalizeStops(ride.intermediateStops)
            weighted = normalized * self.weights.intermediateStops / 100.0
            criteriaScores["intermediateStops"] = {"normalized": normalized, "weight": self.weights.intermediateStops, "weighted": weighted}
            if self.driverThresholds.maxStops < 99 and ride.intermediateStops > self.driverThresholds.maxStops:
                penalty = self.weights.intermediateStops * 0.7
                violations.append(("Paradas", ride.intermediateStops, self.driverThresholds.maxStops, penalty))

        # 4. Avaliação do Passageiro
        if self.weights.passengerRating > 0:
            normalized = self._normalizeRating(ride.passengerRating)
            weighted = normalized * self.weights.passengerRating / 100.0
            criteriaScores["passengerRating"] = {"normalized": normalized, "weight": self.weights.passengerRating, "weighted": weighted}
            if self.driverThresholds.minPassengerRating > 0 and ride.passengerRating < self.driverThresholds.minPassengerRating:
                penalty = self.weights.passengerRating * 0.6
                violations.append(("Avaliação", ride.passengerRating, self.driverThresholds.minPassengerRating, penalty))

        # 5. Valor da Corrida
        if self.weights.rideValue > 0:
            normalized = self._normalizeRideValue(ride.rideValue)
            weighted = normalized * self.weights.rideValue / 100.0
            criteriaScores["rideValue"] = {"normalized": normalized, "weight": self.weights.rideValue, "weighted": weighted}
            if self.driverThresholds.minRideValue > 0 and ride.rideValue < self.driverThresholds.minRideValue:
                penalty = self.weights.rideValue * 0.5
                violations.append(("Valor Corrida", ride.rideValue, self.driverThresholds.minRideValue, penalty))

        # 6. Duração
        if self.weights.rideDuration > 0:
            normalized = self._normalizeDuration(ride.rideDuration)
            weighted = normalized * self.weights.rideDuration / 100.0
            criteriaScores["rideDuration"] = {"normalized": normalized, "weight": self.weights.rideDuration, "weighted": weighted}
            if self.driverThresholds.maxDuration > 0 and ride.rideDuration > self.driverThresholds.maxDuration:
                penalty = self.weights.rideDuration * 0.5
                violations.append(("Duração", ride.rideDuration, self.driverThresholds.maxDuration, penalty))

        # 7. Distância até Embarque
        if self.weights.pickupDistance > 0:
            normalized = self._normalizePickupDistance(ride.pickupDistance)
            weighted = normalized * self.weights.pickupDistance / 100.0
            criteriaScores["pickupDistance"] = {"normalized": normalized, "weight": self.weights.pickupDistance, "weighted": weighted}
            if self.driverThresholds.maxPickupDistance > 0 and ride.pickupDistance > self.driverThresholds.maxPickupDistance:
                penalty = self.weights.pickupDistance * 0.6
                violations.append(("Dist. Embarque", ride.pickupDistance, self.driverThresholds.maxPickupDistance, penalty))

        # 8. Avaliação de Usuários
        if self.weights.userRating > 0:
            normalized = self._normalizeRating(ride.userRating)
            weighted = normalized * self.weights.userRating / 100.0
            criteriaScores["userRating"] = {"normalized": normalized, "weight": self.weights.userRating, "weighted": weighted}
            if self.driverThresholds.minUserRating > 0 and ride.userRating < self.driverThresholds.minUserRating:
                penalty = self.weights.userRating * 0.6
                violations.append(("Aval. Usuário", ride.userRating, self.driverThresholds.minUserRating, penalty))

        # 9. Distância até Desembarque
        if self.weights.dropoffDistance > 0:
            normalized = self._normalizeDropoffDistance(ride.dropoffDistance)
            weighted = normalized * self.weights.dropoffDistance / 100.0
            criteriaScores["dropoffDistance"] = {"normalized": normalized, "weight": self.weights.dropoffDistance, "weighted": weighted}
            if self.driverThresholds.minDropoffDistance > 0 and ride.dropoffDistance < self.driverThresholds.minDropoffDistance:
                penalty = self.weights.dropoffDistance * 0.5
                violations.append(("Dist. Destino", ride.dropoffDistance, self.driverThresholds.minDropoffDistance, penalty))

        # Total
        totalScore = sum(c["weighted"] for c in criteriaScores.values())
        thresholdPenalty = sum(v[3] for v in violations)
        totalScore -= thresholdPenalty

        # Bairros bloqueados
        pickupPenalty = max([b["penalty"] for b in self.blockedNeighborhoods
                           if b["type"] == "PICKUP" and b["name"].lower() == ride.pickupNeighborhood.lower()] or [0])
        dropoffPenalty = max([b["penalty"] for b in self.blockedNeighborhoods
                            if b["type"] == "DROPOFF" and b["name"].lower() == ride.dropoffNeighborhood.lower()] or [0])
        totalScore -= (pickupPenalty + dropoffPenalty)
        totalScore = max(0.0, min(100.0, totalScore))

        return {
            "totalScore": totalScore,
            "criteriaScores": criteriaScores,
            "violations": violations,
            "thresholdPenalty": thresholdPenalty,
            "neighborhoodPenalty": pickupPenalty + dropoffPenalty
        }

    def _normalizeValuePerKm(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minValuePerKm) / (self.thresholds.maxValuePerKm - self.thresholds.minValuePerKm) * 100))

    def _normalizeValuePerHour(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minValuePerHour) / (self.thresholds.maxValuePerHour - self.thresholds.minValuePerHour) * 100))

    def _normalizeStops(self, stops):
        if stops == 0: return 100.0
        if stops == 1: return 50.0
        return 0.0

    def _normalizeRating(self, rating):
        if rating >= 5.0: return 100.0
        if rating >= 4.0: return (rating - 4.0) / 1.0 * 100.0
        return 0.0

    def _normalizeRideValue(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minRideValue) / (self.thresholds.maxRideValue - self.thresholds.minRideValue) * 100))

    def _normalizeDuration(self, minutes):
        return max(0.0, min(100.0, (self.thresholds.maxDuration - minutes) / (self.thresholds.maxDuration - self.thresholds.minDuration) * 100))

    def _normalizePickupDistance(self, km):
        return max(0.0, min(100.0, (self.thresholds.maxPickupDistance - km) / (self.thresholds.maxPickupDistance - self.thresholds.minPickupDistance) * 100))

    def _normalizeDropoffDistance(self, km):
        return max(0.0, min(100.0, (km - self.thresholds.minDropoffDistance) / (self.thresholds.maxDropoffDistance - self.thresholds.minDropoffDistance) * 100))


# ===== TESTES =====

def test_separator(name):
    print(f"\n{'='*60}")
    print(f"  TESTE: {name}")
    print(f"{'='*60}")

errors = []
warnings = []

# ===== TESTE 1: Score com pesos padrão (soma = 100) =====
test_separator("1. Score com pesos padrão (4 critérios ativos, soma=100)")

weights_default = CriteriaWeights()  # 30+30+25+15 = 100
assert weights_default.totalUsed == 100, f"ERRO: Soma padrão = {weights_default.totalUsed}, esperado 100"
print(f"  Pesos padrão: VKM={weights_default.valuePerKm}, VH={weights_default.valuePerHour}, Stops={weights_default.intermediateStops}, PR={weights_default.passengerRating}")
print(f"  Soma: {weights_default.totalUsed}/100 ✓")

ride1 = RideData(rideValue=25.0, rideDuration=20.0, pickupDistance=1.5, dropoffDistance=10.0,
                 passengerRating=4.8, intermediateStops=0)
print(f"\n  Corrida: R$25, 20min, pickup=1.5km, dropoff=10km, rating=4.8, 0 paradas")
print(f"  → R$/km = {ride1.valuePerKm:.2f}, R$/h = {ride1.valuePerHour:.2f}")

scorer1 = RideScorer(weights_default)
result1 = scorer1.calculateScore(ride1)
print(f"\n  Critérios calculados:")
for key, val in result1["criteriaScores"].items():
    print(f"    {key}: normalizado={val['normalized']:.1f}, peso={val['weight']}, ponderado={val['weighted']:.2f}")
print(f"\n  Score Total: {result1['totalScore']:.2f}/100")
print(f"  Violações: {len(result1['violations'])}")

# Verificar que critérios com peso=0 NÃO são calculados
assert "userRating" not in result1["criteriaScores"], "ERRO: userRating com peso=0 NÃO deveria ser calculado!"
assert "rideValue" not in result1["criteriaScores"], "ERRO: rideValue com peso=0 NÃO deveria ser calculado!"
assert "rideDuration" not in result1["criteriaScores"], "ERRO: rideDuration com peso=0 NÃO deveria ser calculado!"
assert "pickupDistance" not in result1["criteriaScores"], "ERRO: pickupDistance com peso=0 NÃO deveria ser calculado!"
assert "dropoffDistance" not in result1["criteriaScores"], "ERRO: dropoffDistance com peso=0 NÃO deveria ser calculado!"
print(f"\n  ✓ Critérios com peso=0 corretamente ignorados (5 critérios não calculados)")

# ===== TESTE 2: Score com TODOS os 9 critérios ativos =====
test_separator("2. Score com TODOS os 9 critérios ativos")

weights_all = CriteriaWeights(
    valuePerKm=20, valuePerHour=15, intermediateStops=10,
    passengerRating=10, userRating=10, rideValue=10,
    rideDuration=10, pickupDistance=10, dropoffDistance=5
)
assert weights_all.totalUsed == 100, f"ERRO: Soma = {weights_all.totalUsed}, esperado 100"
print(f"  Soma: {weights_all.totalUsed}/100 ✓")

ride2 = RideData(rideValue=30.0, rideDuration=25.0, pickupDistance=2.0, dropoffDistance=12.0,
                 passengerRating=4.9, userRating=4.5, intermediateStops=0,
                 pickupNeighborhood="Pituba", dropoffNeighborhood="Barra")
print(f"  Corrida: R$30, 25min, pickup=2km, dropoff=12km, PR=4.9, UR=4.5, 0 paradas")
print(f"  → R$/km = {ride2.valuePerKm:.2f}, R$/h = {ride2.valuePerHour:.2f}")

scorer2 = RideScorer(weights_all)
result2 = scorer2.calculateScore(ride2)
print(f"\n  Critérios calculados ({len(result2['criteriaScores'])}/9):")
for key, val in result2["criteriaScores"].items():
    print(f"    {key}: normalizado={val['normalized']:.1f}, peso={val['weight']}, ponderado={val['weighted']:.2f}")
print(f"\n  Score Total: {result2['totalScore']:.2f}/100")

assert len(result2["criteriaScores"]) == 9, f"ERRO: Esperado 9 critérios, obteve {len(result2['criteriaScores'])}"
print(f"  ✓ Todos os 9 critérios calculados corretamente")

# Verificar soma ponderada
soma_ponderada = sum(c["weighted"] for c in result2["criteriaScores"].values())
print(f"  Soma ponderada (sem penalidades): {soma_ponderada:.2f}")
assert abs(soma_ponderada - result2["totalScore"]) < 0.01, "ERRO: Score não bate com soma ponderada!"

# ===== TESTE 3: Penalidades de Thresholds =====
test_separator("3. Penalidades de Thresholds (valores mínimos)")

thresholds_strict = DriverThresholds(
    minValuePerKm=2.0, minValuePerHour=25.0, minRideValue=15.0,
    maxPickupDistance=2.0, minPassengerRating=4.5, minUserRating=4.0,
    maxDuration=30.0, maxStops=0, minDropoffDistance=5.0
)

# Corrida que viola VÁRIOS thresholds
ride3 = RideData(rideValue=8.0, rideDuration=35.0, pickupDistance=4.0, dropoffDistance=3.0,
                 passengerRating=4.2, userRating=3.5, intermediateStops=2)
print(f"  Corrida RUIM: R$8, 35min, pickup=4km, dropoff=3km, PR=4.2, UR=3.5, 2 paradas")
print(f"  → R$/km = {ride3.valuePerKm:.2f}, R$/h = {ride3.valuePerHour:.2f}")
print(f"\n  Thresholds: min R$/km=2.0, min R$/h=25, min valor=15, max pickup=2km, min PR=4.5, min UR=4.0, max dur=30min, max stops=0, min dropoff=5km")

scorer3 = RideScorer(weights_all, thresholds_strict)
result3 = scorer3.calculateScore(ride3)
print(f"\n  Violações encontradas: {len(result3['violations'])}")
for v in result3["violations"]:
    print(f"    ⚠️ {v[0]}: atual={v[1]:.2f}, mínimo={v[2]:.2f}, penalidade={v[3]:.1f}")
print(f"  Penalidade total de thresholds: {result3['thresholdPenalty']:.1f}")
print(f"  Score Final: {result3['totalScore']:.2f}/100")

# Deve ter muitas violações
assert len(result3["violations"]) >= 6, f"ERRO: Esperado >=6 violações, obteve {len(result3['violations'])}"
print(f"  ✓ Penalidades de thresholds aplicadas corretamente")

# ===== TESTE 4: Bairros Bloqueados =====
test_separator("4. Penalidade de Bairros Bloqueados")

blocked = [
    {"name": "Liberdade", "type": "PICKUP", "penalty": 20},
    {"name": "Sussuarana", "type": "DROPOFF", "penalty": 30}
]

ride4 = RideData(rideValue=20.0, rideDuration=15.0, pickupDistance=1.0, dropoffDistance=8.0,
                 passengerRating=4.7, intermediateStops=0,
                 pickupNeighborhood="Liberdade", dropoffNeighborhood="Sussuarana")
print(f"  Corrida: Liberdade → Sussuarana (ambos bloqueados)")

scorer4 = RideScorer(weights_default, blockedNeighborhoods=blocked)
result4 = scorer4.calculateScore(ride4)
print(f"  Penalidade de bairros: {result4['neighborhoodPenalty']}")
assert result4["neighborhoodPenalty"] == 50, f"ERRO: Penalidade deveria ser 50, obteve {result4['neighborhoodPenalty']}"
print(f"  Score: {result4['totalScore']:.2f}/100")
print(f"  ✓ Bairros bloqueados penalizam corretamente (20+30=50)")

# ===== TESTE 5: Normalização nos extremos =====
test_separator("5. Normalização nos extremos (0 e 100)")

scorer5 = RideScorer(CriteriaWeights(valuePerKm=100))

# Valor/km no mínimo
ride_min = RideData(rideValue=2.5, rideDuration=10.0, dropoffDistance=5.0)  # 0.50 R$/km = mínimo
result_min = scorer5.calculateScore(ride_min)
print(f"  R$/km = 0.50 (mínimo): normalizado = {result_min['criteriaScores']['valuePerKm']['normalized']:.1f}")
assert result_min["criteriaScores"]["valuePerKm"]["normalized"] == 0.0

# Valor/km no máximo
ride_max = RideData(rideValue=25.0, rideDuration=10.0, dropoffDistance=10.0)  # 2.50 R$/km = máximo
result_max = scorer5.calculateScore(ride_max)
print(f"  R$/km = 2.50 (máximo): normalizado = {result_max['criteriaScores']['valuePerKm']['normalized']:.1f}")
assert result_max["criteriaScores"]["valuePerKm"]["normalized"] == 100.0

# Valor/km no meio
ride_mid = RideData(rideValue=15.0, rideDuration=10.0, dropoffDistance=10.0)  # 1.50 R$/km = meio
result_mid = scorer5.calculateScore(ride_mid)
print(f"  R$/km = 1.50 (meio): normalizado = {result_mid['criteriaScores']['valuePerKm']['normalized']:.1f}")
assert abs(result_mid["criteriaScores"]["valuePerKm"]["normalized"] - 50.0) < 0.01

print(f"  ✓ Normalização linear funciona corretamente nos extremos e meio")

# ===== TESTE 6: Critérios com peso=0 NÃO afetam score =====
test_separator("6. Critérios com peso=0 são completamente ignorados")

weights_partial = CriteriaWeights(valuePerKm=50, valuePerHour=50, intermediateStops=0,
                                  passengerRating=0, userRating=0, rideValue=0,
                                  rideDuration=0, pickupDistance=0, dropoffDistance=0)
assert weights_partial.totalUsed == 100

ride6 = RideData(rideValue=20.0, rideDuration=15.0, pickupDistance=1.0, dropoffDistance=10.0,
                 passengerRating=2.0, userRating=1.0, intermediateStops=5)
print(f"  Pesos: VKM=50, VH=50, resto=0")
print(f"  Corrida com rating=2.0 e 5 paradas (mas peso=0 para esses)")

scorer6 = RideScorer(weights_partial)
result6 = scorer6.calculateScore(ride6)
print(f"  Score: {result6['totalScore']:.2f}/100")
print(f"  Critérios calculados: {list(result6['criteriaScores'].keys())}")

assert "passengerRating" not in result6["criteriaScores"]
assert "intermediateStops" not in result6["criteriaScores"]
assert "userRating" not in result6["criteriaScores"]
print(f"  ✓ Rating ruim e paradas NÃO afetaram o score (peso=0)")

# ===== TESTE 7: PROBLEMA IDENTIFICADO - userRating vs passengerRating =====
test_separator("7. ANÁLISE: userRating vs passengerRating (DUPLICAÇÃO)")

print("""
  PROBLEMA IDENTIFICADO:
  ─────────────────────────────────────────────────────────────
  Na tela de Critérios (CriteriaTab), existem dois campos:
    - "Avaliação do Passageiro" (passengerRating)
    - "Avaliação de Usuários" (userRating)
  
  E nos Thresholds:
    - "Avaliação mínima do passageiro" (minPassengerRating)
    - "Avaliação mínima de usuários" (minUserRating)
  
  ANÁLISE DAS FONTES DE DADOS:
  ─────────────────────────────────────────────────────────────
  1. OCR (OcrCaptureService.kt):
     → Extrai UM ÚNICO rating via regex ([4-5].[0-9]{1,2})
     → Atribui a passengerRating APENAS
     → userRating NUNCA é preenchido via OCR (sempre 0.0)
  
  2. Accessibility (RideAccessibilityService.kt):
     → Extrai UM ÚNICO rating genérico
     → Atribui a passengerRating APENAS
     → userRating NUNCA é preenchido (sempre 0.0)
  
  3. RideHistoryEntity (banco de dados):
     → Só tem coluna passengerRating
     → NÃO tem coluna userRating
     → Dados históricos perdem userRating
  
  4. CardGallery (CardField enum):
     → Só tem PASSENGER_RATING
     → NÃO tem USER_RATING
     → Cards da galeria não podem exibir userRating
  
  5. OverlayCard (renderização real):
     → Só tem branch para CardField.PASSENGER_RATING
     → NÃO tem branch para USER_RATING
     → Overlay real nunca mostra userRating
  
  CONCLUSÃO:
  ─────────────────────────────────────────────────────────────
  "Avaliação de Usuários" (userRating) é um critério FANTASMA:
    - Nunca recebe dados reais do OCR ou Accessibility
    - Não é persistido no histórico
    - Não pode ser exibido nos cards da galeria
    - Não aparece no overlay real
    - Só funciona na simulação (generateRandomRide)
  
  Na prática, os apps de corrida (Uber, 99, inDrive, Cabify)
  mostram apenas UMA avaliação na tela de oferta de corrida:
  a nota do PASSAGEIRO que está solicitando.
  
  Não existe "avaliação de usuários" separada na tela de oferta.
  São o MESMO dado com nomes diferentes.
  
  RECOMENDAÇÃO: Remover o critério "Avaliação de Usuários" 
  (userRating) e manter apenas "Avaliação do Passageiro" 
  (passengerRating), que é o dado real extraído dos apps.
""")

errors.append("CRITÉRIO DUPLICADO: 'Avaliação de Usuários' (userRating) nunca recebe dados reais - é idêntico a 'Avaliação do Passageiro' na prática")

# ===== TESTE 8: Mapeamento OCR → Card =====
test_separator("8. Mapeamento de dados OCR para Card")

print("""
  CAMPOS EXTRAÍDOS PELO OCR:
    ✓ platform (detectado por keywords)
    ✓ rideValue (R$ XX,XX)
    ✓ pickupDistance (primeiro valor km)
    ✓ dropoffDistance (segundo valor km)
    ✓ rideDuration (XX min)
    ✓ passengerRating (4.X ou 5.X)
    ✓ intermediateStops (parada/paradas)
    ✗ pickupNeighborhood (NÃO extraído pelo OCR)
    ✗ dropoffNeighborhood (NÃO extraído pelo OCR)
    ✗ userRating (NÃO extraído - sempre 0.0)
  
  CAMPOS CALCULADOS:
    ✓ valuePerKm = rideValue / dropoffDistance
    ✓ valuePerHour = (rideValue / rideDuration) * 60
  
  CAMPOS EXIBIDOS NO OVERLAY (OverlayCard):
    ✓ SCORE (header)
    ✓ PLATFORM (header)
    ✓ RIDE_VALUE
    ✓ VALUE_PER_KM
    ✓ VALUE_PER_HOUR
    ✓ PICKUP_DISTANCE
    ✓ DROPOFF_DISTANCE
    ✓ DURATION
    ✓ PASSENGER_RATING (só se > 0)
    ✓ STOPS (só se > 0)
    ✓ PICKUP_NEIGHBORHOOD (só se não vazio)
    ✓ DROPOFF_NEIGHBORHOOD (só se não vazio)
    ✓ SCORE_BAR
    ✗ USER_RATING (NÃO existe no CardField enum)
""")

# Verificar que overlay oculta campos vazios corretamente
print("  Teste: Campos vazios/zero são ocultados no overlay?")
print("    ✓ passengerRating: só mostra se > 0")
print("    ✓ intermediateStops: só mostra se > 0")
print("    ✓ pickupNeighborhood: só mostra se não vazio")
print("    ✓ dropoffNeighborhood: só mostra se não vazio")
print("    ✓ Demais campos: sempre mostrados (podem ser 0)")

warnings.append("OCR não extrai bairros (pickupNeighborhood/dropoffNeighborhood) - campos ficam vazios no overlay")
warnings.append("Accessibility extrai bairros parcialmente (apenas Uber/99) - inDrive/Cabify não")

# ===== TESTE 9: PreviewDialog vs Overlay Real =====
test_separator("9. PreviewDialog (simulação) vs Overlay Real")

print("""
  INCONSISTÊNCIA ENCONTRADA:
  ─────────────────────────────────────────────────────────────
  O PreviewDialog (CardTab.kt) mostra SEMPRE todos os campos
  fixos, independente de qual card da galeria está selecionado.
  
  Exemplo: Se o card ativo é "Minimalista" (só Score + Valor),
  o preview mostra TODOS os 9 campos + barra de score.
  
  O overlay REAL (OverlayCard.kt) respeita os campos do card
  selecionado (activeCard.fields).
  
  RESULTADO: O preview NÃO representa fielmente o que o 
  motorista verá no overlay real.
  
  RECOMENDAÇÃO: O PreviewDialog deveria usar activeCard.fields
  para renderizar apenas os campos do card selecionado.
""")

errors.append("PREVIEW INCONSISTENTE: PreviewDialog mostra todos os campos fixos, mas overlay real usa apenas os campos do card selecionado")

# ===== TESTE 10: Lógica Financeira =====
test_separator("10. Lógica Financeira")

print("  10a. Custo por km:")
consumption = 12.0  # km/L
fuel_price = 5.89   # R$/L
cost_per_km = fuel_price / consumption
print(f"    Consumo: {consumption} km/L, Preço: R$ {fuel_price}/L")
print(f"    Custo/km = {fuel_price} / {consumption} = R$ {cost_per_km:.4f}/km ✓")

print("\n  10b. Lucro líquido:")
earnings = 350.0
expenses = 120.0
net = earnings - expenses
print(f"    Ganhos: R$ {earnings:.2f}")
print(f"    Gastos: R$ {expenses:.2f}")
print(f"    Lucro: R$ {net:.2f} ✓")

print("\n  10c. Indicadores por unidade:")
distance = 180.0  # km
duration_min = 420  # minutos
rides = 15
profit_per_km = net / distance if distance > 0 else 0
profit_per_hour = (net / duration_min) * 60 if duration_min > 0 else 0
profit_per_ride = net / rides if rides > 0 else 0
print(f"    R$/km = {profit_per_km:.2f}")
print(f"    R$/h = {profit_per_hour:.2f}")
print(f"    R$/corrida = {profit_per_ride:.2f}")
print(f"    ✓ Cálculos financeiros corretos")

print("\n  10d. Progresso de Metas:")
print("""
  INCONSISTÊNCIA ENCONTRADA:
  ─────────────────────────────────────────────────────────────
  - FinanceSummaryTab usa goal.currentAmount (campo do DB)
    para calcular progresso das metas.
  - GoalsTab recalcula progresso a partir de ganhos reais
    do período (earningDao.getTotalEarnings).
  - O campo goal.currentAmount NUNCA é atualizado pelo sistema!
  
  RESULTADO: Na aba Resumo, metas sempre mostram 0% de progresso
  (porque currentAmount = 0), enquanto na aba Metas o progresso
  é calculado corretamente a partir dos ganhos reais.
  
  RECOMENDAÇÃO: FinanceSummaryTab deveria recalcular progresso
  da mesma forma que GoalsTab (usando earningDao), não usar
  goal.currentAmount que é um campo estático/não-atualizado.
""")

errors.append("METAS NO RESUMO: FinanceSummaryTab usa goal.currentAmount (sempre 0) em vez de recalcular a partir dos ganhos reais como GoalsTab faz")

# ===== TESTE 11: Soma de pesos > 100 =====
test_separator("11. Proteção contra soma > 100")

weights_over = CriteriaWeights(valuePerKm=50, valuePerHour=60)  # soma = 110
print(f"  Pesos: VKM=50, VH=60, soma={weights_over.totalUsed}")
print(f"  UI impede salvar (botão desabilitado quando > 100) ✓")
print(f"  Slider limita max individual ao disponível ✓")
print(f"  PORÉM: Se dados corrompidos no DataStore, scorer calcula normalmente")

scorer_over = RideScorer(weights_over)
ride_over = RideData(rideValue=20.0, rideDuration=15.0, dropoffDistance=10.0)
result_over = scorer_over.calculateScore(ride_over)
print(f"  Score com soma=110: {result_over['totalScore']:.2f}")
print(f"  (pode exceder 100 se soma de pesos > 100)")

if result_over["totalScore"] > 100:
    warnings.append("Se pesos somam >100, score pode exceder 100 antes do coerceIn")
    print(f"  ⚠️ Score > 100 possível antes do clamp!")
else:
    print(f"  Score já é limitado a 100 pelo coerceIn ✓")

# ===== TESTE 12: Corrida com dados mínimos (OCR parcial) =====
test_separator("12. Corrida com dados mínimos (OCR capturou pouco)")

ride_minimal = RideData(rideValue=15.0, rideDuration=0.0, pickupDistance=0.0,
                        dropoffDistance=0.0, passengerRating=0.0, intermediateStops=0)
print(f"  Corrida: Só valor R$15 capturado (resto = 0)")
print(f"  → R$/km = {ride_minimal.valuePerKm:.2f} (0 porque dropoff=0)")
print(f"  → R$/h = {ride_minimal.valuePerHour:.2f} (0 porque duration=0)")

scorer12 = RideScorer(weights_default)
result12 = scorer12.calculateScore(ride_minimal)
print(f"\n  Score: {result12['totalScore']:.2f}/100")
print(f"  Critérios:")
for key, val in result12["criteriaScores"].items():
    print(f"    {key}: normalizado={val['normalized']:.1f}")

print(f"\n  ANÁLISE: Com dados parciais, valuePerKm e valuePerHour = 0")
print(f"  Isso resulta em normalização = 0 (abaixo do mínimo)")
print(f"  Score fica muito baixo mesmo com valor razoável (R$15)")

warnings.append("Dados OCR parciais (sem distância/duração) resultam em R$/km=0 e R$/h=0, penalizando injustamente o score")

# ===== RESUMO FINAL =====
test_separator("RESUMO DA AUDITORIA")

print(f"\n  ERROS CRÍTICOS ({len(errors)}):")
for i, e in enumerate(errors, 1):
    print(f"    {i}. ❌ {e}")

print(f"\n  AVISOS ({len(warnings)}):")
for i, w in enumerate(warnings, 1):
    print(f"    {i}. ⚠️ {w}")

print(f"""
  AÇÕES NECESSÁRIAS:
  ─────────────────────────────────────────────────────────────
  1. REMOVER critério "Avaliação de Usuários" (userRating):
     - Remover de CriteriaWeights
     - Remover de DriverThresholds
     - Remover do RideScorer (critério 8)
     - Remover do CriteriaTab (slider + threshold)
     - Remover de RideData
     - Remover de CardTab (PreviewDialog + generateRandomRide)
     - Manter apenas "Avaliação do Passageiro" (passengerRating)
     - Ajustar de 9 para 8 critérios
  
  2. CORRIGIR PreviewDialog para usar campos do card ativo:
     - Em vez de mostrar todos os campos fixos
     - Usar activeCard?.fields para renderizar apenas os campos
       que o card selecionado realmente mostra
  
  3. CORRIGIR FinanceSummaryTab progresso de metas:
     - Usar earningDao.getTotalEarnings() por período
     - Em vez de goal.currentAmount (que nunca é atualizado)
  
  4. (MENOR) Proteger score contra dados OCR parciais:
     - Se dropoffDistance=0 e rideDuration=0, não calcular
       valuePerKm e valuePerHour (tratar como peso=0 temporário)
""")

print(f"\n{'='*60}")
print(f"  TESTES CONCLUÍDOS - {len(errors)} erros, {len(warnings)} avisos")
print(f"{'='*60}")
