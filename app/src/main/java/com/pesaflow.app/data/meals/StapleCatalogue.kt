package com.pesaflow.app.data.meals

// Comrade-quantity staple catalogue. Units are how students actually buy
// (bunches, quarter cabbages, 2kg packs) — never grams. Prices are Nairobi
// kiosk estimates grounded in KAMIS retail + KNBS CPI (cabbage +25% YoY,
// cooking oil ~359/L): starting points the user corrects once, then their
// correction becomes truth. daysPerPack assumes one cooking student.
data class Staple(
    val name: String,
    // e.g. "bunch", "quarter", "2kg pack", "1kg pack", "1 litre", "handful", "6 pcs"
    val unitLabel: String,
    // pack size in stock units (1 pack = 1.0 unless weighed, e.g. 2.0 kg)
    val qtyFull: Double,
    // packs consumed per cooking day
    val dailyUse: Double,
    // kiosk estimate per pack — editable everywhere it is shown
    val defaultPrice: Double
) {
    // Whole cooking days one pack covers.
    fun daysPerPack(): Double = if (dailyUse > 0) qtyFull / dailyUse else 0.0
    // Ledger amount for buying [packs] packs.
    fun buyAmount(packs: Int): Double = defaultPrice * packs.coerceAtLeast(0)
}

val STAPLES: List<Staple> = listOf(
    Staple("Unga", "2kg pack", qtyFull = 1.0, dailyUse = 1.0 / 7, defaultPrice = 250.0),
    Staple("Sukuma wiki", "bunch", qtyFull = 1.0, dailyUse = 1.0 / 3, defaultPrice = 40.0),
    Staple("Cabbage", "quarter", qtyFull = 1.0, dailyUse = 1.0 / 2, defaultPrice = 40.0),
    Staple("Ndengu", "1kg pack", qtyFull = 1.0, dailyUse = 1.0 / 5, defaultPrice = 160.0),
    Staple("Rice", "1kg pack", qtyFull = 1.0, dailyUse = 1.0 / 4, defaultPrice = 170.0),
    Staple("Beans", "1kg pack", qtyFull = 1.0, dailyUse = 1.0 / 5, defaultPrice = 165.0),
    Staple("Cooking oil", "1 litre", qtyFull = 1.0, dailyUse = 1.0 / 14, defaultPrice = 360.0),
    Staple("Omena", "handful", qtyFull = 1.0, dailyUse = 1.0 / 2, defaultPrice = 50.0),
    Staple("Eggs", "6 pcs", qtyFull = 1.0, dailyUse = 1.0 / 3, defaultPrice = 110.0),
    Staple("Tomatoes", "4 pcs", qtyFull = 1.0, dailyUse = 1.0 / 3, defaultPrice = 60.0),
    Staple("Onions", "5 pcs", qtyFull = 1.0, dailyUse = 1.0 / 4, defaultPrice = 50.0),
    Staple("Githeri mix", "2kg pack", qtyFull = 1.0, dailyUse = 1.0 / 6, defaultPrice = 180.0)
)

fun stapleByName(name: String): Staple? =
    STAPLES.firstOrNull { it.name.equals(name, ignoreCase = true) }
