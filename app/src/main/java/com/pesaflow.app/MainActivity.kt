package com.pesaflow.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.ui.income.IncomeScreen
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.review.ReviewScreen
import com.pesaflow.app.ui.savings.SavingsScreen
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSettingsNeutral
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.ui.bills.BillsScreen
import com.pesaflow.app.ui.budgets.BudgetsScreen
import com.pesaflow.app.ui.dashboard.DashboardScreen
import com.pesaflow.app.ui.dashboard.PesaBuddyAssistant
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.debt.DebtTrackingScreen
import com.pesaflow.app.ui.insights.InsightsScreen
import com.pesaflow.app.ui.onboarding.OnboardingScreen
import com.pesaflow.app.ui.reports.NetWorthScreen
import com.pesaflow.app.ui.reports.ReportsScreen
import com.pesaflow.app.ui.search.SearchScreen
import androidx.compose.material3.FloatingActionButton
import com.pesaflow.app.ui.semester.SemesterScreen
import com.pesaflow.app.ui.transactions.TransactionsScreen
import com.pesaflow.app.ui.settings.SettingsScreen
import com.pesaflow.app.ui.stock.BelongingsScreen
import com.pesaflow.app.ui.stock.KitchenScreen
import com.pesaflow.app.ui.theme.PesaFlowTheme
import com.pesaflow.app.ui.university.MealPlannerScreen
import com.pesaflow.app.ui.university.UniversityScreen
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.analytics.AnalyticsScreen
import com.pesaflow.app.ui.dashboard.weekdayProfile
import com.pesaflow.app.ui.dashboard.factorForToday
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.TransactionType as TxType
import com.pesaflow.app.ui.notifications.NotificationChecker
import com.pesaflow.app.ui.exports.ExportScreen
import com.pesaflow.app.ui.recurring.RecurringScreen
import com.pesaflow.app.ui.goals.GoalsScreen
import com.pesaflow.app.ui.contacts.ContactBookScreen
import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.ContactEntry


class MainActivity : ComponentActivity() {


    private val viewModel: FinanceViewModel by viewModels()


    private var onboarded by mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch splash (navy + icon) instead of a color flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Edge-to-edge: cinematic canvases draw behind status + nav bars;
        // content layers take inset padding themselves.
        WindowCompat.setDecorFitsSystemWindows(window, false)


        // Process intents from external clipboard sources or system shares
        handleIncomingSharedText(intent)


        // NOTE: no permission requests here on purpose. SMS is requested on the
        // onboarding SMS step (with rationale), notifications on first report
        // preview tap. Cold-start prompts get auto-denied and hurt Play review.


        val prefs = getPreferences(MODE_PRIVATE)
        onboarded = prefs.getBoolean("onboarding_done", false)
        viewModel.setUserName(prefs.getString("user_name", "") ?: "")


        // Re-arm one-time reminder chains (breakfast/lunch/night/Sunday): if Android killed
        // them, every app open restores the next firing. Idempotent by unique name.
        val nprefs = getSharedPreferences("pesaflow_prefs", MODE_PRIVATE)
        // Backfill for existing users: lunch_scan was introduced after they
        // onboarded, so the key is absent (not off) — default it ON once.
        if (!nprefs.contains("lunch_scan")) {
            nprefs.edit().putBoolean("lunch_scan", true).apply()
        }
        if (nprefs.getBoolean("breakfast_reminder", false)) ReminderScheduler.scheduleBreakfast(this)
        if (nprefs.getBoolean("lunch_reminder", false)) ReminderScheduler.scheduleLunch(this)
        if (nprefs.getBoolean("night_report", false)) ReminderScheduler.scheduleNightReport(this)
        if (nprefs.getBoolean("sunday_report", false)) ReminderScheduler.scheduleSundayReport(this)
        if (nprefs.getBoolean("lunch_scan", false)) ReminderScheduler.scheduleLunchScan(this)
        // Core chains have no off-switch: re-arm every cold start so the app
        // works for years, not one day. Daily/weekly stay behind user toggles.
        ReminderScheduler.scheduleDailyDigest(this)
        ReminderScheduler.scheduleMonthlyReport(this)
        ReminderScheduler.scheduleBudgetCrossingAlert(this)


        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            PesaFlowTheme(mode = themeMode) {
                if (onboarded) {
                    PesaFlowAppNav(viewModel = viewModel)
                } else {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onDone = {
                            getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("onboarding_done", true)
                                .putString("user_name", viewModel.userName.value)
                                .apply()
                            onboarded = true
                        }
                    )
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingSharedText(intent)
    }


    private fun handleIncomingSharedText(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                // Everything shared lands in Pending for Confirm / Ignore —
                // nothing unverified ever touches the permanent ledger.
                val parsedMpesa = MpesaParser.parseMessage(sharedText)
                if (parsedMpesa != null) {
                    viewModel.queueSharedTransaction(parsedMpesa)
                } else {
                    viewModel.queueSharedText(sharedText)
                }
            }
        }
    }
}


// Bottom navigation across the feature screens. State-based (no nav library)
// so no new dependency is required.
@Composable
private fun PesaFlowAppNav(viewModel: FinanceViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var moreSection by remember { mutableStateOf<String?>(null) }
    var showSpeedDial by remember { mutableStateOf(false) }
    var quickAddType by remember { mutableStateOf<TransactionType?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val demoPrefs = remember(context) {
        context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    }
    var demoMode by remember(demoPrefs) { mutableStateOf(demoPrefs.getBoolean("demo_mode", false)) }
    DisposableEffect(demoPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "demo_mode") demoMode = demoPrefs.getBoolean("demo_mode", false)
        }
        demoPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { demoPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val rows = CsvImporter.parseCsvDataAuto(text)
                viewModel.importTransactions(rows) { added, skipped ->
                    importResult = "Imported $added transaction(s)" +
                        (if (skipped > 0) " · $skipped duplicate(s) skipped" else "") + " ✅"
                }
            } catch (e: Exception) {
                importResult = "Import failed: ${e.message}"
            }
        }
    }

    BackHandler(enabled = selectedTab == 4 && moreSection != null) {
        moreSection = null
    }

    val transactions by viewModel.allTransactions.collectAsState()

    val budgets by viewModel.budgets.collectAsState()

    val goals by viewModel.savingsGoals.collectAsState()

    val bills by viewModel.bills.collectAsState()

    val debts by viewModel.debts.collectAsState()

    val profile by viewModel.universityProfile.collectAsState()

    val notifMetrics = remember(transactions, budgets) {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val dayStart = (cal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000
        val expenses = transactions.filter { it.type == com.pesaflow.app.data.models.TransactionType.EXPENSE && !it.isSample && it.dateTimestamp <= now }
        val todaySpend = expenses.filter { it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val yesterdaySpend = expenses.filter { it.dateTimestamp >= dayStart - dayMs && it.dateTimestamp < dayStart }.sumOf { it.amount }
        val spentByCategory = expenses.filter { it.dateTimestamp >= dayStart - 30L * dayMs }
            .groupBy { it.category.lowercase() }
            .mapValues { e -> e.value.sumOf { it.amount } }
        // Same active-budget rule as SafeSpendMath: latest active ALL wins,
        // else active category sum. firstOrNull froze the oldest ALL forever.
        val monthlyAll = com.pesaflow.app.data.finance.masterOrCategoryTotal(
            budgets, com.pesaflow.app.data.models.BudgetType.MONTHLY, now
        ).takeIf { it > 0 }
        val dailyExplicit = com.pesaflow.app.data.finance.masterOrCategoryTotal(
            budgets, com.pesaflow.app.data.models.BudgetType.DAILY, now
        ).takeIf { it > 0 }
        val dailyTarget = dailyExplicit ?: (monthlyAll?.div(30.0) ?: 500.0)
        val pace = weekdayProfile(
            expenses.map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) },
            now
        )
        val weekdayFactor = factorForToday(pace, now)
        Triple(todaySpend, yesterdaySpend, spentByCategory) to (dailyTarget to weekdayFactor)
    }
    val notifTodaySpend = notifMetrics.first.first
    val notifYesterdaySpend = notifMetrics.first.second
    val notifSpentByCategory = notifMetrics.first.third
    val notifDailyTarget = notifMetrics.second.first
    val notifWeekdayFactor = notifMetrics.second.second


    Scaffold(
        topBar = {
            if (demoMode) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "DEMO MODE",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "Sample data only — not your financial records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // The + lives on Home and Money only — it must not follow
            // the user onto Plan, Insight or You.
            if (selectedTab == 0 || selectedTab == 2) {
            FloatingActionButton(
                onClick = { showSpeedDial = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add transaction")
            }
            }
        },
        bottomBar = {
            // Home = what matters now · Plan = what am I planning · Money = what happened
            // Insight = what is changing · You = how PesaFlow understands me.
            com.pesaflow.app.ui.theme.PpBottomBar(
                items = listOf(
                    com.pesaflow.app.ui.theme.PpNavItem("Home", Icons.Filled.Home),
                    com.pesaflow.app.ui.theme.PpNavItem("Plan", Icons.Filled.AccountBalanceWallet),
                    com.pesaflow.app.ui.theme.PpNavItem("Money", Icons.AutoMirrored.Filled.ReceiptLong),
                    com.pesaflow.app.ui.theme.PpNavItem("Insight", Icons.Filled.Lightbulb),
                    com.pesaflow.app.ui.theme.PpNavItem("You", Icons.Filled.Person)
                ),
                selectedIndex = selectedTab,
                onSelect = { i ->
                    selectedTab = i
                    if (i == 4) moreSection = null
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it },
                    onNavigate = { route ->
                        when (route) {
                            NavRoutes.SEARCH -> { selectedTab = 4; moreSection = NavRoutes.SEARCH }
                            NavRoutes.INSIGHTS -> { selectedTab = 3 }
                            NavRoutes.TRANSACTIONS -> { selectedTab = 2 }
                            NavRoutes.BUDGETS -> { selectedTab = 1 }
                            NavRoutes.SAVINGS -> { selectedTab = 4; moreSection = NavRoutes.SAVINGS }
                            NavRoutes.BILLS -> { selectedTab = 4; moreSection = NavRoutes.BILLS }
                            NavRoutes.MEALS -> { selectedTab = 4; moreSection = NavRoutes.MEALS }
                            NavRoutes.REPORTS -> { selectedTab = 4; moreSection = NavRoutes.REPORTS }
                            NavRoutes.NETWORTH -> { selectedTab = 4; moreSection = NavRoutes.NETWORTH }
                            NavRoutes.BUDDY -> { selectedTab = 4; moreSection = NavRoutes.BUDDY }
                            NavRoutes.REVIEW -> { selectedTab = 4; moreSection = NavRoutes.REVIEW }
                            NavRoutes.ADD_EXPENSE -> { quickAddType = TransactionType.EXPENSE }
                        }
                    }
                )
                1 -> BudgetsScreen(viewModel = viewModel)
                2 -> TransactionsScreen(
                    viewModel = viewModel,
                    onQuickAdd = { quickAddType = it }
                )
                3 -> InsightsScreen(viewModel = viewModel)
                else -> when (moreSection) {
                    NavRoutes.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    NavRoutes.DEBT -> DebtTrackingScreen(viewModel = viewModel)
                    NavRoutes.SEARCH -> SearchScreen(viewModel = viewModel)
                    NavRoutes.UNIVERSITY -> UniversityScreen(viewModel = viewModel)
                    NavRoutes.SEMESTER -> SemesterScreen(viewModel = viewModel, onNavigate = { route ->
                        when (route) {
                            NavRoutes.BILLS -> { moreSection = NavRoutes.BILLS }
                            NavRoutes.DEBT -> { moreSection = NavRoutes.DEBT }
                            NavRoutes.MEALS -> { moreSection = NavRoutes.MEALS }
                            NavRoutes.UNIVERSITY -> { moreSection = NavRoutes.UNIVERSITY }
                        }
                    })
                    NavRoutes.MEALS -> MealPlannerScreen(viewModel = viewModel)
                    NavRoutes.BILLS -> BillsScreen(viewModel = viewModel)
                    // More → Budgets was a dead tap (no branch → fell back to
                    // the More list). Same screen as the Budgets tab.
                    NavRoutes.BUDGETS -> BudgetsScreen(viewModel = viewModel)
                    NavRoutes.INCOME -> IncomeScreen(viewModel = viewModel)
                    NavRoutes.BUDDY -> PesaBuddyAssistant(viewModel = viewModel)
                    NavRoutes.NETWORTH -> NetWorthScreen(viewModel = viewModel)
                    NavRoutes.SAVINGS -> SavingsScreen(viewModel = viewModel)
                    NavRoutes.REPORTS -> ReportsScreen(viewModel = viewModel)
                    NavRoutes.INSIGHTS -> InsightsScreen(viewModel = viewModel)
                    NavRoutes.THINGS -> BelongingsScreen(viewModel = viewModel)
                    NavRoutes.KITCHEN -> KitchenScreen(viewModel = viewModel)
                    NavRoutes.REVIEW -> ReviewScreen(viewModel = viewModel)
                    NavRoutes.ANALYTICS -> AnalyticsScreen(transactions = transactions)
                    NavRoutes.NOTIFICATIONS -> NotificationChecker(
                        bills = bills, debts = debts, budgets = budgets, goals = goals,
                        spentByCategory = notifSpentByCategory,
                        todaySpend = notifTodaySpend,
                        yesterdaySpend = notifYesterdaySpend,
                        dailyTarget = notifDailyTarget,
                        weekdayFactor = notifWeekdayFactor
                    )
                    NavRoutes.EXPORT -> ExportScreen(
                        transactions = transactions, budgets = budgets, goals = goals,
                        bills = bills, debts = debts, profile = profile
                    )
                    NavRoutes.RECURRING -> RecurringScreen(
                        transactions = transactions,
                        onMakeBill = { p ->
                            viewModel.addBill(
                                p.merchant, p.amount, p.nextExpectedDate, p.category,
                                com.pesaflow.app.data.analytics.billFrequencyFor(p.medianIntervalDays)
                            )
                        }
                    )
                    NavRoutes.GOALS -> GoalsScreen(goals = goals, transactions = transactions)
                    NavRoutes.CONTACTS -> {
                        val prefs = context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                        val contacts = ContactBook.readAll(prefs)
                        val contactRuleScanStatus by viewModel.contactRuleScanStatus.collectAsState()
                        ContactBookScreen(
                            contacts = contacts,
                            reprocessStatus = contactRuleScanStatus,
                            onSave = { name, display, rel, cat, scope, notes, matchTerms ->
                                val saved = ContactBook.save(prefs, name, display, rel, cat, scope, notes, matchTerms)
                                if (saved) {
                                    com.pesaflow.app.data.ledger.saveContactMemory(
                                        prefs, name, rel, cat, scope, matchTerms
                                    )
                                    viewModel.reprocessContactSmsHistory()
                                }
                            },
                            onDelete = { name ->
                                ContactBook.delete(prefs, name)
                                com.pesaflow.app.data.ledger.deleteContactMemory(prefs, name)
                            }
                        )
                    }
                    else -> MoreScreen(onSelect = { moreSection = it })
                }
            }

            quickAddType?.let { type ->
                QuickAddDialog(viewModel = viewModel, defaultType = type, onDismiss = { quickAddType = null })
            }

            if (showSpeedDial) {
                AlertDialog(
                    onDismissRequest = { showSpeedDial = false },
                    title = { Text("Add") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.EXPENSE }, modifier = Modifier.fillMaxWidth()) {
                                Text("− Spend", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; quickAddType = TransactionType.INCOME }, modifier = Modifier.fillMaxWidth()) {
                                Text("+ Income", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; selectedTab = 3 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Parse text (NLP)", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSpeedDial = false; csvPicker.launch("text/*") }, modifier = Modifier.fillMaxWidth()) {
                                Text("Import CSV", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSpeedDial = false }) { Text("Close") } }
                )
            }

            importResult?.let { msg ->
                AlertDialog(
                    onDismissRequest = { importResult = null },
                    title = { Text("CSV Import") },
                    text = { Text(msg) },
                    confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } }
                )
            }
        }
    }
}


@Composable
private fun MoreScreen(onSelect: (String) -> Unit) {
    data class Entry(
        val section: String,
        val icon: ImageVector,
        val title: String,
        val subtitle: String,
        val route: String
    )
    val entries = listOf(
        Entry("Money", Icons.Filled.Menu, "Analytics", "Category trends, comparisons and heatmaps", NavRoutes.ANALYTICS),
        Entry("Money", Icons.Filled.DateRange, "Reports", "Daily, weekly, monthly and annual summaries", NavRoutes.REPORTS),
        Entry("Money", Icons.Filled.AccountBalance, "Net Worth", "Cash, savings, investments and debts", NavRoutes.NETWORTH),
        Entry("Money", Icons.Filled.AccountBalanceWallet, "Income", "Track money sources and expected payments", NavRoutes.INCOME),
        Entry("Money", Icons.Filled.Search, "Search", "Find a transaction by merchant or category", NavRoutes.SEARCH),
        Entry("Planning", Icons.Filled.Home, "Bills", "Due dates, recurring bills and payer details", NavRoutes.BILLS),
        Entry("Planning", Icons.Filled.AccountBox, "Debt Tracking", "Money owed, borrowed and repaid", NavRoutes.DEBT),
        Entry("Planning", Icons.Filled.Savings, "Savings", "Savings goals and progress", NavRoutes.SAVINGS),
        Entry("Planning", Icons.Filled.Star, "Goal Planner", "Goal pace, risk and suggestions", NavRoutes.GOALS),
        Entry("Planning", Icons.Filled.DateRange, "Recurring", "Spending patterns and monthly commitments", NavRoutes.RECURRING),
        Entry("Student life", Icons.Filled.DateRange, "Semester", "Term plan, runway and fees", NavRoutes.SEMESTER),
        Entry("Student life", Icons.Filled.Favorite, "Meal Planner", "Plan meals with your food budget", NavRoutes.MEALS),
        Entry("Student life", Icons.Filled.DateRange, "Kitchen Stock", "Track staple quantities and refill needs", NavRoutes.KITCHEN),
        Entry("Student life", Icons.Filled.ShoppingCart, "My Things", "Track what you own and still need", NavRoutes.THINGS),
        Entry("Student life", Icons.Filled.Star, "University", "Campus, timetable and allowance planning", NavRoutes.UNIVERSITY),
        Entry("Tools", Icons.Filled.Star, "Export & Backup", "Export transactions or back up your data", NavRoutes.EXPORT),
        Entry("Tools", Icons.Filled.Person, "Contact Book", "Remember people and categorize transactions", NavRoutes.CONTACTS),
        Entry("Tools", Icons.Filled.CheckCircle, "Weekly review", "Review uncategorized items and duplicates", NavRoutes.REVIEW),
        Entry("App", Icons.Filled.Face, "PesaBuddy", "Ask questions about your saved money data", NavRoutes.BUDDY),
        Entry("App", Icons.Filled.Favorite, "Notifications", "Configure reminders and financial alerts", NavRoutes.NOTIFICATIONS),
        Entry("App", Icons.Filled.Settings, "Settings", "Language, appearance, notifications and data", NavRoutes.SETTINGS)
    )
    var featureQuery by remember { mutableStateOf("") }
    val query = featureQuery.trim()
    val filtered = entries.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) ||
            it.subtitle.contains(query, ignoreCase = true) ||
            it.section.contains(query, ignoreCase = true)
    }
    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSettingsNeutral, bgRes = R.drawable.bg_settings_neutral)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("More features", style = com.pesaflow.app.ui.theme.ppTypography.h1, color = com.pesaflow.app.ui.theme.ppColors.textPrimary)
            Text("Find student tools and account settings", style = com.pesaflow.app.ui.theme.ppTypography.bodyMedium, color = com.pesaflow.app.ui.theme.ppColors.textTertiary)
            androidx.compose.material3.OutlinedTextField(
                value = featureQuery,
                onValueChange = { featureQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search features") },
                trailingIcon = if (featureQuery.isNotBlank()) {
                    {
                        TextButton(onClick = { featureQuery = "" }) { Text("Clear") }
                    }
                } else null
            )
            if (filtered.isEmpty()) {
                androidx.compose.material3.Card(
                    colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No features found", fontWeight = FontWeight.SemiBold)
                        Text("Try another search, or clear the search to browse all tools.")
                    }
                }
            } else {
                filtered.groupBy { it.section }.forEach { (section, sectionEntries) ->
                    MoreSection(section) {
                        sectionEntries.forEach { entry ->
                            MoreRow(entry.icon, entry.title, entry.subtitle) { onSelect(entry.route) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
            color = com.pesaflow.app.ui.theme.ppColors.gold,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}


@Composable
private fun MoreRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = com.pesaflow.app.ui.theme.ppShapes.cardCompact,
        colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.pesaflow.app.ui.theme.ppColors.border)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    title,
                    style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                    color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                )
            },
            supportingContent = {
                Text(
                    subtitle,
                    style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                    color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                )
            },
            leadingContent = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.pesaflow.app.ui.theme.ppColors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = com.pesaflow.app.ui.theme.ppColors.brightBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
