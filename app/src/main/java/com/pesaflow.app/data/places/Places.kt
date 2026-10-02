package com.pesaflow.app.data.places

import androidx.room.*

// Local knowledge catalogue: places with prices. User-entered first —
// bundled seeds (if any) carry source + date and lose to fresh user data.
// Never invent prices: unknown means unknown, stated plainly.
@Entity(tableName = "places")
data class Place(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    // FOOD_OUTLET, SUPERMARKET, MARKET, STAGE, HOSTEL, FACILITY
    val kind: String = "FOOD_OUTLET",
    val area: String = "",
    val priceMin: Double = 0.0,
    val priceMax: Double = 0.0,
    val note: String = "",
    // USER_ENTERED or PUBLIC_DATASET
    val source: String = "USER_ENTERED",
    val verifiedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RankedOption(
    val place: Place,
    val expectedCost: Double,
    val reasons: List<String>,
    val evidence: String // USER_REPORTED, PUBLIC_DATASET, STALE
)

/**
 * Rank food options against a meal budget. Hard constraints exclude;
 * survivors order by affordability, then evidence quality. Pure logic.
 */
fun rankFoodOptions(
    options: List<Place>,
    mealBudget: Double,
    transportEachWay: Double = 0.0,
    now: Long = System.currentTimeMillis()
): Pair<List<RankedOption>, Int> {
    val foods = options.filter { it.kind == "FOOD_OUTLET" }
    val ranked = mutableListOf<RankedOption>()
    var excluded = 0
    for (p in foods) {
        val price = when {
            p.priceMin > 0 -> p.priceMin
            p.priceMax > 0 -> p.priceMax
            else -> 0.0
        }
        val total = price + transportEachWay * 2
        if (mealBudget > 0 && price > 0 && total > mealBudget) {
            excluded++
            continue
        }
        val stale = now - p.verifiedAt > 180L * 24 * 60 * 60 * 1000
        val evidence = when {
            p.source == "USER_ENTERED" && !stale -> "USER_REPORTED"
            stale -> "STALE"
            else -> "PUBLIC_DATASET"
        }
        val reasons = mutableListOf<String>()
        if (price > 0) reasons.add("KSh ${price.toInt()}")
        if (transportEachWay > 0) reasons.add("+KSh ${(transportEachWay * 2).toInt()} transport")
        if (p.area.isNotBlank()) reasons.add(p.area)
        ranked.add(RankedOption(p, total, reasons, evidence))
    }
    ranked.sortWith(compareBy<RankedOption> { it.expectedCost }
        .thenBy {
            when (it.evidence) {
                "USER_REPORTED" -> 0
                "PUBLIC_DATASET" -> 1
                else -> 2
            }
        })
    return ranked to excluded
}
