package com.ngbautoroad.data.model

/**
 * Módulo de Controle Financeiro para Motoristas de Aplicativo
 *
 * Categorias de gastos:
 * - Combustível/Energia (gasolina, etanol, GNV, eletricidade)
 * - Veículo (parcela/aluguel, seguro, IPVA, licenciamento, multas)
 * - Manutenção (óleo, filtros, pneus, freios, revisão, funilaria)
 * - Operacional (alimentação, água, estacionamento, pedágio, lavagem)
 * - Outros (celular/plano, suporte celular, câmera, acessórios)
 *
 * Tipos de veículo: Combustão, Híbrido, 100% Elétrico
 */

// === TIPO DE VEÍCULO ===

enum class VehicleType(val displayName: String, val costPerKmDefault: Double) {
    COMBUSTION("Combustão (Gasolina/Etanol/Flex)", 0.40),
    HYBRID("Híbrido", 0.20),
    ELECTRIC("100% Elétrico", 0.08)
}

enum class FuelType(val displayName: String) {
    GASOLINE("Gasolina"),
    ETHANOL("Etanol"),
    FLEX("Flex"),
    DIESEL("Diesel"),
    GNV("GNV"),
    ELECTRIC("Eletricidade"),
    HYBRID_GAS("Híbrido - Gasolina"),
    HYBRID_ETHANOL("Híbrido - Etanol")
}

// === CATEGORIAS DE GASTOS ===

enum class ExpenseCategory(val displayName: String, val icon: String) {
    // Combustível/Energia
    FUEL("Combustível/Energia", "⛽"),

    // Veículo
    CAR_PAYMENT("Parcela/Aluguel do Carro", "🚗"),
    INSURANCE("Seguro", "🛡️"),
    IPVA("IPVA", "📋"),
    LICENSING("Licenciamento", "📄"),
    FINES("Multas", "⚠️"),

    // Manutenção
    OIL_CHANGE("Troca de Óleo", "🛢️"),
    TIRES("Pneus", "🔘"),
    BRAKES("Freios", "🔧"),
    REVISION("Revisão", "🔩"),
    BODYWORK("Funilaria/Pintura", "🎨"),
    OTHER_MAINTENANCE("Outra Manutenção", "🔧"),

    // Operacional
    FOOD("Alimentação", "🍔"),
    PARKING("Estacionamento", "🅿️"),
    TOLL("Pedágio", "🛣️"),
    CAR_WASH("Lavagem", "💧"),
    WATER("Água/Bebidas", "🥤"),

    // Outros
    PHONE_PLAN("Plano de Celular", "📱"),
    PHONE_HOLDER("Suporte Celular", "📲"),
    CAMERA("Câmera/Dashcam", "📷"),
    ACCESSORIES("Acessórios", "🎒"),
    OTHER("Outros", "📦")
}

enum class ExpenseCategoryGroup(val displayName: String, val categories: List<ExpenseCategory>) {
    FUEL_ENERGY("Combustível/Energia", listOf(ExpenseCategory.FUEL)),
    VEHICLE("Veículo", listOf(
        ExpenseCategory.CAR_PAYMENT, ExpenseCategory.INSURANCE,
        ExpenseCategory.IPVA, ExpenseCategory.LICENSING, ExpenseCategory.FINES
    )),
    MAINTENANCE("Manutenção", listOf(
        ExpenseCategory.OIL_CHANGE, ExpenseCategory.TIRES, ExpenseCategory.BRAKES,
        ExpenseCategory.REVISION, ExpenseCategory.BODYWORK, ExpenseCategory.OTHER_MAINTENANCE
    )),
    OPERATIONAL("Operacional", listOf(
        ExpenseCategory.FOOD, ExpenseCategory.PARKING, ExpenseCategory.TOLL,
        ExpenseCategory.CAR_WASH, ExpenseCategory.WATER
    )),
    OTHER("Outros", listOf(
        ExpenseCategory.PHONE_PLAN, ExpenseCategory.PHONE_HOLDER,
        ExpenseCategory.CAMERA, ExpenseCategory.ACCESSORIES, ExpenseCategory.OTHER
    ))
}

// === REGISTRO DE GASTO ===

data class Expense(
    val id: Long = 0,
    val category: ExpenseCategory = ExpenseCategory.FUEL,
    val amount: Double = 0.0,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,          // Gasto fixo mensal (parcela, seguro, etc)
    val recurringDay: Int = 1,                 // Dia do mês para gastos recorrentes
    val liters: Double? = null,                // Para combustível
    val pricePerLiter: Double? = null,         // Para combustível
    val odometer: Int? = null,                 // Km no odômetro
    val fuelType: FuelType? = null             // Tipo de combustível
)

// === REGISTRO DE GANHO ===

data class Earning(
    val id: Long = 0,
    val platform: Platform = Platform.UBER,
    val amount: Double = 0.0,
    val tips: Double = 0.0,
    val bonus: Double = 0.0,
    val distance: Double = 0.0,               // km rodados
    val duration: Int = 0,                     // minutos
    val ridesCount: Int = 1,                   // número de corridas
    val date: Long = System.currentTimeMillis(),
    val description: String = ""
) {
    val totalAmount: Double get() = amount + tips + bonus
    val valuePerKm: Double get() = if (distance > 0) totalAmount / distance else 0.0
    val valuePerHour: Double get() = if (duration > 0) (totalAmount / duration) * 60.0 else 0.0
}

// === CONFIGURAÇÃO DO VEÍCULO ===

data class VehicleConfig(
    val type: VehicleType = VehicleType.COMBUSTION,
    val fuelType: FuelType = FuelType.FLEX,
    val averageConsumption: Double = 10.0,     // km/l ou km/kWh
    val fuelPrice: Double = 5.50,              // R$/litro ou R$/kWh
    val monthlyFixedCosts: Double = 0.0,       // Total de custos fixos mensais
    val vehicleName: String = "",              // Ex: "Onix 2023"
    val licensePlate: String = ""              // Placa
) {
    val costPerKm: Double
        get() = if (averageConsumption > 0) fuelPrice / averageConsumption else type.costPerKmDefault
}

// === RESUMO FINANCEIRO ===

data class FinancialSummary(
    val totalEarnings: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val totalKm: Double = 0.0,
    val totalHours: Double = 0.0,
    val totalRides: Int = 0,
    val profitPerKm: Double = 0.0,
    val profitPerHour: Double = 0.0,
    val profitPerRide: Double = 0.0,
    val expensesByCategory: Map<ExpenseCategory, Double> = emptyMap(),
    val earningsByPlatform: Map<Platform, Double> = emptyMap(),
    val period: FinancePeriod = FinancePeriod.TODAY
)

enum class FinancePeriod(val displayName: String) {
    TODAY("Hoje"),
    WEEK("Semana"),
    MONTH("Mês"),
    YEAR("Ano"),
    ALL("Tudo")
}

// === METAS ===

data class FinancialGoal(
    val dailyTarget: Double = 0.0,            // Meta diária (R$)
    val weeklyTarget: Double = 0.0,           // Meta semanal (R$)
    val monthlyTarget: Double = 0.0           // Meta mensal (R$)
)

// === LEMBRETES DE MANUTENÇÃO ===

data class MaintenanceReminder(
    val id: Long = 0,
    val title: String = "",
    val category: ExpenseCategory = ExpenseCategory.OIL_CHANGE,
    val nextDate: Long = 0,                   // Próxima data
    val nextOdometer: Int = 0,                // Próximo km
    val intervalDays: Int = 0,                // Intervalo em dias
    val intervalKm: Int = 0,                  // Intervalo em km
    val isActive: Boolean = true
)
