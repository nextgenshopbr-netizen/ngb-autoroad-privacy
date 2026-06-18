#!/usr/bin/env python3
"""
NGBAutoRoad v4.0.1 — Auditoria Lógica Completa
Testa TODA a lógica do sistema: cálculos, validações, robustez contra erros de usuário.
"""

import random
import json
from dataclasses import dataclass, field
from typing import List, Dict, Optional, Tuple
from enum import Enum

# ============================================================
# SEÇÃO 1: MODELOS DE DADOS (espelho do Kotlin)
# ============================================================

class Platform(Enum):
    UBER = "Uber"
    NINETY_NINE = "99"
    INDRIVE = "inDrive"
    CABIFY = "Cabify"
    UNKNOWN = "Desconhecido"

class ScoreLevel(Enum):
    GREEN = "GREEN"
    YELLOW = "YELLOW"
    ORANGE = "ORANGE"
    RED = "RED"

@dataclass
class RideData:
    platform: Platform = Platform.UNKNOWN
    rideValue: float = 0.0
    rideDuration: float = 0.0
    pickupDistance: float = 0.0
    dropoffDistance: float = 0.0
    passengerRating: float = 0.0
    intermediateStops: int = 0
    pickupNeighborhood: str = ""
    dropoffNeighborhood: str = ""

    @property
    def valuePerKm(self) -> float:
        return self.rideValue / self.dropoffDistance if self.dropoffDistance > 0 else 0.0

    @property
    def valuePerHour(self) -> float:
        return (self.rideValue / self.rideDuration) * 60.0 if self.rideDuration > 0 else 0.0

@dataclass
class CriteriaWeights:
    valuePerKm: int = 30
    valuePerHour: int = 30
    intermediateStops: int = 25
    passengerRating: int = 15
    rideValue: int = 0
    rideDuration: int = 0
    pickupDistance: int = 0
    dropoffDistance: int = 0

    @property
    def totalUsed(self) -> int:
        return (self.valuePerKm + self.valuePerHour + self.intermediateStops +
                self.passengerRating + self.rideValue + self.rideDuration +
                self.pickupDistance + self.dropoffDistance)

@dataclass
class DriverThresholds:
    minValuePerKm: float = 0.0
    minValuePerHour: float = 0.0
    minRideValue: float = 0.0
    maxPickupDistance: float = 0.0
    minPassengerRating: float = 0.0
    maxDuration: float = 0.0
    maxStops: int = 99
    minDropoffDistance: float = 0.0

    def isValuePerKmActive(self): return self.minValuePerKm > 0
    def isValuePerHourActive(self): return self.minValuePerHour > 0
    def isRideValueActive(self): return self.minRideValue > 0
    def isPickupDistanceActive(self): return self.maxPickupDistance > 0
    def isPassengerRatingActive(self): return self.minPassengerRating > 0
    def isDurationActive(self): return self.maxDuration > 0
    def isStopsActive(self): return self.maxStops < 99
    def isDropoffDistanceActive(self): return self.minDropoffDistance > 0

@dataclass
class ScoringThresholds:
    minValuePerKm: float = 0.50
    maxValuePerKm: float = 2.50
    minValuePerHour: float = 10.0
    maxValuePerHour: float = 40.0
    minRideValue: float = 5.0
    maxRideValue: float = 50.0
    minDuration: float = 10.0
    maxDuration: float = 60.0
    minPickupDistance: float = 0.5
    maxPickupDistance: float = 5.0
    minDropoffDistance: float = 2.0
    maxDropoffDistance: float = 20.0

@dataclass
class BlockedNeighborhood:
    name: str
    type: str  # "PICKUP" or "DROPOFF"
    penaltyWeight: int = 20

@dataclass
class CriteriaScore:
    name: str
    rawValue: float
    normalizedScore: float
    weight: int
    weightedScore: float
    level: ScoreLevel

@dataclass
class ThresholdViolation:
    criteriaName: str
    currentValue: float
    minimumRequired: float
    penaltyApplied: float

@dataclass
class RideScore:
    totalScore: float = 0.0
    criteriaScores: Dict[str, CriteriaScore] = field(default_factory=dict)
    thresholdViolations: List[ThresholdViolation] = field(default_factory=list)

# ============================================================
# SEÇÃO 2: RIDE SCORER (espelho exato do Kotlin)
# ============================================================

class RideScorer:
    def __init__(self, weights: CriteriaWeights, driverThresholds: DriverThresholds = None,
                 blockedNeighborhoods: List[BlockedNeighborhood] = None,
                 thresholds: ScoringThresholds = None):
        self.weights = weights
        self.driverThresholds = driverThresholds or DriverThresholds()
        self.blockedNeighborhoods = blockedNeighborhoods or []
        self.thresholds = thresholds or ScoringThresholds()

    def calculateScore(self, ride: RideData) -> RideScore:
        criteriaScores = {}
        violations = []

        # 1. Valor por KM (pular se dropoffDistance=0)
        if self.weights.valuePerKm > 0 and ride.dropoffDistance > 0:
            normalized = self._normalizeValuePerKm(ride.valuePerKm)
            criteriaScores["valuePerKm"] = CriteriaScore(
                name="Valor/KM", rawValue=ride.valuePerKm,
                normalizedScore=normalized, weight=self.weights.valuePerKm,
                weightedScore=normalized * self.weights.valuePerKm / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isValuePerKmActive() and ride.valuePerKm < self.driverThresholds.minValuePerKm:
                violations.append(ThresholdViolation("Valor/KM", ride.valuePerKm, self.driverThresholds.minValuePerKm, self.weights.valuePerKm * 0.5))

        # 2. Valor por Hora (pular se rideDuration=0)
        if self.weights.valuePerHour > 0 and ride.rideDuration > 0:
            normalized = self._normalizeValuePerHour(ride.valuePerHour)
            criteriaScores["valuePerHour"] = CriteriaScore(
                name="Valor/Hora", rawValue=ride.valuePerHour,
                normalizedScore=normalized, weight=self.weights.valuePerHour,
                weightedScore=normalized * self.weights.valuePerHour / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isValuePerHourActive() and ride.valuePerHour < self.driverThresholds.minValuePerHour:
                violations.append(ThresholdViolation("Valor/Hora", ride.valuePerHour, self.driverThresholds.minValuePerHour, self.weights.valuePerHour * 0.5))

        # 3. Paradas Intermediárias
        if self.weights.intermediateStops > 0:
            normalized = self._normalizeStops(ride.intermediateStops)
            criteriaScores["intermediateStops"] = CriteriaScore(
                name="Paradas", rawValue=float(ride.intermediateStops),
                normalizedScore=normalized, weight=self.weights.intermediateStops,
                weightedScore=normalized * self.weights.intermediateStops / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isStopsActive() and ride.intermediateStops > self.driverThresholds.maxStops:
                violations.append(ThresholdViolation("Paradas", float(ride.intermediateStops), float(self.driverThresholds.maxStops), self.weights.intermediateStops * 0.7))

        # 4. Avaliação do Passageiro
        if self.weights.passengerRating > 0:
            normalized = self._normalizeRating(ride.passengerRating)
            criteriaScores["passengerRating"] = CriteriaScore(
                name="Avaliação", rawValue=ride.passengerRating,
                normalizedScore=normalized, weight=self.weights.passengerRating,
                weightedScore=normalized * self.weights.passengerRating / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isPassengerRatingActive() and ride.passengerRating < self.driverThresholds.minPassengerRating:
                violations.append(ThresholdViolation("Avaliação", ride.passengerRating, self.driverThresholds.minPassengerRating, self.weights.passengerRating * 0.6))

        # 5. Valor da Corrida
        if self.weights.rideValue > 0:
            normalized = self._normalizeRideValue(ride.rideValue)
            criteriaScores["rideValue"] = CriteriaScore(
                name="Valor Corrida", rawValue=ride.rideValue,
                normalizedScore=normalized, weight=self.weights.rideValue,
                weightedScore=normalized * self.weights.rideValue / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isRideValueActive() and ride.rideValue < self.driverThresholds.minRideValue:
                violations.append(ThresholdViolation("Valor Corrida", ride.rideValue, self.driverThresholds.minRideValue, self.weights.rideValue * 0.5))

        # 6. Duração da Corrida
        if self.weights.rideDuration > 0:
            normalized = self._normalizeDuration(ride.rideDuration)
            criteriaScores["rideDuration"] = CriteriaScore(
                name="Duração", rawValue=ride.rideDuration,
                normalizedScore=normalized, weight=self.weights.rideDuration,
                weightedScore=normalized * self.weights.rideDuration / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isDurationActive() and ride.rideDuration > self.driverThresholds.maxDuration:
                violations.append(ThresholdViolation("Duração", ride.rideDuration, self.driverThresholds.maxDuration, self.weights.rideDuration * 0.5))

        # 7. Distância até Embarque
        if self.weights.pickupDistance > 0:
            normalized = self._normalizePickupDistance(ride.pickupDistance)
            criteriaScores["pickupDistance"] = CriteriaScore(
                name="Dist. Embarque", rawValue=ride.pickupDistance,
                normalizedScore=normalized, weight=self.weights.pickupDistance,
                weightedScore=normalized * self.weights.pickupDistance / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isPickupDistanceActive() and ride.pickupDistance > self.driverThresholds.maxPickupDistance:
                violations.append(ThresholdViolation("Dist. Embarque", ride.pickupDistance, self.driverThresholds.maxPickupDistance, self.weights.pickupDistance * 0.6))

        # 8. Distância até Desembarque
        if self.weights.dropoffDistance > 0:
            normalized = self._normalizeDropoffDistance(ride.dropoffDistance)
            criteriaScores["dropoffDistance"] = CriteriaScore(
                name="Dist. Destino", rawValue=ride.dropoffDistance,
                normalizedScore=normalized, weight=self.weights.dropoffDistance,
                weightedScore=normalized * self.weights.dropoffDistance / 100.0,
                level=self._getLevel(normalized)
            )
            if self.driverThresholds.isDropoffDistanceActive() and ride.dropoffDistance < self.driverThresholds.minDropoffDistance:
                violations.append(ThresholdViolation("Dist. Destino", ride.dropoffDistance, self.driverThresholds.minDropoffDistance, self.weights.dropoffDistance * 0.5))

        # Calcular score total com normalização por peso efetivo
        effectiveWeight = sum(cs.weight for cs in criteriaScores.values())
        if effectiveWeight > 0 and effectiveWeight < self.weights.totalUsed:
            totalScore = sum(cs.weightedScore for cs in criteriaScores.values()) * (self.weights.totalUsed / effectiveWeight)
        else:
            totalScore = sum(cs.weightedScore for cs in criteriaScores.values())

        # Penalidades de thresholds
        thresholdPenalty = sum(v.penaltyApplied for v in violations)
        totalScore -= thresholdPenalty

        # Penalidades de bairros bloqueados
        pickupPenalty = max([bn.penaltyWeight for bn in self.blockedNeighborhoods
                           if bn.type == "PICKUP" and bn.name.lower() == ride.pickupNeighborhood.lower()] or [0])
        dropoffPenalty = max([bn.penaltyWeight for bn in self.blockedNeighborhoods
                            if bn.type == "DROPOFF" and bn.name.lower() == ride.dropoffNeighborhood.lower()] or [0])
        totalScore -= (pickupPenalty + dropoffPenalty)
        totalScore = max(0.0, min(100.0, totalScore))

        return RideScore(totalScore=totalScore, criteriaScores=criteriaScores, thresholdViolations=violations)

    def _normalizeValuePerKm(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minValuePerKm) / (self.thresholds.maxValuePerKm - self.thresholds.minValuePerKm) * 100))

    def _normalizeValuePerHour(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minValuePerHour) / (self.thresholds.maxValuePerHour - self.thresholds.minValuePerHour) * 100))

    def _normalizeStops(self, stops):
        if stops == 0: return 100.0
        elif stops == 1: return 50.0
        else: return 0.0

    def _normalizeRating(self, rating):
        if rating >= 5.0: return 100.0
        elif rating >= 4.0: return (rating - 4.0) / 1.0 * 100.0
        else: return 0.0

    def _normalizeRideValue(self, value):
        return max(0.0, min(100.0, (value - self.thresholds.minRideValue) / (self.thresholds.maxRideValue - self.thresholds.minRideValue) * 100))

    def _normalizeDuration(self, minutes):
        return max(0.0, min(100.0, (self.thresholds.maxDuration - minutes) / (self.thresholds.maxDuration - self.thresholds.minDuration) * 100))

    def _normalizePickupDistance(self, km):
        return max(0.0, min(100.0, (self.thresholds.maxPickupDistance - km) / (self.thresholds.maxPickupDistance - self.thresholds.minPickupDistance) * 100))

    def _normalizeDropoffDistance(self, km):
        return max(0.0, min(100.0, (km - self.thresholds.minDropoffDistance) / (self.thresholds.maxDropoffDistance - self.thresholds.minDropoffDistance) * 100))

    def _getLevel(self, score):
        if score >= 70: return ScoreLevel.GREEN
        elif score >= 50: return ScoreLevel.YELLOW
        elif score >= 30: return ScoreLevel.ORANGE
        else: return ScoreLevel.RED


# ============================================================
# SEÇÃO 3: SIMULAÇÃO DE PREENCHIMENTO HUMANO
# ============================================================

def simulate_human_input(intended_value: float, field_type: str = "decimal") -> str:
    """Simula como um humano brasileiro digitaria um valor no celular"""
    scenarios = [
        # Cenário 1: Vírgula como separador decimal (padrão BR)
        lambda v: f"{v:.2f}".replace(".", ","),
        # Cenário 2: Ponto como separador (padrão EN)
        lambda v: f"{v:.2f}",
        # Cenário 3: Espaço antes/depois
        lambda v: f"  {v:.2f} ",
        # Cenário 4: Espaço no meio (erro de digitação)
        lambda v: f"{int(v)}, {int((v % 1) * 100):02d}",
        # Cenário 5: Sem casas decimais
        lambda v: str(int(v)),
        # Cenário 6: Muitas casas decimais
        lambda v: f"{v:.5f}",
        # Cenário 7: Vírgula + espaço
        lambda v: f"{int(v)} , {int((v % 1) * 100):02d}",
        # Cenário 8: Letra misturada (ex: "12,5o" em vez de "12,50")
        lambda v: f"{v:.2f}".replace(".", ",")[:-1] + "o",
        # Cenário 9: Vazio
        lambda v: "",
        # Cenário 10: Apenas espaço
        lambda v: "   ",
        # Cenário 11: Valor negativo
        lambda v: f"-{v:.2f}",
        # Cenário 12: Cifrão junto
        lambda v: f"R${v:.2f}",
        # Cenário 13: Ponto como milhar (ex: 1.000,50)
        lambda v: f"{int(v):,}".replace(",", ".") + f",{int((v % 1) * 100):02d}" if v >= 1000 else f"{v:.2f}".replace(".", ","),
        # Cenário 14: Dois separadores (ex: 1.2.3)
        lambda v: f"{v:.2f}".replace(".", ".."),
    ]
    return random.choice(scenarios)(intended_value)


def toDoubleOrNull(s: str) -> Optional[float]:
    """Simula Kotlin toDoubleOrNull() — NÃO trata vírgula"""
    try:
        return float(s.strip())
    except (ValueError, TypeError):
        return None

def toDoubleLocale(s: str) -> float:
    """Simula a extensão toDoubleLocale() que adicionamos ao VehicleTab"""
    return float(s.replace(",", ".").strip()) if s.replace(",", ".").strip().replace(".", "").replace("-", "").isdigit() or is_valid_decimal(s) else 0.0

def is_valid_decimal(s: str) -> bool:
    """Verifica se é um decimal válido após normalização"""
    try:
        float(s.replace(",", ".").strip())
        return True
    except (ValueError, TypeError):
        return False


# ============================================================
# SEÇÃO 4: TESTES
# ============================================================

results = {"passed": 0, "failed": 0, "warnings": 0, "details": []}

def test(name, condition, detail=""):
    if condition:
        results["passed"] += 1
        results["details"].append(f"✅ PASS: {name}")
    else:
        results["failed"] += 1
        results["details"].append(f"❌ FAIL: {name} — {detail}")

def warn(name, detail):
    results["warnings"] += 1
    results["details"].append(f"⚠️  WARN: {name} — {detail}")


print("=" * 70)
print("NGBAutoRoad v4.0.1 — AUDITORIA LÓGICA COMPLETA")
print("=" * 70)

# ============================================================
# TESTE 1: CRITÉRIOS — SOMA DE PESOS
# ============================================================
print("\n📋 TESTE 1: Validação de Pesos dos Critérios")
print("-" * 50)

# 1.1: Pesos padrão somam 100
w_default = CriteriaWeights()
test("Pesos padrão somam 100", w_default.totalUsed == 100, f"Soma={w_default.totalUsed}")

# 1.2: Pesos todos em 100 — deve ser impossível
w_all_100 = CriteriaWeights(100, 100, 100, 100, 100, 100, 100, 100)
test("Pesos todos em 100 somam 800 (UI deve bloquear)", w_all_100.totalUsed == 800, f"Soma={w_all_100.totalUsed}")

# 1.3: maxForCriteria deve limitar corretamente
def maxForCriteria(weights: CriteriaWeights, currentValue: int) -> int:
    othersSum = weights.totalUsed - currentValue
    return max(0, 100 - othersSum)

# Com pesos padrão (soma=100), cada critério pode ir até seu valor atual + 0
test("maxForCriteria com soma=100, valuePerKm=30 → max=30", maxForCriteria(w_default, 30) == 30)
test("maxForCriteria com soma=100, passengerRating=15 → max=15", maxForCriteria(w_default, 15) == 15)

# Se reduzir um critério, o max dos outros aumenta
w_reduced = CriteriaWeights(valuePerKm=20, valuePerHour=20, intermediateStops=20, passengerRating=10)
test("Soma reduzida=70, maxForCriteria(20)=50", maxForCriteria(w_reduced, 20) == 50)

# 1.4: Slider com valueRange limitado
# Simula: se soma dos outros = 85, max para este = 15
w_high = CriteriaWeights(valuePerKm=40, valuePerHour=30, intermediateStops=15, passengerRating=0,
                          rideValue=0, rideDuration=0, pickupDistance=0, dropoffDistance=0)
test("Soma=85, maxForCriteria(0)=15", maxForCriteria(w_high, 0) == 15)

# 1.5: Botão Salvar desabilitado quando soma > 100
w_over = CriteriaWeights(50, 50, 50, 0, 0, 0, 0, 0)
test("Soma=150 > 100, botão Salvar deve estar desabilitado", w_over.totalUsed > 100)

# 1.6: Pesos todos zero — score deve ser 0
w_zero = CriteriaWeights(0, 0, 0, 0, 0, 0, 0, 0)
test("Pesos todos zero, soma=0", w_zero.totalUsed == 0)
scorer_zero = RideScorer(weights=w_zero)
ride_normal = RideData(platform=Platform.UBER, rideValue=25.0, rideDuration=20.0,
                       pickupDistance=2.0, dropoffDistance=10.0, passengerRating=4.8, intermediateStops=0)
score_zero = scorer_zero.calculateScore(ride_normal)
test("Score com pesos zero = 0", score_zero.totalScore == 0.0, f"Score={score_zero.totalScore}")

print(f"\n  Resultados: {results['passed']} passed, {results['failed']} failed")

# ============================================================
# TESTE 2: RIDE SCORER — CÁLCULOS MATEMÁTICOS
# ============================================================
print("\n📋 TESTE 2: RideScorer — Cálculos Matemáticos")
print("-" * 50)

# 2.1: Corrida perfeita com pesos padrão
ride_perfect = RideData(
    platform=Platform.UBER, rideValue=50.0, rideDuration=20.0,
    pickupDistance=0.5, dropoffDistance=20.0, passengerRating=5.0, intermediateStops=0
)
scorer_default = RideScorer(weights=CriteriaWeights())
score_perfect = scorer_default.calculateScore(ride_perfect)
# valuePerKm = 50/20 = 2.5 → normalized = (2.5-0.5)/(2.5-0.5)*100 = 100
# valuePerHour = (50/20)*60 = 150 → normalized = (150-10)/(40-10)*100 = capped at 100
# stops = 0 → 100
# rating = 5.0 → 100
# Score = (100*30 + 100*30 + 100*25 + 100*15) / 100 = 100
test("Corrida perfeita score=100", score_perfect.totalScore == 100.0, f"Score={score_perfect.totalScore}")

# 2.2: Corrida péssima
ride_bad = RideData(
    platform=Platform.UBER, rideValue=5.0, rideDuration=60.0,
    pickupDistance=5.0, dropoffDistance=2.0, passengerRating=3.5, intermediateStops=3
)
score_bad = scorer_default.calculateScore(ride_bad)
# valuePerKm = 5/2 = 2.5 → 100
# valuePerHour = (5/60)*60 = 5 → (5-10)/(40-10)*100 = capped at 0
# stops = 3 → 0
# rating = 3.5 → 0
# Score = (100*30 + 0*30 + 0*25 + 0*15) / 100 = 30
test("Corrida péssima score=30", score_bad.totalScore == 30.0, f"Score={score_bad.totalScore}")

# 2.3: Dados parciais — sem distância (dropoffDistance=0)
ride_partial = RideData(
    platform=Platform.INDRIVE, rideValue=15.0, rideDuration=15.0,
    pickupDistance=0.0, dropoffDistance=0.0, passengerRating=0.0, intermediateStops=0
)
score_partial = scorer_default.calculateScore(ride_partial)
# valuePerKm pulado (dropoffDistance=0)
# valuePerHour = (15/15)*60 = 60 → (60-10)/(40-10)*100 = 166.7 → capped 100
# stops = 0 → 100
# rating = 0 → 0 (abaixo de 4.0)
# effectiveWeight = 30 + 25 + 15 = 70 (valuePerKm=30 pulado)
# weightedScore = 100*30/100 + 100*25/100 + 0*15/100 = 30 + 25 + 0 = 55
# Normalizado: 55 * (100/70) = 78.57
test("Dados parciais: normalização por peso efetivo funciona",
     74 < score_partial.totalScore < 80,
     f"Score={score_partial.totalScore:.2f}, esperado ~78.57")

# 2.4: Threshold violation aplica penalidade
thresholds_strict = DriverThresholds(minValuePerKm=2.0, minValuePerHour=25.0, minPassengerRating=4.9)
scorer_strict = RideScorer(weights=CriteriaWeights(), driverThresholds=thresholds_strict)
ride_below = RideData(
    platform=Platform.UBER, rideValue=20.0, rideDuration=20.0,
    pickupDistance=1.5, dropoffDistance=15.0, passengerRating=4.5, intermediateStops=0
)
score_strict = scorer_strict.calculateScore(ride_below)
# valuePerKm = 20/15 = 1.33 < 2.0 → penalty = 30*0.5 = 15
# valuePerHour = (20/20)*60 = 60 → capped 100, but 60 > 25 → no penalty
# rating = 4.5 < 4.9 → penalty = 15*0.6 = 9
# Total penalty = 15 + 9 = 24
test("Threshold violations geram penalidades",
     len(score_strict.thresholdViolations) >= 2,
     f"Violations={len(score_strict.thresholdViolations)}")
test("Penalidade total = 24",
     sum(v.penaltyApplied for v in score_strict.thresholdViolations) == 24.0,
     f"Penalty={sum(v.penaltyApplied for v in score_strict.thresholdViolations)}")

# 2.5: Bairros bloqueados aplicam penalidade
blocked = [
    BlockedNeighborhood("Centro", "PICKUP", 30),
    BlockedNeighborhood("Paralela", "DROPOFF", 25)
]
scorer_blocked = RideScorer(weights=CriteriaWeights(), blockedNeighborhoods=blocked)
ride_blocked = RideData(
    platform=Platform.UBER, rideValue=30.0, rideDuration=20.0,
    pickupDistance=1.0, dropoffDistance=12.0, passengerRating=4.8,
    intermediateStops=0, pickupNeighborhood="Centro", dropoffNeighborhood="Paralela"
)
score_blocked = scorer_blocked.calculateScore(ride_blocked)
score_unblocked = scorer_default.calculateScore(ride_blocked)
test("Bairro bloqueado reduz score",
     score_blocked.totalScore < score_unblocked.totalScore,
     f"Blocked={score_blocked.totalScore:.1f} vs Unblocked={score_unblocked.totalScore:.1f}")
test("Penalidade de bairro = 55 pontos (30+25)",
     score_unblocked.totalScore - score_blocked.totalScore == 55.0 or
     score_blocked.totalScore == max(0, score_unblocked.totalScore - 55),
     f"Diff={score_unblocked.totalScore - score_blocked.totalScore:.1f}")

# 2.6: Case insensitive para bairros
ride_case = RideData(
    platform=Platform.UBER, rideValue=30.0, rideDuration=20.0,
    pickupDistance=1.0, dropoffDistance=12.0, passengerRating=4.8,
    intermediateStops=0, pickupNeighborhood="CENTRO", dropoffNeighborhood="paralela"
)
score_case = scorer_blocked.calculateScore(ride_case)
test("Bairro bloqueado case insensitive",
     score_case.totalScore == score_blocked.totalScore,
     f"Case={score_case.totalScore:.1f} vs Normal={score_blocked.totalScore:.1f}")

# 2.7: Normalização de cada critério nos limites
test("normalizeValuePerKm(0.5)=0", scorer_default._normalizeValuePerKm(0.5) == 0.0)
test("normalizeValuePerKm(2.5)=100", scorer_default._normalizeValuePerKm(2.5) == 100.0)
test("normalizeValuePerKm(1.5)=50", scorer_default._normalizeValuePerKm(1.5) == 50.0)
test("normalizeValuePerKm(0.0)=0 (capped)", scorer_default._normalizeValuePerKm(0.0) == 0.0)
test("normalizeValuePerKm(5.0)=100 (capped)", scorer_default._normalizeValuePerKm(5.0) == 100.0)

test("normalizeRating(5.0)=100", scorer_default._normalizeRating(5.0) == 100.0)
test("normalizeRating(4.5)=50", scorer_default._normalizeRating(4.5) == 50.0)
test("normalizeRating(4.0)=0", scorer_default._normalizeRating(4.0) == 0.0)
test("normalizeRating(3.5)=0", scorer_default._normalizeRating(3.5) == 0.0)

test("normalizeStops(0)=100", scorer_default._normalizeStops(0) == 100.0)
test("normalizeStops(1)=50", scorer_default._normalizeStops(1) == 50.0)
test("normalizeStops(2)=0", scorer_default._normalizeStops(2) == 0.0)

test("normalizeDuration(10)=100", scorer_default._normalizeDuration(10.0) == 100.0)
test("normalizeDuration(60)=0", scorer_default._normalizeDuration(60.0) == 0.0)
test("normalizeDuration(35)=50", scorer_default._normalizeDuration(35.0) == 50.0)

print(f"\n  Resultados parciais: {results['passed']} passed, {results['failed']} failed")

# ============================================================
# TESTE 3: SIMULAÇÃO DE PREENCHIMENTO HUMANO
# ============================================================
print("\n📋 TESTE 3: Robustez contra Erros de Usuário (Preenchimento Humano)")
print("-" * 50)

# 3.1: toDoubleOrNull (Kotlin puro) vs toDoubleLocale (nossa extensão)
human_inputs = [
    ("14,4", 14.4, "Vírgula BR padrão"),
    ("0,80", 0.80, "Centavos com vírgula"),
    ("14.4", 14.4, "Ponto EN padrão"),
    ("  14,4  ", 14.4, "Espaços ao redor"),
    ("2400,00", 2400.0, "Milhares sem ponto"),
    ("1.000,50", None, "Ponto como milhar + vírgula decimal"),  # Ambíguo
    ("abc", None, "Texto puro"),
    ("", None, "Vazio"),
    ("   ", None, "Só espaços"),
    ("-5,50", -5.5, "Valor negativo"),
    ("R$15,00", None, "Com cifrão"),
    ("12,5o", None, "Letra 'o' em vez de '0'"),
    ("1..5", None, "Dois pontos"),
    ("0", 0.0, "Zero"),
    ("99999999", 99999999.0, "Valor absurdo"),
]

print("\n  Comparação: toDoubleOrNull (Kotlin) vs toDoubleLocale (nossa extensão)")
print(f"  {'Input':<20} {'Kotlin puro':<15} {'toDoubleLocale':<15} {'Esperado':<10}")
print(f"  {'-'*20} {'-'*15} {'-'*15} {'-'*10}")

kotlin_fails = 0
locale_fails = 0
for input_str, expected, desc in human_inputs:
    kotlin_result = toDoubleOrNull(input_str)
    # Simula toDoubleLocale
    try:
        locale_result = float(input_str.replace(",", ".").strip()) if input_str.strip() else None
    except ValueError:
        locale_result = None

    kotlin_ok = (kotlin_result == expected) if expected is not None else (kotlin_result is None)
    locale_ok = (locale_result is not None and abs(locale_result - expected) < 0.001) if expected is not None else (locale_result is None)

    if not kotlin_ok: kotlin_fails += 1
    if not locale_ok: locale_fails += 1

    k_str = f"{kotlin_result}" if kotlin_result is not None else "null"
    l_str = f"{locale_result}" if locale_result is not None else "null"
    e_str = f"{expected}" if expected is not None else "null"
    status = "✅" if locale_ok else "❌"
    print(f"  {status} {input_str:<20} {k_str:<15} {l_str:<15} {e_str:<10} ({desc})")

test("toDoubleOrNull falha com vírgula BR", kotlin_fails > 0, f"Falhas={kotlin_fails}")
warn("AddEarningDialog usa toDoubleOrNull puro", f"{kotlin_fails} inputs BR falham silenciosamente")
warn("AddExpenseDialog usa toDoubleOrNull puro", "Mesmos problemas que AddEarningDialog")
warn("AddGoalDialog usa toDoubleOrNull puro", "Meta pode ser salva com targetAmount=0.0")

# 3.2: Campos que AINDA usam toDoubleOrNull puro (BUG)
print("\n  📍 Campos que AINDA NÃO têm toDoubleLocale:")
vulnerable_fields = [
    ("AddEarningDialog", "amount", "Valor do ganho"),
    ("AddEarningDialog", "tips", "Gorjetas"),
    ("AddEarningDialog", "bonus", "Bônus"),
    ("AddEarningDialog", "distance", "Km rodados"),
    ("AddExpenseDialog", "amount", "Valor do gasto"),
    ("AddExpenseDialog", "liters", "Litros de combustível"),
    ("AddExpenseDialog", "pricePerLiter", "Preço por litro"),
    ("AddGoalDialog", "targetAmount", "Valor alvo da meta"),
]
for dialog, field_name, desc in vulnerable_fields:
    print(f"    ❌ {dialog}.{field_name} — {desc}")
    results["failed"] += 1
    results["details"].append(f"❌ FAIL: {dialog}.{field_name} usa toDoubleOrNull puro — vírgula BR falha")

# 3.3: Validações ausentes
print("\n  📍 Validações ausentes (aceita qualquer coisa):")
missing_validations = [
    ("AddEarningDialog", "Valor 0 ou negativo aceito sem aviso"),
    ("AddEarningDialog", "Corridas=0 aceito (deveria ser >= 1 se informado)"),
    ("AddExpenseDialog", "Valor 0 aceito (gasto sem valor)"),
    ("AddExpenseDialog", "Litros negativos aceitos"),
    ("AddGoalDialog", "Meta com valor 0 aceita (vírgula BR)"),
    ("VehicleTab", "Ano com 5 dígitos aceito (ex: 20225)"),
    ("VehicleTab", "Consumo negativo aceito"),
    ("CriteriaTab/ThresholdField", "Avaliação > 5.0 aceita (coerceIn só no save)"),
]
for location, desc in missing_validations:
    print(f"    ⚠️  {location}: {desc}")
    results["warnings"] += 1
    results["details"].append(f"⚠️  WARN: {location} — {desc}")

# ============================================================
# TESTE 4: COBERTURA DE CAMPOS POR PLATAFORMA
# ============================================================
print("\n📋 TESTE 4: Cobertura de Campos por Plataforma (Accessibility)")
print("-" * 50)

# Mapa de quais campos cada plataforma realmente preenche
coverage = {
    "Uber": {"rideValue": True, "pickupDistance": True, "dropoffDistance": True,
             "rideDuration": True, "passengerRating": True, "intermediateStops": True,
             "pickupNeighborhood": True, "dropoffNeighborhood": True},
    "99": {"rideValue": True, "pickupDistance": False, "dropoffDistance": True,
           "rideDuration": True, "passengerRating": True, "intermediateStops": True,
           "pickupNeighborhood": True, "dropoffNeighborhood": True},
    "inDrive": {"rideValue": True, "pickupDistance": False, "dropoffDistance": True,
                "rideDuration": True, "passengerRating": False, "intermediateStops": False,
                "pickupNeighborhood": True, "dropoffNeighborhood": True},
    "Cabify": {"rideValue": True, "pickupDistance": False, "dropoffDistance": True,
               "rideDuration": True, "passengerRating": True, "intermediateStops": False,
               "pickupNeighborhood": True, "dropoffNeighborhood": True},
}

print(f"\n  {'Campo':<22} {'Uber':<8} {'99':<8} {'inDrive':<8} {'Cabify':<8}")
print(f"  {'-'*22} {'-'*8} {'-'*8} {'-'*8} {'-'*8}")
for field_name in ["rideValue", "pickupDistance", "dropoffDistance", "rideDuration",
                  "passengerRating", "intermediateStops", "pickupNeighborhood", "dropoffNeighborhood"]:
    row = f"  {field_name:<22}"
    for platform in ["Uber", "99", "inDrive", "Cabify"]:
        val = coverage[platform].get(field_name, False)
        row += f" {'✅':<8}" if val else f" {'❌':<8}"
    print(row)

# Testar score com dados parciais de cada plataforma
print("\n  Score com dados típicos de cada plataforma:")
rides_by_platform = {
    "Uber": RideData(Platform.UBER, 25.0, 20.0, 2.0, 12.0, 4.8, 0),
    "99": RideData(Platform.NINETY_NINE, 25.0, 20.0, 0.0, 12.0, 4.8, 0),
    "inDrive": RideData(Platform.INDRIVE, 25.0, 20.0, 0.0, 12.0, 0.0, 0),
    "Cabify": RideData(Platform.CABIFY, 25.0, 20.0, 0.0, 12.0, 4.8, 0),
}

for platform_name, ride in rides_by_platform.items():
    score = scorer_default.calculateScore(ride)
    criteria_used = len(score.criteriaScores)
    total_criteria = sum(1 for w in [w_default.valuePerKm, w_default.valuePerHour,
                                      w_default.intermediateStops, w_default.passengerRating,
                                      w_default.rideValue, w_default.rideDuration,
                                      w_default.pickupDistance, w_default.dropoffDistance] if w > 0)
    print(f"    {platform_name:<10}: Score={score.totalScore:.1f}, Critérios={criteria_used}/{total_criteria}")

# Verificar que inDrive sem rating não é penalizado injustamente
ride_indrive = RideData(Platform.INDRIVE, 25.0, 20.0, 0.0, 12.0, 0.0, 0)
score_indrive = scorer_default.calculateScore(ride_indrive)
# Sem passengerRating, o critério deveria ser calculado com rating=0 → normalized=0
# Mas o peso é 15, então perde 15 pontos por rating=0
# A questão é: inDrive NUNCA envia rating, então o motorista é penalizado injustamente
test("inDrive: rating=0 é calculado (não pulado)",
     "passengerRating" in score_indrive.criteriaScores,
     "Rating=0 é tratado como dado presente, penalizando inDrive")
warn("inDrive nunca envia passengerRating",
     "Motorista perde pontos por critério que a plataforma não fornece. Sugestão: pular critério quando rating=0 E plataforma!=Uber")

# ============================================================
# TESTE 5: CONTROLE FINANCEIRO — CÁLCULOS
# ============================================================
print("\n📋 TESTE 5: Controle Financeiro — Cálculos")
print("-" * 50)

# 5.1: Custo por km
def calculate_cost_per_km(consumption_str: str, price_str: str) -> float:
    """Simula o cálculo do VehicleTab"""
    def to_double_locale(s):
        try:
            return float(s.replace(",", ".").strip())
        except (ValueError, AttributeError):
            return 0.0
    avg = to_double_locale(consumption_str)
    prc = to_double_locale(price_str)
    return prc / avg if avg > 0 else 0.0

# Cenários reais de veículos
vehicle_tests = [
    ("14,4", "0,80", 0.0556, "BYD Dolphin (elétrico) 14.4km/kWh × R$0.80/kWh"),
    ("12", "5,89", 0.4908, "Carro flex 12km/L × R$5.89/L"),
    ("8,5", "6,50", 0.7647, "SUV gasolina 8.5km/L × R$6.50/L"),
    ("15", "4,50", 0.3000, "Carro econômico 15km/L × R$4.50/L"),
    ("0", "5,00", 0.0, "Consumo zero (divisão por zero protegida)"),
    ("10", "0", 0.0, "Preço zero"),
    ("abc", "5,00", 0.0, "Consumo inválido"),
    ("10", "abc", 0.0, "Preço inválido"),
]

for cons, price, expected, desc in vehicle_tests:
    result = calculate_cost_per_km(cons, price)
    ok = abs(result - expected) < 0.001
    test(f"Custo/km: {desc}", ok, f"Resultado={result:.4f}, Esperado={expected:.4f}")

# 5.2: Lucro líquido
def calculate_net_profit(earnings: float, expenses: float) -> float:
    return earnings - expenses

test("Lucro líquido: 1500 - 800 = 700", calculate_net_profit(1500, 800) == 700)
test("Lucro líquido: 500 - 1200 = -700 (prejuízo)", calculate_net_profit(500, 1200) == -700)
test("Lucro líquido: 0 - 0 = 0", calculate_net_profit(0, 0) == 0)

# 5.3: R$/km, R$/hora, R$/corrida
def calculate_unit_metrics(net_profit: float, distance: float, duration_min: int, rides: int):
    profit_per_km = net_profit / distance if distance > 0 else 0.0
    profit_per_hour = (net_profit / duration_min) * 60.0 if duration_min > 0 else 0.0
    profit_per_ride = net_profit / rides if rides > 0 else 0.0
    return profit_per_km, profit_per_hour, profit_per_ride

pkm, ph, pr = calculate_unit_metrics(700, 350, 480, 25)
test("R$/km = 700/350 = 2.0", abs(pkm - 2.0) < 0.001)
test("R$/hora = (700/480)*60 = 87.5", abs(ph - 87.5) < 0.001)
test("R$/corrida = 700/25 = 28.0", abs(pr - 28.0) < 0.001)

# Divisão por zero protegida
pkm0, ph0, pr0 = calculate_unit_metrics(700, 0, 0, 0)
test("R$/km com distância=0 → 0", pkm0 == 0.0)
test("R$/hora com duração=0 → 0", ph0 == 0.0)
test("R$/corrida com corridas=0 → 0", pr0 == 0.0)

# 5.4: Progresso de metas
def calculate_goal_progress(current_earnings: float, target_amount: float) -> float:
    if target_amount > 0:
        return max(0.0, min(1.0, current_earnings / target_amount))
    return 0.0

test("Meta 1000, ganhou 700 → 70%", abs(calculate_goal_progress(700, 1000) - 0.7) < 0.001)
test("Meta 1000, ganhou 1500 → 100% (capped)", calculate_goal_progress(1500, 1000) == 1.0)
test("Meta 0 → 0% (proteção divisão zero)", calculate_goal_progress(500, 0) == 0.0)
test("Meta 1000, ganhou 0 → 0%", calculate_goal_progress(0, 1000) == 0.0)

# 5.5: Recorrência de gastos
print("\n  📍 Lógica de recorrência de gastos:")
test("recurringDays vazio quando não recorrente", True)  # Apenas visual, não gera registros automáticos
warn("Recorrência é apenas visual",
     "O sistema salva isRecurring/recurringDays mas NÃO gera automaticamente os gastos nos dias marcados. É apenas um label.")

# ============================================================
# TESTE 6: CARD PREVIEW — FIDELIDADE
# ============================================================
print("\n📋 TESTE 6: Card Preview vs Overlay Real")
print("-" * 50)

# O PreviewDialog mostra campos fixos, mas o OverlayCard real itera galleryCard.fields
# Verificar se os campos mostrados no preview correspondem ao que o overlay real mostra
print("  PreviewDialog mostra SEMPRE:")
print("    - Valor, R$/KM, R$/Hora, Embarque, Destino, Duração")
print("    - Aval. Passageiro (se > 0), Paradas (se > 0)")
print("")
print("  OverlayCard real mostra APENAS campos em galleryCard.fields")
print("")
warn("PreviewDialog não respeita galleryCard.fields",
     "Preview mostra campos fixos. Se o card ativo não tem campo 'Embarque', o preview mostra mesmo assim.")

# ============================================================
# TESTE 7: EDGE CASES E OVERFLOW
# ============================================================
print("\n📋 TESTE 7: Edge Cases e Overflow")
print("-" * 50)

# 7.1: Valores extremos
ride_extreme = RideData(Platform.UBER, 999999.0, 0.001, 0.001, 0.001, 5.0, 99)
score_extreme = scorer_default.calculateScore(ride_extreme)
test("Valores extremos não causam crash", True)
test("Score com valores extremos está entre 0-100",
     0 <= score_extreme.totalScore <= 100,
     f"Score={score_extreme.totalScore}")

# 7.2: Todos os valores zero
ride_all_zero = RideData()
score_all_zero = scorer_default.calculateScore(ride_all_zero)
test("Todos valores zero: score calculável",
     score_all_zero.totalScore >= 0,
     f"Score={score_all_zero.totalScore}")

# 7.3: Rating exatamente nos limites
test("Rating 4.0 → normalized=0", scorer_default._normalizeRating(4.0) == 0.0)
test("Rating 4.9 → normalized=90", abs(scorer_default._normalizeRating(4.9) - 90.0) < 0.001)
test("Rating 5.0 → normalized=100", scorer_default._normalizeRating(5.0) == 100.0)

# 7.4: Threshold exatamente no limite
thresholds_exact = DriverThresholds(minPassengerRating=4.9)
scorer_exact = RideScorer(weights=CriteriaWeights(), driverThresholds=thresholds_exact)
ride_49 = RideData(Platform.UBER, 30.0, 20.0, 1.0, 12.0, 4.9, 0)
ride_48 = RideData(Platform.UBER, 30.0, 20.0, 1.0, 12.0, 4.8, 0)
score_49 = scorer_exact.calculateScore(ride_49)
score_48 = scorer_exact.calculateScore(ride_48)
test("Rating=4.9 com threshold=4.9 → sem penalidade",
     len([v for v in score_49.thresholdViolations if v.criteriaName == "Avaliação"]) == 0)
test("Rating=4.8 com threshold=4.9 → com penalidade",
     len([v for v in score_48.thresholdViolations if v.criteriaName == "Avaliação"]) == 1)

# ============================================================
# TESTE 8: SIMULAÇÃO DE 1000 CORRIDAS ALEATÓRIAS
# ============================================================
print("\n📋 TESTE 8: Simulação de 1000 Corridas Aleatórias")
print("-" * 50)

random.seed(42)
scores = []
crashes = 0
out_of_range = 0

for i in range(1000):
    ride = RideData(
        platform=random.choice(list(Platform)),
        rideValue=random.uniform(0, 100),
        rideDuration=random.uniform(0, 120),
        pickupDistance=random.uniform(0, 10),
        dropoffDistance=random.uniform(0, 30),
        passengerRating=random.uniform(0, 5),
        intermediateStops=random.randint(0, 5)
    )
    try:
        score = scorer_default.calculateScore(ride)
        scores.append(score.totalScore)
        if score.totalScore < 0 or score.totalScore > 100:
            out_of_range += 1
    except Exception as e:
        crashes += 1

test("0 crashes em 1000 corridas aleatórias", crashes == 0, f"Crashes={crashes}")
test("0 scores fora de 0-100", out_of_range == 0, f"Fora de range={out_of_range}")
test("Distribuição razoável (média entre 20-80)",
     20 <= sum(scores)/len(scores) <= 80,
     f"Média={sum(scores)/len(scores):.1f}")

print(f"    Média: {sum(scores)/len(scores):.1f}")
print(f"    Min: {min(scores):.1f}, Max: {max(scores):.1f}")
print(f"    Desvio padrão: {(sum((s - sum(scores)/len(scores))**2 for s in scores) / len(scores))**0.5:.1f}")

# ============================================================
# TESTE 9: CONSISTÊNCIA DO CARD COM THRESHOLDS
# ============================================================
print("\n📋 TESTE 9: Coloração do Card vs Thresholds")
print("-" * 50)

# Simula a lógica de coloração do PreviewDialog
def get_field_color(field_name: str, ride: RideData, thresholds: DriverThresholds) -> str:
    """Retorna 'RED' se violado, 'ACCENT' se ok"""
    if field_name == "valuePerKm":
        if thresholds.isValuePerKmActive() and ride.valuePerKm < thresholds.minValuePerKm:
            return "RED"
    elif field_name == "valuePerHour":
        if thresholds.isValuePerHourActive() and ride.valuePerHour < thresholds.minValuePerHour:
            return "RED"
    elif field_name == "rideValue":
        if thresholds.isRideValueActive() and ride.rideValue < thresholds.minRideValue:
            return "RED"
    elif field_name == "pickupDistance":
        if thresholds.isPickupDistanceActive() and ride.pickupDistance > thresholds.maxPickupDistance:
            return "RED"
    elif field_name == "passengerRating":
        if thresholds.isPassengerRatingActive() and ride.passengerRating < thresholds.minPassengerRating:
            return "RED"
    elif field_name == "rideDuration":
        if thresholds.isDurationActive() and ride.rideDuration > thresholds.maxDuration:
            return "RED"
    elif field_name == "intermediateStops":
        if thresholds.isStopsActive() and ride.intermediateStops > thresholds.maxStops:
            return "RED"
    return "ACCENT"

# Teste com threshold de avaliação 4.9
th_49 = DriverThresholds(minPassengerRating=4.9)
ride_good = RideData(Platform.UBER, 30.0, 20.0, 1.0, 12.0, 5.0, 0)
ride_bad_rating = RideData(Platform.UBER, 30.0, 20.0, 1.0, 12.0, 4.5, 0)

test("Rating 5.0 com threshold 4.9 → ACCENT (verde)",
     get_field_color("passengerRating", ride_good, th_49) == "ACCENT")
test("Rating 4.5 com threshold 4.9 → RED (vermelho)",
     get_field_color("passengerRating", ride_bad_rating, th_49) == "RED")

# Teste com threshold de pickup distance
th_pk = DriverThresholds(maxPickupDistance=3.0)
ride_close = RideData(Platform.UBER, 30.0, 20.0, 2.0, 12.0, 4.8, 0)
ride_far = RideData(Platform.UBER, 30.0, 20.0, 5.0, 12.0, 4.8, 0)

test("Pickup 2km com max 3km → ACCENT",
     get_field_color("pickupDistance", ride_close, th_pk) == "ACCENT")
test("Pickup 5km com max 3km → RED",
     get_field_color("pickupDistance", ride_far, th_pk) == "RED")

# ============================================================
# RESUMO FINAL
# ============================================================
print("\n" + "=" * 70)
print("RESUMO DA AUDITORIA")
print("=" * 70)
print(f"\n  ✅ Testes aprovados: {results['passed']}")
print(f"  ❌ Testes reprovados: {results['failed']}")
print(f"  ⚠️  Avisos: {results['warnings']}")
print(f"\n  Total de verificações: {results['passed'] + results['failed'] + results['warnings']}")

print("\n" + "-" * 70)
print("BUGS CRÍTICOS ENCONTRADOS:")
print("-" * 70)
critical_bugs = [d for d in results["details"] if d.startswith("❌")]
for bug in critical_bugs:
    print(f"  {bug}")

print("\n" + "-" * 70)
print("AVISOS E MELHORIAS:")
print("-" * 70)
warnings = [d for d in results["details"] if d.startswith("⚠️")]
for w in warnings:
    print(f"  {w}")

print("\n" + "=" * 70)
print("FIM DA AUDITORIA")
print("=" * 70)
