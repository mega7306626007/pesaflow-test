package com.pesaflow.app.ui.budgets

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.BudgetType
import kotlin.math.ceil
import kotlin.math.round

// Six researched setups — rent is NOT universal. Living with parents means a
// Home upkeep envelope instead of Rent; cooking ability moves Food more than
// any preset; commute distance (2026 fares: short hop ~40–65, long routes
// ~190 peak) decides whether Transport is survival or pocket change.
enum class Persona(val label: String, val blurb: String) {
    PARENTS_FAR("Home · far commute", "Parents' roof, long matatu daily"),
    PARENTS_NEAR("Home · short hop", "Parents' roof, walk or short hop"),
    RENT_WALK("Rented · walk", "Own place, campus on foot"),
    RENT_COMMUTE("Rented · commute", "Own place plus daily fares"),
    HOSTEL_NOCOOK("Hostel · buy food", "Hostel bed, every meal bought"),
    HOSTEL_COOK("Hostel · cook", "Hostel bed, groceries + cooker")
}

enum class BudgetRule(val label: String) {
    CAMPUS("Campus Survival"),
    SPLIT("50/30/20")
}

// Lifestyle presets: same engine, different appetites. Early commuters who
// skip lunch want transport-first; foodies protect Food; savers push Savings.
enum class LifestylePreset(val label: String) {
    BALANCED("Balanced"),
    COMMUTER_LITE("Commuter lite"),
    FOODIE("Foodie"),
    SAVER("Saver")
}

data class BudgetSuggestion(val category: String, val amount: Int, val percent: Int, val reason: String, val shielded: Boolean)

data class SmartBudgetResult(
    val suggestions: List<BudgetSuggestion>,
    val dropped: List<String>,
    val tightMode: Boolean,
    val summary: String,
    val periodName: String
)

private data class TierDef(
    val category: String,
    val weight: Double,
    val tier: Int, // 1 survival, 2 study/mobility-essential, 3 mobility, 4 stability, 5 lifestyle
    val floorMonthly: Int = 0,
    val mobilityEssential: Boolean = false
)

// Persona tiers. Floors grounded in 2026 campus reality: long commutes run
// ~6–9k/mo return, short hops ~1.5–3k; bought meals ~9–15k/mo, groceries
// ~5–8k, home-fed ~3.5–4.5k; campus beds ~5–24k/yr, rentals far above.
private fun personaTiers(persona: Persona): List<TierDef> = when (persona) {
    Persona.PARENTS_FAR -> listOf(
        TierDef("Home", 4.0, 1, floorMonthly = 1000),
        TierDef("Transport", 28.0, 2, floorMonthly = 6500, mobilityEssential = true),
        // Home-fed: eats at home (~50/day plates), so Food is tier 2 and
        // unshielded — fares eat first, never the other way round.
        TierDef("Food", 10.0, 2),
        TierDef("School", 8.0, 2, floorMonthly = 300),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 12.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 4.0, 5),
        TierDef("Shopping", 4.0, 5),
        TierDef("Personal Care", 3.0, 5),
        TierDef("Other", 0.0, 5)
    )
    Persona.PARENTS_NEAR -> listOf(
        TierDef("Home", 4.0, 1, floorMonthly = 1000),
        TierDef("Transport", 8.0, 3, floorMonthly = 1500),
        // Home-fed like FAR: small plate money, tier 2, unshielded.
        TierDef("Food", 12.0, 2),
        TierDef("School", 10.0, 2, floorMonthly = 300),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 16.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 6.0, 5),
        TierDef("Shopping", 6.0, 5),
        TierDef("Personal Care", 4.0, 5),
        TierDef("Other", 0.0, 5)
    )
    Persona.RENT_WALK -> listOf(
        TierDef("Rent", 30.0, 1, floorMonthly = 6000),
        TierDef("Transport", 2.0, 5),
        TierDef("Food", 24.0, 1, floorMonthly = 5000),
        TierDef("School", 8.0, 2, floorMonthly = 300),
        TierDef("Water", 2.0, 1, floorMonthly = 300),
        TierDef("Electricity", 2.0, 1, floorMonthly = 400),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 8.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 3.0, 5),
        TierDef("Shopping", 3.0, 5),
        TierDef("Personal Care", 3.0, 5),
        TierDef("Other", 0.0, 5)
    )
    Persona.RENT_COMMUTE -> listOf(
        TierDef("Rent", 26.0, 1, floorMonthly = 6000),
        TierDef("Transport", 20.0, 2, floorMonthly = 4500, mobilityEssential = true),
        TierDef("Food", 20.0, 1, floorMonthly = 4500),
        TierDef("School", 8.0, 2, floorMonthly = 300),
        TierDef("Water", 2.0, 1, floorMonthly = 300),
        TierDef("Electricity", 2.0, 1, floorMonthly = 400),
        TierDef("Data", 3.0, 2),
        TierDef("Airtime", 2.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 5.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 2.0, 5),
        TierDef("Shopping", 2.0, 5),
        TierDef("Personal Care", 2.0, 5),
        TierDef("Other", 0.0, 5)
    )
    Persona.HOSTEL_NOCOOK -> listOf(
        TierDef("Rent", 16.0, 1, floorMonthly = 2500),
        TierDef("Food", 34.0, 1, floorMonthly = 9000),
        TierDef("Transport", 3.0, 5),
        TierDef("School", 10.0, 2, floorMonthly = 300),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 8.0, 4),
        TierDef("Debt", 3.0, 4),
        TierDef("Entertainment", 4.0, 5),
        TierDef("Shopping", 4.0, 5),
        TierDef("Personal Care", 3.0, 5),
        TierDef("Other", 0.0, 5)
    )
    Persona.HOSTEL_COOK -> listOf(
        TierDef("Rent", 18.0, 1, floorMonthly = 2500),
        TierDef("Food", 24.0, 1, floorMonthly = 5500),
        TierDef("Transport", 4.0, 3, floorMonthly = 500),
        TierDef("School", 10.0, 2, floorMonthly = 300),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 12.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 4.0, 5),
        TierDef("Shopping", 4.0, 5),
        TierDef("Personal Care", 3.0, 5),
        TierDef("Other", 0.0, 5)
    )
}.filter { it.weight > 0 }

private fun splitTiers(): List<TierDef> = listOf(
    TierDef("Rent", 20.0, 1, floorMonthly = 2000),
    TierDef("Food", 15.0, 1, floorMonthly = 3000),
    TierDef("Transport", 8.0, 3),
    TierDef("Bills", 7.0, 4),
    TierDef("Savings", 20.0, 4),
    TierDef("Entertainment", 12.0, 5),
    TierDef("Shopping", 10.0, 5),
    TierDef("Personal Care", 8.0, 5)
).filter { it.weight > 0 }

fun periodScale(type: BudgetType): Double = when (type) {
    BudgetType.DAILY -> 1.0 / 30
    BudgetType.WEEKLY -> 7.0 / 30
    BudgetType.MONTHLY -> 1.0
    BudgetType.SEMESTER -> 4.0
    BudgetType.ANNUAL -> 12.0
}

fun periodNameOf(type: BudgetType): String = when (type) {
    BudgetType.DAILY -> "daily"
    BudgetType.WEEKLY -> "weekly"
    BudgetType.MONTHLY -> "monthly"
    BudgetType.SEMESTER -> "semester"
    else -> "annual"
}

fun monthlyBillReserve(bill: Bill, now: Long): Int {
    if (bill.paidBy != "ME") return 0
    val remaining = bill.amountRemaining.takeIf { it > 0 } ?: bill.amount
    if (remaining <= 0) return 0
    return when (bill.frequency.uppercase()) {
        "MONTHLY" -> ceil(remaining).toInt()
        "WEEKLY" -> ceil(remaining * 52.0 / 12.0).toInt()
        "DAILY" -> ceil(remaining * 30.0).toInt()
        else -> {
            val daysUntilDue = ceil((bill.dueDate - now).coerceAtLeast(0L) / 86_400_000.0).toInt()
            if (daysUntilDue <= 30) ceil(remaining).toInt()
            else ceil(remaining * 30.0 / daysUntilDue).toInt().coerceAtMost(ceil(remaining).toInt())
        }
    }
}

/**
 * Smart budget engine — survival first when money is tight.
 * - Persona tiers encode who pays rent, who commutes, who cooks.
 * - Funds tier 1 (Food/Rent/Home/Water/Power/Health) floors before anything else.
 * - Essential commutes survive tight mode; optional fares shrink to token.
 * - Savings/lifestyle drop to zero in tight mode instead of starving Food.
 * - Never invents spending history; bill floors + averages are passed in by callers.
 */
fun smartBudget(
    monthlyBase: Double,
    rule: BudgetRule,
    period: BudgetType,
    persona: Persona,
    openBillByCategory: Map<String, Int> = emptyMap(),
    avg90ByCategory: Map<String, Int> = emptyMap(),
    style: LifestylePreset = LifestylePreset.BALANCED,
    // Declared envelopes (matatu preset, onboarding, manual): a stated number
    // is a promise — the plan never suggests below it on monthly periods.
    declaredByCategory: Map<String, Int> = emptyMap()
): SmartBudgetResult {
    val scale = periodScale(period)
    val periodName = periodNameOf(period)
    val step = if (period == BudgetType.DAILY) 10.0 else 50.0
    val tiers = if (rule == BudgetRule.CAMPUS) personaTiers(persona)
    else splitTiers()
    // Lifestyle preset reshapes weights before anything else runs.
    val styledTiers = tiers.map { t ->
        val w = when (style) {
            LifestylePreset.COMMUTER_LITE -> when (t.category) {
                "Food" -> 20.0
                "Transport" -> 22.0
                "Entertainment" -> 1.0
                "Shopping" -> 1.0
                else -> t.weight
            }
            LifestylePreset.FOODIE -> when (t.category) {
                "Food" -> 36.0
                "Entertainment" -> 5.0
                else -> t.weight
            }
            LifestylePreset.SAVER -> when (t.category) {
                "Savings" -> 18.0
                "Entertainment" -> 1.0
                "Shopping" -> 1.0
                "Personal Care" -> 1.0
                else -> t.weight
            }
            else -> t.weight
        }
        t.copy(weight = w)
    }

    // Tight mode: monthly base below survival floors → protect tier 1 first.
    // Essential commutes count as survival: fares are funded like food.
    val floorTotal = styledTiers.filter { it.tier == 1 || it.mobilityEssential }.sumOf { it.floorMonthly }
    val tightMode = monthlyBase < maxOf(8000.0, floorTotal * 1.5)

    val totalWeight = styledTiers.sumOf { it.weight }.coerceAtLeast(1.0)
    val ordered = styledTiers.sortedWith(compareBy({ it.tier }, { -it.weight }))

    // Phase 1: floors for survival tier AND essential commutes, scaled.
    val floorScaled = ordered.associate { t ->
        t.category to if (t.tier == 1 || t.mobilityEssential) round(t.floorMonthly * scale / step) * step else 0.0
    }
    var floorSum = floorScaled.values.sum()
    var remaining = (monthlyBase * scale - floorSum).coerceAtLeast(0.0)

    // Phase 2: share remaining by weight, lifestyle last (zeroed in tight mode).
    // Then blend with the user's own 90-day average per category (±30% clamp):
    // history personalises the weights instead of only decorating the reason.
    val alloc = mutableMapOf<String, Double>()
    ordered.forEach { t ->
        var share = if (totalWeight > 0) remaining * (t.weight / totalWeight) else 0.0
        if (tightMode && (t.tier == 5 || (t.tier == 4 && t.category == "Savings"))) share = 0.0
        if (tightMode && t.category == "Transport" && !t.mobilityEssential) {
            share = minOf(share, 500 * scale) // token fares only
        }
        val avgHist = avg90ByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value?.toDouble() ?: 0.0
        if (avgHist > 0 && share > 0) {
            share = (0.5 * share + 0.5 * avgHist).coerceIn(avgHist * 0.7, avgHist * 1.3)
        }
        alloc[t.category] = (floorScaled[t.category] ?: 0.0) + share
    }

    // Phase 3: neat rounding + bill/declared floors (monthly only) + drop dust.
    val suggestions = mutableListOf<BudgetSuggestion>()
    val dropped = mutableListOf<String>()
    ordered.forEach { t ->
        val raw = alloc[t.category] ?: 0.0
        var neat = (round(raw / step) * step).toInt()
        val billFloor = if (period == BudgetType.MONTHLY) {
            openBillByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value ?: 0
        } else 0
        if (neat < billFloor) neat = billFloor
        val declaredFloor = if (period == BudgetType.MONTHLY) {
            declaredByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value ?: 0
        } else 0
        if (neat < declaredFloor) neat = declaredFloor
        val avg = avg90ByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value ?: 0
        if (neat <= 0 && billFloor <= 0 && declaredFloor <= 0) {
            dropped.add(t.category)
        } else {
            val pct = if (monthlyBase > 0) ((neat / (monthlyBase * scale)) * 100).toInt() else 0
            val homeFed = persona == Persona.PARENTS_FAR || persona == Persona.PARENTS_NEAR
            val reason = when {
                billFloor > 0 && neat <= billFloor -> "Covers your open ${t.category.lowercase()} bill"
                declaredFloor > 0 && neat <= declaredFloor -> "Kept at your set ${t.category.lowercase()} budget"
                t.category == "Food" && homeFed -> "Home-fed — small plate money, fares eat first"
                t.category == "Food" && persona == Persona.HOSTEL_NOCOOK -> "Every meal bought — protect it fully"
                t.category == "Food" -> if (tightMode) "Protected first — eating comes before everything" else "Survival tier — funded first"
                t.category == "Rent" -> "Roof over your head — non-negotiable"
                t.category == "Home" -> "Chip in at home — keeps the roof happy"
                t.category == "Transport" && t.mobilityEssential -> "Non-negotiable commute — fares first"
                t.category == "Transport" -> "Small fare buffer — remove it if you truly walk everywhere"
                t.category == "Savings" && tightMode -> "Paused while money is tight — resume when base grows"
                t.tier == 5 && tightMode -> "Cut in tight mode — add back when base grows"
                avg > neat -> "Under your 3-month avg (KSh $avg) — stretch goal"
                else -> "Tier ${t.tier} · ${t.weight.toInt()}% weight"
            }
            suggestions.add(BudgetSuggestion(t.category, neat, pct, reason, t.tier == 1))
        }
    }

    val total = suggestions.sumOf { it.amount }
    val summary = if (tightMode) {
        "Tight mode: KSh ${monthlyBase.toInt()}/mo funds survival first for ${persona.label}. " +
            "Savings + lifestyle paused — KSh ${total.toInt()} $periodName planned."
    } else {
        "KSh ${monthlyBase.toInt()}/mo → KSh ${total.toInt()} $periodName across ${suggestions.size} envelopes (${persona.label})."
    }
    return SmartBudgetResult(suggestions, dropped, tightMode, summary, periodName)
}

fun parsePersona(answers: String): Persona {
    // Explicit confirmation wins (onboarding review card, settings switcher):
    // a stated setup beats any derivation, and survives answer edits.
    answers.split("|").firstOrNull { it.startsWith("persona=") }?.substringAfter("=")?.uppercase()?.let { named ->
        Persona.values().firstOrNull { it.name == named }?.let { return it }
    }
    val home = answers.split("|").firstOrNull { it.startsWith("home=") }?.substringAfter("=")?.uppercase()
    val commute = answers.split("|").firstOrNull { it.startsWith("commute=") }?.substringAfter("=")?.uppercase()
    val cooking = answers.split("|").firstOrNull { it.startsWith("cooking=") }?.substringAfter("=")?.uppercase()
    if (home != null) {
        val far = commute == "FAR"
        val cooks = cooking != "NO"
        return when (home) {
            "PARENTS" -> if (far) Persona.PARENTS_FAR else Persona.PARENTS_NEAR
            "RENTAL" -> if (far) Persona.RENT_COMMUTE else Persona.RENT_WALK
            else -> if (cooks) Persona.HOSTEL_COOK else Persona.HOSTEL_NOCOOK
        }
    }
    // Legacy answers from the old Hostel/Commuting binary.
    if (answers.contains("living=COMMUTER", ignoreCase = true)) return Persona.RENT_COMMUTE
    return Persona.HOSTEL_COOK
}


/** Monthly survival cost for a setup: tier-1 floors summed. Feeds the
 *  emergency-fund suggestion in Savings — one hard month, covered. */
fun personaSurvivalMonthly(persona: Persona): Int =
    personaTiers(persona).filter { it.tier == 1 }.sumOf { it.floorMonthly }
