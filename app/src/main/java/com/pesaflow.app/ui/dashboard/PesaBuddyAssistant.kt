package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.*
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.SkinBuddy
import com.pesaflow.app.ui.theme.TintBuddyTwilight
import com.pesaflow.app.ui.theme.skinCardColor
import com.pesaflow.app.R


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PesaBuddyAssistant(viewModel: FinanceViewModel) {
    val availableBalance by viewModel.availableBalance.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpenses by viewModel.monthlyExpenses.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    var chatFocused by remember { mutableStateOf(false) }


    var chatInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateOf<List<ChatMessage>>(emptyList()) }


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBuddyTwilight, bgRes = R.drawable.bg_buddy_twilight, blurRadius = if (chatFocused) 4f else 0f)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PesaBuddy 🤖", fontWeight = FontWeight.Bold)
                        Text("online • answers from your data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(onClick = { messages.value = emptyList() }) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chat Messages Area
            AtmosphereBand(
                workspace = AtmoWorkspace.BUDDY,
                title = "Ask anything",
                subtitle = "Answers from your data",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            ScrollableChatArea(messages = messages.value, modifier = Modifier.weight(1f))


            // Quick suggestions (tap to ask)
            if (messages.value.isEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("How am I doing?", "Top spends?", "Run out?", "Bills due?", "Afford 500?", "Laptop by December?", "My stock?", "What I lack?").forEach { s ->
                        AssistChip(onClick = { chatFocused = true; processUserInput(s, viewModel, messages) }, label = { Text(s) })
                    }
                }
            }


            // Input Area
            ChatInputArea(
                chatInput = chatInput,
                onFocusChange = { chatFocused = it },
                onSend = { userInput ->
                    processUserInput(userInput, viewModel, messages)
                    chatInput = ""
                    chatFocused = false
                }
            )
        }
    }
    }
}


@Composable
fun ChatInputArea(
    chatInput: String,
    onSend: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {}
) {
    var localInput by remember { mutableStateOf(chatInput) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = localInput,
            onValueChange = { localInput = it },
            placeholder = { Text("Ask PesaBuddy...") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).onFocusChanged { onFocusChange(it.isFocused) }
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (localInput.isNotBlank()) {
                    onSend(localInput)
                    localInput = ""
                }
            },
            shape = CircleShape,
            contentPadding = PaddingValues(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}


@Composable
fun ScrollableChatArea(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = state,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(messages) { message ->
            ChatMessageBubble(message = message)
        }
    }
}


@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val time = remember(message.timestamp) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (message.isUser) MaterialTheme.colorScheme.primary else skinCardColor(SkinBuddy),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}


enum class ChatMessageRole { USER, ASSISTANT }


data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)


fun processUserInput(
    userInput: String,
    viewModel: FinanceViewModel,
    messages: MutableState<List<ChatMessage>>,
    mlAssisted: Boolean = false
) {
    // Add user message to chat
    messages.value = listOf(
        ChatMessage(text = userInput, isUser = true),
        ChatMessage(text = "Thinking...", isUser = false)
    )

    // Intent layer: normalize (append-only synonyms, old branches keep matching),
    // follow-up memory ("and yesterday?"), close-tie disambiguation.
    val qRaw = userInput.lowercase()
    val q = BuddyBrain.normalize(qRaw)
    BuddyBrain.rewriteFollowUp(qRaw, q)?.let { return processUserInput(it, viewModel, messages) }
    val early: String? = BuddyBrain.disambiguate(q)
    // Trained-model assist: only when the rules draw a blank, once per
    // query. A sure model routes into its verified branch; anything else
    // falls through to the generic fallback exactly as before.
    if (early == null && !mlAssisted) {
        val ruleTop = BuddyBrain.classify(q).firstOrNull()?.conf ?: 0f
        if (ruleTop < 0.35f) {
            val ctx = viewModel.getApplication<android.app.Application>().applicationContext
            val suggestion = com.pesaflow.app.data.ml.MlIntentAssist.suggest(ctx, qRaw)
            if (suggestion != null && BuddyBrain.shouldMlAssist(ruleTop, suggestion.confidence)) {
                BuddyBrain.mlAssistExpansion(suggestion.label)?.let { expansion ->
                    return processUserInput("$userInput $expansion", viewModel, messages, mlAssisted = true)
                }
            }
        }
    }
    val txs = viewModel.allTransactions.value
    val nowCal = java.util.Calendar.getInstance()
    val nowMs = nowCal.timeInMillis
    val dayStart = (nowCal.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    // "This week" means the Monday-start calendar week, matching the
    // dashboard chart and every insight — not a rolling 7 days.
    val week = com.pesaflow.app.data.time.thisWeekRange(nowMs)
    fun inMonth(ts: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            c.get(java.util.Calendar.MONTH) == nowCal.get(java.util.Calendar.MONTH)
    }
    val monthTx = txs.filter { inMonth(it.dateTimestamp) && !it.isSample }
    val monthIncome = monthTx.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val monthExpense = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val todaySpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= dayStart }.sumOf { it.amount }
    val weekSpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in week && com.pesaflow.app.data.time.inPastOrNow(it.dateTimestamp, nowMs) }.sumOf { it.amount }
    val balance = viewModel.availableBalance.value
    val saved = viewModel.totalSavings.value
    val goals = viewModel.savingsGoals.value
    val belongings = viewModel.belongings.value
    val pantry = viewModel.kitchenStock.value
    val byCat = monthTx.filter { it.type == TransactionType.EXPENSE }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    val topCat = byCat.maxByOrNull { it.value }
    val budget = viewModel.budgets.value.firstOrNull { it.category == "ALL" }
    val daysLeft = nowCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - nowCal.get(java.util.Calendar.DAY_OF_MONTH) + 1
    val yesterdayStart = dayStart - 24L * 60 * 60 * 1000
    val yesterdaySpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= yesterdayStart && it.dateTimestamp < dayStart }.sumOf { it.amount }
    val openBills = viewModel.bills.value.filter { it.status != "PAID" }
    val openBillTotal = openBills.filter { it.paidBy == "ME" }.sumOf { it.amount }
    val externallyFundedBillTotal = openBills.filter { it.paidBy != "ME" }.sumOf { it.amount }
    val openDebts = viewModel.debts.value.filter { it.status != "PAID" }
    val openDebtTotal = openDebts.sumOf { it.amount }
    // Money already spoken for: unpaid bills + debts I owe. Runway/afford/safe
    // answer against what's actually free, never the raw balance.
    val iOweTotal = openDebts.filter { it.direction == "I_OWE" }.sumOf { it.amount }
    val committed = openBillTotal + iOweTotal
    val freeBalance = balance - committed
    val mealCount = viewModel.mealItems.value.size
    val foodBudgetAmt = viewModel.budgets.value.firstOrNull { it.category == "Food" }?.limitAmount
    val askedCat = byCat.keys.filter { it !in listOf("Food", "Transport", "Rent", "Airtime", "Data") }.firstOrNull { q.contains(it.lowercase()) }
    val nmRaw = viewModel.nickname.value.ifBlank { viewModel.userName.value }
    val nmEx = if (nmRaw.isNotBlank()) " $nmRaw" else ""
    val foodTotal = byCat["Food"] ?: 0.0
    val transportTotal = byCat["Transport"] ?: 0.0
    // Persona tracks: advice divides by setup — far commuters never hear
    // "walk", non-cooks never hear "cook at home".
    val persona = com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers())
    val farCommute = persona == com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR || persona == com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE
    val noCook = persona == com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK
    val response = early ?: when {
        q.contains("hello") || q == "hi" || q.contains("hey") || q.contains("habari") || q.contains("sasa") || q.contains("mambo") ->
            run {
                // Onboarding answers feed the greeting: your stated worry, if any.
                val worry = viewModel.getOnboardingAnswers().split("|")
                    .firstOrNull { it.startsWith("worry=") }
                    ?.removePrefix("worry=")?.takeIf { it.isNotBlank() }
                "Hey$nmEx! I'm PesaBuddy 👋." +
                    (if (worry != null) " I remember $worry worries you most — ask 'what should I buy first?' anytime." else "") +
                    " Ask me about spending, budgets, savings, stock or balances."
            }

        q.contains("help") || q.contains("what can you") || q.contains("how do i") || q.contains("unaeza") || q.contains("nisaidie") || q.contains("saidia") ->
            "I answer from your real records: today/yesterday/this week, income, biggest expense, any category ('how much shopping?'), budget status, safe daily spend, savings, balance, bills, debts, even your meal plan. Try 'give me a summary!'"

        q.contains("thank") || q.contains("asante") || q.contains("poa") ->
            "Karibu sana! 🎉 Keep tracking — small daily records beat big monthly guesses."

        q.contains("today") || q.contains("leo") ->
            if (txs.isEmpty()) "No transactions recorded yet — add your first expense and I'll track it here."
            else "Today ume-spend KSh ${todaySpend.toInt()} so far. Month total: KSh ${monthExpense.toInt()}."

        (q.contains("week") || q.contains("wiki")) && askedCat != null ->
            "$askedCat this week: KSh ${txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp in week && com.pesaflow.app.data.time.inPastOrNow(it.dateTimestamp, nowMs) && it.category.equals(askedCat, ignoreCase = true) }.sumOf { it.amount }.toInt()}."

        ((q.contains("yesterday") || q.contains("jana")) && askedCat != null) ->
            "Yesterday $askedCat: KSh ${txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= yesterdayStart && it.dateTimestamp < dayStart && it.category.equals(askedCat, ignoreCase = true) }.sumOf { it.amount }.toInt()}."

        q.contains("top spends") || q.contains("biggest expenses") || q.contains("largest expenses") || q.contains("orodha") ->
            run {
                val top5 = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample }.sortedByDescending { it.amount }.take(5)
                if (top5.isEmpty()) "No expenses yet — nothing to rank."
                else "Biggest hits: " + top5.joinToString("; ") { "${it.merchant.take(20)} KSh ${it.amount.toInt()}" } + "."
            }

        q.contains("run out") || q.contains("nitakwisha") || q.contains("how long will") ->
            run {
                if (txs.isEmpty()) "No data yet — log a week first."
                else {
                    val elapsed = com.pesaflow.app.data.time.daysElapsedInWeek(nowMs).coerceAtLeast(1)
                    val burn = if (weekSpend > 0) weekSpend / elapsed else monthExpense / 30
                    if (burn <= 0 || freeBalance <= 0) {
                        if (balance <= 0) "KSh ${balance.toInt()} left, no burn rate yet — keep logging."
                        else "KSh ${balance.toInt()} in, but KSh ${committed.toInt()} is already spoken for (bills + deni you owe) — free: KSh ${freeBalance.toInt()}."
                    } else "At ~KSh ${burn.toInt()}/day, free KSh ${freeBalance.toInt()} lasts ~${(freeBalance / burn).toInt()} days." +
                        (if (committed > 0) " (after KSh ${committed.toInt()} bills + deni.)" else "")
                }
            }

        q.contains("due this week") || q.contains("due soon") || q.contains("bills due") || q.contains("inadaiwa") ->
            run {
                val soon = openBills.filter { it.dueDate <= System.currentTimeMillis() + 7 * 24L * 60 * 60 * 1000 }.sortedBy { it.dueDate }
                if (soon.isEmpty()) "Nothing due in the next 7 days. 🎉"
                else "Due this week: " + soon.joinToString("; ") { "${it.name} KSh ${it.amount.toInt()}" } + "."
            }

        q.contains("week") || q.contains("wiki") || q.contains("7 days") ->
            if (txs.isEmpty()) "No transactions recorded yet."
            else "This week (Mon–today) ume-spend roughly KSh ${weekSpend.toInt()}. That's about KSh ${(weekSpend / com.pesaflow.app.data.time.daysElapsedInWeek(nowMs).coerceAtLeast(1)).toInt()} per day."

        q.contains("budget") || q.contains("bajeti") ->
            if (budget == null) "You haven't set a monthly budget yet — add one on the Budget tab (use category ALL) and I'll watch it for you."
            else {
                val pct = if (budget.limitAmount > 0) (monthExpense / budget.limitAmount * 100).toInt() else 0
                "Budget: KSh ${budget.limitAmount.toInt()} monthly. Spent KSh ${monthExpense.toInt()} ($pct%). " +
                    if (pct >= 100) "Umekross the line — cut non-essentials for the rest of the month. ⚠️"
                    else if (pct >= 80) "Careful — you're at $pct%. Slow down on variable spending."
                    else "Budget iko safe so far. 👌"
            }

        q.contains("safe") || q.contains("daily") || q.contains("per day") || q.contains("kila siku") || q.contains("can i spend") ->
            if (txs.isEmpty() && budget == null) "Add some income/expenses or set a budget first, then I'll compute your safe daily spend."
            else {
                val pool = if (budget != null) (budget.limitAmount - monthExpense).coerceAtLeast(0.0) else freeBalance.coerceAtLeast(0.0)
                "You can roughly spend KSh ${(pool / daysLeft).toInt()} per day for the remaining $daysLeft days." +
                    (if (budget == null && committed > 0) " (after KSh ${committed.toInt()} bills + deni.)" else "") +
                    " Hii ni estimate, not a guarantee."
            }

        q.contains("biggest") || q.contains("largest") || q.contains("most") || q.contains("mingi") || q.contains("kubwa") || q.contains("natumia pesa mingi") || q.contains("burn") || q.contains("wapi") ->
            if (topCat == null) "No expenses recorded yet — I can't rank what doesn't exist. Add a few transactions first."
            else "Your biggest expense this month is ${topCat.key}: KSh ${topCat.value.toInt()} out of KSh ${monthExpense.toInt()}. That's where saving starts. 💡"

        q.contains("balance") || q.contains("baki") || q.contains("niko na") || q.contains("remaining") || q.contains("left") || q.contains("nisalio") ->
            run {
                val appCtx = viewModel.getApplication<android.app.Application>().applicationContext
                val mp = com.pesaflow.app.data.parsers.readMpesaBalance(appCtx)
                "Available balance: KSh ${balance.toInt()}. This month: KSh ${monthIncome.toInt()} in, KSh ${monthExpense.toInt()} out." +
                    (mp?.let { " M-Pesa SMS says KSh ${it.first.toInt()} (${com.pesaflow.app.data.parsers.balanceAgeText(it.second, System.currentTimeMillis())})." } ?: "")
            }

        q.contains("afford") || q.contains("naeza") || q.contains("can i buy") ->
            run {
                val num = Regex("(\\d[\\d,]*)").find(q)?.value?.replace(",", "")?.toDoubleOrNull()
                    ?: BuddyBrain.extractAmount(q)
                val wctx = viewModel.getApplication<android.app.Application>().applicationContext
                val wnote = com.pesaflow.app.data.parsers.readMpesaBalance(wctx)?.let { " (wallet: KSh ${it.first.toInt()})" } ?: ""
                if (num == null) "Tell me the price — e.g. 'afford 500?' — and I'll check it against your balance and budget."
                else if (num <= freeBalance && (budget == null || monthExpense + num <= budget.limitAmount)) "Yes — KSh ${num.toInt()} fits: free KSh ${freeBalance.toInt()}" + (if (budget != null) " and inside budget. ✅" else ". ✅") + wnote
                else "Careful — KSh ${num.toInt()} vs free KSh ${freeBalance.toInt()}" + (if (committed > 0) " (KSh ${committed.toInt()} tied in bills + deni)" else " (balance KSh ${balance.toInt()})") + (if (budget != null) " and only KSh ${(budget.limitAmount - monthExpense).toInt()} budget left." else ".") + wnote + " Sleep on it? 😴"
            }

        q.contains("split") ->
            "Open the Semester tab → rent splitter: set roommates 1–8 and per-person share updates live. Any budget can also be shared from the Budget tab. 🤝"

        q.contains("transport") || q.contains("fare") || q.contains("nauli") || q.contains("matatu") || q.contains("boda") ->
            run {
                val facts = viewModel.userContextFacts.value
                val home = facts["housing.current"] ?: "home"
                val mode = facts["transport.primaryMode"] ?: "your usual mode"
                val stages = facts["transport.homeToCampus"]
                if (txs.isEmpty()) "No spending logged yet — I'll estimate transport once your ledger has data."
                else "Your usual route: $home → campus by $mode" +
                    (stages?.let { " via $it" } ?: "") +
                    ". Log a fare and I'll track the real cost."
            }

        q.contains("yesterday") || q.contains("jana") ->
            "Yesterday uli-spend KSh ${yesterdaySpend.toInt()}. Today so far: KSh ${todaySpend.toInt()}."

        q.contains("busy") || q.contains("free") || q.contains("lecture") || q.contains("timetable") || q.contains("darasa") ->
            run {
                val appCtx = viewModel.getApplication<android.app.Application>().applicationContext
                val week = com.pesaflow.app.data.schedule.WeekPlan.load(appCtx)
                if (week.values.all { it.isEmpty() }) "Timetable iko empty — set your lecture week under More → Meal Planner, then ask me. 🗓️"
                else {
                    val dayMap = listOf("monday" to "Mon", "tuesday" to "Tue", "wednesday" to "Wed", "thursday" to "Thu", "friday" to "Fri", "saturday" to "Sat", "sunday" to "Sun")
                    val named = dayMap.firstOrNull { q.contains(it.first) }?.second
                    if (named != null) {
                        val slots = week[named].orEmpty()
                        if (slots.isEmpty()) "$named uko free — bulk-cook evening, week sorted. 🍱"
                        else {
                            val tip = com.pesaflow.app.data.schedule.WeekPlan.suggestionFor(named, slots) ?: ""
                            "$named busy: ${slots.sorted().joinToString(", ")}. $tip"
                        }
                    } else if (q.contains("cook") || q.contains("free")) {
                        val free = com.pesaflow.app.data.schedule.WeekPlan.freeEvenings(appCtx)
                        if (free.isEmpty()) "Every evening busy — Sunday ndio bulk-cook day. 🍱"
                        else "Free evenings: ${free.joinToString(", ")} — best bulk-cook nights. 🍱"
                    } else {
                        val busiest = week.maxByOrNull { it.value.size }
                        "Heaviest: ${busiest?.key ?: "—"} (${busiest?.value?.size ?: 0} slots). Ask 'busy wednesday?' for a day."
                    }
                }
            }

        q.contains("summary") || q.contains("breakdown") || q.contains("split") || q.contains("report") || q.contains("overview") || q.contains("muhtasari") ->
            if (txs.isEmpty()) "No data yet — add transactions and I'll summarize them here."
            else {
                val top3 = byCat.entries.sortedByDescending { it.value }.take(3)
                    .joinToString("; ") { "${it.key} KSh ${it.value.toInt()}" }
                "Month so far: KSh ${monthIncome.toInt()} in, KSh ${monthExpense.toInt()} out, balance KSh ${balance.toInt()}. Top: $top3."
            }

        q.contains("how am i") || q.contains("nitakuwa aje") || q.contains("verdict") || q.contains("am i ok") || q.contains("naisonga") ->
            if (txs.isEmpty()) "No data yet — log income and spending for a week, then ask me again."
            else if (monthIncome > 0 && monthExpense > monthIncome) "Honestly$nmEx? You're spending above income by KSh ${(monthExpense - monthIncome).toInt()} — trim ${topCat?.key ?: "variable costs"} first. 🛑"
            else if (monthIncome > 0) "You're on track$nmEx: keeping KSh ${(monthIncome - monthExpense).toInt()} of KSh ${monthIncome.toInt()} income. Endelea hivyo! ✅"
            else "KSh ${monthExpense.toInt()} spent, no income logged — add income (tap + Income) for a real verdict."

        askedCat != null ->
            "Spending on $askedCat: KSh ${byCat[askedCat]!!.toInt()} this month."

        q.contains("bill") || q.contains("rent due") || q.contains("lipia") ->
            if (openBills.isEmpty()) "No open bills — nyumba iko sorted. 🎉"
            else "You have ${openBills.size} open bill(s): KSh ${openBillTotal.toInt()} marked for you and KSh ${externallyFundedBillTotal.toInt()} marked for someone else to cover. " +
                openBills.take(3).joinToString("; ") {
                    "${it.name} KSh ${it.amount.toInt()} (${when (it.paidBy) {
                        "ME" -> "you"
                        "PARENTS" -> "parents"
                        "SPONSOR" -> "sponsor"
                        "HELB" -> "HELB"
                        else -> "someone else"
                    }})"
                } + "."

        q.contains("debt") || q.contains("owe") || q.contains("borrow") || q.contains("madeni") || q.contains("deni") ->
            if (openDebts.isEmpty()) "No open debts on record. Clean slate! 🎉"
            else "Open debts: ${openDebts.size} totalling KSh ${openDebtTotal.toInt()}: " +
                openDebts.take(3).joinToString("; ") { "${it.person} KSh ${it.amount.toInt()}" } + "."

        q.contains("menu") || q.contains("meal") || q.contains("food budget") || q.contains("chakula") ->
            if (mealCount == 0) "No foods saved yet — open Meal Planner (More tab), add starch + mboga with prices, then generate a week menu."
            else "You have $mealCount foods saved" +
                (if (foodBudgetAmt != null) " with a KSh ${foodBudgetAmt.toInt()} monthly food budget (KSh ${(foodBudgetAmt / 30).toInt()}/day)." else ". Set a Food budget so menus stay under it.") +
                " Generate Day/Week/Month/Semester menus from the planner."

        q.contains("bye") || q.contains("later") || q.contains("baadaye") || q.contains("tutaonana") ->
            "Baadaye! 👋 Kumbuka: log it the moment you spend it."

        q.contains("cut") || q.contains("reduce") || q.contains("punguza") || q.contains("what can i save") ->
            run {
                val tips = mutableListOf<String>()
                if (foodTotal > monthExpense * 0.3) tips.add(
                    if (noCook) "Food is ${((foodTotal / monthExpense) * 100).toInt()}% of spending — kibanda lunch plates over fast food could save KSh ${(foodTotal * 0.2).toInt()}/month"
                    else "Food is ${((foodTotal / monthExpense) * 100).toInt()}% of spending — cooking at home 3x/week could save KSh ${(foodTotal * 0.2).toInt()}/month"
                )
                if (transportTotal > monthExpense * 0.2) tips.add(
                    if (farCommute) "Transport is high — off-peak travel or the early bus beats peak fares that run ~2x"
                    else "Transport is high — try shared rides or walking short distances"
                )
                if (byCat.any { it.key == "Kujibamba" && it.value > monthExpense * 0.1 }) tips.add("Kujibamba (grooming) is ${((byCat["Kujibamba"]!! / monthExpense) * 100).toInt()}% — cut back to save")
                if (byCat.any { it.key == "Airtime" && it.value > 500 }) tips.add("Airtime KSh ${byCat["Airtime"]!!.toInt()} — switch to WiFi bundles for data")
                if (tips.isEmpty()) tips.add("Your spending looks balanced! Try the 24-hour rule on non-essentials.")
                "Here's what to cut: ${tips.joinToString(". ")}."
            }

        q.contains("track") || q.contains("on track") || q.contains("am i") || q.contains("progress") ->
            if (budget == null) "No budget set — add one on the Budget tab and I'll track your progress. 📊"
            else {
                val pct = if (budget.limitAmount > 0) (monthExpense / budget.limitAmount * 100).toInt() else 0
                val daysInMonth = nowCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val dayOfMonth = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
                val expected = budget.limitAmount * dayOfMonth / daysInMonth
                val diff = monthExpense - expected
                if (monthExpense <= expected) "On track! You've spent KSh ${monthExpense.toInt()} vs KSh ${expected.toInt()} expected by day $dayOfMonth. $pct% of budget. ✅"
                else "Over by KSh ${diff.toInt()} (KSh ${monthExpense.toInt()} vs KSh ${expected.toInt()} expected). $pct% used with $daysLeft days left. ⚠️"
            }

        q.contains("compare") || q.contains("vs last") || q.contains("difference") ->
            run {
                val ref = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -1) }
                val lastMonthExp = txs.filter {
                    it.type == TransactionType.EXPENSE && !it.isSample &&
                        java.util.Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.let { c ->
                            c.get(java.util.Calendar.YEAR) == ref.get(java.util.Calendar.YEAR) &&
                                c.get(java.util.Calendar.MONTH) == ref.get(java.util.Calendar.MONTH)
                        }
                }.sumOf { it.amount }
                if (lastMonthExp <= 0) "No data from last month to compare — keep logging! 📊"
                else {
                    val diff = monthExpense - lastMonthExp
                    val pct = ((diff / lastMonthExp) * 100).toInt()
                    if (diff <= 0) "This month: KSh ${monthExpense.toInt()} vs last month KSh ${lastMonthExp.toInt()}. You're ${-pct}% down! 🎉"
                    else "This month: KSh ${monthExpense.toInt()} vs last month KSh ${lastMonthExp.toInt()}. You're up $pct% — watch ${topCat?.key ?: "spending"}. ⚠️"
                }
            }

        q.contains("all category") || q.contains("breakdown") || q.contains("every category") ->
            if (byCat.isEmpty()) "No expenses to break down yet."
            else {
                val lines = byCat.entries.sortedByDescending { it.value }.take(8)
                    .joinToString("; ") { "${it.key} KSh ${it.value.toInt()}" }
                "Your categories: $lines. Total KSh ${monthExpense.toInt()}."
            }

        q.contains("saving") || q.contains("saved") || q.contains("how much saved") ->
            if (goals.isEmpty()) "No savings goals set. Add one on the Savings tab to start tracking. 🎯"
            else {
                val totalTarget = goals.sumOf { it.targetAmount }
                val totalSaved = goals.sumOf { it.currentAmount }
                val pct = if (totalTarget > 0) (totalSaved / totalTarget * 100).toInt() else 0
                "You've saved KSh ${totalSaved.toInt()} of KSh ${totalTarget.toInt()} ($pct%) across ${goals.size} goals."
            }

        q.contains("food") && (q.contains("how much") || q.contains("spend")) ->
            "Food this month: KSh ${foodTotal.toInt()}." +
                if (foodBudgetAmt != null) " Budget KSh ${foodBudgetAmt.toInt()} — ${if (foodTotal <= foodBudgetAmt) "under budget ✅" else "over budget ⚠️"}"
                else ". Set a Food budget on the Budget tab."

        q.contains("rent") && (q.contains("how much") || q.contains("spend")) ->
            "Rent this month: KSh ${(byCat["Rent"] ?: 0.0).toInt()}."

        // Goal planning: "can I afford laptop by December?" / "saving for laptop 10000" / "laptop 10000 by dec"
        q.contains("by") && (q.contains("dec") || q.contains("jan") || q.contains("feb") || q.contains("mar") ||
            q.contains("apr") || q.contains("may") || q.contains("jun") || q.contains("jul") ||
            q.contains("aug") || q.contains("sep") || q.contains("oct") || q.contains("nov")) ->
            run {
                val targetMonth = when {
                    q.contains("dec") -> java.util.Calendar.DECEMBER
                    q.contains("jan") -> java.util.Calendar.JANUARY
                    q.contains("feb") -> java.util.Calendar.FEBRUARY
                    q.contains("mar") -> java.util.Calendar.MARCH
                    q.contains("apr") -> java.util.Calendar.APRIL
                    q.contains("may") -> java.util.Calendar.MAY
                    q.contains("jun") -> java.util.Calendar.JUNE
                    q.contains("jul") -> java.util.Calendar.JULY
                    q.contains("aug") -> java.util.Calendar.AUGUST
                    q.contains("sep") -> java.util.Calendar.SEPTEMBER
                    q.contains("oct") -> java.util.Calendar.OCTOBER
                    q.contains("nov") -> java.util.Calendar.NOVEMBER
                    else -> java.util.Calendar.DECEMBER
                }
                val price = Regex("(\\d[\\d,]*)").find(q)?.value?.replace(",", "")?.toDoubleOrNull()
                if (price == null || price <= 0) {
                    "Tell me the price and deadline — e.g. 'laptop 10000 by December' and I'll check if it's possible."
                } else {
                    val target = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.MONTH, targetMonth)
                        set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                        set(java.util.Calendar.MINUTE, 59)
                        set(java.util.Calendar.SECOND, 59)
                    }.timeInMillis
                    val now = System.currentTimeMillis()
                    val daysLeft = ((target - now) / (24L * 60 * 60 * 1000)).coerceAtLeast(1)
                    val savedAlready = goals.filter { it.targetTimestamp <= target }.sumOf { it.currentAmount }
                    val remaining = (price - savedAlready).coerceAtLeast(0.0)
                    val safePool = (budget?.let { (it.limitAmount - monthExpense).coerceAtLeast(0.0) } ?: balance.coerceAtLeast(0.0))
                    val surplusPerDay = (safePool / daysLeft).toInt()
                    val neededPerDay = (remaining / daysLeft).toInt()
                    if (remaining <= 0) {
                        "You've already saved KSh ${savedAlready.toInt()} — that covers KSh ${price.toInt()}! You can buy it now. ✅"
                    } else if (neededPerDay <= surplusPerDay && surplusPerDay > 0) {
                        "Yes, possible! Save KSh $neededPerDay/day for $daysLeft days. Your safe daily spend is KSh $surplusPerDay, so save the difference. 💪"
                    } else if (neededPerDay <= (balance / daysLeft).toInt()) {
                        "Tight but doable — save KSh $neededPerDay/day. You'd need to cut ${topCat?.key ?: "spending"} by that much. 🤔"
                    } else {
                        "KSh ${price.toInt()} by ${java.text.SimpleDateFormat("MMM", java.util.Locale.US).format(java.util.Date(target))} needs KSh $neededPerDay/day. That's more than your surplus of KSh $surplusPerDay/day. Consider extending the deadline or cutting costs. ⚠️"
                    }
                }
            }

        userInput.lowercase().contains("goal") || userInput.lowercase().contains("target") || userInput.lowercase().contains("saving for") ->
            if (goals.isEmpty()) "No savings goals set yet — add one on the Savings tab and I'll track your progress. 🎯"
            else {
                val summary = goals.joinToString("; ") { g ->
                    val pct = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount * 100).toInt() else 0
                    "${g.title}: KSh ${g.currentAmount.toInt()}/${g.targetAmount.toInt()} ($pct%)"
                }
                "Your goals: $summary"
            }

        userInput.lowercase().contains("salary") || userInput.lowercase().contains("income") ->
            run {
                val topIn = monthTx.filter { it.type == TransactionType.INCOME && !it.isOpening }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
                val appSources = viewModel.incomeSources.value
                val expected = appSources.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) }
                val declared = appSources.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { it.displayKind() + if (it.expectedAmount > 0) " " + it.expectedAmount.toInt() + " " + it.frequencyLabel() else "" }
                "This month's income: KSh ${viewModel.monthlyIncome.value.toInt()}." +
                    (topIn?.let { " Mostly: ${it.key} KSh ${it.value.toInt()}." } ?: " Log income to see where it comes from.") +
                    (if (expected > 0) " Expecting ~KSh ${expected.toInt()} (${declared ?: "declared sources"})." else "")
            }

        userInput.lowercase().contains("spend") || userInput.lowercase().contains("burn") ||
            userInput.lowercase().contains("expense") ->
            run {
                val top3 = byCat.entries.sortedByDescending { it.value }.take(3)
                "This month's expenses: KSh ${viewModel.monthlyExpenses.value.toInt()}." +
                    (if (top3.isEmpty()) "" else " Top: " + top3.joinToString(", ") { "${it.key} KSh ${it.value.toInt()}" } + ".")
            }

        userInput.lowercase().contains("save") || userInput.lowercase().contains("savings") ->
            "Total saved: KSh ${viewModel.totalSavings.value.toInt()}. " +
            "You have ${viewModel.savingsGoals.value.size} active savings goals."

        userInput.lowercase().contains("food") || userInput.lowercase().contains("kaini") ||
            userInput.lowercase().contains("chakula") ->
            "Your food expenses: KSh ${foodTotal.toInt()}. " +
            (if (noCook) "Kibanda plates beat fast food for the same stomach!"
            else "Consider cooking at home to save money!")

        userInput.lowercase().contains("transport") || userInput.lowercase().contains("boda") ||
            userInput.lowercase().contains("matatu") ||
            userInput.lowercase().contains("stage") ->
            "Your transport expenses: KSh ${transportTotal.toInt()}. " +
            (if (farCommute) "Long route — off-peak or the early bus dodges ~2x peak fares."
            else "Consider using matatus or shared rides to reduce costs.")

        userInput.lowercase().contains("mpesa") ||
            userInput.lowercase().contains("safaricom") ->
            "M-Pesa transaction parsing is active. SMS-based detection requires permission. " +
            "You can also use Share-to-PesaFlow as a fallback."

        userInput.lowercase().contains("how much") ||
            userInput.lowercase().contains("ngapi") ||
            userInput.lowercase().contains("kitani") ->
            when {
                userInput.lowercase().contains("remaining") ->
                    "You have KSh ${viewModel.availableBalance.value.toInt()} remaining available balance."
                userInput.lowercase().contains("saved") ->
                    "Total savings: KSh ${viewModel.totalSavings.value.toInt()}. You have ${viewModel.savingsGoals.value.size} active savings goals."
                else ->
                    "Based on your data: KSh ${viewModel.availableBalance.value.toInt()} available, " +
                    "KSh ${viewModel.monthlyIncome.value.toInt()} income, KSh ${viewModel.monthlyExpenses.value.toInt()} expenses this month."
            }

        q.contains("lack") || q.contains("missing") || q.contains("don't have") || q.contains("dont have") || q.contains("need to buy") || q.contains("ninahitaji") || q.contains("what i need") ->
            run {
                val needs = belongings.filter { it.status == "NEED" }.sortedWith(compareBy({ it.priority }, { it.estCost }))
                if (needs.isEmpty()) "Nothing on your need list — add clothes, books and the rest under More → My Things. 🎒"
                else "You lack ${needs.size}: " + needs.take(5).joinToString("; ") { "${it.name} (~KSh ${it.estCost.toInt()})" } +
                    ". Owning it all costs ~KSh ${needs.sumOf { it.estCost }.toInt()}."
            }

        q.contains("have what") || q.contains("own what") || q.contains("my things") || q.contains("vitu vyangu") ->
            run {
                val haves = belongings.filter { it.status == "HAVE" }
                if (haves.isEmpty() && belongings.isEmpty()) "You haven't listed anything yet — More → My Things takes 30 seconds. 🎒"
                else if (haves.isEmpty()) "Everything listed is still on the need side — no haves yet. 💪"
                else "You have ${haves.size}: " + haves.take(6).joinToString(", ") { it.name } + "."
            }

        q.contains("stock") || q.contains("kitchen") || q.contains("cupboard") || q.contains("unga") ->
            if (pantry.isEmpty()) "Cupboard is empty on record — add unga, oil and friends under More → Kitchen Stock. 🫙"
            else {
                val ranked = pantry.sortedBy { stockDaysLeft(it) }
                "Cupboard: " + ranked.take(5).joinToString("; ") { "${it.name} ~${stockDaysLeft(it).toInt()}d left" } + "."
            }

        q.contains("how long") || q.contains("itanidumu") || q.contains("itaisha") || q.contains("will it last") ->
            if (pantry.isEmpty()) "No stock tracked — add your kitchen items first, then ask me. 🫙"
            else {
                val lowest = pantry.minByOrNull { stockDaysLeft(it) }
                if (lowest == null) "No stock tracked."
                else "${lowest.name} runs out first: ~${stockDaysLeft(lowest).toInt()} day(s) left, refill ≈ KSh ${stockRefillCost(lowest).toInt()}."
            }

        q.contains("replenish") || q.contains("restock") || q.contains("refill") || q.contains("jaza") ->
            if (pantry.isEmpty()) "Nothing to replenish on record — More → Kitchen Stock first. 🫙"
            else {
                val low = pantry.sortedBy { stockDaysLeft(it) }.take(4)
                "Refill plan: " + low.joinToString("; ") { "${it.name} by ${java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(java.util.Date(stockReplenishDate(it)))} (KSh ${stockRefillCost(it).toInt()})" } +
                    ". All-in: KSh ${pantry.sumOf { stockRefillCost(it) }.toInt()}."
            }

        q.contains("buy first") || q.contains("nini nunue") || q.contains("what first") || q.contains("priorit") ->
            run {
                val needs = belongings.filter { it.status == "NEED" }.sortedWith(compareBy({ it.priority }, { it.estCost }))
                val urgentStock = pantry.filter { stockDaysLeft(it) <= 3 }.sortedBy { stockDaysLeft(it) }
                val first = StringBuilder()
                urgentStock.firstOrNull()?.let { first.append("Kitchen first: ${it.name} (~${stockDaysLeft(it).toInt()}d left). ") }
                needs.firstOrNull()?.let { first.append("Then buy: ${it.name} (~KSh ${it.estCost.toInt()}).") }
                if (first.isEmpty()) "Nothing urgent — cupboard stocked, need-list empty. Enjoy the calm. 🎉"
                else first.toString()
            }

        q.contains("rent") || q.contains("hostel") ->
            "Rent/hostel spending: KSh ${(byCat["Rent"] ?: 0.0).toInt()} on record."

        q.contains("airtime") || q.contains("bundles") || q.contains("data") || q.contains("credit") ->
            "Airtime + data spending: KSh ${((byCat["Airtime"] ?: 0.0) + (byCat["Data"] ?: 0.0)).toInt()} on record."

        q.contains("surviv") || q.contains("stretch") || q.contains("make it to") ||
            ((q.contains("until") || q.contains("till")) && (q.contains("friday") || q.contains("saturday") || q.contains("sunday") || q.contains("monday") || q.contains("month") || Regex("\\d+\\s*days?").containsMatchIn(q))) ->
            run {
                val foods = viewModel.mealItems.value.filter { it.source == "Cook" }
                val stock = viewModel.kitchenStock.value
                if (stock.isEmpty()) {
                    "I can't plan survival on an empty cupboard — add your unga & co under More → Kitchen Stock first. 🫙"
                } else {
                    val weekOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
                    val todayIdx = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                    val dayNum = Regex("(\\d+)\\s*days?").find(q)?.groupValues?.get(1)?.toIntOrNull()
                    val namedDay = weekOrder.firstOrNull { q.contains(it) }?.let { (weekOrder.indexOf(it) - todayIdx + 7) % 7 }
                    val days = when {
                        dayNum != null -> dayNum.coerceIn(1, 60)
                        namedDay != null -> if (namedDay == 0) 7 else namedDay
                        q.contains("month") -> {
                            val c = java.util.Calendar.getInstance()
                            c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - c.get(java.util.Calendar.DAY_OF_MONTH) + 1
                        }
                        else -> 7
                    }
                    val plan = com.pesaflow.app.ui.university.planSurvival(stock, foods, days, balance.coerceAtLeast(0.0))
                    when {
                        plan.noFiller && plan.days.size < days -> "Stock covers ${plan.days.size} of $days days, and no Cook staples saved for the gap — add cheap ones in Meal Planner first. 🍳"
                        plan.days.all { it.fromStock } -> "Yes — your stock alone carries all $days days. Spend nothing. 💪"
                        plan.possible -> "Yes — stock covers ${plan.days.count { it.fromStock }} days, top-up KSh ${plan.totalCost.toInt()} (${plan.shopping.joinToString { "${it.first} ×${it.second}" }}), inside your KSh ${balance.toInt()}."
                        else -> "Tight — top-up needs KSh ${plan.totalCost.toInt()} but you hold KSh ${balance.toInt()} (short KSh ${plan.shortfall.toInt()})."
                    }
                }
            }

        q.contains("cooked") || q.contains("nimepika") || q.contains("nimebika") || q.contains("nimechemsha") ||
            (q.contains("used") && pantry.any { q.contains(it.name.lowercase()) }) ->
            run {
                val named = pantry.firstOrNull { q.contains(it.name.lowercase()) }
                if (named != null) {
                    viewModel.logStockUse(named)
                    val left = (named.qtyLeft - named.dailyUse).coerceAtLeast(0.0)
                    val leftStr = if (left == left.toInt().toDouble()) "${left.toInt()}" else String.format(java.util.Locale.US, "%.1f", left)
                    val daysAfter = if (named.dailyUse > 0) ((left / named.dailyUse).toInt()).coerceAtLeast(0) else -1
                    "${named.name}: burned a cooking day — ~$leftStr ${named.unit} left" +
                        (if (daysAfter >= 0) " (~${daysAfter}d)" else "") + ". Bar moved, enjoy. 🍳"
                } else if (pantry.isEmpty()) {
                    "Cupboard is empty on record — add your stock under More → Kitchen Stock first. 🫙"
                } else {
                    pantry.forEach { viewModel.logStockUse(it) }
                    "Burned a cooking day off everything: " + pantry.take(4).joinToString(", ") {
                        "${it.name} ~${((it.qtyLeft - it.dailyUse).coerceAtLeast(0.0) / it.dailyUse).toInt()}d left"
                    } + ". Bars moved, zero typing. 🍳"
                }
            }

        else -> "Hmm, I only answer from your real records. Try: 'leo nime-spend how much?', 'biggest expense?', 'is my budget safe?', 'my stock?', 'what do I lack?', 'replenish what?', or 'what should I buy first?'"
    }

    // Remember a confident intent so entity-only follow-ups resolve next turn.
    BuddyBrain.classify(q).firstOrNull()?.takeIf { it.conf >= 0.5f }?.let { BuddyMemory.lastIntent = it.name }
    // Append, don't wipe: keep the conversation, drop the stale "Thinking...".
    messages.value = (messages.value.filterNot { !it.isUser && it.text == "Thinking..." } +
        listOf(ChatMessage(text = userInput, isUser = true), ChatMessage(text = response, isUser = false))
        ).takeLast(40)
}