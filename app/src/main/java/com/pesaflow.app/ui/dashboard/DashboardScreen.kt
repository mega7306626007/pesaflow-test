package com.pesaflow.app.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.prefs.AppPrefs
import com.pesaflow.app.data.time.startOfWeek
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.theme.categoryEmoji
import com.pesaflow.app.ui.theme.BudgetProgressBar
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PpCard
import com.pesaflow.app.ui.theme.PpCardKind
import com.pesaflow.app.ui.theme.PpSectionHeader
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.ui.theme.ppShapes
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.AtmoType
import com.pesaflow.app.ui.theme.HeroFinanceCard
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintDashboardForest
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val availableBalance by viewModel.availableBalance.collectAsState()
    val mpesaBal by viewModel.mpesaBalance.collectAsState()
    val cashBal by viewModel.cashBalance.collectAsState()
    val bankBal by viewModel.bankBalance.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val hideBalances by viewModel.hideBalances.collectAsState()
    val hiddenSections by viewModel.hiddenSections.collectAsState()
    val profile by viewModel.universityProfile.collectAsState()
    val financialSnapshot by viewModel.financialSnapshot.collectAsState()
    val semesterRunway = remember(transactions, profile, financialSnapshot.committed) {
        profile?.let {
            com.pesaflow.app.data.finance.calculateSemesterRunway(
                transactions = transactions,
                startTimestamp = it.semesterStartTimestamp,
                endTimestamp = it.semesterEndTimestamp,
                startingFunding = it.startingFunding,
                committed = financialSnapshot.committed.toDouble(),
                now = System.currentTimeMillis()
            )
        }
    }

    // Weekday pattern defaults to THIS week — all-time is opt-in, never the
    // default. A 5000-SMS history must not masquerade as "this week".
    var weekdayScope by remember { mutableStateOf("week") }
    val weekdaySpending = remember(transactions, weekdayScope) {
        val cal = Calendar.getInstance()
        // Canonical Monday-start week: the old set(DAY_OF_WEEK, firstDayOfWeek)
        // followed the device locale, so some phones charted Sun-start weeks
        // while every insight assumed Monday.
        val weekStart = startOfWeek(System.currentTimeMillis())
        val map = mutableMapOf("Mon" to 0.0, "Tue" to 0.0, "Wed" to 0.0, "Thu" to 0.0, "Fri" to 0.0, "Sat" to 0.0, "Sun" to 0.0)
        transactions.filter {
            it.type == TransactionType.EXPENSE && !it.isSample &&
                (weekdayScope == "all" || it.dateTimestamp >= weekStart)
        }.forEach { tx ->
            cal.timeInMillis = tx.dateTimestamp
            val dayStr = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                Calendar.SUNDAY -> "Sun"
                else -> null
            }
            if (dayStr != null) {
                map[dayStr] = (map[dayStr] ?: 0.0) + tx.amount
            }
        }
        map
    }


    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var showAllPending by remember { mutableStateOf(false) }
    var showReconcile by remember { mutableStateOf(false) }
    // Unsure-first ordering for the pending queue (computed once per queue
    // change, reused by the list below).
    val orderPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    val orderedPending = remember(pendingTransactions) {
        pendingTransactions.sortedBy {
            com.pesaflow.app.data.ledger.ConfidenceMemory.effective(orderPrefs, it.merchant, it.confidenceScore)
        }
    }
    var ledgerFilter by remember { mutableStateOf<String?>(null) }
    var showCustomize by remember { mutableStateOf(false) }
    // First-run coach: 3 steps, then never again (typed DataStore flag).
    val coachContext = LocalContext.current
    val coachDoneByPrefs by AppPrefs.coachDone(coachContext).collectAsState(initial = null)
    var coachStep by remember(coachDoneByPrefs) { mutableStateOf(if (coachDoneByPrefs == true) 99 else 0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintDashboardForest, bgRes = R.drawable.bg_dashboard_forest)
        Scaffold(
            containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("PesaPlanner Hub ⚡", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showCustomize = true }) { Text("Tune") }
                    TextButton(onClick = { viewModel.setHideBalances(!hideBalances) }) {
                        Text(if (hideBalances) "Show" else "Hide")
                    }
                    IconButton(onClick = { onNavigate(NavRoutes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search transactions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        when (currentLanguage) {
                            AppLanguage.ENGLISH -> "EN"
                            AppLanguage.KISWAHILI -> "SW"
                            AppLanguage.SHENG -> "SH"
                            else -> "MIX"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            // Global Add lives in MainActivity Scaffold — no duplicate FAB here.
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Transparent).padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Spacer(Modifier.height(PesaSpacing.xs))
            }


            // Personalized header: greeting first, actions after the hero.
            item {
                HomeGreeting(userName = userName)
            }


            // Hero financial card — canonical snapshot drives it: safe-to-spend
            // hero (primary horizon), Held + Flexible supporting, Why? unfolds
            // the engine's own explanation. One hero, two supports, no dumps.
            item {
                val snap by viewModel.financialSnapshot.collectAsState()
                val heroLabel = when (snap.primaryHorizon) {
                    com.pesaflow.app.data.finance.Horizon.TODAY -> "Flexible money · today"
                    com.pesaflow.app.data.finance.Horizon.WEEK -> "Flexible money · this week"
                    com.pesaflow.app.data.finance.Horizon.UNTIL_NEXT_INCOME -> "Flexible money · to next income"
                    com.pesaflow.app.data.finance.Horizon.MONTH -> "Flexible money · this month"
                    com.pesaflow.app.data.finance.Horizon.SEMESTER ->
                        if (semesterRunway == null) "Flexible money · after commitments" else "Flexible money · term"
                }
                val heroValue = when (snap.primaryHorizon) {
                    com.pesaflow.app.data.finance.Horizon.TODAY -> snap.safeToday
                    com.pesaflow.app.data.finance.Horizon.WEEK -> snap.safeWeek
                    com.pesaflow.app.data.finance.Horizon.UNTIL_NEXT_INCOME -> snap.safeUntilIncome
                    com.pesaflow.app.data.finance.Horizon.MONTH -> snap.safeMonth
                    com.pesaflow.app.data.finance.Horizon.SEMESTER ->
                        semesterRunway?.availableAfterCommitments?.let(com.pesaflow.app.data.finance.Money::of)
                            ?: snap.flexible
                }
                val fmt = com.pesaflow.app.data.finance.MoneyFormatter
                val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
                val greeting = remember(userName) {
                    val name = userName.ifBlank { "there" }
                    when (hour) {
                        in 5..11 -> "Good morning, $name"
                        in 12..16 -> "Good afternoon, $name"
                        in 17..21 -> "Good evening, $name"
                        else -> "Hello, $name"
                    }
                }
                val dateLine = remember {
                    java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date())
                }
                HeroFinanceCard(
                    greeting = greeting,
                    dateLine = dateLine,
                    availableLabel = heroLabel,
                    availableValue = if (hideBalances) "KSh ••••" else fmt.compact(heroValue),
                    stats = listOf(
                        "Current balance" to if (hideBalances) "••••" else fmt.compact(snap.liquid),
                        "Flexible money" to if (hideBalances) "••••" else fmt.compact(snap.flexible)
                    ),
                    onHideToggle = { viewModel.setHideBalances(!hideBalances) },
                    hideLabel = if (hideBalances) "Show" else "Hide"
                )
                var showWhy by remember { mutableStateOf(false) }
                TextButton(onClick = { showWhy = !showWhy }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showWhy) "Hide why ▴" else "Why? ▾", style = MaterialTheme.typography.bodySmall)
                }
                if (showWhy && !hideBalances) {
                    val why = snap.explanations["safeToday"]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Money → Freedom → Pressure: nothing disappeared, committed + buffer set aside.
                            Text(
                                "Current balance  ${fmt.compact(snap.liquid)}  (Recorded)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "− Committed  ${fmt.compact(snap.committed)}  (bills, debts, fees, goals)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "− Safety buffer  ${fmt.compact(snap.riskBuffer)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "= Flexible money  ${fmt.compact(snap.flexible)}  (Calculated)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                why?.why ?: "Based on your recorded ledger. Projections are labelled separately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            (why?.contributors.orEmpty()).forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }


            // Quick actions sit right under the hero: spend, receive.
            item {
                HomeQuickActions(viewModel = viewModel, onQuickAdd = onQuickAdd)
            }


            // Recent activity — latest first, full history one tap away.
            if ("recent" !in hiddenSections) {
                item {
                    PpSectionHeader(
                        title = "Recent activity",
                        actionLabel = "Full history →",
                        onAction = { onNavigate(NavRoutes.TRANSACTIONS) }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ledgerFilter == null,
                            onClick = { ledgerFilter = null },
                            label = { Text("All") }
                        )
                        listOf("INCOME" to "Income", "EXPENSE" to "Expenses", "SAVING" to "Savings", "INVESTMENT" to "Investments").forEach { (v, label) ->
                            FilterChip(
                                selected = ledgerFilter == v,
                                onClick = { ledgerFilter = if (ledgerFilter == v) null else v },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                if (transactions.isEmpty()) {
                    item {
                        PesaEmptyState(
                            title = "No transactions yet",
                            explanation = "Your financial story starts here — log your first one.",
                            actionLabel = "Add your first expense",
                            onAction = { onNavigate(NavRoutes.ADD_EXPENSE) }
                        )
                    }
                } else {
                    items(transactions.filter { ledgerFilter == null || it.type.name == ledgerFilter }.take(5), key = { it.id }) { tx ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                when (v) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        viewModel.deleteTransactionWithUndo(tx)
                                        scope.launch {
                                            snackbar.currentSnackbarData?.dismiss()
                                            val r = snackbar.showSnackbar("Deleted ${tx.merchant}.", "Undo", duration = SnackbarDuration.Long)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                                        }
                                        true
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> { editingTx = tx; false }
                                    else -> false
                                }
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val dir = dismissState.dismissDirection
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        when (dir) {
                                            SwipeToDismissBoxValue.StartToEnd -> ppColors.brightBlue
                                            SwipeToDismissBoxValue.EndToStart -> ppColors.error
                                            else -> Color.Transparent
                                        },
                                        ppShapes.cardCompact
                                    ).padding(16.dp),
                                    contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        if (dir == SwipeToDismissBoxValue.StartToEnd) Icons.Filled.Edit else Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = if (dir == SwipeToDismissBoxValue.StartToEnd) ppColors.textOnBlue else ppColors.textPrimary
                                    )
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(ppColors.surface, ppShapes.cardCompact)
                                    .border(1.dp, ppColors.border, ppShapes.cardCompact)
                                    .padding(
                                        horizontal = ppSpacing.lg,
                                        vertical = ppSpacing.md
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${categoryEmoji(tx.category)} ${tx.merchant}",
                                        style = ppTypography.labelLarge,
                                        color = ppColors.textPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${tx.category} · ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(tx.dateTimestamp))}",
                                        style = ppTypography.bodySmall,
                                        color = ppColors.textTertiary
                                    )
                                }
                                Text(
                                    text = "${when (tx.type) { TransactionType.INCOME -> "+"; TransactionType.TRANSFER -> "↔"; else -> "-" }} KSh ${tx.amount.toInt()}",
                                    style = ppTypography.financialSmall,
                                    color = if (tx.type == TransactionType.INCOME) ppColors.income else ppColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }


            // Smart insight right after recent activity — advice on fresh numbers.
            item {
                SmartInsightsCard(
                    transactions = transactions,
                    budgets = budgets,
                    lang = currentLanguage,
                    name = userName,
                    bills = bills,
                    debts = debts,
                    goals = savingsGoals,
                    incomeSources = viewModel.incomeSources.collectAsState().value,
                    persona = com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
                )
            }

            // Forward projection: paydays + bills + subscriptions vs money held.
            item {
                val inflowSources by viewModel.incomeSources.collectAsState()
                Next30DaysCard(
                    transactions = transactions,
                    bills = bills,
                    hide = hideBalances,
                    inflowDays = com.pesaflow.app.data.income.nextInflowDay(inflowSources),
                    incomeSources = inflowSources
                )
            }


            // Upcoming budgets: category, spent, remaining, percent, progress —
            // glanceable, never a spreadsheet.
            if (budgets.isNotEmpty()) {
                item {
                    PpCard(kind = PpCardKind.LARGE) {
                        Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.md)) {
                            PpSectionHeader(
                                title = "Budget progress",
                                actionLabel = "Budgets →",
                                onAction = { onNavigate(NavRoutes.BUDGETS) }
                            )
                            budgets.filter { it.limitAmount > 0 && it.category != "ALL" }.take(5).forEach { b ->
                                // Current-period progress per budget type — a stale
                                // stored window must never sum whole histories.
                                val nowMs = System.currentTimeMillis()
                                val win = com.pesaflow.app.data.finance.budgetWindowRange(
                                    b.type, nowMs,
                                    profile?.semesterStartTimestamp ?: 0L,
                                    profile?.semesterEndTimestamp ?: 0L
                                )
                                val spent = transactions.filter {
                                    it.type == TransactionType.EXPENSE && !it.isSample &&
                                        it.dateTimestamp in win && it.dateTimestamp <= nowMs &&
                                        it.category.equals(b.category, ignoreCase = true)
                                }.sumOf { it.amount }
                                val remaining = (b.limitAmount - spent).coerceAtLeast(0.0)
                                val pct = (spent / b.limitAmount * 100).coerceIn(0.0, 100.0).toFloat()
                                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.xs)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            "${categoryEmoji(b.category)} ${b.category}",
                                            style = ppTypography.labelLarge,
                                            color = ppColors.textPrimary
                                        )
                                        Text(
                                            "KSh ${spent.toInt()}/${b.limitAmount.toInt()}",
                                            style = ppTypography.bodySmall,
                                            color = ppColors.textTertiary
                                        )
                                    }
                                    BudgetProgressBar(fraction = pct / 100f, showPercent = false)
                                    Text(
                                        if (remaining > 0) "KSh ${remaining.toInt()} left · ${pct.toInt()}% used"
                                        else "Over by KSh ${(spent - b.limitAmount).toInt()} · ${pct.toInt()}% used",
                                        style = ppTypography.bodySmall,
                                        color = ppColors.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }


            // M-Pesa wallet balance from the last SMS (display-only; the ledger
            // stays the source of truth for all math).
            item {
                val dashContext = LocalContext.current
                com.pesaflow.app.data.parsers.readMpesaBalance(dashContext)?.let { (amt, at) ->
                    Text(
                        if (hideBalances) "📲 M-Pesa wallet: KSh ••••"
                        else "📲 M-Pesa wallet: KSh ${amt.toInt()} · " + com.pesaflow.app.data.parsers.balanceAgeText(at, System.currentTimeMillis()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // Money positions: ledger balance vs M-Pesa wallet side by side.
            item {
                val dashContext = LocalContext.current
                val wallet = com.pesaflow.app.data.parsers.readMpesaBalance(dashContext)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Money positions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ledger", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else availableBalance.toKSh(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("M-Pesa wallet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••"
                                    else wallet?.let { "KSh ${it.first.toInt()}" } ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            wallet?.let { "Wallet read " + com.pesaflow.app.data.parsers.balanceAgeText(it.second, System.currentTimeMillis()) + " · wallet is M-Pesa only, ledger covers everything." }
                                ?: "No wallet reading yet — first M-Pesa text sets it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Per-method split: which pocket holds the money.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("M-Pesa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${mpesaBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${cashBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bank", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (hideBalances) "KSh ••••" else "KSh ${bankBal.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Spendable now: unconfirmed SMS rows are real money that
                        // already moved, so the ledger understates reality until
                        // they confirm. Shown only while the queue is non-empty.
                        val pendingIn = pendingTransactions
                            .filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                        val pendingOut = pendingTransactions
                            .filter { it.type != TransactionType.INCOME }.sumOf { it.amount }
                        if (!hideBalances && (pendingIn > 0 || pendingOut > 0)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Spendable now: KSh ${com.pesaflow.app.data.money.spendableNow(availableBalance, pendingIn, pendingOut).toInt()} " +
                                    "(ledger ± ${pendingTransactions.size} confirming)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        // Drift check: ledger's M-Pesa pocket vs the last SMS
                        // reading, in tolerance zones. Small gaps get a gentle
                        // nudge + the 1-tap fix; only large gaps go red. Minor
                        // wobbles (fees, rounding) never stress the user.
                        wallet?.let { (wamt, _) ->
                            val (zone, drift) =
                                com.pesaflow.app.data.money.evaluateDrift(wamt, mpesaBal)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (hideBalances) "Drift check: KSh ••••"
                                else when (zone) {
                                    com.pesaflow.app.data.money.DriftZone.IN_SYNC ->
                                        "Drift check: ledger matches SMS ✓"
                                    com.pesaflow.app.data.money.DriftZone.MINOR ->
                                        "Drift check: KSh ${kotlin.math.abs(drift).toInt()} small gap — likely a fee or one unlogged row."
                                    com.pesaflow.app.data.money.DriftZone.MAJOR ->
                                        "Drift check: KSh ${kotlin.math.abs(drift).toInt()} " + (if (drift > 0) "(ledger higher — spending missing?)" else "(SMS higher — income missing?)")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hideBalances) MaterialTheme.colorScheme.onSurfaceVariant
                                else when (zone) {
                                    com.pesaflow.app.data.money.DriftZone.IN_SYNC -> MaterialTheme.colorScheme.primary
                                    com.pesaflow.app.data.money.DriftZone.MINOR -> com.pesaflow.app.ui.theme.ppColors.warning
                                    com.pesaflow.app.data.money.DriftZone.MAJOR -> MaterialTheme.colorScheme.error
                                }
                            )
                            if (!hideBalances && zone != com.pesaflow.app.data.money.DriftZone.IN_SYNC) {
                                TextButton(onClick = { showReconcile = true }) {
                                    Text("Reconcile KSh ${kotlin.math.abs(drift).toInt()} → ledger ⚖️")
                                }
                            }
                        }
                        if (showReconcile) {
                            val wamt = com.pesaflow.app.data.parsers.readMpesaBalance(dashContext)?.first ?: 0.0
                            val drift = mpesaBal - wamt
                            AlertDialog(
                                onDismissRequest = { showReconcile = false },
                                title = { Text("Reconcile drift?") },
                                text = {
                                    Text(
                                        "Ledger M-Pesa KSh ${mpesaBal.toInt()} vs SMS KSh ${wamt.toInt()}. " +
                                            "Books KSh ${kotlin.math.abs(drift).toInt()} as “Balance adjustment” " +
                                            (if (drift > 0) "(spending)." else "(income).")
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.reconcileWallet(wamt)
                                        showReconcile = false
                                        scope.launch {
                                            snackbar.currentSnackbarData?.dismiss()
                                            snackbar.showSnackbar("Reconciled ✓", duration = SnackbarDuration.Short)
                                        }
                                    }) { Text("Book it") }
                                },
                                dismissButton = { TextButton(onClick = { showReconcile = false }) { Text("Cancel") } }
                            )
                        }
                    }
                }
            }


            // Feature shortcuts: icon rail, not another list — distinct from More.
            item {
                ShortcutRail(onNavigate = onNavigate)
            }


            // Money rhythms (from PesaFlow main): fare/rent/payday hypotheses
            // learned from the ledger — confirm once, they stop asking.
            item {
                val rhythms by viewModel.userRhythms.collectAsState()
                val storedKinds = remember(rhythms) { rhythms.map { it.kind to it.category }.toSet() }
                val hyps = remember(transactions, storedKinds) {
                    RhythmEngine().propose(
                        transactions.filter { !it.isSample },
                        com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
                    ).filter { (it.kind.name to it.category) !in storedKinds }.take(2)
                }
                if (hyps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hyps.forEach { h ->
                            RhythmConfirmCard(
                                hypothesis = h,
                                onConfirm = {
                                    viewModel.upsertRhythm(
                                        com.pesaflow.app.data.models.UserRhythm(
                                            kind = h.kind.name,
                                            category = h.category,
                                            confidence = h.confidence,
                                            hint = h.hint,
                                            dayOfMonth = h.dayOfMonth,
                                            amount = h.amount,
                                            sourceCode = h.supportingCodes.firstOrNull().orEmpty(),
                                            confirmed = true
                                        )
                                    )
                                },
                                onDismiss = {
                                    viewModel.upsertRhythm(
                                        com.pesaflow.app.data.models.UserRhythm(
                                            kind = h.kind.name,
                                            category = h.category,
                                            confidence = h.confidence,
                                            hint = h.hint,
                                            dayOfMonth = h.dayOfMonth,
                                            amount = h.amount,
                                            dismissed = true
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }


            // Warning banners (80%/100% monthly budget thresholds). Progress is
            // measured against the CURRENT month, never the budget's stored
            // window (those go stale and sum whole histories).
            budgets.filter { it.type == BudgetType.MONTHLY && it.limitAmount > 0 }.mapNotNull { b ->
                val nowMs = System.currentTimeMillis()
                val win = com.pesaflow.app.data.time.monthRange(nowMs)
                // Envelope-scoped: ALL reads everything, Food reads Food.
                // Total-spend-against-every-envelope contradicted the Budgets
                // tab ("Food crossed 3793 of 1200" next to a healthy card).
                val spent = com.pesaflow.app.data.finance.envelopeSpend(transactions, b.category, win, nowMs)
                val pct = (spent / b.limitAmount * 100).toInt()
                if (pct >= 80) Triple(b, spent, pct) else null
            }.take(2).forEach { (b, spent, pct) ->
                // Pace tempers the klaxon: 85% on day 28 is a steady month
                // (calm card), 85% on day 3 is hot (red card). Over cap always red.
                val cal = java.util.Calendar.getInstance()
                val pace = com.pesaflow.app.data.finance.evaluateCategoryPace(
                    spent, b.limitAmount,
                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                    cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                )
                val hot = pct >= 100 || pace == com.pesaflow.app.data.finance.BudgetPace.AT_RISK ||
                    pace == com.pesaflow.app.data.finance.BudgetPace.EXCEEDED
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hot) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            if (pct >= 100) "⛔ ${b.category} budget crossed: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()}."
                            else if (hot) "⚠️ ${b.category} at $pct%: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()} — ahead of pace."
                            else "👌 ${b.category} at $pct%: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()} — still within pace.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hot) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            // Smart Analyzer Tab - Weekday breakdown + Semester Runway + Motivational messages
            val motivationalMessages = listOf(
                "Every coin saved is a step closer to your degree! 🎓",
                "Small cuts today, big freedom tomorrow. 💪",
                "Your future self will thank you for this decision. ✨",
                "Consistent small savings beat sporadic big wins. 🌟",
                "Don't let today's spending steal tomorrow's opportunities. 🚀"
            )
            val todayCal = Calendar.getInstance()
            val todayDayStr = when (todayCal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                else -> "Sun"
            }
            val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Smart Analyzer 🧠", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        // Weekday spending breakdown — this week by default,
                        // all-time only on request.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Weekday Spending Breakdown", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(selected = weekdayScope == "week", onClick = { weekdayScope = "week" }, label = { Text("This week") })
                                FilterChip(selected = weekdayScope == "all", onClick = { weekdayScope = "all" }, label = { Text("All time") })
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            weekdays.forEach { day ->
                                val spent = weekdaySpending[day] ?: 0.0
                                val isToday = day == todayDayStr
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .background(
                                            color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(6.dp)
                                ) {
                                    Column {
                                        Text(day, style = MaterialTheme.typography.bodySmall, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                        Text("KSh ${spent.toInt()}", style = MaterialTheme.typography.bodySmall, color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Semester runway
                        Text("Semester Runway 🛤️", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val runway = semesterRunway
                        if (runway != null) {
                            val dailyShortfall = if (runway.daysRemaining > 0 && runway.availableAfterCommitments < 0) {
                                -runway.availableAfterCommitments / runway.daysRemaining
                            } else null
                            val safeDaily = if (runway.daysRemaining > 0) {
                                (runway.availableAfterCommitments / runway.daysRemaining).coerceAtLeast(0.0)
                            } else null
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    when {
                                        runway.isUpcoming -> "Semester starts in ${runway.daysUntilStart} days"
                                        runway.isEnded -> "Semester ended"
                                        else -> "${runway.daysRemaining} days left in semester"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "After commitments: " + if (hideBalances) "••••" else
                                        com.pesaflow.app.data.finance.MoneyFormatter.compact(
                                            com.pesaflow.app.data.finance.Money.of(runway.availableAfterCommitments)
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (runway.availableAfterCommitments < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (safeDaily != null) {
                                    Text(
                                        "Safe daily runway: " + if (hideBalances) "••••" else
                                            com.pesaflow.app.data.finance.MoneyFormatter.compact(
                                                com.pesaflow.app.data.finance.Money.of(safeDaily)
                                            ) + "/day",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (dailyShortfall != null) {
                                    Text(
                                        "Shortfall: " + if (hideBalances) "••••" else
                                            com.pesaflow.app.data.finance.MoneyFormatter.compact(
                                                com.pesaflow.app.data.finance.Money.of(dailyShortfall)
                                            ) + "/day needed to cover the current gap",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            Text("Set the actual semester start and end dates under More → University to see your semester runway.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Motivational message
                        val msgIndex = ((System.currentTimeMillis() / (1000 * 60 * 60 * 24)) % motivationalMessages.size).toInt()
                        Text(
                            motivationalMessages[msgIndex],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }


            // Safe-to-spend Engine Section (hero position under balance)
            if ("safe" !in hiddenSections) {
            item {
                ExplainChip(
                    label = "What is safe-to-spend?",
                    body = "Income minus budgets, bills due and goals — the amount actually okay to use today. It moves as you log spending."
                )
            }
            item {
                SafeToSpendCard(
                    transactions = transactions,
                    budgets = budgets,
                    goals = savingsGoals,
                    bills = bills,
                    flexibleCash = financialSnapshot.flexible.toDouble(),
                    hide = hideBalances
                )
            }
            }


            // Pending Verification Engine List View (max 3, expandable)
            if (pendingTransactions.isNotEmpty() && "pending" !in hiddenSections) {
                item {
                    // Bulk bar: every "sure" row (history vouches ≥85%) confirms
                    // in one tap; unsure rows stay for human eyes.
                    val dashPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                    val sureRows = remember(pendingTransactions) {
                        pendingTransactions.filter {
                            com.pesaflow.app.data.ledger.ConfidenceMemory.effective(dashPrefs, it.merchant, it.confidenceScore) >= com.pesaflow.app.data.parsers.PendingPolicy.SURE_CONFIDENCE
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pending (${pendingTransactions.size}) 🔔", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        // Scrollable actions: "Remove duplicates + Confirm all
                        // sure (225) + View all" crushed into one row on narrow
                        // screens, stretching button text off-screen.
                        Row(
                            modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pendingTransactions.size >= 2) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val n = viewModel.removeDuplicatePending()
                                        snackbar.currentSnackbarData?.dismiss()
                                        snackbar.showSnackbar(
                                            if (n == 0) "No duplicates — queue is clean."
                                            else "$n duplicate${if (n == 1) "" else "s"} removed.",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }) { Text("Remove duplicates") }
                            }
                            if (sureRows.isNotEmpty()) {
                                TextButton(onClick = {
                                    viewModel.approveAllPending(sureRows)
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val r = snackbar.showSnackbar("${sureRows.size} confirmed — history vouched.", "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
                                        if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                                    }
                                }) { Text("Confirm all sure (${sureRows.size})") }
                            }
                            TextButton(onClick = { showAllPending = !showAllPending }) {
                                Text(if (showAllPending) "Less" else "View all")
                            }
                        }
                    }
                }
                // Unsure-first: rows needing human eyes float up; sure rows sink
                // toward the one-tap bulk button instead of hogging top slots.
                items(orderedPending.take(if (showAllPending) Int.MAX_VALUE else 3)) { pending ->
                    // Pill taps dismiss the keyboard: typing a category then
                    // tapping a suggestion left the keyboard shoving the card.
                    val keyboard = LocalSoftwareKeyboardController.current
                    var editCat by remember(pending.id) { mutableStateOf(pending.category) }
                    // Type can be wrong at parse time (P2P-to-self, withdrawals) —
                    // fix it here instead of delete-and-retype.
                    var editType by remember(pending.id) { mutableStateOf(pending.type) }
                    // Dismiss-first + Short: M3 Long lingers 10s and rapid
                    // confirms queued 20s+ of banner over the bottom nav.
                    // Undo stays available — just inside a 4s window.
                    fun snack(msg: String) {
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val r = snackbar.showSnackbar(msg, "Undo", withDismissAction = true, duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New SMS Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("KSh ${pending.amount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Merchant: ${pending.merchant}", style = MaterialTheme.typography.bodyMedium)
                            Text("Suggested Category: ${pending.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Calibrated badge: your history vouches for this merchant.
                            val calPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            val effConf = remember(pending) {
                                com.pesaflow.app.data.ledger.ConfidenceMemory.effective(calPrefs, pending.merchant, pending.confidenceScore)
                            }
                            if (effConf >= 0.85f) {
                                Text("✓ Sure — matches your history (${(effConf * 100).toInt()}%).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            } else if (pending.confidenceScore < 0.75f) {
                                Text("⚠️ ${(pending.confidenceScore * 100).toInt()}% sure — check category and type before confirming.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            OutlinedTextField(
                                value = editCat,
                                onValueChange = { editCat = it },
                                label = { Text("Confirm as category") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Tap-to-pick: learned + inferred categories as chips so
                            // correct rows never need the keyboard at all.
                            val quickPicks = listOfNotNull(
                                com.pesaflow.app.data.ledger.CategoryMemory.lookup(calPrefs, pending.merchant),
                                com.pesaflow.app.data.parsers.MpesaParser.inferCategory(pending.merchant, editType).takeIf { it != "Other" }
                            ).distinct().take(3)
                            if (quickPicks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    quickPicks.forEach { c ->
                                        FilterChip(selected = editCat == c, onClick = { keyboard?.hide(); editCat = c }, label = { Text(c) })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            com.pesaflow.app.ui.theme.SegChoice(
                                options = listOf(
                                    com.pesaflow.app.ui.theme.SegOption("EXPENSE", "Spent", "−"),
                                    com.pesaflow.app.ui.theme.SegOption("INCOME", "Received", "+"),
                                    com.pesaflow.app.ui.theme.SegOption("SAVING", "Saved", "◉"),
                                    com.pesaflow.app.ui.theme.SegOption("TRANSFER", "Moved", "⇄")
                                ),
                                selected = editType.name,
                                onSelect = {
                                    keyboard?.hide()
                                    editType = runCatching { TransactionType.valueOf(it) }.getOrDefault(editType)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    viewModel.rejectPending(pending)
                                    snack("Ignored — pending removed.")
                                }) { Text("Ignore", color = MaterialTheme.colorScheme.error) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.approvePending(pending, editCat.ifBlank { pending.category }, editType)
                                    snack("Saved to ledger.")
                                }) { Text("Confirm") }
                            }
                        }
                    }
                }
            }


            // Category spending breakdown (top 5)
            if (transactions.any { it.type == TransactionType.EXPENSE && !it.isSample }) {
                item {
                    val catSpending = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }
                        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                        .entries.sortedByDescending { it.value }.take(5)
                    if (catSpending.isNotEmpty()) {
                        PpCard(kind = PpCardKind.LARGE) {
                            Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.md)) {
                                PpSectionHeader(title = "Spending by category")
                                val maxCat = catSpending.firstOrNull()?.value ?: 1.0
                                catSpending.forEach { (cat, amt) ->
                                    val barWidth = (amt / maxCat).coerceIn(0.0, 1.0).toFloat()
                                    Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.xs)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                "${categoryEmoji(cat)} $cat",
                                                style = ppTypography.labelLarge,
                                                color = ppColors.textPrimary
                                            )
                                            Text(
                                                "KSh ${amt.toInt()}",
                                                style = ppTypography.financialSmall,
                                                color = ppColors.textPrimary
                                            )
                                        }
                                        com.pesaflow.app.ui.theme.PpProgress(fraction = barWidth)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // See insights teaser
            item {
                PpCard(kind = PpCardKind.INFO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Spending Insights 💡", style = ppTypography.h3, color = ppColors.textPrimary)
                            Text(
                                "Charts, trends and advice from your data",
                                style = ppTypography.bodySmall,
                                color = ppColors.textTertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(ppSpacing.md))
                        com.pesaflow.app.ui.theme.PpSecondaryButton(text = "See insights", onClick = { onNavigate(NavRoutes.INSIGHTS) })
                    }
                }
            }


        }
    }
    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }
    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false },
            title = { Text("Tune your home", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick the sections you want to see.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("safe" to "Safe-to-spend", "pending" to "Pending approvals", "recent" to "Recent activity").forEach { (key, label) ->
                        FilterChip(selected = key !in hiddenSections, onClick = { viewModel.toggleSection(key) }, label = { Text(label) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomize = false }) { Text("Done") } }
        )
    }
    // Coach marks: log → approve → safe-spend. Dismissed forever after step 3.
    // Gated on an explicit false: while DataStore is still loading (null),
    // show nothing rather than flashing the tour at opted-out users.
    if (coachDoneByPrefs == false && coachStep < 3) {
        val coachTitle = when (coachStep) {
            0 -> "1 · Log in seconds ⚡"
            1 -> "2 · Approve, don't type ✅"
            else -> "3 · Spend what's safe 🎯"
        }
        val coachBody = when (coachStep) {
            0 -> "Tap + below for any expense. Amount, where, done — under 5 seconds."
            1 -> "M-Pesa texts land here as pending. Sure ones confirm all at once — the rest get your eyes, one by one."
            else -> "Safe-to-spend is your one number: what's actually okay to use today."
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.BottomCenter) {
            Card(Modifier.fillMaxWidth().padding(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(coachTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(coachBody, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scope.launch { AppPrefs.setCoachDone(coachContext) }; coachStep = 99 }) { Text("Skip tour") }
                        Button(onClick = {
                            if (coachStep >= 2) { scope.launch { AppPrefs.setCoachDone(coachContext) }; coachStep = 99 }
                            else coachStep++
                        }) { Text(if (coachStep >= 2) "Start" else "Next") }
                    }
                }
            }
        }
    }
    }
}


// Horizontal feature rail: glanceable icons with labels — deliberately not a
// list like More. Every icon is a real button for screen readers.
@Composable
private fun ShortcutRail(onNavigate: (String) -> Unit) {
    val shortcuts = listOf(
        Triple(NavRoutes.TRANSACTIONS, "Ledger", Icons.Filled.List),
        Triple(NavRoutes.BUDGETS, "Budgets", Icons.Filled.Star),
        Triple(NavRoutes.SAVINGS, "Savings", Icons.Filled.Savings),
        Triple(NavRoutes.BILLS, "Bills", Icons.Filled.Home),
        Triple(NavRoutes.MEALS, "Meals", Icons.Filled.Favorite),
        Triple(NavRoutes.REPORTS, "Reports", Icons.Filled.Info),
        Triple(NavRoutes.NETWORTH, "Net Worth", Icons.Filled.AccountBox),
        Triple(NavRoutes.BUDDY, "Buddy", Icons.Filled.Face),
        Triple(NavRoutes.REVIEW, "Review", Icons.Filled.CheckCircle)
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Jump to", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shortcuts) { (route, label, icon) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Open $label",
                        onClick = { onNavigate(route) }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}