package com.pesaflow.app.ui.semester

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.SectionHeader
import com.pesaflow.app.ui.university.UniversityFinancialPlanner
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.CinematicBackdrop
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.pesaflow.app.data.schedule.WeekPlan
import com.pesaflow.app.ui.theme.TintSemesterGold
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(viewModel: FinanceViewModel, onNavigate: (String) -> Unit = {}) {
    val profile by viewModel.universityProfile.collectAsState()
    val financialSnapshot by viewModel.financialSnapshot.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val mealItems by viewModel.mealItems.collectAsState()
    val context = LocalContext.current
    var roommates by remember { mutableStateOf(2) }
    var fare by remember { mutableStateOf(context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE).getString("school_fare_one_way", "").orEmpty()) }
    var commuteDays by remember { mutableStateOf("5") }
    var daysTouched by remember { mutableStateOf(false) }
    // Timetable truth: class days drive the commute count; per-day first/last
    // hours drive the peak verdict. Asked here, editable here, grid-compatible.
    var classTimes by remember { mutableStateOf(WeekPlan.loadTimes(context)) }
    val weekGrid = remember { WeekPlan.load(context) }
    val commuteDayList = remember(weekGrid, classTimes) { WeekPlan.commuteDays(weekGrid, classTimes) }
    LaunchedEffect(commuteDayList) {
        if (!daysTouched) commuteDays = commuteDayList.size.toString()
    }
    // Autofill from the Transport budget already set (onboarding or scan) —
    // the daily-fare question must never arrive with a blank box twice.
    val transportMonthly = budgets.firstOrNull { it.category == "Transport" && it.type == BudgetType.MONTHLY }?.limitAmount
    LaunchedEffect(transportMonthly) {
        if (fare.isBlank() && (transportMonthly ?: 0.0) > 0) {
            val days = commuteDays.toIntOrNull()?.takeIf { it > 0 } ?: 5
            fare = (transportMonthly!! / 2 / days / 4.33).toInt().toString()
        }
    }
    var showChamaDialog by remember { mutableStateOf(false) }
    val chamas by viewModel.chamaGroups.collectAsState()
    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val doc = buildSemesterPdf(transactions, budgets, profile)
                    try {
                        doc.writeTo(out)
                    } finally {
                        doc.close()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "PDF failed — check storage and retry.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    // Honest countdown: no fabricated +120d fallback. Unset dates say so
    // with a one-tap fix instead of a confident fake number.
    val endSet = (profile?.semesterEndTimestamp ?: 0L) > now
    val end = profile?.semesterEndTimestamp?.takeIf { it > now } ?: now
    val daysLeft = ((end - now) / day).coerceAtLeast(0)
    val openBills = bills.filter { it.status != "PAID" }
    val openDebts = debts.filter { it.status != "PAID" }
    val rentBills = openBills.filter {
        it.category.equals("Rent", ignoreCase = true) ||
            it.name.contains("rent", ignoreCase = true) ||
            it.name.contains("hostel", ignoreCase = true)
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSemesterGold, bgRes = R.drawable.bg_semester_gold)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Semester 🎓", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.OASIS,
                title = "Semester oasis",
                subtitle = "Funding · runway · fees countdown"
            )
            // Countdown hero
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Semester Countdown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!endSet) {
                        Text(
                            "No dates set",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { onNavigate(NavRoutes.UNIVERSITY) }) { Text("Set semester dates →") }
                    } else {
                        Text(
                            "$daysLeft days left",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        profile?.universityName?.takeIf { it.isNotBlank() }?.let { "$it · Semester ${profile?.currentSemester ?: 1}" } ?: "Set your university under More → University",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { pdfSaver.launch("pesaplanner-semester.pdf") }) { Text("Semester statement (PDF)") }
                }
            }

            // Fees countdown (HELB vs fees owed)
            run {
                val p = profile
                if ((p?.feesAmount ?: 0.0) > 0) {
                    val daysToFees = ((p!!.feesDueDate - now) / day).coerceAtLeast(0)
                    val cover = if (p.helbExpected > 0) (p.helbExpected / p.feesAmount * 100).toInt() else 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Fees Countdown 🎓", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "KSh ${p.feesAmount.toInt()} due in $daysToFees days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (daysToFees <= 14) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (p.fundingSource == "SELF") "Self-sponsored — pocket + logged income carry the fees. 💪"
                                else if (p.helbExpected > 0) "HELB expected KSh ${p.helbExpected.toInt()} covers $cover% of it."
                                else "Set HELB expectation in onboarding or University profile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (p.fundingSource != "SELF" && p.helbExpected > 0) {
                                val afterFees = p.helbExpected - p.feesAmount
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (afterFees >= 0) "After fees: KSh ${afterFees.toInt()} of HELB stays for upkeep. 🍲"
                                    else "Fees exceed HELB by KSh ${(-afterFees).toInt()} — plan the gap early, don't borrow it. 🙂",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (afterFees >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }


            // Live planner (reused, same math everywhere)
            UniversityFinancialPlanner(
                profile = profile,
                transactions = transactions,
                committed = financialSnapshot.committed.toDouble(),
                viewModel = viewModel
            )

            // Rent / hostel split
            if (rentBills.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Rent Split 🏠", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        rentBills.forEach { bill ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bill.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "KSh ${bill.amount.toInt()} ÷ $roommates = KSh ${(bill.amount / roommates).toInt()} each",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { roommates = (roommates - 1).coerceAtLeast(1) }) { Text("−") }
                                    Text("$roommates", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { roommates = (roommates + 1).coerceAtMost(8) }) { Text("+") }
                                }
                            }
                        }
                    }
                }
            }

            // Matatu preset: fare × commute days → Transport budget
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Matatu Preset 🚌", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Timetable school run: class days from the grid, first/last
                    // lecture per day asked + editable, peak verdict follows.
                    Text(
                        "Class days: " + commuteDayList.joinToString(" · ") + " (from timetable)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    commuteDayList.forEach { d ->
                        val t = classTimes[d]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = t?.first?.toString() ?: "",
                                onValueChange = { v ->
                                    val f = v.toIntOrNull()
                                    val next = classTimes.toMutableMap()
                                    if (v.isBlank()) next.remove(d)
                                    else if (f != null) next[d] = f to (next[d]?.second ?: (f + 8).coerceAtMost(22))
                                    classTimes = next
                                    WeekPlan.saveTimes(context, next)
                                },
                                label = { Text("First") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = t?.second?.toString() ?: "",
                                onValueChange = { v ->
                                    val l = v.toIntOrNull()
                                    val next = classTimes.toMutableMap()
                                    if (v.isBlank()) next.remove(d)
                                    else if (l != null) {
                                        val f = next[d]?.first ?: (l - 8).coerceAtLeast(5)
                                        next[d] = f to l
                                    }
                                    classTimes = next
                                    WeekPlan.saveTimes(context, next)
                                },
                                label = { Text("Last") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    val earliestFirst = classTimes.values.minOfOrNull { it.first }
                    val latestLast = classTimes.values.maxOfOrNull { it.second }
                    WeekPlan.peakNote(earliestFirst, latestLast)?.let { note ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fare,
                            onValueChange = { fare = it },
                            label = { Text("Fare one-way") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = commuteDays,
                            onValueChange = { commuteDays = it; daysTouched = true },
                            label = { Text("Days/wk") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // One rule for commute math: the tested Journey engine
                    // (return fare × days), not a second inline formula.
                    val commuteJourney = remember(fare, commuteDays) {
                        com.pesaflow.app.data.finance.Journey(
                            id = "semester-commute", from = "Home", to = "Campus",
                            mode = "MATATU",
                            fareOneWay = fare.toDoubleOrNull() ?: 0.0,
                            daysPerWeek = commuteDays.toIntOrNull() ?: 0,
                            verified = false
                        )
                    }
                    val weeklyTransport = com.pesaflow.app.data.finance.journeyWeekly(commuteJourney)
                    val monthlyTransport = com.pesaflow.app.data.finance.journeyMonthly(commuteJourney) ?: 0.0
                    val fareCenter = fare.toDoubleOrNull()?.takeIf { it > 0 }
                    val days = commuteDays.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val weeklyLow = fareCenter?.let { (it - 50).coerceAtLeast(0.0) * 2 * days }
                    val weeklyHigh = fareCenter?.let { (it + 50) * 2 * days }
                    Text(
                        "Fare matching uses this amount ±KSh 50 on timetable days, within 2 hours of class.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if ((transportMonthly ?: 0.0) > 0) {
                        Text(
                            "Current Transport budget: KSh ${transportMonthly!!.toInt()}/month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Budget midpoint: KSh ${monthlyTransport.toInt()}/month" +
                                (weeklyTransport?.let { " (KSh ${it.toInt()}/wk)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            // Upsert, never insert: repeated taps update the one
                            // Transport envelope instead of stacking duplicates.
                            onClick = {
                                context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("school_fare_one_way", fare).apply()
                                viewModel.upsertBudget("Transport", monthlyTransport, BudgetType.MONTHLY)
                            },
                            enabled = monthlyTransport > 0
                        ) { Text("Set Budget") }
                    }
                    if (weeklyLow != null && weeklyHigh != null) {
                        Text(
                            "At ±KSh 50 per ride: KSh ${weeklyLow.toInt()}–${weeklyHigh.toInt()}/week · " +
                                "KSh ${(weeklyLow * 4.33).toInt()}–${(weeklyHigh * 4.33).toInt()}/month. " +
                                "The budget uses your entered fare as its midpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            // Chama tracker: rotating savings, who eats next
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Chama 🤝", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showChamaDialog = true }) { Text("+ New") }
                    }
                    if (chamas.isEmpty()) {
                        Text("No chama yet. Track merry-go-rounds: members, contribution, who's next.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        chamas.forEach { g ->
                            val members = g.members.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val nextIdx = if (members.isNotEmpty()) g.paidCycles % members.size else 0
                            val nextDate = g.startTimestamp + (g.paidCycles + 1L) * g.cycleDays * day
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (members.isEmpty()) "Add members to start rotation"
                                        else "Next: ${members[nextIdx]} · KSh ${g.contribution.toInt()} · ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(nextDate))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.advanceChama(g) }) { Text("Paid ✓") }
                                TextButton(onClick = { viewModel.deleteChamaGroup(g.id) }) { Text("×", color = Color.Red) }
                            }
                        }
                    }
                }
            }


            if (showChamaDialog) {
                var cname by remember { mutableStateOf("") }
                var camt by remember { mutableStateOf("") }
                var cmembers by remember { mutableStateOf("") }
                var ccycle by remember { mutableStateOf("30") }
                AlertDialog(
                    onDismissRequest = { showChamaDialog = false },
                    title = { Text("New Chama") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = cname, onValueChange = { cname = it }, label = { Text("Group name") })
                            OutlinedTextField(value = camt, onValueChange = { camt = it }, label = { Text("Contribution (KSh)") })
                            OutlinedTextField(value = cmembers, onValueChange = { cmembers = it }, label = { Text("Members, comma order") })
                            OutlinedTextField(value = ccycle, onValueChange = { ccycle = it }, label = { Text("Cycle days") })
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val amt = camt.toDoubleOrNull()
                            val cyc = ccycle.toIntOrNull()
                            if (cname.isNotBlank() && amt != null && amt > 0 && cyc != null && cyc > 0 && cmembers.isNotBlank()) {
                                viewModel.addChamaGroup(cname.trim(), amt, cmembers.trim(), cyc)
                                showChamaDialog = false
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { showChamaDialog = false }) { Text("Cancel") } }
                )
            }


            // Hub links: everything semester-lives here
            SectionHeader(title = "Semester Hub")
            HubRow(title = "Bills", subtitle = "${openBills.size} open · KSh ${openBills.sumOf { it.amount }.toInt()}", onClick = { onNavigate(NavRoutes.BILLS) })
            HubRow(title = "Debts", subtitle = "${openDebts.size} open · KSh ${openDebts.sumOf { it.amount }.toInt()}", onClick = { onNavigate(NavRoutes.DEBT) })
            HubRow(title = "Meal Planner", subtitle = "${mealItems.size} foods saved", onClick = { onNavigate(NavRoutes.MEALS) })
            HubRow(title = "University Profile", subtitle = "Allowance, campus, semester", onClick = { onNavigate(NavRoutes.UNIVERSITY) })
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


    }
private fun buildSemesterPdf(
    transactions: List<Transaction>,
    budgets: List<Budget>,
    profile: UniversityProfile?
): android.graphics.pdf.PdfDocument {
    val doc = android.graphics.pdf.PdfDocument()
    val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val c = page.canvas
    val title = android.graphics.Paint().apply { textSize = 22f; isFakeBoldText = true }
    val body = android.graphics.Paint().apply { textSize = 12f }
    var y = 60f
    fun line(s: String, big: Boolean = false) {
        c.drawText(s.take(80), 40f, y, if (big) title else body)
        y += if (big) 30f else 20f
    }
    line("PesaPlanner — Semester Statement", true)
    line(profile?.universityName?.takeIf { it.isNotBlank() } ?: "University")
    val income = transactions.filter { it.isEarnedIncome() && !it.isSample }.sumOf { it.amount }
    val spent = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }.sumOf { it.amount }
    line("Income: KSh ${income.toInt()}")
    line("Expenses: KSh ${spent.toInt()}")
    line("Balance: KSh ${(income - spent).toInt()}")
    line("Top categories:")
    transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.take(5)
        .forEach { line("  ${it.key}: KSh ${it.value.toInt()}") }
    line("Budgets:")
    budgets.forEach { line("  ${it.category} (${it.type.name}): KSh ${it.limitAmount.toInt()}") }
    doc.finishPage(page)
    return doc
}


@Composable
private fun HubRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
