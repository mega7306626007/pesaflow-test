package com.pesaflow.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.ui.theme.AtmoType
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.glass
import com.pesaflow.app.ui.analytics.ExpenditureGraphsCard
import com.pesaflow.app.ui.analytics.HistoricalTrendLineChart
import com.pesaflow.app.R
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.changeVsPrevious
import com.pesaflow.app.data.time.daysElapsedInWeek
import com.pesaflow.app.data.time.inPastOrNow
import com.pesaflow.app.data.time.previousWeekRange
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.ui.language.Copy4
import com.pesaflow.app.ui.language.alertsTitle
import com.pesaflow.app.ui.language.emptyReportsBody
import com.pesaflow.app.ui.language.lensChamaTitle
import com.pesaflow.app.ui.language.lensFoodMoveTitle
import com.pesaflow.app.ui.language.lensHelbTitle
import com.pesaflow.app.ui.language.lensHustleTitle
import com.pesaflow.app.ui.language.moneySpoken
import com.pesaflow.app.ui.language.reportsTitle
import com.pesaflow.app.ui.language.verdictBodyOver
import com.pesaflow.app.ui.language.verdictBodyUnder
import com.pesaflow.app.ui.language.verdictOverPace
import com.pesaflow.app.ui.language.verdictUnderPace
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintReports
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.launch


private const val DAY_MS = 24L * 60 * 60 * 1000


private fun dayStartOf(now: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


private fun monthStartOf(now: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    c.set(java.util.Calendar.DAY_OF_MONTH, 1)
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


private fun yearStartOf(now: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
    c.set(java.util.Calendar.MONTH, 0)
    c.set(java.util.Calendar.DAY_OF_MONTH, 1)
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val lang by viewModel.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(2) }
    val tabs = listOf(
        Copy4("Daily", "Kila siku", "Daily", "Daily"),
        Copy4("Weekly", "Kila wiki", "Wiki", "Weekly"),
        Copy4("Monthly", "Mwezi", "Mwezi", "Month"),
        Copy4("Semester", "Muhula", "Sem", "Sem"),
        Copy4("Annual", "Mwaka", "Mwaka", "Year")
    )

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintReports, bgRes = R.drawable.bg_reports)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(reportsTitle(lang), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title.pick(lang)) }
                        )
                    }
                }
                VerdictHeroCard(
                    tab = selectedTab,
                    transactions = transactions,
                    budgets = budgets,
                    lang = lang
                )
                CampusLensRow(
                    transactions = transactions,
                    debts = debts,
                    lang = lang
                )
                ReportAlertsCard(
                    lang = lang,
                    onSent = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ExpenditureGraphsCard(transactions = transactions)
                }
                when (selectedTab) {
                    0 -> DailyReportContent(transactions = transactions)
                    1 -> WeeklyReportContent(transactions = transactions)
                    2 -> MonthlyReportContent(viewModel = viewModel)
                    3 -> SemesterReportContent(transactions = transactions)
                    else -> AnnualReportContent(transactions = transactions)
                }
            }
        }
    }
}


@Composable
fun VerdictHeroCard(
    tab: Int,
    transactions: List<Transaction>,
    budgets: List<Budget>,
    lang: AppLanguage
) {
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val windowDays = when (tab) {
        0 -> 1
        1 -> 7
        3 -> 120
        4 -> 365
        else -> cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
    val elapsed = when (tab) {
        0 -> windowDays
        // Calendar week to-date: Mon = 1, never a rolling 7.
        1 -> daysElapsedInWeek(now)
        3, 4 -> windowDays
        else -> cal.get(java.util.Calendar.DAY_OF_MONTH)
    }
    val start = when (tab) {
        0 -> dayStartOf(now)
        1 -> thisWeekRange(now).startInclusive
        3 -> now - 120 * DAY_MS
        4 -> yearStartOf(now)
        else -> monthStartOf(now)
    }
    val spent = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= start && it.dateTimestamp <= now }.sumOf { it.amount }
    val allLimit = budgets.firstOrNull { it.category == "ALL" }?.limitAmount ?: 0.0
    // Weekly verdict paces the monthly budget by day (allLimit / 30): the old
    // rolling-7 window compared a week's spend against the whole month.
    val expected = if (allLimit > 0 && tab == 1) allLimit / 30 * elapsed
        else if (allLimit > 0) allLimit * elapsed / windowDays else 0.0
    val under = spent <= expected

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                if (under) verdictUnderPace(lang).pick(lang) else verdictOverPace(lang).pick(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (under) Color(0xFF00C853) else Color(0xFFFF8A65)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                moneySpoken(spent, lang),
                style = AtmoType.figure,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (spent == 0.0 && expected == 0.0) emptyReportsBody(lang)
                else if (under) verdictBodyUnder(spent, expected, lang)
                else verdictBodyOver(spent, expected, lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Projection: current burn carried to window end.
            if (elapsed > 0 && spent > 0) {
                val projected = spent / elapsed * windowDays
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Projected ${moneySpoken(projected, lang)} by window end at this burn.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun LensMini(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
fun CampusLensRow(
    transactions: List<Transaction>,
    debts: List<Debt>,
    lang: AppLanguage
) {
    val now = System.currentTimeMillis()
    val twoWeeks = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= now - 14 * DAY_MS }
    val burn = if (twoWeeks.isNotEmpty()) twoWeeks.sumOf { it.amount } / 14.0 else 0.0
    val balance = transactions.filter { !it.isSample }.sumOf {
        when (it.type) {
            TransactionType.INCOME -> it.amount
            TransactionType.EXPENSE -> -it.amount
            TransactionType.SAVING -> -it.amount
            TransactionType.INVESTMENT -> -it.amount
            TransactionType.TRANSFER -> 0.0
        }
    }
    val runwayDays = if (burn > 0 && balance > 0) (balance / burn).toInt() else -1
    val runwayValue = if (runwayDays < 0) "—" else "$runwayDays d"
    val runwaySub = Copy4("days at this burn", "siku kwa mwendo huu", "days kwa hii pace", "days kwa hii pace").pick(lang)

    val topIncome = transactions.filter { it.type == TransactionType.INCOME && !it.isSample && !it.isOpening }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .maxByOrNull { it.value }
    val hustleValue = topIncome?.let { moneySpoken(it.value, lang) } ?: "—"
    val hustleSub = (topIncome?.key ?: Copy4("no income yet", "hakuna kipato", "hakuna mullah", "hakuna mullah bado").pick(lang)) +
        " · " + Copy4("top income", "kipato kikuu", "mullah kubwa", "mullah kubwa").pick(lang)

    val food = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.category.equals("Food", ignoreCase = true) && it.dateTimestamp >= now - 30 * DAY_MS }.sumOf { it.amount }
    val move = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.category.equals("Transport", ignoreCase = true) && it.dateTimestamp >= now - 30 * DAY_MS }.sumOf { it.amount }
    val foodMoveValue = "${moneySpoken(food, lang)} / ${moneySpoken(move, lang)}"
    val foodMoveSub = Copy4("food vs transport", "chakula vs usafiri", "munch vs mathree", "food vs transport").pick(lang)

    val openDebts = debts.filter { it.status != "PAID" }
    val deniValue = if (openDebts.isEmpty()) "0" else "${openDebts.size} · ${moneySpoken(openDebts.sumOf { it.amount }, lang)}"
    val deniSub = Copy4("open deni, no shame", "madeni wazi", "madeni open", "open deni").pick(lang)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LensMini(label = lensHelbTitle(lang), value = runwayValue, sub = runwaySub, modifier = Modifier.weight(1f))
            LensMini(label = lensHustleTitle(lang), value = hustleValue, sub = hustleSub, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LensMini(label = lensFoodMoveTitle(lang), value = foodMoveValue, sub = foodMoveSub, modifier = Modifier.weight(1f))
            LensMini(label = lensChamaTitle(lang), value = deniValue, sub = deniSub, modifier = Modifier.weight(1f))
        }
    }
}


@Composable
fun ReportAlertsCard(lang: AppLanguage, onSent: (String) -> Unit) {
    val context = LocalContext.current
    var pendingPreview by remember { mutableStateOf<String?>(null) }
    fun canNotify(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun sendText(kind: String): String = when (kind) {
        "night" -> Copy4(
            "Night report sent — check your shade.",
            "Ripoti ya usiku imetumwa — angalia.",
            "Repoti imetumwa — check shade.",
            "Night report sent — check shade."
        ).pick(lang)
        "sunday" -> Copy4(
            "Sunday report sent — check your shade.",
            "Ripoti ya Jumapili imetumwa — angalia.",
            "Repoti ya Sunday imetumwa — check shade.",
            "Sunday report sent — check shade."
        ).pick(lang)
        else -> Copy4(
            "Monthly report sent — check your shade.",
            "Ripoti ya mwezi imetumwa — angalia.",
            "Repoti ya mwezi imetumwa — check shade.",
            "Monthly report sent — check shade."
        ).pick(lang)
    }
    fun blockedText(): String = Copy4(
        "Permission off — report ran, enable notifications to see it.",
        "Ruhusa haijatolewa — washa arifa uione.",
        "Permission off — washa notifications uione.",
        "Permission off — washa notifications uione."
    ).pick(lang)
    fun runPreview(kind: String) {
        when (kind) {
            "night" -> ReminderScheduler.previewNightReport(context)
            "sunday" -> ReminderScheduler.previewSundayReport(context)
            else -> ReminderScheduler.previewMonthlyReport(context)
        }
    }
    // Notifications are requested here — at the moment the user asks for a
    // report — never on cold start. Grant → preview fires immediately.
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingPreview ?: return@rememberLauncherForActivityResult
        pendingPreview = null
        if (granted) runPreview(kind)
        onSent(if (granted) sendText(kind) else blockedText())
    }
    fun fire(kind: String) {
        if (canNotify()) {
            runPreview(kind)
            onSent(sendText(kind))
        } else if (android.os.Build.VERSION.SDK_INT >= 33) {
            pendingPreview = kind
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runPreview(kind)
            onSent(blockedText())
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glass(shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(alertsTitle(lang) + " 🔔", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                Copy4(
                    "Night 9:30pm · Sunday 8pm · 1st of month. Toggle them in Settings → Notifications.",
                    "Usiku 9:30 · Jumapili 8pm · tarehe 1. Badilisha kwenye Settings.",
                    "Night 9:30 · Sunday 8pm · tarehe 1. Settings uki-switch.",
                    "Night 9:30 · Sunday 8pm · 1st. Settings → Notifications."
                ).pick(lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { fire("night") }) {
                    Text(Copy4("Night now", "Usiku sai", "Night sai", "Night now").pick(lang))
                }
                OutlinedButton(onClick = { fire("sunday") }) {
                    Text(Copy4("Sunday now", "Jumapili sai", "Sunday sai", "Sunday now").pick(lang))
                }
                OutlinedButton(onClick = { fire("monthly") }) {
                    Text(Copy4("Monthly now", "Mwezi sai", "Mwezi sai", "Monthly now").pick(lang))
                }
            }
        }
    }
}


@Composable
fun DailyReportContent(transactions: List<Transaction>) {
    val now = System.currentTimeMillis()
    val start = dayStartOf(now)
    val today = transactions.filter { it.dateTimestamp >= start && !it.isSample }
    val income = today.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val expenses = today.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val top = today.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .maxByOrNull { it.value }

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Daily Summary", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "− KSh ${expenses.toInt()}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "+ KSh ${income.toInt()} in · ${today.size} transaction(s)" +
                (top?.let { " · top: ${it.key} KSh ${it.value.toInt()}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (today.isEmpty()) {
            Text("Nothing logged today yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            today.sortedByDescending { it.dateTimestamp }.forEach { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.merchant, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${when (tx.type) { TransactionType.INCOME -> "+"; TransactionType.TRANSFER -> "↔"; else -> "−" }} KSh ${tx.amount.toInt()}",
                        fontWeight = FontWeight.Bold,
                        color = if (tx.type == TransactionType.INCOME) Color.Green else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


@Composable
fun WeeklyReportContent(transactions: List<Transaction>) {
    val now = System.currentTimeMillis()
    // Calendar week to-date (Mon–today) vs the complete previous week.
    // The old rolling last-7-days never matched the dashboard chart or the
    // Sunday report, so three screens showed three different "weeks".
    val week = thisWeekRange(now)
    val prevWeek = previousWeekRange(now)
    val elapsed = daysElapsedInWeek(now).coerceAtLeast(1)
    fun inWeekToDate(ts: Long) = ts in week && inPastOrNow(ts, now)
    val weekTx = transactions.filter { inWeekToDate(it.dateTimestamp) && !it.isSample }
    val income = weekTx.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val expenses = weekTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val prevExpenses = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in prevWeek }.sumOf { it.amount }
    val weekVsPrev = changeVsPrevious(expenses, prevExpenses)
    val dailyPoints = week.days().filter { it <= now }.map { d0 ->
        weekTx.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= d0 && it.dateTimestamp < addDays(d0, 1) }.sumOf { it.amount }
    }
    val top = weekTx.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .maxByOrNull { it.value }

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Weekly Overview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "− KSh ${expenses.toInt()}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "+ KSh ${income.toInt()} in · avg KSh ${(expenses / elapsed).toInt()}/day" +
                (top?.let { " · top: ${it.key} KSh ${it.value.toInt()}" } ?: "") +
                (weekVsPrev?.let {
                    // weekVsPrev is non-null only when prevExpenses > 0, so a
                    // dust baseline here just means absolutes, not a percent.
                    if (com.pesaflow.app.data.time.isDustBaseline(prevExpenses))
                        " · KSh ${expenses.toInt()} vs KSh ${prevExpenses.toInt()} last week"
                    else " · ${if (it > 0) "up $it%" else "down ${-it}%"} vs last week (KSh ${prevExpenses.toInt()})"
                } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (weekTx.any { it.type == TransactionType.EXPENSE }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("This week's daily spend (Mon–today)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HistoricalTrendLineChart(
                        points = dailyPoints,
                        chartDescription = "This week's daily spend, oldest to newest."
                    )
                }
            }
        } else {
            Text("No spending this week yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
fun MonthlyReportContent(viewModel: FinanceViewModel) {
    val income by viewModel.monthlyIncome.collectAsState()
    val expenses by viewModel.monthlyExpenses.collectAsState()
    val balance by viewModel.availableBalance.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()

    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Summary hero card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Monthly Overview", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "− KSh ${expenses.toInt()}",
                    style = AtmoType.figure,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "+ KSh ${income.toInt()} in · balance KSh ${balance.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Key metrics as floating cards
        MetricCard(
            label = "Income This Month",
            value = "KSh ${income.toInt()}",
            icon = Icons.Default.Add,
            color = Color.Green
        )
        MetricCard(
            label = "Expenses This Month",
            value = "KSh ${expenses.toInt()}",
            icon = Icons.Default.ShoppingCart,
            color = Color.Red
        )
        MetricCard(
            label = "Available Balance",
            value = "KSh ${balance.toInt()}",
            icon = Icons.Default.AccountBox,
            color = MaterialTheme.colorScheme.primary
        )

        // Real insight card: computed from your ledger, never canned text
        val monthInsight = remember(transactions) {
            val c = java.util.Calendar.getInstance()
            fun inM(ts: Long, off: Int): Boolean {
                val r = (c.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, off) }
                val t = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                return t.get(java.util.Calendar.YEAR) == r.get(java.util.Calendar.YEAR) &&
                    t.get(java.util.Calendar.MONTH) == r.get(java.util.Calendar.MONTH)
            }
            val cur = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && inM(it.dateTimestamp, 0) }
            val prev = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample && inM(it.dateTimestamp, -1) }
            val curTotal = cur.sumOf { it.amount }
            val prevTotal = prev.sumOf { it.amount }
            val top = cur.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
            val biggest = cur.maxByOrNull { it.amount }
            val mpesa = cur.filter { it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }.sumOf { it.amount }
            buildString {
                if (cur.isEmpty()) append("No spending logged this month yet — add your first expense and this card comes alive.")
                else {
                    if (prevTotal > 0) {
                        val d = ((curTotal - prevTotal) / prevTotal * 100).toInt()
                        append(if (d > 0) "Up $d% vs last month (KSh ${curTotal.toInt()} vs KSh ${prevTotal.toInt()}). " else "Down ${-d}% vs last month — keep it up. ")
                    } else append("KSh ${curTotal.toInt()} spent so far. ")
                    top?.let { append("Biggest drain: ${it.key} KSh ${it.value.toInt()}. ") }
                    biggest?.let { append("Single biggest hit: ${it.merchant.take(24)} KSh ${it.amount.toInt()}. ") }
                    if (mpesa > 0) append("M-Pesa KSh ${mpesa.toInt()} vs manual KSh ${(curTotal - mpesa).toInt()}.")
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Smart Insight 💡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    monthInsight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("This month: KSh ${expenses.toInt()} expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


@Composable
fun SemesterReportContent(transactions: List<Transaction>) {
    val now = System.currentTimeMillis()
    val start = now - 120 * DAY_MS
    val window = transactions.filter { it.dateTimestamp >= start && !it.isSample }
    val income = window.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val expenses = window.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val top3 = window.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.take(3)

    PeriodSummaryCard(
        title = "Semester Window (120 days)",
        income = income,
        expenses = expenses,
        count = window.size,
        top = top3
    )
}


@Composable
fun AnnualReportContent(transactions: List<Transaction>) {
    val cal = java.util.Calendar.getInstance()
    val year = cal.get(java.util.Calendar.YEAR)
    val window = transactions.filter {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
        c.get(java.util.Calendar.YEAR) == year && !it.isSample
    }
    val income = window.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val expenses = window.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val top3 = window.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }.take(3)

    PeriodSummaryCard(
        title = "Annual $year",
        income = income,
        expenses = expenses,
        count = window.size,
        top = top3
    )
}


@Composable
private fun PeriodSummaryCard(
    title: String,
    income: Double,
    expenses: Double,
    count: Int,
    top: List<Map.Entry<String, Double>>
) {
    Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "− KSh ${expenses.toInt()}",
            style = AtmoType.figure,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "+ KSh ${income.toInt()} in · $count transaction(s)" +
                if (income > 0) " · saved KSh ${(income - expenses).toInt()}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Income vs Expense bar
        if (income > 0 || expenses > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Income vs Expense", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxVal = maxOf(income, expenses, 1.0)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("In", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00C853), modifier = Modifier.width(20.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (income / maxVal).toFloat() },
                            modifier = Modifier.weight(1f).height(12.dp),
                            color = Color(0xFF00C853),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text("KSh ${income.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(80.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Out", style = MaterialTheme.typography.labelSmall, color = Color.Red, modifier = Modifier.width(20.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (expenses / maxVal).toFloat() },
                            modifier = Modifier.weight(1f).height(12.dp),
                            color = Color.Red,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text("KSh ${expenses.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(80.dp))
                    }
                }
            }
        }
        if (top.isEmpty()) {
            Text("No expenses in this period yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Category breakdown with visual bars
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Category Breakdown", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxCat = top.firstOrNull()?.value ?: 1.0
                    top.forEach { e ->
                        val barWidth = (e.value / maxCat).coerceIn(0.0, 1.0).toFloat()
                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.key, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                Text("KSh ${e.value.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                            }
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { barWidth },
                                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
