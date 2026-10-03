package com.pesaflow.app.ui.university

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.KitchenStock
import com.pesaflow.app.data.models.MealItem
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.schedule.WeekPlan
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintMealsSpice
import com.pesaflow.app.ui.theme.LinkOptionCard
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCard
import com.pesaflow.app.ui.theme.SkinMeals


private val MEAL_TYPES = listOf("Breakfast", "Lunch", "Supper", "Snack")
private val COMPONENTS = listOf("Starch", "Mboga", "Protein", "Complete")
private val RULE_DAYS = listOf("Any", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Weekdays", "Weekends")
private val RULE_KINDS = listOf("Must include", "Only source", "Max price")
private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")


private data class MealRule(val day: String, val kind: String, val value: String, val enabled: Boolean = true) {
    fun matches(weekday: String): Boolean {
        if (!enabled) return false
        return when (day) {
            "Any" -> true
            "Weekdays" -> weekday in listOf("Mon", "Tue", "Wed", "Thu", "Fri")
            "Weekends" -> weekday == "Sat" || weekday == "Sun"
            else -> day == weekday
        }
    }

    fun describe(): String = (when (kind) {
        "Must include" -> "$day: must include $value"
        "Only source" -> "$day: $value only"
        else -> "$day: max KSh $value/plate"
    }) + if (enabled) "" else " (paused)"
}


private fun weekdayName(offsetDays: Int): String {
    val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, offsetDays) }
    return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Mon"
        java.util.Calendar.TUESDAY -> "Tue"
        java.util.Calendar.WEDNESDAY -> "Wed"
        java.util.Calendar.THURSDAY -> "Thu"
        java.util.Calendar.FRIDAY -> "Fri"
        java.util.Calendar.SATURDAY -> "Sat"
        else -> "Sun"
    }
}


// Survival engine (pure: same math feeds the card, PesaBuddy and tests).
// Model, stated openly: one staple dinner a day; expiring stock burns first,
// then cheapest staple-days; stock always before spending; top-up cash buys
// the gap at the cheapest Cook staple's price. Fractional days don't cook dinner (floored).
data class SurvivalDay(val label: String, val staple: String, val fromStock: Boolean, val cost: Double)

data class SurvivalPlan(
    val days: List<SurvivalDay>,
    val shopping: List<Pair<String, Int>>,
    val totalCost: Double,
    val cash: Double,
    val possible: Boolean,
    val shortfall: Double,
    val noFiller: Boolean
)

fun planSurvival(
    stock: List<KitchenStock>,
    cookStaples: List<MealItem>,
    days: Int,
    cash: Double
): SurvivalPlan {
    if (days <= 0) return SurvivalPlan(emptyList(), emptyList(), 0.0, cash, true, 0.0, false)
    val nowMs = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    data class Pile(val name: String, var wholeDays: Int, val dailyCost: Double, val urgent: Boolean)
    val piles = stock
        .filter { it.dailyUse > 0 && it.qtyLeft > 0 }
        .map {
            val whole = (it.qtyLeft / it.dailyUse).toInt().coerceAtLeast(0)
            val dailyCost = if (it.qtyFull > 0 && it.pricePerPack > 0) it.pricePerPack / it.qtyFull * it.dailyUse else 0.0
            // Expiry-first: "eat in N days" or expiring within 5 days burns before cheap-but-safe stock.
            val urgent = (it.eatByDays in 1..5) ||
                (it.expiryTimestamp > 0L && it.expiryTimestamp - nowMs <= 5 * dayMs)
            Pile(it.name, whole, dailyCost, urgent)
        }
        .filter { it.wholeDays > 0 }
        .sortedWith(compareBy({ !it.urgent }, { it.dailyCost }))
        .toMutableList()
    val outDays = mutableListOf<SurvivalDay>()
    var remaining = days
    for (pile in piles) {
        while (remaining > 0 && pile.wholeDays > 0) {
            outDays.add(SurvivalDay("Day ${outDays.size + 1} (${weekdayName(outDays.size)})", pile.name, true, 0.0))
            pile.wholeDays--
            remaining--
        }
        if (remaining <= 0) break
    }
    val shopping = mutableListOf<Pair<String, Int>>()
    var totalCost = 0.0
    var noFiller = false
    if (remaining > 0) {
        val cheapest = cookStaples.sortedBy { it.price }.firstOrNull()
        if (cheapest == null) {
            noFiller = true
        } else {
            repeat(remaining) {
                outDays.add(SurvivalDay("Day ${outDays.size + 1} (${weekdayName(outDays.size)})", cheapest.name, false, cheapest.price))
            }
            shopping.add(cheapest.name to remaining)
            totalCost = cheapest.price * remaining
        }
        remaining = 0
    }
    val possible = !noFiller && totalCost <= cash
    return SurvivalPlan(outDays, shopping, totalCost, cash, possible, (totalCost - cash).coerceAtLeast(0.0), noFiller)
}


private data class Staple(
    val name: String,
    val mealType: String,
    val price: Double,
    val component: String,
    val source: String
)


// Editable starter estimates; current shop prices are not verified centrally.
private val STAPLES = listOf(
    Staple("Sukuma wiki", "Lunch", 30.0, "Mboga", "Cook"),
    Staple("Ugali", "Lunch", 30.0, "Starch", "Cook"),
    Staple("Rice + beans", "Lunch", 70.0, "Complete", "Cook"),
    Staple("Chapati + ndengu", "Lunch", 60.0, "Complete", "Cook"),
    Staple("Pilau", "Lunch", 100.0, "Complete", "Buy"),
    Staple("Boiled eggs (2)", "Breakfast", 40.0, "Protein", "Cook"),
    Staple("Chai + mandazi", "Breakfast", 50.0, "Complete", "Buy"),
    Staple("Githeri", "Supper", 60.0, "Complete", "Cook"),
    Staple("Rice + cabbage", "Supper", 60.0, "Complete", "Cook"),
    Staple("Bananas", "Snack", 50.0, "Complete", "Buy")
 )


// One persona gear: the planner's Transport|Hostel|Tight|Full presets derive
// from the app-wide setup (home/commute/cooking) instead of living a second,
// diverging life. Pure mapping — tested.
fun defaultMealPersona(appPersona: com.pesaflow.app.ui.budgets.Persona): String = when (appPersona) {
    com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR -> "Transport"
    com.pesaflow.app.ui.budgets.Persona.PARENTS_NEAR -> "Tight"
    com.pesaflow.app.ui.budgets.Persona.RENT_WALK -> "Hostel"
    com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE -> "Transport"
    com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK -> "Full"
    com.pesaflow.app.ui.budgets.Persona.HOSTEL_COOK -> "Hostel"
}


private fun scannedMonthlyFoodOf(answers: String): Int? {
    val m = Regex("scan_food=(\\d+)").find(answers) ?: return null
    return m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
}

data class PlannedDay(
    val label: String,
    val slots: Map<String, List<MealItem>>,
    val total: Double,
    val overBudget: Boolean
) {
    val items: List<MealItem> get() = slots.values.flatten()
}


data class PlannedWeek(
    val label: String,
    val days: Int,
    val total: Double
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlannerScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val mealItems by viewModel.mealItems.collectAsState()
    val kitchenStock by viewModel.kitchenStock.collectAsState()
    val ledgerTxns by viewModel.allTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    // Engine link: survival top-ups can ride the snapshot's flexible money.
    val engineSnap by viewModel.financialSnapshot.collectAsState()
    // Campus pack: real spots + plates near this university, if we know it.
    val uniProfile by viewModel.universityProfile.collectAsState()
    val campusSpots = remember(uniProfile?.universityName) {
        com.pesaflow.app.data.meals.spotsFor(uniProfile?.universityName ?: "")
    }
    // Your reported spots feed the ranked card below (USER_REPORTED first).
    val mySpots by viewModel.places.collectAsState()

    val foodBudget = budgets.firstOrNull { it.category == "Food" }?.limitAmount
    var monthlyFoodInput by remember { mutableStateOf(foodBudget?.toInt()?.toString() ?: viewModel.let { scannedMonthlyFoodOf(it.getOnboardingAnswers()) }?.toString().orEmpty()) }
    var selectedPeriod by remember { mutableStateOf("Week") }
    var menu by remember { mutableStateOf<List<PlannedDay>>(emptyList()) }

    // Item entry state
    var foodName by remember { mutableStateOf("") }
    var foodPrice by remember { mutableStateOf("") }
    var foodType by remember { mutableStateOf("Lunch") }
    var foodComponent by remember { mutableStateOf("Complete") }
    var foodSource by remember { mutableStateOf("Buy") }
    // Persona default: non-cooks plan from bought meals only; cooks see all.
    var sourceFilter by remember {
        val persona = com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
        mutableStateOf(if (persona == com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK) "Buy" else "Any")
    }
    var seed by remember { mutableStateOf(0) }
    // Friendlier planning controls
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Price") }
    var maxPlate by remember { mutableStateOf("Any") }
    var includeBreakfast by remember { mutableStateOf(true) }
    var includeLunch by remember { mutableStateOf(true) }
    var includeSupper by remember { mutableStateOf(true) }
    var people by remember { mutableStateOf(1) }
    var forceProtein by remember { mutableStateOf(false) }
    var checkedGroceries by remember { mutableStateOf(setOf<String>()) }
    // Portion scale: cooking quarters/halves (unga for lunch, cabbage for supper)
    // scales the shopping estimate — fractions stretch both stock and cash.
    var portionScale by remember { mutableStateOf(1.0) }
    var editingItem by remember { mutableStateOf<MealItem?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var logMsg by remember { mutableStateOf<String?>(null) }
    // Menu editing: which day is open in the edit dialog (all periods).
    var editingDayIdx by remember { mutableStateOf<Int?>(null) }
    // Proof ticks: brief ✓ that reverts so actions stay tappable.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }
    // Stale-menu tripwire: which controls the visible menu was built with.
    var menuSig by remember { mutableStateOf("") }
    // Declared early: persona + menu-today persistence below both read it.
    val mealPrefs = remember { context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    // Curated personas: one tap configures the whole planner for a student
    // life — transport kids get lunch-only near the stage, hostel cooks get
    // the full kitchen, survivors get the cheapest plates. Remembered.
    var persona by remember { mutableStateOf(mealPrefs.getString("meal_persona", "") ?: "") }
    // Controls without persistence: auto-follow reconfigures without
    // overwriting the user's stored pick.
    fun applyMealControls(name: String) {
        when (name) {
            "Transport" -> {
                includeBreakfast = false; includeLunch = true; includeSupper = false
                sourceFilter = "Buy"; maxPlate = "150"; forceProtein = false
            }
            "Hostel" -> {
                includeBreakfast = true; includeLunch = true; includeSupper = true
                sourceFilter = "Cook"; maxPlate = "Any"; forceProtein = false
            }
            "Tight" -> {
                includeBreakfast = false; includeLunch = true; includeSupper = true
                sourceFilter = "Any"; maxPlate = "50"; forceProtein = false
            }
            else -> {
                includeBreakfast = true; includeLunch = true; includeSupper = true
                sourceFilter = "Any"; maxPlate = "Any"; forceProtein = true
            }
        }
    }
    fun applyPersona(name: String) {
        persona = name
        mealPrefs.edit().putString("meal_persona", name).apply()
        applyMealControls(name)
    }
    // Auto-follow: no manual pick (blank) or explicit Auto tracks the profile —
    // change home/commute/cooking and the planner reconfigures itself.
    val appPersona = remember(viewModel.getOnboardingAnswers()) {
        com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
    }
    val effectiveMealPersona = if (persona == "Auto" || persona.isBlank()) defaultMealPersona(appPersona) else persona
    LaunchedEffect(effectiveMealPersona) {
        if (persona == "Auto" || persona.isBlank()) applyMealControls(effectiveMealPersona)
    }
    // Survival mode: stretch stock till a date, top-ups only for the gap
    var survivalDays by remember { mutableStateOf(7) }
    var survivalCash by remember { mutableStateOf("") }
    var survivalPlan by remember { mutableStateOf<SurvivalPlan?>(null) }
    // Optional food rules, e.g. "Fridays = meat only". Persisted, enforced daily.
    var mealRules by remember {
        mutableStateOf(
            mealPrefs.getString("meal_rules", "").orEmpty().split(";")
                .mapNotNull { row ->
                    val parts = row.split("|")
                    // v1 rows have 3 parts (always enabled); v2 appends 1/0.
                    if ((parts.size == 3 || parts.size == 4) && parts[0] in RULE_DAYS && parts[1] in RULE_KINDS && parts[2].isNotBlank()) {
                        MealRule(parts[0], parts[1], parts[2], parts.getOrNull(3) != "0")
                    } else null
                }
        )
    }
    var newRuleDay by remember { mutableStateOf("Fri") }
    var newRuleKind by remember { mutableStateOf("Must include") }
    var newRuleValue by remember { mutableStateOf("Protein") }
    fun saveRules(rules: List<MealRule>) {
        mealRules = rules
        mealPrefs.edit().putString("meal_rules", rules.joinToString(";") { "${it.day}|${it.kind}|${it.value}|${if (it.enabled) 1 else 0}" }).apply()
    }
    var lunchReminder by remember { mutableStateOf(mealPrefs.getBoolean("lunch_reminder", false)) }

    val scannedFood = remember { scannedMonthlyFoodOf(viewModel.getOnboardingAnswers()) }
    val monthlyFoodRaw = monthlyFoodInput.toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0
    // Calibrated allowance: your real 90-day Food average caps the figure (×1.2 headroom)
    // unless you set a deliberate Food budget envelope — then your word wins.
    val foodAvg90 = ledgerTxns.filter {
        it.type == TransactionType.EXPENSE && !it.isSample && it.category == "Food" &&
            it.dateTimestamp >= System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
    }.sumOf { it.amount } / 3
    val monthlyFood = when {
        foodBudget != null -> monthlyFoodRaw
        foodAvg90 > 0 -> minOf(monthlyFoodRaw, foodAvg90 * 1.2)
        else -> monthlyFoodRaw
    }
    val dailyAllowance = monthlyFood / 30
    // Shared pot: cooking for N splits the same daily money N ways up.
    val effectiveAllowance = dailyAllowance * people
    val periodDays = when (selectedPeriod) {
        "Day" -> 1
        "Week" -> 7
        "Month" -> 30
        else -> 120
    }
    val periodBudget = effectiveAllowance * periodDays
    val menuTotal = menu.sumOf { it.total }
    val overDays = menu.count { it.overBudget }

    val plateCap = when (maxPlate) {
        "50" -> 50.0
        "100" -> 100.0
        "150" -> 150.0
        "200" -> 200.0
        else -> Double.MAX_VALUE
    }

    // Eat-first: stock flagged "eat in 1/5 days" or expiring within 5 days
    // jumps the queue in every generated plate.
    val priorityNames = remember(kitchenStock) {
        val nowMs = System.currentTimeMillis()
        kitchenStock.filter {
            it.eatByDays > 0 || (it.expiryTimestamp > 0L && it.expiryTimestamp - nowMs <= 5 * 24L * 60 * 60 * 1000)
        }.map { it.name.lowercase() }.toSet()
    }
    fun isPriorityFood(mealName: String): Boolean {
        val words = mealName.lowercase().split(Regex("[^a-z]+")).filter { it.length > 2 }
        return priorityNames.any { n -> n in mealName.lowercase() || words.any { w -> w in n } }
    }

    fun plateFor(mealType: String, dayIndex: Int, allowanceLeft: Double, source: String = sourceFilter, cap: Double = plateCap): List<MealItem> {
        val list = mealItems
            .filter { it.mealType == mealType && (source == "Any" || it.source == source) && it.price <= cap }
            .sortedWith(compareBy({ !isPriorityFood(it.name) }, { it.price }))
        if (list.isEmpty()) return emptyList()
        // Prefer a ready complete meal, rotating daily for variety
        list.filter { it.component == "Complete" }.takeIf { it.isNotEmpty() }?.let {
            val plate = mutableListOf(it[dayIndex % it.size])
            if (forceProtein) {
                val hasProtein = plate.any { p -> p.component == "Protein" }
                if (!hasProtein) {
                    list.filter { p -> p.component == "Protein" }.minByOrNull { p -> p.price }?.let { prot -> plate.add(prot) }
                }
            }
            return plate
        }
        // Otherwise build starch + mboga (+ protein if it fits — or forced)
        val plate = mutableListOf<MealItem>()
        var spent = 0.0
        val starches = list.filter { it.component == "Starch" }
        val mbogas = list.filter { it.component == "Mboga" }
        val proteins = list.filter { it.component == "Protein" }
        val others = list.filter { it.component != "Starch" && it.component != "Mboga" && it.component != "Protein" }
        if (starches.isNotEmpty()) {
            val s = starches[dayIndex % starches.size]
            plate.add(s); spent += s.price
        }
        if (mbogas.isNotEmpty()) {
            val m = mbogas[dayIndex % mbogas.size]
            plate.add(m); spent += m.price
        }
        if (plate.isEmpty() && others.isNotEmpty()) {
            val o = others[dayIndex % others.size]
            plate.add(o); spent += o.price
        }
        if (proteins.isNotEmpty() && (forceProtein || spent + proteins.minOf { it.price } <= allowanceLeft)) {
            val sorted = proteins.sortedBy { it.price }
            if (plate.none { it.id == sorted[dayIndex % sorted.size].id }) {
                plate.add(sorted[dayIndex % sorted.size])
            }
        }
        return plate
    }

    fun generateMenu() {
        menuSig = "$sourceFilter|$maxPlate|$includeBreakfast|$includeLunch|$includeSupper|$people|$forceProtein|$selectedPeriod|$seed"
        checkedGroceries = emptySet()
        if (monthlyFood <= 0.0) {
            menu = emptyList()
            return
        }
        val mains = MEAL_TYPES.filter { it != "Snack" }.filter {
            (it != "Breakfast" || includeBreakfast) && (it != "Lunch" || includeLunch) && (it != "Supper" || includeSupper)
        }
        val hasMains = mains.any { t -> mealItems.any { it.mealType == t && (sourceFilter == "Any" || it.source == sourceFilter) && it.price <= plateCap } }
        if (!hasMains) {
            menu = emptyList()
            return
        }
        menu = (1..periodDays).map { day ->
            var spent = 0.0
            val slots = linkedMapOf<String, List<MealItem>>()
            // Your rules shape this whole day: source/cap narrow the pool
            // (mains AND snack), must-include tops plates up afterwards.
            val wd = weekdayName(day - 1)
            val dayRules = mealRules.filter { it.matches(wd) }
            val daySource = dayRules.firstOrNull { it.kind == "Only source" }?.value ?: sourceFilter
            val dayCap = dayRules.filter { it.kind == "Max price" }
                .mapNotNull { it.value.toDoubleOrNull()?.takeIf { v -> v > 0 } }
                .minOrNull() ?: plateCap
            mains.forEach { t ->
                val plate = plateFor(t, day - 1 + seed, (effectiveAllowance / 3).coerceAtLeast(effectiveAllowance * 0.2), daySource, dayCap).toMutableList()
                dayRules.filter { it.kind == "Must include" }.forEach { r ->
                    if (plate.none { it.component == r.value }) {
                        mealItems
                            .filter { it.component == r.value && (daySource == "Any" || it.source == daySource) && it.price <= dayCap }
                            .minByOrNull { it.price }
                            ?.let { plate.add(it) }
                    }
                }
                slots[t] = plate
                spent += plate.sumOf { it.price } * people
            }
            val snacks = mealItems
                .filter { it.mealType == "Snack" && (daySource == "Any" || it.source == daySource) && it.price <= dayCap }
                .sortedBy { it.price }
            val snack = snacks.getOrNull(if (snacks.isNotEmpty()) (day - 1 + seed) % snacks.size else 0)
            if (snack != null && spent + snack.price * people <= effectiveAllowance) {
                slots["Snack"] = listOf(snack)
                spent += snack.price * people
            } else {
                slots["Snack"] = emptyList()
            }
            PlannedDay(
                label = if (periodDays == 1) "Today" else "Day $day",
                slots = slots,
                total = spent,
                overBudget = spent > effectiveAllowance
            )
        }
        // Persist day-1 for the night report's "supposed to eat X — did you?" question.
        menu.firstOrNull()?.let { d0 ->
            val flat = listOf("Breakfast", "Lunch", "Supper").mapNotNull { s ->
                d0.slots[s]?.takeIf { it.isNotEmpty() }?.let { s + ":" + it.joinToString("+") { m -> m.name } }
            }.joinToString("|")
            if (flat.isNotBlank()) mealPrefs.edit().putString("menu_today", flat).apply()
        }
    }

    // One-tap rescue: swap each over-budget day's priciest plate for the
    // cheapest same-meal alternative. Repeat taps keep cheapening.
    fun fixOverDays() {
        menu = menu.map { d ->
            if (!d.overBudget) return@map d
            val slots = d.slots.toMutableMap()
            val target = slots.filter { it.value.isNotEmpty() }
                .maxByOrNull { it.value.sumOf { i -> i.price } }
            if (target != null) {
                val priciest = target.value.maxByOrNull { it.price }
                if (priciest != null) {
                    val cheaper = mealItems
                        .filter { it.mealType == target.key && it.id != priciest.id && (sourceFilter == "Any" || it.source == sourceFilter) && it.price < priciest.price }
                        .minByOrNull { it.price }
                    if (cheaper != null) {
                        slots[target.key] = target.value.map { if (it.id == priciest.id) cheaper else it }
                    }
                }
            }
            val total = slots.values.flatten().sumOf { it.price } * people
            d.copy(slots = slots, total = total, overBudget = total > effectiveAllowance)
        }
    }

    // One-tap week: cheapest compliant 7 days, zero tuning. Generates with a
    // fresh seed, then auto-fixes over-budget days (up to 3 passes).
    fun autoPlanWeek() {
        selectedPeriod = "Week"
        seed = (1..1000).random()
        generateMenu()
        repeat(3) {
            if (menu.any { it.overBudget }) fixOverDays()
        }
        if (menu.isEmpty()) {
            logMsg = "Add some foods first — then I plan the whole week. 🍳"
            return
        }
        val total = menu.sumOf { it.total }
        val bad = menu.count { it.overBudget }
        val buys = mealItems.filter { it.source == "Buy" }.map { it.price }
        val buyWeek = if (buys.isNotEmpty()) buys.average() * 3 * 7 * people else 0.0
        val saved = (buyWeek - total).coerceAtLeast(0.0)
        logMsg = "Week planned: KSh ${total.toInt()}" +
            (if (bad > 0) " ($bad day(s) still over — tap Fix over)" else " (all days in budget ✅)") +
            (if (buyWeek > 0) " · ~KSh ${saved.toInt()} saved vs buying." else ".") +
            " Log it below 👇"
    }


    // Menu surgery: recompute a day after hand edits.
    fun refreshDay(d: PlannedDay): PlannedDay {
        val total = d.slots.values.flatten().sumOf { it.price } * people
        return d.copy(slots = d.slots, total = total, overBudget = total > effectiveAllowance)
    }

    // Swap cycles an item to the next cheapest same-meal alternative.
    fun swapItem(dayIdx: Int, slot: String, item: MealItem) {
        val candidates = mealItems
            .filter { it.mealType == slot && (sourceFilter == "Any" || it.source == sourceFilter) }
            .sortedBy { it.price }
        if (candidates.size < 2) return
        val pos = candidates.indexOfFirst { it.id == item.id }
        val next = candidates[if (pos < 0) 0 else (pos + 1) % candidates.size]
        menu = menu.mapIndexed { i, d ->
            if (i != dayIdx) d
            else {
                val slots = d.slots.toMutableMap()
                slots[slot] = slots[slot].orEmpty().map { if (it.id == item.id) next else it }
                refreshDay(d.copy(slots = slots))
            }
        }
    }

    fun removeItem(dayIdx: Int, slot: String, item: MealItem) {
        menu = menu.mapIndexed { i, d ->
            if (i != dayIdx) d
            else {
                val slots = d.slots.toMutableMap()
                slots[slot] = slots[slot].orEmpty().filter { it.id != item.id }
                refreshDay(d.copy(slots = slots))
            }
        }
    }

    // Copy a good day across the whole menu, then tweak.
    fun copyDay(dayIdx: Int) {
        val src = menu.getOrNull(dayIdx) ?: return
        menu = menu.map { d -> refreshDay(d.copy(slots = src.slots.mapValues { it.value.toList() })) }
    }

    // Cheapen a day: every plate becomes its cheapest same-meal alternative.
    fun cheapenDay(dayIdx: Int) {
        menu = menu.mapIndexed { i, d ->
            if (i != dayIdx) d
            else {
                val slots = d.slots.mapValues { (slot, items) ->
                    items.map { item ->
                        mealItems
                            .filter { it.mealType == slot && (sourceFilter == "Any" || it.source == sourceFilter) }
                            .minByOrNull { it.price } ?: item
                    }
                }
                refreshDay(d.copy(slots = slots))
            }
        }
    }

    fun dayLine(d: PlannedDay): String {
        val parts = d.slots.filter { it.value.isNotEmpty() }.map { (slot, items) ->
            "$slot: ${items.joinToString("+") { "${it.name}(${it.price.toInt()})" }}"
        }
        return "${d.label} — KSh ${d.total.toInt()}${if (d.overBudget) " OVER" else ""}\n  " + parts.joinToString("\n  ")
    }

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = buildMenuPdf(menu, selectedPeriod, monthlyFood, dailyAllowance, periodBudget, people)
                    try {
                        doc.writeTo(out)
                    } finally {
                        doc.close()
                    }
                }
                logMsg = "PDF saved ✅"
            } catch (e: Exception) {
                logMsg = "PDF failed: ${e.message}"
            }
        }
    }

    fun shareMenu() {
        if (menu.isEmpty()) return
        val sb = StringBuilder("PesaFlow $selectedPeriod Menu — food KSh ${monthlyFood.toInt()}/month (KSh ${dailyAllowance.toInt()}/day)\n\n")
        if (periodDays == 1) {
            menu.forEach { sb.append(dayLine(it)).append("\n\n") }
        } else if (selectedPeriod == "Semester") {
            menu.chunked(7).forEachIndexed { i, week ->
                sb.append("Week ${i + 1} — KSh ${week.sumOf { it.total }.toInt()}\n")
            }
            sb.append("\n")
            menu.forEach { sb.append(dayLine(it)).append("\n") }
        } else {
            menu.forEach { sb.append(dayLine(it)).append("\n") }
        }
        if (mealRules.any { it.enabled }) {
            sb.append("\nRules: " + mealRules.filter { it.enabled }.joinToString("; ") { it.describe() } + "\n")
        }
        sb.append("\nShopping list:\n")
        menu.flatMap { it.items }
            .groupBy { it.name to it.price }
            .map { (key, list) -> Triple(key.first, key.second, list.size * people) }
            .sortedByDescending { it.third }
            .forEach { (name, price, count) ->
                sb.append("  $name ×$count — KSh ${(price * count).toInt()}\n")
            }
        sb.append("\nTotal: KSh ${menuTotal.toInt()} of KSh ${periodBudget.toInt()} $selectedPeriod budget. Days over daily allowance: $overDays.")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PesaFlow $selectedPeriod menu")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Download menu"))
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintMealsSpice, bgRes = R.drawable.bg_meals_spice)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Meal Planner 🍲", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.MEALS,
                title = "Plates under budget",
                subtitle = "Menus · rules · survival"
            )
            SkinCard(skin = SkinMeals) {
                Text("Plate money for today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                SkinAccentLine(SkinMeals.accent)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (monthlyFood > 0) "KSh ${dailyAllowance.toInt()} / day" else "Food budget not set",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (monthlyFood > 0) "KSh " + periodBudget.toInt() + " for " + periodDays + " days - menu KSh " + menuTotal.toInt() + if (overDays > 0) " (" + overDays + " over)" else " (fits)"
                    else "Set a Food amount before generating a budget-based menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (monthlyFood > 0 && overDays > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                if (priorityNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Eat-first from your cupboard: " + priorityNames.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Budget link card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Monthly Food Budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (monthlyFood > 0) "KSh ${monthlyFood.toInt()} / month = KSh ${dailyAllowance.toInt()} per day" +
                            (if (foodBudget != null) " (from your Food budget)" else if (scannedFood != null) " (from your M-Pesa scan - edit me)" else if (foodAvg90 > 0) " (based on recent Food spending)" else " (your estimate)")
                        else "No food budget or spending estimate is available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (monthlyFood > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (monthlyFood <= 0) {
                        Text(
                            "Enter a monthly amount, or use your recorded spending below. The planner won't guess a budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (monthlyFood > 0) {
                        Text(
                            com.pesaflow.app.data.meals.mealTierLabel(
                                com.pesaflow.app.data.meals.mealTier(dailyAllowance.toDouble())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Onboarding context, read back: the grocery spot stated
                    // at setup surfaces where food money is planned.
                    val ctxFacts by viewModel.userContextFacts.collectAsState()
                    ctxFacts["food.grocerySpot"]?.takeIf { it.isNotBlank() }?.let { spot ->
                        Text(
                            "Buying groceries at $spot 🛒",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = monthlyFoodInput,
                        onValueChange = { monthlyFoodInput = it },
                        label = { Text("Adjust monthly food budget (KSh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (scannedFood != null && monthlyFoodInput != scannedFood.toString()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { monthlyFoodInput = scannedFood.toString() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Use my M-Pesa figure: KSh " + scannedFood + " (about KSh " + (scannedFood / 30) + "/day)") }
                    }
                    if (foodAvg90 > 0 && monthlyFoodInput.toDoubleOrNull()?.toInt() != foodAvg90.toInt()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { monthlyFoodInput = foodAvg90.toInt().toString() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Use your recorded 90-day Food average: KSh ${foodAvg90.toInt()}/month") }
                    }
                    if (foodBudget == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.addBudget("Food", monthlyFood, BudgetType.MONTHLY)
                                ack("foodsave")
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if ("foodsave" in acked) "Saved ✓" else "Save KSh ${monthlyFood.toInt()} as my Food budget") }
                    }
                }
            }

            if (foodBudget != null) {
                val monthStartCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val foodSpentMonth = ledgerTxns.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.category == "Food" && it.dateTimestamp >= monthStartCal }.sumOf { it.amount }
                val foodLeft = (foodBudget - foodSpentMonth).coerceAtLeast(0.0)
                LinkOptionCard(
                    skin = SkinMeals,
                    title = "Fund menu from Food budget?",
                    body = "KSh " + foodLeft.toInt() + " left of KSh " + foodBudget.toInt() + " this month. Apply it as the menu figure?",
                    actionLabel = "Use KSh " + foodLeft.toInt(),
                    confirmTitle = "Set menu figure to KSh " + foodLeft.toInt() + "?",
                    confirmBody = "Your monthly food input becomes the budget remainder. You can still edit it after.",
                    confirmLabel = "Apply",
                    onConfirm = { monthlyFoodInput = foodLeft.toInt().toString() }
                )
            }
            // Add food card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("My Foods (${mealItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tag each food by meal and by part: starch (ugali, rice, chapati), mboga (sukuma, cabbage, beans), protein (eggs, meat, omena) — or Complete for ready plates like pilau.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = foodName, onValueChange = { foodName = it }, label = { Text("Food (e.g. Sukuma wiki)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = foodPrice, onValueChange = { foodPrice = it }, label = { Text("Price (KSh)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Meal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = MEAL_TYPES.map { com.pesaflow.app.ui.theme.SegOption(it, it) },
                        selected = foodType,
                        onSelect = { foodType = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Part", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = COMPONENTS.map { com.pesaflow.app.ui.theme.SegOption(it, it) },
                        selected = foodComponent,
                        onSelect = { foodComponent = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Cook", "Cook", "🍳"),
                            com.pesaflow.app.ui.theme.SegOption("Buy", "Buy", "🍲")
                        ),
                        selected = foodSource,
                        onSelect = { foodSource = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val price = foodPrice.toDoubleOrNull()
                            if (foodName.isNotBlank() && price != null && price > 0) {
                                viewModel.addMealItem(foodName.trim(), foodType, price, foodComponent, foodSource)
                                foodName = ""
                                foodPrice = ""
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Add Food", color = MaterialTheme.colorScheme.onPrimary) }

                    if (mealItems.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Starter meal prices are estimates. Adjust them to what your local shop or kibanda charges.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                STAPLES.forEach {
                                    viewModel.addMealItem(it.name, it.mealType, it.price, it.component, it.source)
                                }
                                ack("staples")
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if ("staples" in acked) "Added 10 ✓" else "Add 10 staples pack 🧺 — start in one tap") }
                        // Local starter suggestions, not live-verified listings.
                        // Skips plates already in My Foods; prices stay editable.
                        if (campusSpots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nearby names and prices are starter estimates, not live-verified. Check locally before budgeting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val missing = campusSpots.filter { s ->
                                mealItems.none { it.name.equals("${s.item} (${s.spot})", ignoreCase = true) }
                            }
                            OutlinedButton(
                                onClick = {
                                    missing.forEach {
                                        viewModel.addMealItem("${it.item} (${it.spot})", it.mealType, it.price, it.component, "Buy")
                                    }
                                    ack("campus")
                                },
                                enabled = missing.isNotEmpty(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if ("campus" in acked) "Added ✓"
                                    else if (missing.isEmpty()) "${campusSpots.first().university} pack in ✓ — prices editable below"
                                    else "Add ${campusSpots.first().university} local estimates 🍲 — ${missing.size} meal ideas"
                                )
                            }
                        }
                        // Ranked your-spots: user reports outrank the bundled
                        // pack; over-budget plates excluded with a count.
                        // Same rankFoodOptions the tests pin — one rule, both places.
                        val myRanked = remember(mySpots, dailyAllowance) {
                            com.pesaflow.app.data.places.rankFoodOptions(
                                mySpots.filter { it.kind == "FOOD_OUTLET" },
                                mealBudget = dailyAllowance
                            )
                        }
                        val missingSpots = myRanked.first.filter { r ->
                            mealItems.none { it.name.equals("${r.place.name} (${r.place.area})", ignoreCase = true) }
                        }
                        if (mySpots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Your spots, ranked 🥇", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Your reports first — over-budget plates filtered out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (missingSpots.isEmpty()) {
                                        Text("All your spots are in My Foods ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    missingSpots.take(4).forEach { r ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(r.place.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                                Text(
                                                    "KSh ${r.expectedCost.toInt()}" + if (r.place.area.isNotBlank()) " · ${r.place.area}" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            val spotKey = "myspot:${r.place.id}"
                                            TextButton(
                                                onClick = {
                                                    viewModel.addMealItem("${r.place.name} (${r.place.area})", "Lunch", r.expectedCost, "Complete", "Buy")
                                                    ack(spotKey)
                                                },
                                                enabled = spotKey !in acked
                                            ) { Text(if (spotKey in acked) "Added ✓" else "+ Food", color = MaterialTheme.colorScheme.primary) }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    if (myRanked.second > 0) {
                                        Text(
                                            "${myRanked.second} over your KSh ${dailyAllowance.toInt()} plate budget — skipped.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        // Your spots: plates you report beat the bundled pack
                        // (USER_REPORTED ranks first). Stored locally; shared
                        // only through the Online hub opt-in.
                        val userPlaces by viewModel.places.collectAsState()
                        var showSpotDialog by remember { mutableStateOf(false) }
                        var spotName by remember { mutableStateOf("") }
                        var spotPrice by remember { mutableStateOf("") }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showSpotDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("＋ Suggest a spot — your plates beat our pack") }
                        userPlaces.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${p.name} · KSh ${p.priceMin.toInt()} · your report ✅",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.deletePlace(p.id) }) { Text("×") }
                            }
                        }
                        if (showSpotDialog) {
                            AlertDialog(
                                onDismissRequest = { showSpotDialog = false },
                                title = { Text("Suggest a spot") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = spotName, onValueChange = { spotName = it }, label = { Text("Spot + plate (e.g. Mama Njoroge chapati)") }, modifier = Modifier.fillMaxWidth())
                                        OutlinedTextField(value = spotPrice, onValueChange = { spotPrice = it }, label = { Text("Price (KSh)") }, modifier = Modifier.fillMaxWidth())
                                        Text("Shows as your report next to campus prices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        spotPrice.toDoubleOrNull()?.takeIf { it > 0 }?.let { price ->
                                            viewModel.addPlace(spotName, uniProfile?.universityName ?: "", price)
                                            spotName = ""
                                            spotPrice = ""
                                            showSpotDialog = false
                                        }
                                    }) { Text("Save") }
                                },
                                dismissButton = { TextButton(onClick = { showSpotDialog = false }) { Text("Cancel") } }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search foods") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sort:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf("Price", "Name").forEach { s ->
                                FilterChip(selected = sortBy == s, onClick = { sortBy = s }, label = { Text(s) })
                            }
                        }
                        val visibleFoods = mealItems
                            .filter { searchQuery.isBlank() || it.name.contains(searchQuery.trim(), ignoreCase = true) }
                            .let { list -> if (sortBy == "Name") list.sortedBy { it.name.lowercase() } else list.sortedBy { it.price } }
                        if (visibleFoods.isEmpty()) {
                            Text("No foods match \"$searchQuery\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MEAL_TYPES.forEach { t ->
                            val list = visibleFoods.filter { it.mealType == t }
                            if (list.isNotEmpty()) {
                                Text(t, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                list.forEach { item ->
                                    if (editingItem?.id == item.id) {
                                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(value = editPrice, onValueChange = { editPrice = it }, label = { Text("Price (KSh)") }, modifier = Modifier.weight(1f))
                                            TextButton(onClick = {
                                                val cur = editPrice.toDoubleOrNull() ?: 0.0
                                                editPrice = (cur - 10).coerceAtLeast(0.0).toInt().toString()
                                            }) { Text("−10") }
                                            TextButton(onClick = {
                                                val cur = editPrice.toDoubleOrNull() ?: 0.0
                                                editPrice = (cur + 10).toInt().toString()
                                            }) { Text("+10") }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(onClick = {
                                                val price = editPrice.toDoubleOrNull()
                                                if (editName.isNotBlank() && price != null && price > 0) {
                                                    viewModel.deleteMealItem(item.id)
                                                    viewModel.addMealItem(editName.trim(), item.mealType, price, item.component, item.source)
                                                    editingItem = null
                                                }
                                            }) { Text("Save") }
                                            TextButton(onClick = { editingItem = null }) { Text("Cancel") }
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${item.name} · ${item.component} · ${item.source} — KSh ${item.price.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { editingItem = item; editName = item.name; editPrice = item.price.toInt().toString() }) {
                                                Text("Edit", style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = { viewModel.addMealItem(item.name + " (copy)", item.mealType, item.price, item.component, item.source) }) {
                                                Text("Copy", style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = { viewModel.deleteMealItem(item.id) }) {
                                                Text("Remove", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // My rules (optional): your say, enforced every generation
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("My Food Rules 📏 (${mealRules.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Optional — e.g. Fridays = meat only. Rules shape every menu you generate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mealRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Which rules actually grabbed days in the visible menu.
                        val matched = mealRules.filter { r ->
                            menu.isNotEmpty() && (1..periodDays).any { d -> r.matches(weekdayName(d - 1)) }
                        }.toSet()
                        // Specific-day source rules quietly override Any-day ones.
                        val anySrc = mealRules.firstOrNull { it.enabled && it.day == "Any" && it.kind == "Only source" }
                        val overrideSrc = mealRules.firstOrNull { it.enabled && it.day != "Any" && it.kind == "Only source" }
                        if (anySrc != null && overrideSrc != null) {
                            Text(
                                "⚖️ ${overrideSrc.day} ${overrideSrc.value} wins over Any-day ${anySrc.value} on those days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        mealRules.forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "• ${r.describe()}" + if (r in matched) " ✓" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { saveRules(mealRules.map { if (it == r) it.copy(enabled = !it.enabled) else it }) }) {
                                    Text(if (r.enabled) "Pause" else "Resume", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { saveRules(mealRules - r) }) {
                                    Text("Remove", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        TextButton(onClick = { saveRules(emptyList()) }) {
                            Text("Clear all rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1 · Which days?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Any", "Any", "📅"),
                            com.pesaflow.app.ui.theme.SegOption("Mon", "Mon"),
                            com.pesaflow.app.ui.theme.SegOption("Tue", "Tue"),
                            com.pesaflow.app.ui.theme.SegOption("Wed", "Wed"),
                            com.pesaflow.app.ui.theme.SegOption("Thu", "Thu"),
                            com.pesaflow.app.ui.theme.SegOption("Fri", "Fri"),
                            com.pesaflow.app.ui.theme.SegOption("Sat", "Sat"),
                            com.pesaflow.app.ui.theme.SegOption("Sun", "Sun"),
                            com.pesaflow.app.ui.theme.SegOption("Weekdays", "Weekdays"),
                            com.pesaflow.app.ui.theme.SegOption("Weekends", "Weekends")
                        ),
                        selected = newRuleDay,
                        onSelect = { newRuleDay = it }
                    )
                    Text("Weekdays = Mon–Fri · Weekends = Sat–Sun", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2 · What kind of rule?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Must include", "Include", "➕"),
                            com.pesaflow.app.ui.theme.SegOption("Only source", "Source", "🍳"),
                            com.pesaflow.app.ui.theme.SegOption("Max price", "Max KSh", "💰")
                        ),
                        selected = newRuleKind,
                        onSelect = {
                            newRuleKind = it
                            newRuleValue = when (it) {
                                "Must include" -> "Protein"
                                "Only source" -> "Cook"
                                else -> ""
                            }
                        }
                    )
                    Text(
                        "Include = plate must contain it · Source = cook/buy only · Max = price cap per plate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("3 · Details", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    when (newRuleKind) {
                        "Must include" -> com.pesaflow.app.ui.theme.SegChoice(
                            options = listOf("Starch", "Mboga", "Protein").map {
                                com.pesaflow.app.ui.theme.SegOption(it, it)
                            },
                            selected = newRuleValue,
                            onSelect = { newRuleValue = it }
                        )
                        "Only source" -> com.pesaflow.app.ui.theme.SegChoice(
                            options = listOf(
                                com.pesaflow.app.ui.theme.SegOption("Cook", "Cook", "🍳"),
                                com.pesaflow.app.ui.theme.SegOption("Buy", "Buy", "🍲")
                            ),
                            selected = newRuleValue,
                            onSelect = { newRuleValue = it }
                        )
                        else -> OutlinedTextField(value = newRuleValue, onValueChange = { newRuleValue = it }, label = { Text("Max KSh per plate") }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val dayLabel = when (newRuleDay) {
                        "Any" -> "every day"
                        "Weekdays" -> "weekdays (Mon–Fri)"
                        "Weekends" -> "weekends"
                        else -> newRuleDay
                    }
                    Text(
                        "👉 On $dayLabel, " + when (newRuleKind) {
                            "Must include" -> "every plate gets $newRuleValue."
                            "Only source" -> "$newRuleValue food only."
                            else -> "plates capped at KSh ${newRuleValue.ifBlank { "?" }}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val dupExists = newRuleValue.trim().isNotEmpty() && mealRules.any {
                        it.day == newRuleDay && it.kind == newRuleKind && it.value.equals(newRuleValue.trim(), ignoreCase = true)
                    }
                    Button(
                        onClick = {
                            val v = newRuleValue.trim()
                            val ok = v.isNotEmpty() && (newRuleKind != "Max price" || v.toDoubleOrNull()?.let { it > 0 } == true)
                            if (ok) {
                                saveRules(mealRules + MealRule(newRuleDay, newRuleKind, v))
                                newRuleValue = if (newRuleKind == "Max price") "" else newRuleValue
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        enabled = !dupExists,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(if (dupExists) "Rule exists ✓" else "Add rule", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }

            // Who eats? Curated presets — one tap, whole planner configured.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Who eats? 🍽️", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Auto follows your profile (home/commute/cooking) — currently $effectiveMealPersona. Tap any preset to take manual control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Auto", "Auto", "✨"),
                            com.pesaflow.app.ui.theme.SegOption("Transport", "Transport", "🚌"),
                            com.pesaflow.app.ui.theme.SegOption("Hostel", "Hostel", "🏠"),
                            com.pesaflow.app.ui.theme.SegOption("Tight", "Tight", "🫙"),
                            com.pesaflow.app.ui.theme.SegOption("Full", "Full", "💼")
                        ),
                        selected = if (persona.isBlank()) "Auto" else persona,
                        onSelect = { v ->
                            if (v == "Auto") {
                                persona = "Auto"
                                mealPrefs.edit().putString("meal_persona", "Auto").apply()
                                applyMealControls(effectiveMealPersona)
                            } else applyPersona(v)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Auto-plan hero: one tap, cheapest compliant week, zero tuning.
            // Fine controls live in the Generator card below for tweakers.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Auto-plan my week ⚡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cheapest 7 days from your foods + stock, over-budget days auto-fixed. Tune below if you're picky.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { autoPlanWeek() },
                        enabled = monthlyFood > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Plan my week", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Generator card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Generate Menu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Plates pair starch + mboga (+ protein when it fits). Items rotate so days differ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf("Day", "Week", "Month", "Semester").map {
                            com.pesaflow.app.ui.theme.SegOption(it, it)
                        },
                        selected = selectedPeriod,
                        onSelect = { selectedPeriod = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cook at home or buy ready?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Any", "Any", "🍽️"),
                            com.pesaflow.app.ui.theme.SegOption("Cook", "Cook", "🍳"),
                            com.pesaflow.app.ui.theme.SegOption("Buy", "Buy", "🍲")
                        ),
                        selected = sourceFilter,
                        onSelect = { sourceFilter = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Max per plate?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf("Any", "50", "100", "150", "200").map {
                            com.pesaflow.app.ui.theme.SegOption(it, if (it == "Any") "Any" else "≤$it")
                        },
                        selected = maxPlate,
                        onSelect = { maxPlate = it }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Include?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = includeBreakfast, onClick = { includeBreakfast = !includeBreakfast }, label = { Text("Bfast") })
                        FilterChip(selected = includeLunch, onClick = { includeLunch = !includeLunch }, label = { Text("Lunch") })
                        FilterChip(selected = includeSupper, onClick = { includeSupper = !includeSupper }, label = { Text("Supper") })
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cooking for $people ${if (people == 1) "person" else "people"}", style = MaterialTheme.typography.bodySmall)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(1, 2, 3, 4).map {
                            com.pesaflow.app.ui.theme.SegOption(it.toString(), "$it 👤")
                        },
                        selected = people.toString(),
                        onSelect = { people = it.toInt() }
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Always add protein", style = MaterialTheme.typography.bodySmall)
                            Text("Cheapest protein joins every plate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = forceProtein, onCheckedChange = { forceProtein = it })
                    }
                    run {
                        val cooks = mealItems.filter { it.source == "Cook" }.map { it.price }
                        val buys = mealItems.filter { it.source == "Buy" }.map { it.price }
                        if (cooks.isNotEmpty() && buys.isNotEmpty()) {
                            val diff = ((buys.average() - cooks.average()) * 30).toInt()
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (diff > 0) "🍳 Cooking averages KSh ${cooks.average().toInt()} vs buying KSh ${buys.average().toInt()} — roughly KSh $diff/month less."
                                else "Buying looks cheaper than your cook options right now — check prices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val breakfastReminder = remember { mutableStateOf(mealPrefs.getBoolean("breakfast_reminder", false)) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Breakfast picker at 7:00 daily", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = breakfastReminder.value,
                            onCheckedChange = {
                                breakfastReminder.value = it
                                mealPrefs.edit().putBoolean("breakfast_reminder", it).apply()
                                if (it) ReminderScheduler.scheduleBreakfast(context) else ReminderScheduler.cancelBreakfast(context)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Lunch picker at 12:30 daily", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = lunchReminder,
                            onCheckedChange = {
                                lunchReminder = it
                                mealPrefs.edit().putBoolean("lunch_reminder", it).apply()
                                if (it) ReminderScheduler.scheduleLunch(context) else ReminderScheduler.cancelLunch(context)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { generateMenu() },
                            enabled = monthlyFood > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Generate", color = MaterialTheme.colorScheme.onPrimary) }
                        TextButton(onClick = { seed = (1..1000).random(); generateMenu() }, enabled = mealItems.isNotEmpty() && monthlyFood > 0) { Text("Shuffle") }
                        TextButton(onClick = { fixOverDays() }, enabled = menu.any { it.overBudget }) { Text("Fix over") }
                        OutlinedButton(onClick = { shareMenu() }, enabled = menu.isNotEmpty(), shape = RoundedCornerShape(16.dp)) { Text("Share") }
                        OutlinedButton(
                            onClick = { pdfSaver.launch("pesaflow-${selectedPeriod.lowercase()}-menu.pdf") },
                            enabled = menu.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("PDF") }
                    }
                    if (menu.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            when {
                                mealItems.isEmpty() -> "No foods yet — tap “Add 10 staples pack” above to start in one tap."
                                !includeBreakfast && !includeLunch && !includeSupper -> "All meals switched off — enable at least one above."
                                maxPlate != "Any" -> "Nothing fits ≤KSh $maxPlate — loosen the cap or add cheaper foods."
                                sourceFilter != "Any" -> "No foods tagged “$sourceFilter” — switch sourcing to Any or retag foods."
                                else -> "Tip: add at least a starch and a mboga under an enabled meal first."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Survive till…: stretch your stock to a date, top-ups only for the gap
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Survive till… 🏕️", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your kitchen stock first, cheapest top-ups only for the gap. Counts one staple dinner a day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val monthEndDays = remember {
                        val c = java.util.Calendar.getInstance()
                        c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - c.get(java.util.Calendar.DAY_OF_MONTH) + 1
                    }
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("3", "3d"),
                            com.pesaflow.app.ui.theme.SegOption("7", "7d"),
                            com.pesaflow.app.ui.theme.SegOption("14", "14d"),
                            com.pesaflow.app.ui.theme.SegOption(monthEndDays.toString(), "Month end", "📅")
                        ),
                        selected = survivalDays.toString(),
                        onSelect = { survivalDays = it.toInt(); survivalPlan = null }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val endLabel = remember(survivalDays) {
                        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, survivalDays - 1) }
                        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(c.time)
                    }
                    Text("Target: $endLabel ($survivalDays days)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = survivalCash,
                        onValueChange = { survivalCash = it; survivalPlan = null },
                        label = { Text("Top-up cash (KSh, 0 = stock only)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // One unit: the engine's flexible money fills the top-up —
                    // survival and the snapshot finally read the same wallet.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Engine flexible: KSh ${engineSnap.flexible.toDouble().toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            survivalCash = engineSnap.flexible.toDouble().toInt().toString()
                            survivalPlan = null
                        }) { Text("Use it") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val snapshot = kitchenStock.filter { it.dailyUse > 0 && it.qtyLeft > 0 }
                    Text(
                        if (snapshot.isEmpty()) "No stock tracked — add unga & friends under More → Kitchen Stock first."
                        else "On shelf: " + snapshot.sortedBy { it.qtyLeft / it.dailyUse }.joinToString(", ") {
                            "${it.name} ~${(it.qtyLeft / it.dailyUse).toInt()}d"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // Survival honors your Any-day rules too: filler staples
                            // must pass the same source/price gates as menus.
                            val filler = mealItems.filter { it.source == "Cook" }.filter { item ->
                                val srcOk = mealRules
                                    .filter { it.day == "Any" && it.kind == "Only source" }
                                    .all { it.value == item.source }
                                val capOk = mealRules
                                    .filter { it.day == "Any" && it.kind == "Max price" }
                                    .mapNotNull { it.value.toDoubleOrNull() }
                                    .all { item.price <= it }
                                srcOk && capOk
                            }
                            survivalPlan = planSurvival(
                                kitchenStock,
                                filler,
                                survivalDays,
                                survivalCash.toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = snapshot.isNotEmpty()
                    ) { Text("Plan survival", color = MaterialTheme.colorScheme.onPrimary) }
                    survivalPlan?.let { plan ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            when {
                                plan.noFiller -> "❌ Gap days need buying, but no Cook staples saved — add cheap ones to your foods first."
                                plan.days.all { it.fromStock } -> "✅ Stock alone carries all $survivalDays days. Spend nothing. 💪"
                                plan.possible -> "✅ Covered: stock + KSh ${plan.totalCost.toInt()} top-up (you have KSh ${plan.cash.toInt()})."
                                else -> "⚠️ Short KSh ${plan.shortfall.toInt()} — top-up needs KSh ${plan.totalCost.toInt()}, you have KSh ${plan.cash.toInt()}."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (plan.possible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        plan.days.forEach { d ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${d.label}: ${d.staple}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text(
                                    if (d.fromStock) "stock ✓" else "buy KSh ${d.cost.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (d.fromStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (plan.shopping.isNotEmpty()) {
                            val shapingS = mealRules.filter { it.enabled && it.day == "Any" }
                            if (shapingS.isNotEmpty()) {
                                Text(
                                    "📏 Filler respects: " + shapingS.joinToString("; ") { it.describe() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (mealRules.any { it.enabled && it.day != "Any" }) {
                                Text(
                                    "Day-specific rules rest while surviving — staples don't keep weekdays.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Buy: " + plan.shopping.joinToString(", ") { "${it.first} ×${it.second}" } + " = KSh ${plan.totalCost.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.addManualTransaction(plan.totalCost, TransactionType.EXPENSE, "Food", "Survival top-up ($survivalDays days)", PaymentMethod.CASH)
                                    logMsg = "Logged KSh ${plan.totalCost.toInt()} top-up ✅"
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Log KSh ${plan.totalCost.toInt()} top-up") }
                        }
                    }
                }
            }

            // Today so far (auto): your real Food spending moves this bar — no typing.
            // When a Day menu exists, it also calls out plan-vs-reality.
            run {
                val dayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val ateToday = ledgerTxns
                    .filter {
                        it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE && !it.isSample &&
                            it.category.equals("Food", ignoreCase = true) && it.dateTimestamp >= dayStart
                    }
                    .sumOf { it.amount }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Today so far (auto) ⚡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ate KSh ${ateToday.toInt()} of KSh ${dailyAllowance.toInt()} daily food money",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (ateToday <= dailyAllowance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (ateToday / dailyAllowance).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (ateToday <= dailyAllowance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        if (periodDays == 1 && menu.isNotEmpty()) {
                            val planned = menu.sumOf { it.total }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (ateToday <= planned) "Under today's menu (KSh ${planned.toInt()}) by KSh ${(planned - ateToday).toInt()}. 👌"
                                else "Over today's menu (KSh ${planned.toInt()}) by KSh ${(ateToday - planned).toInt()}. ⚠️",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Menu output: detailed list for a day, tables for longer periods
            if (menu.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "$selectedPeriod Menu",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Total KSh ${menuTotal.toInt()} of KSh ${periodBudget.toInt()} · avg KSh ${(menuTotal / menu.size).toInt()}/day · $overDays day(s) over daily allowance" +
                                if (people > 1) " · ×$people people" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (menuTotal <= periodBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (menuTotal / periodBudget).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (menuTotal <= periodBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        run {
                            val allItems = menu.flatMap { it.items }
                            val unique = allItems.map { it.name }.toSet().size
                            val cookTotal = allItems.filter { it.source == "Cook" }.sumOf { it.price } * people
                            val buyTotal = allItems.filter { it.source == "Buy" }.sumOf { it.price } * people
                            Text(
                                "$unique unique plates · 🍳 Cook KSh ${cookTotal.toInt()} · 🛒 Buy KSh ${buyTotal.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Everything-working-together echo: filters + rules that shaped this menu.
                            val shaping = mealRules.filter { r ->
                                (1..periodDays).any { d -> r.matches(weekdayName(d - 1)) }
                            }
                            val skippedMeals = listOf(
                                if (!includeBreakfast) "Bfast" else null,
                                if (!includeLunch) "Lunch" else null,
                                if (!includeSupper) "Supper" else null
                            ).filterNotNull().joinToString("/")
                            val cookedWith = buildString {
                                append("Cooked with: ")
                                append(if (maxPlate == "Any") "any price" else "max KSh $maxPlate")
                                append(" · $sourceFilter · x$people")
                                if (skippedMeals.isNotEmpty()) append(" · skipping $skippedMeals")
                                if (forceProtein) append(" · protein forced")
                                if (shaping.isNotEmpty()) {
                                    append(" · Rules: ")
                                    append(shaping.joinToString("; ") { it.describe() })
                                }
                            }
                            Text(
                                cookedWith,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.addManualTransaction(menuTotal, TransactionType.EXPENSE, "Food", "Meal plan ($selectedPeriod)", PaymentMethod.CASH)
                                logMsg = "Logged KSh ${menuTotal.toInt()} to spending ✅"
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Log entire menu to spending") }
                        logMsg?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (menu.isNotEmpty() && menuSig != "$sourceFilter|$maxPlate|$includeBreakfast|$includeLunch|$includeSupper|$people|$forceProtein|$selectedPeriod|$seed") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Filters changed since this menu — regenerate 🔄",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (menu.isNotEmpty() && forceProtein && overDays > 0) {
                            Text(
                                "Forced protein is pushing $overDays day(s) over — loosen the cap or unforce it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        run {
                            val tip = menu.flatMap { it.items }.mapNotNull { picked ->
                                mealItems.filter { it.mealType == picked.mealType && it.price < picked.price }
                                    .minByOrNull { it.price }
                                    ?.let { alt -> Triple(picked, alt, picked.price - alt.price) }
                            }.maxByOrNull { it.third }
                            if (tip != null && tip.third >= 10) {
                                Text(
                                    "💡 Swap ${tip.first.name} (${tip.first.price.toInt()}) → ${tip.second.name} (${tip.second.price.toInt()}) to save KSh ${tip.third.toInt()}/day.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Text("Edit a day ✏️", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            menu.forEachIndexed { i, d ->
                                FilterChip(
                                    selected = editingDayIdx == i,
                                    onClick = { editingDayIdx = if (editingDayIdx == i) null else i },
                                    label = { Text(if (periodDays == 1) "Today" else "D${i + 1}·${d.total.toInt()}") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        editingDayIdx?.let { di ->
                            menu.getOrNull(di)?.let { d ->
                                AlertDialog(
                                    onDismissRequest = { editingDayIdx = null },
                                    title = { Text("Edit ${d.label}") },
                                    text = {
                                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                            Text(
                                                "Day total KSh ${d.total.toInt()} (allowance KSh ${effectiveAllowance.toInt()})" +
                                                    if (d.overBudget) " — over ⚠️" else " — fits ✓",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            d.slots.filter { it.value.isNotEmpty() }.forEach { (slot, items) ->
                                                Text(slot, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                items.forEach { item ->
                                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            "• ${item.name} (KSh ${item.price.toInt()})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        TextButton(onClick = { swapItem(di, slot, item) }) { Text("Swap") }
                                                        TextButton(onClick = { removeItem(di, slot, item) }) {
                                                            Text("Remove", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    },
                                    confirmButton = { TextButton(onClick = { editingDayIdx = null }) { Text("Done") } },
                                    dismissButton = {
                                        Row {
                                            TextButton(onClick = { copyDay(di) }) { Text("Copy to all") }
                                            TextButton(onClick = { cheapenDay(di) }) { Text("Cheapen") }
                                        }
                                    }
                                )
                            }
                        }

                        if (periodDays == 1) {
                            // Detailed list layout for a single day
                            menu.forEach { day ->
                                day.slots.filter { it.value.isNotEmpty() }.forEach { (slot, items) ->
                                    Text("$slot — KSh ${(items.sumOf { it.price } * people).toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    items.forEach { item ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("• ${item.name} (${item.component})", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Text("KSh ${item.price.toInt()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Text(
                                    "Day total: KSh ${day.total.toInt()} (allowance KSh ${effectiveAllowance.toInt()}" +
                                        if (people > 1) " shared ×$people" else "" + ")" +
                                        if (day.overBudget) " — over ⚠️" else " — fits ✓",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (day.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addManualTransaction(day.total, TransactionType.EXPENSE, "Food", "Meal plan (${day.label})", PaymentMethod.CASH)
                                        logMsg = "Logged ${day.label} KSh ${day.total.toInt()} ✅"
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Log ${day.label} to spending") }
                            }
                        } else if (selectedPeriod == "Semester") {
                            // Weekly summary table for the semester (★ marks cheapest week)
                            MenuTableHeader(showMeals = false)
                            val weeks = menu.chunked(7)
                            val cheapest = weeks.minOfOrNull { w -> w.sumOf { it.total } }
                            weeks.forEachIndexed { i, week ->
                                val total = week.sumOf { it.total }
                                MenuDataRow(
                                    cells = listOf(if (cheapest != null && total == cheapest && weeks.size > 1) "★ Wk ${i + 1}" else "Wk ${i + 1}", "${week.size}d", "—", "—", "KSh ${total.toInt()}"),
                                    highlight = total > effectiveAllowance * week.size,
                                    zebra = i % 2 == 1
                                )
                            }
                        } else {
                            // Day-by-day table for week/month
                            MenuTableHeader(showMeals = true)
                            menu.forEachIndexed { i, day ->
                                fun short(slot: String): String {
                                    val items = day.slots[slot].orEmpty()
                                    return if (items.isEmpty()) "—" else items.joinToString("+") { it.name.take(8) }
                                }
                                MenuDataRow(
                                    cells = listOf(day.label.replace("Day ", "D"), short("Breakfast"), short("Lunch"), short("Supper"), "${day.total.toInt()}"),
                                    highlight = day.overBudget,
                                    zebra = i % 2 == 1
                                )
                            }
                        }
                    }
                }
            }
            // Shared lecture-week revision: OCR confirm, manual grid, and tips below all read this.
            var weekTick by remember { mutableStateOf(0) }
            var weekSavedMsg by remember { mutableStateOf<String?>(null) }
            // Timetable import: PDF or photo → on-device OCR → suggestions you confirm.
            // Manual grid below stays the source of truth; this just pre-ticks it.
            run {
                var ocrBusy by remember { mutableStateOf(false) }
                var ocrError by remember { mutableStateOf<String?>(null) }
                var ocrSuggestion by remember { mutableStateOf<Map<String, Set<String>>?>(null) }
                fun sampledBitmap(ins: java.io.InputStream, cap: Int): android.graphics.Bitmap? {
                    val bytes = ins.readBytes()
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var s = 1
                    while ((bounds.outWidth / s) > cap || (bounds.outHeight / s) > cap) s *= 2
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
                    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
                fun runOcr(bmp: android.graphics.Bitmap) {
                    ocrBusy = true
                    ocrError = null
                    try {
                        val client = com.google.mlkit.vision.text.TextRecognition.getClient(
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.Builder().build()
                        )
                        client.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0))
                            .addOnSuccessListener { visionText ->
                                val sug = WeekPlan.suggestFromText(visionText.text)
                                ocrSuggestion = sug
                                if (sug.isEmpty()) ocrError = "Couldn't read times — hold it straight, or tick manually below. 🙂"
                                ocrBusy = false
                            }
                            .addOnFailureListener {
                                ocrError = "Reader unavailable (needs Play Services once) — tick manually below. 🙂"
                                ocrBusy = false
                            }
                    } catch (e: Exception) {
                        ocrError = "Reader unavailable — tick manually below. 🙂"
                        ocrBusy = false
                    }
                }
                val pdfPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                            val renderer = android.graphics.pdf.PdfRenderer(fd)
                            try {
                                if (renderer.pageCount > 0) {
                                    val page = renderer.openPage(0)
                                    try {
                                        val bmp = android.graphics.Bitmap.createBitmap(
                                            page.width * 2, page.height * 2,
                                            android.graphics.Bitmap.Config.ARGB_8888
                                        )
                                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        runOcr(bmp)
                                    } finally {
                                        page.close()
                                    }
                                } else {
                                    ocrError = "That PDF looks empty. 🙂"
                                }
                            } finally {
                                renderer.close()
                            }
                        } ?: run { ocrError = "Couldn't open that PDF. 🙂" }
                    } catch (e: Exception) {
                        ocrError = "Couldn't read that PDF — photo or manual works too. 🙂"
                    }
                }
                val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    try {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            val bmp = sampledBitmap(ins, 1600)
                            if (bmp != null) runOcr(bmp)
                            else ocrError = "Couldn't read that photo. 🙂"
                        }
                    } catch (e: Exception) {
                        ocrError = "Couldn't read that photo. 🙂"
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Import timetable 📄🖼️", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Snap it or pick the PDF — I'll pre-tick what I read, you confirm. Guide, not law.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) { Text("PDF") }
                            OutlinedButton(onClick = { photoPicker.launch("image/*") }) { Text("Photo") }
                        }
                        if (ocrBusy) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Reading… 🔍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ocrError?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        ocrSuggestion?.let { sug ->
                            if (sug.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "I read: " + sug.entries.joinToString("; ") { e -> "${e.key} ${e.value.sorted().joinToString("/")}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { WeekPlan.apply(context, sug); ocrSuggestion = null; weekTick++; weekSavedMsg = "Timetable applied ✓ — check the grid below." }) { Text("Use suggestions ✅") }
                                    TextButton(onClick = { ocrSuggestion = null }) { Text("Discard") }
                                }
                            }
                        }
                    }
                }
            }
            // Lecture week: tick busy slots once — menus, suggestions and Buddy read it.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("My lecture week 🗓️", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Morn = morning lectures, Aft = afternoon, Eve = evening. Busy lunch → heavy supper. Guide, not law — tick roughly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    key(weekTick) {
                        val week = WeekPlan.load(context)
                        WeekPlan.DAYS.forEach { d ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(d, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                                WeekPlan.SLOTS.forEach { s ->
                                    FilterChip(
                                        selected = week[d].orEmpty().contains(s),
                                        onClick = { WeekPlan.toggle(context, d, s); weekTick++; weekSavedMsg = null },
                                        label = { Text(if (s == WeekPlan.MORNING) "Morn" else if (s == WeekPlan.AFTERNOON) "Aft" else "Eve") }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    weekSavedMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Button(
                        onClick = {
                            weekTick++
                            val w = WeekPlan.load(context)
                            val busy = w.filterValues { it.isNotEmpty() }
                            weekSavedMsg = if (busy.isEmpty()) "Saved ✓ — all days free."
                            else "Saved ✓ — " + busy.entries.joinToString("; ") { e -> "${e.key} ${e.value.sorted().joinToString("/")}" }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Save week") }
                }
            }
            // Timetable-aware suggestions: heavy meals dodge busy slots.
            key(weekTick) {
                val week = WeekPlan.load(context)
                val tips = menu.mapIndexedNotNull { i, _ ->
                    val wd = weekdayName(i)
                    WeekPlan.suggestionFor(wd, week[wd].orEmpty())
                }.distinct()
                if (menu.isNotEmpty() && tips.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Around your timetable 🗓️", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
            // Shopping list aggregated from the generated menu
            if (menu.isNotEmpty()) {
                val groceries = menu.flatMap { it.items }
                    .groupBy { Triple(it.name, it.price, it.source) }
                    .map { (key, list) -> Triple(key, list.size * people, key.second * list.size * people) }
                    .sortedByDescending { it.second }
                if (groceries.isNotEmpty()) {
                    val bought = checkedGroceries.size
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Shopping List 🛒", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${groceries.size} items · est. KSh ${(groceries.sumOf { it.third } * portionScale).toInt()}" +
                                    if (bought > 0) " · $bought ticked" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Portions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            com.pesaflow.app.ui.theme.SegChoice(
                                options = listOf(
                                    com.pesaflow.app.ui.theme.SegOption("0.25", "¼"),
                                    com.pesaflow.app.ui.theme.SegOption("0.5", "½"),
                                    com.pesaflow.app.ui.theme.SegOption("0.75", "¾"),
                                    com.pesaflow.app.ui.theme.SegOption("1.0", "1×")
                                ),
                                selected = portionScale.toString(),
                                onSelect = { portionScale = it.toDouble() }
                            )
                            Text(
                                "Quarters stretch unga, mchele, cabbage across lunch + supper — estimate scales with portions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            run {
                                val est = (groceries.sumOf { it.third } * portionScale).toInt()
                                Text(
                                    if (est <= periodBudget) "Fits your KSh ${periodBudget.toInt()} food money ✅"
                                    else "KSh ${est - periodBudget.toInt()} over your KSh ${periodBudget.toInt()} food money ⚠️",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (est <= periodBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("Cook", "Buy").forEach { src ->
                                val group = groceries.filter { it.first.third == src }
                                if (group.isNotEmpty()) {
                                    Text(
                                        if (src == "Cook") "🍳 To cook" else "🛒 Ready-made",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    group.forEach { (key, count, total) ->
                                        val id = "${key.first}|${key.second}"
                                        val checked = id in checkedGroceries
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = {
                                                    checkedGroceries = if (it) checkedGroceries + id else checkedGroceries - id
                                                }
                                            )
                                            Text(
                                                "${key.first} ×$count",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("KSh ${(total * portionScale).toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
    }


private fun buildMenuPdf(
    menu: List<PlannedDay>,
    periodLabel: String,
    monthlyFood: Double,
    dailyAllowance: Double,
    periodBudget: Double,
    people: Int
): android.graphics.pdf.PdfDocument {
    val doc = android.graphics.pdf.PdfDocument()
    val teal = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x0B, 0x3D, 0x2E) }
    val lightTeal = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xD9, 0xF2, 0xE3) }
    val title = android.graphics.Paint().apply { textSize = 24f; isFakeBoldText = true; color = android.graphics.Color.WHITE }
    val subtitle = android.graphics.Paint().apply { textSize = 13f; color = android.graphics.Color.WHITE }
    val head = android.graphics.Paint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.rgb(0x0B, 0x3D, 0x2E) }
    val body = android.graphics.Paint().apply { textSize = 12f; color = android.graphics.Color.BLACK }
    val small = android.graphics.Paint().apply { textSize = 10f; color = android.graphics.Color.rgb(0x66, 0x66, 0x66) }
    val green = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x1B, 0x7A, 0x4D) }
    val red = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xC6, 0x28, 0x28) }
    val barTrack = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0xE0, 0xE0, 0xE0) }

    var pageNo = 0
    lateinit var page: android.graphics.pdf.PdfDocument.Page
    var c: android.graphics.Canvas? = null
    var y = 0f
    fun newPage() {
        if (pageNo > 0) doc.finishPage(page)
        pageNo++
        page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        c = page.canvas
        y = 56f
    }
    fun need(h: Float) {
        if (y + h > 800) newPage()
    }
    fun line(s: String, paint: android.graphics.Paint = body, gap: Float = 20f, indent: Float = 40f) {
        need(gap)
        c?.drawText(s.take(88), indent, y, paint)
        y += gap
    }

    newPage()
    // Banner with title + budget line
    c?.drawRect(0f, 0f, 595f, 150f, teal)
    y = 52f
    c?.drawText("PesaFlow $periodLabel Menu", 40f, y, title)
    y += 26f
    c?.drawText(
        "KSh ${monthlyFood.toInt()}/month = KSh ${dailyAllowance.toInt()}/day" + if (people > 1) " (x$people people)" else "",
        40f, y, subtitle
    )
    y += 26f
    c?.drawText("Total KSh ${menu.sumOf { it.total }.toInt()} of KSh ${periodBudget.toInt()}", 40f, y, subtitle)
    y = 178f
    // Budget bar (the "image": proportional visual, no typing needed to read)
    val total = menu.sumOf { it.total }
    val frac = if (periodBudget > 0) (total / periodBudget).toFloat().coerceIn(0f, 1f) else 0f
    c?.drawRect(40f, y, 555f, y + 18f, barTrack)
    c?.drawRect(40f, y, 40f + 515f * frac, y + 18f, if (total <= periodBudget) green else red)
    y += 38f

    menu.forEach { d ->
        need(28f)
        // Day bullet: green fits, red over
        c?.drawCircle(48f, y - 4f, 5f, if (d.overBudget) red else green)
        val over = d.slots.values.flatten().size
        c?.drawText(
            "${d.label} -- KSh ${d.total.toInt()}${if (d.overBudget) "  OVER" else ""}",
            60f, y, head
        )
        y += 24f
        d.slots.filter { it.value.isNotEmpty() }.forEach { (slot, items) ->
            line("$slot: " + items.joinToString(" + ") { "${it.name} (${it.price.toInt()})" }, small, 18f, 60f)
        }
        y += 8f
        if (over == 0) {
            line("(no plates — see app to fix)", small, 18f, 60f)
            y += 8f
        }
    }

    y += 10f
    need(24f)
    c?.drawText("Generated by PesaFlow Meal Planner", 40f, y, small)
    doc.finishPage(page)
    return doc
}


@Composable
private fun MenuTableHeader(showMeals: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text("Day", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(0.7f))
        if (showMeals) {
            Text("Bfast", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1.2f))
            Text("Lunch", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1.2f))
            Text("Supper", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(1.2f))
        } else {
            Text("Days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(0.7f))
            Text("", modifier = Modifier.weight(1.2f))
            Text("", modifier = Modifier.weight(1.2f))
        }
        Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
    }
    Spacer(modifier = Modifier.height(4.dp))
}


@Composable
private fun MenuDataRow(cells: List<String>, highlight: Boolean, zebra: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (zebra) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val weights = listOf(0.7f, 1.2f, 1.2f, 1.2f, 0.9f)
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                style = MaterialTheme.typography.bodySmall,
                color = if (i == cells.lastIndex && highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (i == cells.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                textAlign = if (i == cells.lastIndex) TextAlign.End else TextAlign.Start,
                maxLines = 1
            )
        }
    }
}
