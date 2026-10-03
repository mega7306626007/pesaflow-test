package com.pesaflow.app.ui.stock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.stockDaysLeft
import com.pesaflow.app.data.models.stockRefillCost
import com.pesaflow.app.data.models.stockReplenishDate
import com.pesaflow.app.R
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintKitchenEmber


private val PERISHABLE_PRESETS = listOf(
    Triple("Cabbage", "pcs", 7),
    Triple("Nyanya", "kg", 4),
    Triple("Sukuma wiki", "pcs", 5),
    Triple("Milk", "L", 3),
    Triple("Eggs", "pcs", 14),
    Triple("Bread", "pack", 5)
)

private val STOCK_UNITS = listOf("kg", "L", "pcs", "pack")


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KitchenScreen(viewModel: FinanceViewModel) {
    val stock by viewModel.kitchenStock.collectAsState()
    val myFoods by viewModel.mealItems.collectAsState()

    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var packSize by remember { mutableStateOf("") }
    var leftNow by remember { mutableStateOf("") }
    var dailyUse by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var expiryDays by remember { mutableStateOf("") }
    var eatPriority by remember { mutableStateOf(0) }
    // Proof ticks: brief ✓ that reverts after 2s so actions stay tappable.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }

    val ranked = stock.sortedBy { stockDaysLeft(it) }
    val urgent = ranked.filter { stockDaysLeft(it) <= 3 }
    val refillAll = stock.sumOf { stockRefillCost(it) }
    val dateFmt = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintKitchenEmber, bgRes = R.drawable.bg_kitchen_ember)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Kitchen Stock 🫙", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
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
                workspace = AtmoWorkspace.KITCHEN,
                title = "Cupboard",
                subtitle = "Levels · refills · cost"
            )
            // Summary: what runs out, what refilling everything costs
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        if (stock.isEmpty()) "Empty cupboard — add your unga, oil and friends below."
                        else if (urgent.isEmpty()) "${stock.size} items tracked · all stocked ✅ · refill-all KSh ${refillAll.toInt()}"
                        else "⚠️ ${urgent.size} running out (≤3 days): ${urgent.joinToString(", ") { it.name }} · refill-all KSh ${refillAll.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            GasCard(dateFmt = dateFmt)

            // Perishables: expiry reminders + eat-first priority for the menu generator
            val nowMs = System.currentTimeMillis()
            val perishables = stock.filter { it.expiryTimestamp > 0L }.sortedBy { it.expiryTimestamp }
            val expiringSoon = perishables.filter { it.expiryTimestamp - nowMs <= 2 * 24L * 60 * 60 * 1000 }
            if (perishables.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (expiringSoon.isEmpty()) "Perishables fresh (${perishables.size}) - eat-first order kept"
                            else "Eat first: " + expiringSoon.joinToString(", ") { it.name } + " running out",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (expiringSoon.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        perishables.forEach { item ->
                            val leftDays = ((item.expiryTimestamp - nowMs) / (24L * 60 * 60 * 1000)).toInt()
                            val lvl = (item.qtyLeft / item.qtyFull).toFloat().coerceIn(0f, 1f)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.name + if (item.eatByDays > 0) " - eat in " + item.eatByDays + "d" else "",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        if (leftDays < 0) "Expired " + (-leftDays) + "d ago - throw it out" else if (leftDays == 0) "Eat today" else "Expires in " + leftDays + "d",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (leftDays <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { lvl },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (leftDays <= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = item.eatByDays == 0, onClick = { viewModel.setStockPriority(item, 0) }, label = { Text("No rush") })
                                FilterChip(selected = item.eatByDays == 1, onClick = { viewModel.setStockPriority(item, 1) }, label = { Text("Eat in 1 day") })
                                FilterChip(selected = item.eatByDays == 5, onClick = { viewModel.setStockPriority(item, 5) }, label = { Text("Eat in 5 days") })
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
            Text("Quick add perishable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PERISHABLE_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = name == preset.first,
                        onClick = {
                            name = preset.first
                            unit = preset.second
                            packSize = "1"
                            leftNow = "1"
                            expiryDays = preset.third.toString()
                            eatPriority = if (preset.third <= 4) 1 else 5
                        },
                        label = { Text(preset.first + " " + preset.third + "d") }
                    )
                }
            }
            // Add form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Add stock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("What? (e.g. Unga)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STOCK_UNITS.forEach { u ->
                            FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = packSize, onValueChange = { packSize = it }, label = { Text("Pack size you buy ($unit)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = leftNow, onValueChange = { leftNow = it }, label = { Text("Left on shelf now ($unit)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = dailyUse, onValueChange = { dailyUse = it }, label = { Text("Used per cooking day ($unit)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price per pack from your shop (KSh)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = expiryDays, onValueChange = { expiryDays = it }, label = { Text("Good for (days, empty = not perishable)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Eat-first priority", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = eatPriority == 0, onClick = { eatPriority = 0 }, label = { Text("No rush") })
                        FilterChip(selected = eatPriority == 1, onClick = { eatPriority = 1 }, label = { Text("Eat in 1 day") })
                        FilterChip(selected = eatPriority == 5, onClick = { eatPriority = 5 }, label = { Text("Eat in 5 days") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val full = packSize.toDoubleOrNull()
                            val left = leftNow.toDoubleOrNull()
                            if (name.isNotBlank() && full != null && full > 0 && left != null && left >= 0) {
                                viewModel.addKitchenStock(
                                    name, unit, full, left.coerceAtMost(full),
                                    dailyUse.toDoubleOrNull()?.takeIf { it > 0 } ?: (full / 14),
                                    price.toDoubleOrNull() ?: 0.0,
                                    expiryTimestamp = expiryDays.toDoubleOrNull()?.takeIf { it > 0 }?.let { System.currentTimeMillis() + (it * 24 * 60 * 60 * 1000).toLong() } ?: 0L,
                                    eatByDays = eatPriority
                                )
                                name = ""; packSize = ""; leftNow = ""; dailyUse = ""; price = ""; expiryDays = ""; eatPriority = 0
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Add", color = MaterialTheme.colorScheme.onPrimary) }
                }
            }

            // Usage flow: who burns fastest, what today costs — bars move with every log.
            if (ranked.isNotEmpty()) {
                val burners = ranked.filter { it.dailyUse > 0 }.sortedByDescending { it.dailyUse }
                val maxBurn = burners.firstOrNull()?.dailyUse?.takeIf { it > 0 } ?: 1.0
                val todayCost = ranked.sumOf { b ->
                    if (b.qtyFull > 0 && b.pricePerPack > 0) b.pricePerPack / b.qtyFull * b.dailyUse else 0.0
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Daily burn 🔥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Cupboard costs ~KSh ${todayCost.toInt()}/day at current pace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        burners.take(5).forEach { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(b.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text(
                                    "~${trimNum(b.dailyUse)} ${b.unit}/day",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (b.dailyUse / maxBurn).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // Stock rows: how long, when to replenish, what it takes
            if (ranked.isNotEmpty()) {
                Text("Cupboard (${ranked.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                ranked.forEach { item ->
                    var topUpQty by remember(item.id) { mutableStateOf("1") }
                    var purchaseMethod by remember(item.id) { mutableStateOf(PaymentMethod.CASH) }
                    var purchaseError by remember(item.id) { mutableStateOf<String?>(null) }
                    var purchasing by remember(item.id) { mutableStateOf(false) }
                    val days = stockDaysLeft(item)
                    val cost = stockRefillCost(item)
                    val refillOn = dateFmt.format(java.util.Date(stockReplenishDate(item)))
                    val levelFrac = (item.qtyLeft / item.qtyFull).toFloat().coerceIn(0f, 1f)
                    val animatedFrac by animateFloatAsState(targetValue = levelFrac, label = "stock-${item.id}")
                    val urgencyColor = when {
                        days <= 2 -> MaterialTheme.colorScheme.error
                        days <= 5 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "${trimNum(item.qtyLeft)} / ${trimNum(item.qtyFull)} ${item.unit} · ~${trimNum(item.dailyUse)} ${item.unit}/day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.deleteKitchenStock(item.id) }) {
                                    Text("Drop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { animatedFrac },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = urgencyColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${(levelFrac * 100).toInt()}% left · " +
                                if (days >= 3650) "${item.name} barely runs out — no daily use set."
                                else "Lasts ~${days.toInt()} day(s) · replenish by $refillOn · refill ≈ KSh ${cost.toInt()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = urgencyColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Make it reality: buy ${trimNum(item.qtyFull - item.qtyLeft)} ${item.unit} before $refillOn (≈KSh ${cost.toInt()}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = topUpQty,
                                onValueChange = {
                                    topUpQty = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8)
                                    purchaseError = null
                                },
                                label = { Text("Top up quantity (${item.unit})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val purchaseQuantity = topUpQty.toDoubleOrNull()
                            val purchaseCost = purchaseQuantity?.let { com.pesaflow.app.data.models.stockTopUpCost(item, it) }
                            Text(
                                purchaseCost?.let { "Estimated purchase: KSh ${it.toInt()} · deducted from Food spending" }
                                    ?: "Set a valid pack size and shop price above to calculate and log a top-up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    PaymentMethod.CASH to "Cash",
                                    PaymentMethod.MPESA to "M-Pesa",
                                    PaymentMethod.BANK_TRANSFER to "Bank"
                                ).forEach { (method, label) ->
                                    FilterChip(
                                        selected = purchaseMethod == method,
                                        onClick = { purchaseMethod = method },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            purchaseError?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Fractional use: a quarter cabbage for supper, half unga for lunch —
                            // logStockUse already takes fractions, now the UI offers them.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Use:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾").forEach { (f, label) ->
                                    OutlinedButton(
                                        onClick = { viewModel.logStockUse(item, f); ack("${item.id}:frac$f") },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text(if ("${item.id}:frac$f" in acked) "✓" else label)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.logStockUse(item); ack("${item.id}:cook") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if ("${item.id}:cook" in acked) "Cooked ✓" else "Full day 🍳")
                                }
                                // Cupboard → planner loop: missing foods join the catalog
                                // so future menus and shopping lists include them.
                                if (myFoods.none { it.name.equals(item.name, ignoreCase = true) }) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.addMealItem(item.name, "Lunch", item.pricePerPack, "Complete", "Buy")
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Add to foods") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Cupboard → planner loop: missing foods join the catalog
                                // so future menus and shopping lists include them.
                                if (myFoods.none { it.name.equals(item.name, ignoreCase = true) }) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.addMealItem(item.name, "Lunch", item.pricePerPack, "Complete", "Buy")
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Add to foods") }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.restockKitchen(item); ack("${item.id}:restock") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if ("${item.id}:restock" in acked) "Shelf reset ✓" else "Set shelf to one pack (no purchase)")
                                }
                                Button(
                                    onClick = {
                                        purchaseQuantity?.let { quantity ->
                                            purchasing = true
                                            viewModel.purchaseKitchenStock(item, quantity, purchaseMethod) { error ->
                                                purchasing = false
                                                purchaseError = error
                                                if (error == null) {
                                                    topUpQty = "1"
                                                    ack("${item.id}:topup")
                                                }
                                            }
                                        }
                                    },
                                    enabled = purchaseCost != null && !purchasing,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if ("${item.id}:topup" in acked) "Added + logged ✓" else "Buy + add stock")
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


private fun trimNum(v: Double): String {
    val i = v.toInt()
    return if (v == i.toDouble()) "$i" else String.format(java.util.Locale.US, "%.1f", v)
}


// Gas tracker: research-backed LPG math, campus edition.
// A 6kg cylinder holds ≈40 burner-hours; a comrade cooks ≈1.3h/day → ~30 days.
// days = kg × 6.7 / dailyHours. Progress recedes with days since refill —
// refill ETA and a cook-smart tip ride along.
private const val GAS_HOURS_PER_KG = 6.7

private val GAS_TIPS = listOf(
    "Soak githeri/ndengu overnight — cooks 30–50% faster. 🫘",
    "Lid on, always — traps heat, saves 20–25% gas. 🍲",
    "Cook once, eat twice — supper doubles as tomorrow's lunch. 🍱",
    "Prep everything BEFORE lighting — chopping while burning wastes gas. 🔪",
    "Medium flame cooks the same — high flame just heats the room. 🔥",
    "Small sufuria, small burner — big burner under a small pot wastes gas. 🫕",
    "Rice/ugali finish on residual heat — switch off 2–3 min early. ♨️",
    "Bulk-cook beans once a week, reheat portions. 🫘",
    "One cooking round, not five reheats — plan tea + meals together. ☕",
    "Clean burners monthly — blocked jets drink gas. 🧹"
)


@Composable
private fun GasCard(dateFmt: java.text.SimpleDateFormat) {
    val context = LocalContext.current
    val gasPrefs = remember { context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    var profile by remember { mutableStateOf(gasPrefs.getString("gas_profile", null)) }
    var editing by remember { mutableStateOf(profile == null) }
    val dayMs = 24L * 60 * 60 * 1000
    val now = System.currentTimeMillis()

    fun save(size: Double, boughtAt: Long, hours: Double) {
        val v = "$size|$boughtAt|$hours"
        gasPrefs.edit().putString("gas_profile", v).apply()
        profile = v
        editing = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Cooking gas 🔥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val parts = profile?.split("|")
            val size = parts?.getOrNull(0)?.toDoubleOrNull()
            val boughtAt = parts?.getOrNull(1)?.toLongOrNull()
            val hours = parts?.getOrNull(2)?.toDoubleOrNull()?.takeIf { it > 0 }
            if (editing || size == null || boughtAt == null || hours == null) {
                GasSetupForm(onSave = ::save)
            } else {
                val expected = size * GAS_HOURS_PER_KG / hours
                val elapsed = ((now - boughtAt) / dayMs).toDouble().coerceAtLeast(0.0)
                val fracLeft = (1 - elapsed / expected).coerceIn(0.0, 1.0)
                val eta = boughtAt + (expected * dayMs).toLong()
                val daysLeft = (expected - elapsed).coerceAtLeast(0.0)
                val urgency = when {
                    fracLeft <= 0.15 -> MaterialTheme.colorScheme.error
                    fracLeft <= 0.35 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                val animated by animateFloatAsState(targetValue = fracLeft.toFloat(), label = "gas-level")
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = urgency,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${(fracLeft * 100).toInt()}% left · ~${daysLeft.toInt()} day(s) · refill by ${dateFmt.format(java.util.Date(eta))} · ${trimNum(size)}kg @ ${trimNum(hours)}h/day",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = urgency
                )
                Spacer(modifier = Modifier.height(4.dp))
                val tip = GAS_TIPS[(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) % GAS_TIPS.size]
                Text("💡 $tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { save(size, now, hours) },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Refilled today") }
                    OutlinedButton(
                        onClick = { editing = true },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Adjust") }
                }
            }
        }
    }
}


@Composable
private fun GasSetupForm(onSave: (Double, Long, Double) -> Unit) {
    var size by remember { mutableStateOf(6.0) }
    var hours by remember { mutableStateOf(1.3) }
    var daysAgo by remember { mutableStateOf("0") }
    val dayMs = 24L * 60 * 60 * 1000
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "How heavy is the cylinder, when did it land, how long do you cook daily? The bar recedes from the bought date — refill date predicts itself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3.0 to "3kg", 6.0 to "6kg", 13.0 to "13kg").forEach { (v, label) ->
                FilterChip(selected = size == v, onClick = { size = v }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5 to "Light ½h", 1.0 to "1h", 1.3 to "Avg 1.3h", 2.0 to "Heavy 2h").forEach { (v, label) ->
                FilterChip(selected = hours == v, onClick = { hours = v }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = daysAgo,
            onValueChange = { daysAgo = it },
            label = { Text("Bought how many days ago? (0 = today)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val ago = daysAgo.toIntOrNull()?.coerceAtLeast(0) ?: 0
                onSave(size, System.currentTimeMillis() - ago * dayMs, hours)
            },
            shape = RoundedCornerShape(12.dp)
        ) { Text("Track my gas") }
        Text(
            "6kg ≈ 30 days at comrade pace (1.3h/day). Light cooking stretches past 2 months.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
