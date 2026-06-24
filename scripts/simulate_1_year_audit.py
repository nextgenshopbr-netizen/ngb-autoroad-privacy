# -*- coding: utf-8 -*-
"""
simulate_1_year_audit.py
Simula 1 ano de trabalho (250 dias, 15 corridas/dia) e testa edge cases matematicos.
Mimetiza a logica Kotlin do NGB AutoRoad: FinanceDRE, RideScorer, ShiftManager.
"""

import random
import sys
import math

class SimLogger:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.fatal = []

    def error(self, msg):
        self.errors.append(msg)
    
    def warn(self, msg):
        self.warnings.append(msg)

    def report(self):
        print("\n=== RELATORIO DA SIMULACAO ===")
        print(f"Erros encontrados: {len(self.errors)}")
        print(f"Avisos: {len(self.warnings)}")
        print("\n[ERROS CRITICOS]")
        for idx, e in enumerate(self.errors[:20]):
            print(f"  {idx+1}. {e}")
        if len(self.errors) > 20:
            print(f"  ... e mais {len(self.errors) - 20} erros")

logger = SimLogger()

# ---------------------------------------------------------
# SIMULACAO: FinanceDRE.kt
# ---------------------------------------------------------
def sim_finance_dre(receitaBruta, totalKmTracked, totalDurationMin, correctionFactor, costPerKm, vehicle):
    try:
        totalHours = totalDurationMin / 60.0
        totalKm = totalKmTracked * correctionFactor

        combustivelCost = totalKm * costPerKm
        
        # Desgaste
        desgasteTotal = 0.0
        if vehicle.get('tireLifeKm', 0) > 0 and vehicle.get('tireCost', 0) > 0:
            desgasteTotal += vehicle['tireCost'] / vehicle['tireLifeKm']
        if vehicle.get('brakepadLifeKm', 0) > 0 and vehicle.get('brakepadCost', 0) > 0:
            desgasteTotal += vehicle['brakepadCost'] / vehicle['brakepadLifeKm']
        if vehicle.get('oilChangeKm', 0) > 0 and vehicle.get('oilChangeCost', 0) > 0:
            desgasteTotal += vehicle['oilChangeCost'] / vehicle['oilChangeKm']
        
        if desgasteTotal <= 0:
            desgasteTotal = 0.05
        
        desgasteCost = totalKm * desgasteTotal
        custosVariaveis = combustivelCost + desgasteCost
        
        margemContribuicao = receitaBruta - custosVariaveis
        
        # Se receitaBruta for 0 ou negativa, margemContribuicaoPct pode causar problemas se nao validado
        if receitaBruta > 0:
            margemContribuicaoPct = (margemContribuicao / receitaBruta) * 100.0
        else:
            margemContribuicaoPct = 0.0
            logger.warn("FinanceDRE: receitaBruta <= 0 gerou margem 0.0, potencial distorcao.")
        
        # Checando divisoes por zero
        if totalKm == 0:
            custoVariavelPorKm = 0.0
        else:
            custoVariavelPorKm = custosVariaveis / totalKm
            
        if totalHours == 0:
            receitaPorHora = 0.0
        else:
            receitaPorHora = receitaBruta / totalHours
            
        return {
            'margem': margemContribuicao,
            'custoVariavelKm': custoVariavelPorKm,
            'receitaHora': receitaPorHora
        }
    except Exception as e:
        logger.error(f"FinanceDRE Exception: {str(e)}")

# ---------------------------------------------------------
# SIMULACAO: RideScorer.kt
# ---------------------------------------------------------
def sim_ride_score(rideValue, rideDuration, dropoffDistance, valuePerKmWeight, durationWeight):
    try:
        # Se dropoffDistance for 0, pula (código original faz if ride.dropoffDistance > 0)
        valuePerKm = (rideValue / dropoffDistance) if dropoffDistance > 0 else 0
        
        # Se valuePerKm ficar infinito (ocorreu bug?)
        if math.isinf(valuePerKm):
            logger.error("RideScorer: math.isinf gerado para valuePerKm. Fallback ausente no Kotlin para float division.")
            
        # Normalização Value/KM (0.50 a 2.50)
        range_vpk = 2.50 - 0.50
        norm_vpk = ((valuePerKm - 0.50) / range_vpk) * 100
        norm_vpk = max(0.0, min(100.0, norm_vpk))
        
        # Normalização Duracao (10 a 60 min) -> inverso
        range_dur = 60.0 - 10.0
        norm_dur = ((60.0 - rideDuration) / range_dur) * 100
        norm_dur = max(0.0, min(100.0, norm_dur))
        
        score = (norm_vpk * valuePerKmWeight / 100.0) + (norm_dur * durationWeight / 100.0)
        return score
    except ZeroDivisionError:
        logger.error("RideScorer: ZeroDivisionError em calculo de score (Deveria ter sido protegido pelo if)")
    except Exception as e:
        logger.error(f"RideScorer Exception: {str(e)}")

# ---------------------------------------------------------
# EXECUCAO DE 1 ANO (250 DIAS * 16 CORRIDAS = 4000)
# ---------------------------------------------------------
def run_simulation():
    print("Iniciando simulacao de 4000 corridas (1 ano de dados reais)...")
    
    vehicle = {
        'tireLifeKm': 50000, 'tireCost': 1600,
        'brakepadLifeKm': 20000, 'brakepadCost': 250,
        'oilChangeKm': 10000, 'oilChangeCost': 200
    }
    
    for i in range(4000):
        # Gerando dados normais
        rideValue = max(5.0, random.gauss(20.0, 10.0))
        rideDuration = max(1.0, random.gauss(15.0, 8.0))
        dropoffDistance = max(0.5, random.gauss(10.0, 5.0))
        
        # 1% de anomalias (GPS falho, corrida cancelada, tempo zerado)
        if random.random() < 0.01:
            anomaly_type = random.choice(['zero_distance', 'zero_duration', 'negative_value', 'huge_distance'])
            if anomaly_type == 'zero_distance':
                dropoffDistance = 0.0
            elif anomaly_type == 'zero_duration':
                rideDuration = 0.0
            elif anomaly_type == 'negative_value':
                rideValue = -10.0
                logger.warn("Corrida com valor negativo (Estorno?) injetada.")
            elif anomaly_type == 'huge_distance':
                dropoffDistance = 15000.0 # Erro de GPS, salto
        
        sim_ride_score(rideValue, rideDuration, dropoffDistance, 50, 50)
        
        # Simula DRE diario (acumulando)
        if i % 16 == 0:
            sim_finance_dre(
                receitaBruta=sum([max(0, random.gauss(20.0, 10.0)) for _ in range(16)]),
                totalKmTracked=sum([max(0, random.gauss(10.0, 5.0)) for _ in range(16)]),
                totalDurationMin=sum([max(0, random.gauss(15.0, 8.0)) for _ in range(16)]),
                correctionFactor=1.3, # Odometro descalibrado
                costPerKm=0.30,
                vehicle=vehicle
            )
            
    # Simulando Average em Kotlin para anomalias
    empty_list = []
    try:
        avg = sum(empty_list) / len(empty_list) if len(empty_list) > 0 else float('nan')
        # Em Kotlin, filter{it > 0}.average() -> retorna NaN se vazio
        # Se usarmos NaN numa multiplicacao:
        result = avg * 150.0
        if math.isnan(result):
            logger.error("FinanceDRE: Propagacao de NaN identificada caso os fatores de calibracao sejam vazios/negativos.")
    except Exception as e:
        logger.error(f"List Average Exception: {e}")
        
    logger.report()
    print("Simulacao concluida com sucesso.")

if __name__ == "__main__":
    run_simulation()
