package com.pesaflow.app.ui.onboarding

import android.Manifest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.UniversityProfile
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.ui.language.Copy4
import com.pesaflow.app.viewmodels.FinanceViewModel
import androidx.compose.ui.platform.LocalContext
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.MerchantMemory
import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.deleteContactMemory
import com.pesaflow.app.data.ledger.saveContactMemory
import com.pesaflow.app.data.ledger.suggestMemory
import com.pesaflow.app.data.meals.rentHintFor
import com.pesaflow.app.data.meals.spotsFor
import com.pesaflow.app.data.parsers.SenderCard
import com.pesaflow.app.data.parsers.groupSenderCards
import com.pesaflow.app.data.parsers.SmsScanResult
import com.pesaflow.app.data.parsers.buildDraft
import com.pesaflow.app.data.parsers.monthKey
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.parsers.deduceFare
import com.pesaflow.app.data.parsers.Regime
import com.pesaflow.app.data.parsers.resolveCalendar
import com.pesaflow.app.data.parsers.scanRecentSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private val UNI_CHIPS = listOf("UoN", "KU", "JKUAT", "Maseno", "Egerton", "Other")
private const val DAY_MS = 24L * 60 * 60 * 1000


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun OnboardingScreen(viewModel: FinanceViewModel, onDone: () -> Unit) {
    // Mid-onboarding process death resumes where you left off, not at step 0.
    val bootPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    var step by rememberSaveable {
        mutableStateOf(
            if (bootPrefs.getInt("onboarding_flow_version", 0) >= 2) {
                bootPrefs.getInt("onboarding_step", 0).coerceIn(0, 5)
            } else {
                when (bootPrefs.getInt("onboarding_step", 0).coerceIn(0, 4)) {
                    0 -> 0
                    1 -> 2
                    2 -> 3
                    3 -> 4
                    else -> 5
                }
            }
        )
    }
    // Celebration beats the handoff: seeded → check + stars → home.
    var celebrate by rememberSaveable { mutableStateOf(false) }
    var welcomed by rememberSaveable { mutableStateOf(bootPrefs.getBoolean("onboarding_welcomed", false)) }
    var showDemoConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step, welcomed) {
        bootPrefs.edit()
            .putInt("onboarding_flow_version", 2)
            .putInt("onboarding_step", step)
            .putBoolean("onboarding_welcomed", welcomed)
            .apply()
    }

    fun loadDemoData() {
        showDemoConfirmation = false
        bootPrefs.edit().putBoolean("demo_mode", true).apply()
        viewModel.seedSampleData()
        onDone()
    }

    // Grand opening gate: first impression, instructions, then the 4 steps.
    if (!welcomed) {
        Box(Modifier.fillMaxSize()) {
            GrandOpening(
                onBegin = { welcomed = true },
                onSkip = { showDemoConfirmation = true }
            )
            if (showDemoConfirmation) {
                DemoDataConfirmationDialog(
                    onDismiss = { showDemoConfirmation = false },
                    onConfirm = ::loadDemoData
                )
            }
        }
        return
    }

    // Step 1: identity
    var name by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var university by rememberSaveable { mutableStateOf("") }
    var campus by rememberSaveable { mutableStateOf("") }
    var programme by rememberSaveable { mutableStateOf("") }
    var yearOfStudy by rememberSaveable { mutableStateOf("") }
    var semester by rememberSaveable { mutableStateOf("1") }

    // Step 2: funding
    var fundSource by rememberSaveable { mutableStateOf("HELB") }
    // One HELB figure per semester — the old tranche split fed the same sum
    // downstream, so it asked twice for one number.
    var helbSem by rememberSaveable { mutableStateOf("") }
    var pocket by rememberSaveable { mutableStateOf("") }
    var mpesaNow by rememberSaveable { mutableStateOf("") }
    var bankNow by rememberSaveable { mutableStateOf("") }
    var monthlyBudget by rememberSaveable { mutableStateOf("") }
    var foodBudget by rememberSaveable { mutableStateOf("") }
    var feesAmount by rememberSaveable { mutableStateOf("") }
    var semesterStartMillis by rememberSaveable { mutableStateOf(0L) }
    var endMillis by rememberSaveable { mutableStateOf(0L) }
    var feesDueMillis by rememberSaveable { mutableStateOf(0L) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var showFeesDuePicker by rememberSaveable { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    val feesDuePickerState = rememberDatePickerState()

    // Step 3 (new): tell us about you — 5 quick questions that seed
    // budgets, goals and first-run advice. All optional, all skippable.
    // Every answer below is consumed downstream (budget, goal, income or
    // persona) — nothing here is write-only.
    var sponsorMonthly by rememberSaveable { mutableStateOf("") }
    var rentGuess by rememberSaveable { mutableStateOf("") }
    var transportDaily by rememberSaveable { mutableStateOf("") }
    var airtimeWeekly by rememberSaveable { mutableStateOf("") }
    var saveTarget by rememberSaveable { mutableStateOf("") }
    // Home setup wires the whole app: budgets, meal planner, transport.
    // Six real setups — rent is not universal, cooking moves Food most.
    var homeKind by rememberSaveable { mutableStateOf("Hostel") }
    var commuteLen by rememberSaveable { mutableStateOf("Near") }
    var walkOk by rememberSaveable { mutableStateOf("Yes") }
    var cooksFood by rememberSaveable { mutableStateOf("Yes") }
    val commuteForPlanning = if (commuteLen == "Walk" && walkOk == "No") "Near" else commuteLen
    // Optional context facts (Layer C): stored with provenance, editable,
    // never treated as confirmed truth by inference.
    var grocerySpot by rememberSaveable { mutableStateOf("") }
    var stages by rememberSaveable { mutableStateOf("") }
    // First/last lecture hour (commuters): asked on step 0, saved at finish.
    var firstClassH by rememberSaveable { mutableStateOf("") }
    var lastClassH by rememberSaveable { mutableStateOf("") }
    // Single source of truth: every conditional box below reads this set.
    // The form can never drift from the branch rules.
    val visible = remember(homeKind, commuteLen, walkOk, cooksFood, fundSource) {
        visibleBranches(
            mapOf(
                "home" to homeKind,
                "commute" to commuteLen,
                "walkOk" to walkOk,
                "cooks" to cooksFood,
                "fundSource" to fundSource
            )
        ).toSet()
    }

    // Step 3: SMS priming
    var smsGranted by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactRelationship by rememberSaveable { mutableStateOf("") }
    var contactCategory by rememberSaveable { mutableStateOf("") }
    var contactAliases by rememberSaveable { mutableStateOf("") }
    var contactScope by rememberSaveable { mutableStateOf("BOTH") }
    var onboardingContacts by remember { mutableStateOf(ContactBook.readAll(bootPrefs)) }
    val scanScope = rememberCoroutineScope()
    var scanResult by remember { mutableStateOf<SmsScanResult?>(null) }
    // One card per unknown sender — name once, all their rows file themselves.
    var senderCards by remember { mutableStateOf<List<SenderCard>>(emptyList()) }
    // Fare proposal: same ~7–9am amount across mornings reads as the daily
    // fare — hostel walkers never see it.
    var fareProposal by remember { mutableStateOf<com.pesaflow.app.data.parsers.Deduction?>(null) }
    var fareDismissed by rememberSaveable { mutableStateOf(false) }
    var autoFacesOb by remember {
        mutableStateOf(
            appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("auto_confirm_faces", false)
        )
    }
    var scanProgress by remember { mutableStateOf(0) }
    // Transport-collapse months the detector proposes as break (one-tap confirm).
    var breakPrompt by remember { mutableStateOf<Set<String>?>(null) }
    // Scan-backfilled fields get upgraded by session-aware rhythms at finish
    // (user-typed values are never touched — flags tell them apart).
    var rentFromScan by rememberSaveable { mutableStateOf(false) }
    var transportFromScan by rememberSaveable { mutableStateOf(false) }
    // Explicit setup pick (enum name, "" = auto-detect). Stated beats derived.
    var personaOverride by rememberSaveable { mutableStateOf("") }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var scanQueued by rememberSaveable { mutableStateOf(0) }
    var showSmsRationale by rememberSaveable { mutableStateOf(false) }
    val smsPerm = rememberPermissionState(Manifest.permission.READ_SMS) { granted ->
        smsGranted = granted
    }
    // Rationale-first: explain, then ask. Cold prompts get auto-denied.
    fun askSms() {
        when {
            smsPerm.status.isGranted -> smsGranted = true
            smsPerm.status.shouldShowRationale -> showSmsRationale = true
            else -> smsPerm.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karibu PesaPlanner 👋", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showDemoConfirmation = true }) {
                        Text("Explore sample data")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (step + 1) / 6f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                when (step) {
                    0 -> "Step 1 of 6 · Who are you?"
                    1 -> "Step 2 of 6 · People & categories"
                    2 -> "Step 3 of 6 · Auto-tracking"
                    3 -> "Step 4 of 6 · Semester money"
                    4 -> "Step 5 of 6 · Tell us about you"
                    else -> "Step 6 of 6 · Review & start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Keep old in-progress onboarding positions stable while inserting
            // contact rules before the first SMS scan.
            val page = when (step) {
                1 -> 6
                2 -> 4
                3 -> 2
                4 -> 1
                5 -> 5
                else -> step
            }
            when (page) {
                6 -> {
                    Text("Who should PesaFlow recognize? 👥", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Add people before scanning so their transactions are categorized from the first SMS. You can change these rules later in Contact Book.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Person's name (e.g. Joan)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactRelationship,
                        onValueChange = { contactRelationship = it },
                        label = { Text("Relationship or label (e.g. Brother)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactCategory,
                        onValueChange = { contactCategory = it },
                        label = { Text("Usual transaction category (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = contactAliases,
                        onValueChange = { contactAliases = it },
                        label = { Text("Other names in messages (optional, comma-separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Apply category to", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("BOTH", "Money in & out", "↔"),
                            com.pesaflow.app.ui.theme.SegOption("IN", "Money in", "↓"),
                            com.pesaflow.app.ui.theme.SegOption("OUT", "Money out", "↑")
                        ),
                        selected = contactScope,
                        onSelect = { contactScope = it }
                    )
                    Button(
                        onClick = {
                            val cleanName = contactName.trim()
                            if (cleanName.isBlank() || (contactRelationship.isBlank() && contactCategory.isBlank())) {
                                android.widget.Toast.makeText(
                                    appContext,
                                    "Add a name and a relationship or category.",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val saved = ContactBook.save(
                                    prefs = bootPrefs,
                                    name = cleanName,
                                    displayName = cleanName,
                                    relationship = contactRelationship,
                                    category = contactCategory,
                                    scope = contactScope,
                                    notes = "",
                                    matchTerms = contactAliases
                                ) && saveContactMemory(
                                    prefs = bootPrefs,
                                    name = cleanName,
                                    label = contactRelationship,
                                    category = contactCategory,
                                    scope = contactScope,
                                    matchTerms = contactAliases
                                )
                                if (saved) {
                                    onboardingContacts = ContactBook.readAll(bootPrefs)
                                    contactName = ""
                                    contactRelationship = ""
                                    contactCategory = ""
                                    contactAliases = ""
                                    contactScope = "BOTH"
                                } else {
                                    android.widget.Toast.makeText(
                                        appContext,
                                        "Could not save this contact. Please check the details and try again.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save person") }
                    onboardingContacts.forEach { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, fontWeight = FontWeight.Bold)
                                    Text(
                                        listOf(contact.relationship, contact.category).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    ContactBook.delete(bootPrefs, contact.name)
                                    deleteContactMemory(bootPrefs, contact.name)
                                    onboardingContacts = ContactBook.readAll(bootPrefs)
                                }) { Text("Remove") }
                            }
                        }
                    }
                    if (onboardingContacts.isEmpty()) {
                        Text("No people added yet? You can skip this step and add them later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                0 -> {
                    StepArt(R.drawable.bg_university_campus)
                    Text("Kwanza, unaitwa nani? 🙂", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your name (e.g. Manu)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname, optional — what we call you daily (e.g. Comrade)") }, modifier = Modifier.fillMaxWidth())
                    Text("→ full name is only used when it is serious (warnings); daily talk uses the nickname.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("University", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = UNI_CHIPS.map { com.pesaflow.app.ui.theme.SegOption(it, it, "🎓") },
                        selected = university,
                        onSelect = { university = it }
                    )
                    OutlinedTextField(value = university, onValueChange = { university = it }, label = { Text("University or college") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("Campus (optional, e.g. Main Campus)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = programme, onValueChange = { programme = it }, label = { Text("Programme or course (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = yearOfStudy, onValueChange = { yearOfStudy = it }, label = { Text("Year of study (optional, e.g. Year 2)") }, modifier = Modifier.fillMaxWidth())
                    // Campus food map (main format, our data): where to eat,
                    // what plate, what price — planner-ready below.
                    if (university.isNotBlank()) {
                        val foodSpots = remember(university) { spotsFor(university.trim()).take(4) }
                        if (foodSpots.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Eating near ${university.trim()} 🍽️",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    foodSpots.forEach { s ->
                                        Text(
                                            "${s.spot} · ${s.item} — ~KSh ${s.price.toInt()}" + if (!s.verified) " (typical)" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "Student prices — confirm on the ground, then the meal planner uses them.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    OutlinedTextField(value = semester, onValueChange = { semester = it }, label = { Text("Current semester") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    // Living + costs live here (step 0): where you stay routes
                    // every cost box below through the branch engine.
                    Text("Where do you stay? 🏠", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Parents", "Parents", "🏡"),
                            com.pesaflow.app.ui.theme.SegOption("Hostel", "Hostel", "🏠"),
                            com.pesaflow.app.ui.theme.SegOption("Shared", "Shared", "🤝"),
                            com.pesaflow.app.ui.theme.SegOption("Alone", "Alone", "🧍")
                        ),
                        selected = homeKind,
                        onSelect = { homeKind = it }
                    )
                    Text("Daily trip to campus?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Walk", "Walk", "🚶"),
                            com.pesaflow.app.ui.theme.SegOption("Near", "Near hop", "🛵"),
                            com.pesaflow.app.ui.theme.SegOption("Far", "Far daily", "🚌")
                        ),
                        selected = commuteLen,
                        onSelect = { commuteLen = it }
                    )
                    if ("walkOk" in visible) {
                        Text("Is walking practical on most class days?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        com.pesaflow.app.ui.theme.SegChoice(
                            options = listOf(
                                com.pesaflow.app.ui.theme.SegOption("Yes", "Yes, I walk", "🚶"),
                                com.pesaflow.app.ui.theme.SegOption("No", "No, I sometimes pay fares", "🚌")
                            ),
                            selected = walkOk,
                            onSelect = { walkOk = it }
                        )
                    }
                    Text("Do you cook?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("Yes", "I cook", "🍳"),
                            com.pesaflow.app.ui.theme.SegOption("No", "I buy", "🍲")
                        ),
                        selected = cooksFood,
                        onSelect = { cooksFood = it }
                    )
                    Text(
                        if (homeKind == "Parents") "Home roof — no rent box, budgets swap Rent for a small Home upkeep envelope."
                        else if (cooksFood == "No") "Bought meals cost most — Food gets protected first."
                        else if (commuteForPlanning == "Far") "Long matatu daily — Transport becomes non-negotiable."
                        else if (commuteLen == "Walk" && walkOk == "No") "Walking is not practical every class day — add the fare you usually pay."
                        else "Light setup — more room for Savings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if ("rent" in visible) {
                        OutlinedTextField(value = rentGuess, onValueChange = { rentGuess = it }, label = { Text("Rent/hostel per month? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text("→ your Rent budget + Bills watch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rentHintFor(university)?.let { hint ->
                            Text("Rough guide near ${university.trim()} (verify locally): $hint.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if ("fare" in visible) {
                        OutlinedTextField(value = transportDaily, onValueChange = { transportDaily = it }, label = { Text("Transport per day, to and back? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text("→ Transport budget (×30) + commuter weight in the calculator.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if ("grocery" in visible) {
                        Text("Where do you usually shop? 🛒", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = grocerySpot,
                            onValueChange = { grocerySpot = it },
                            label = { Text("Grocery spot (e.g. Naivas, market)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("→ local food advice uses this spot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if ("stages" in visible) {
                        Text("Common stages/routes? 🚏", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = stages,
                            onValueChange = { stages = it },
                            label = { Text("Stages (e.g. Roysambu, CBD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("→ transport estimates anchor to real routes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if ("classTimes" in visible) {
                        Text("School run 🚌 — first class in, last class out?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = firstClassH,
                                onValueChange = { firstClassH = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("First (e.g. 7)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lastClassH,
                                onValueChange = { lastClassH = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("Last (e.g. 17)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text("→ commute days + peak fares derive from this. Edit anytime under More → Semester.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                1 -> {
                    StepArt(R.drawable.ob_tellus)
                    Text("Tell us about you 📝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "5 quick estimates — they pre-fill your budgets and goals. Skip anything; these are starting points you can edit anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Continuous numbering (main format): hidden boxes never leave
                    // gaps — the count follows what you actually see.
                    var qi = 0
                    OutlinedTextField(value = sponsorMonthly, onValueChange = { sponsorMonthly = it }, label = { Text("${++qi} · Expected monthly upkeep from home? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ tracked as expected sponsor income; it is not added to your held balance until received.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = airtimeWeekly, onValueChange = { airtimeWeekly = it }, label = { Text("${++qi} · Airtime + data per week? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ your Airtime budget (×4).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = saveTarget, onValueChange = { saveTarget = it }, label = { Text("${++qi} · Want to save monthly? (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ creates your Monthly savings goal with a daily pace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> {
                    StepArt(R.drawable.ob_money)
                    Text("Pesa ya semester 💰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Expected support is not cash on hand. Enter what you have right now in each pocket; expected income is tracked separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.pesaflow.app.ui.theme.SegChoice(
                        options = listOf(
                            com.pesaflow.app.ui.theme.SegOption("HELB", "HELB", "🎓"),
                            com.pesaflow.app.ui.theme.SegOption("SELF", "Self-sponsored", "💪"),
                            com.pesaflow.app.ui.theme.SegOption("BOTH", "Both", "🤝")
                        ),
                        selected = fundSource,
                        onSelect = { fundSource = it }
                    )
                    if ("helb" in visible) {
                        OutlinedTextField(value = helbSem, onValueChange = { helbSem = it }, label = { Text("HELB per semester (KSh, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Text("Splits into monthly upkeep automatically — carried forward each term, no re-typing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Self-sponsored 💪 — pocket cash + anything you log as income carries the semester.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(value = pocket, onValueChange = { pocket = it }, label = { Text("Cash you have right now (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mpesaNow, onValueChange = { mpesaNow = it }, label = { Text("M-Pesa balance right now (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bankNow, onValueChange = { bankNow = it }, label = { Text("Bank balance right now (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("These actual balances seed your ledger. HELB and sponsor amounts above remain expected until they arrive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = feesAmount, onValueChange = { feesAmount = it }, label = { Text("Fees owed (KSh, optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ fees reserve. Part of HELB can cover it — we show the uncovered amount.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showFeesDuePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Fees due date: " + if (feesDueMillis > 0L) {
                                java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(feesDueMillis))
                            } else "Not set"
                        )
                    }
                    OutlinedTextField(value = monthlyBudget, onValueChange = { monthlyBudget = it }, label = { Text("Monthly budget (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ your master budget — pace, alerts and safe-to-spend all read it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = foodBudget, onValueChange = { foodBudget = it }, label = { Text("Food budget (KSh)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("→ Food budget + the meal planner's spending figure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Semester starts: " + if (semesterStartMillis > 0L) {
                                java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(semesterStartMillis))
                            } else "Not set"
                        )
                    }
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Semester ends: " + if (endMillis > 0L) {
                                java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(endMillis))
                            } else "Not set"
                        )
                    }
                }
                // Income setup used to interrogate here — dropped. Income arrives
                // from the scan + upkeep/HELB figures; jobs get declared later
                // under More → Income. Welcome screens collect, never grill.
                4 -> {
                    StepArt(R.drawable.ob_sms)
                    Text("Track spending automatically ⚡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "If you allow SMS access, PesaFlow matches recent messages on this device. Most transactions wait for your review; trusted matches may be filed automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { askSms() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text(if (smsGranted) "Enabled ✓" else "Enable SMS detection", color = MaterialTheme.colorScheme.onPrimary) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("First-run check: full year", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Scans recent inbox messages on this device for transaction details. Parsed totals are editable starting estimates; most detected rows wait for review, while trusted matches may be filed automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            if (!smsGranted) {
                                askSms()
                            } else {
                                // State stays on Main; only the inbox query drops to IO.
                                scanScope.launch {
                                    scanning = true
                                    scanProgress = 0
                                    fareProposal = null
                                    fareDismissed = false
                                    val r = withContext(Dispatchers.IO) {
                                        // The label promises five months: use that
                                        // exact calendar cutoff, not a year-long scan.
                                        val scanNow = System.currentTimeMillis()
                                        val scanSince = java.util.Calendar.getInstance().apply {
                                            timeInMillis = scanNow
                                            add(java.util.Calendar.MONTH, -5)
                                        }.timeInMillis
                                        val scanDays = ((scanNow - scanSince + DAY_MS - 1) / DAY_MS).toInt()
                                        // The session filter (at finish) quarantines break.
                                        scanRecentSms(
                                            appContext,
                                            scanDays,
                                            5000,
                                            onProgress = { f, _ -> scanProgress = f },
                                            sinceTimestamp = scanSince
                                        )
                                    }
                                    var queued = 0
                                    r.parsed.forEach { if (viewModel.tryQueuePending(it)) queued++ }
                                    scanQueued = queued
                                    if (monthlyBudget.isBlank() && r.monthlyExpense > 0) monthlyBudget = r.monthlyExpense.toInt().toString()
                                    if (foodBudget.isBlank() && r.monthlyFor("Food") > 0) foodBudget = r.monthlyFor("Food").toInt().toString()
                                    if (rentGuess.isBlank() && r.monthlyFor("Rent") > 0) { rentGuess = r.monthlyFor("Rent").toInt().toString(); rentFromScan = true }
                                    if (transportDaily.isBlank() && r.monthlyFor("Transport") > 0) { transportDaily = (r.monthlyFor("Transport") / 30).toInt().toString(); transportFromScan = true }
                                    scanResult = r
                                    // Sender cards: already-named senders never resurface.
                                    val obPrefs = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                    val knownMemories = com.pesaflow.app.data.ledger.readContactMemories(
                                        obPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
                                    )
                                    senderCards = groupSenderCards(r.parsed, isNamed = {
                                        MerchantMemory.lookup(obPrefs, it) != null ||
                                            com.pesaflow.app.data.ledger.hasContactMemory(it, knownMemories)
                                    })
                                    // Break proposal: collapsed-transport months surface
                                    // once for confirm-or-keep — never auto-excluded.
                                    val proposed = com.pesaflow.app.data.parsers.detectBreakMonths(
                                        com.pesaflow.app.data.parsers.monthlyTransportSeries(r.parsed)
                                    )
                                    if (proposed.isNotEmpty()) breakPrompt = proposed
                                    // Fare rhythm proposal — commuters only. Same
                                    // morning amount ± band across class mornings
                                    // reads as the daily fare: confirm once and
                                    // the Transport box fills itself.
                                    fareProposal = if (homeKind == "Hostel" && commuteForPlanning == "Walk") null
                                    else deduceFare(
                                        r.parsed.map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) }
                                    ) { ts ->
                                        val d = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                                            .get(java.util.Calendar.DAY_OF_WEEK)
                                        d in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
                                    }?.takeIf { it.confidence >= 0.7 }
                                    // Harvest the newest wallet balance for display everywhere.
                                    r.parsed.firstOrNull()?.rawText?.let { raw ->
                                        com.pesaflow.app.data.parsers.parseBalance(raw)?.let { bal ->
                                            com.pesaflow.app.data.parsers.saveMpesaBalance(appContext, bal)
                                        }
                                    }
                                    scanning = false
                                }
                            }
                        },
                        enabled = !scanning,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) { Text(if (scanning) "Scanning... $scanProgress found" else if (scanResult == null) "Scan last 5 months" else "Rescan", color = MaterialTheme.colorScheme.onSecondary) }
                    scanResult?.let { r ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Honest counts: a hit cap reports as a floor ("1,500+"),
                                // never a flat census. The window states its own days.
                                Text(
                                    if (r.capped) "Found " + r.found + "+ texts (scan cap — oldest skipped), " + r.parsed.size + " readable"
                                    else "Found " + r.found + " texts, " + r.parsed.size + " readable",
                                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold
                                )
                                Text("In KSh " + r.incomeTotal.toInt() + ", out KSh " + r.expenseTotal.toInt() + " over " + r.daysBack + " days", style = MaterialTheme.typography.bodySmall)
                                Text("Read " + (r.readRate * 100).toInt() + "% of found texts", style = MaterialTheme.typography.bodySmall)
                                if (r.byMonth.isNotEmpty()) Text(
                                    "By month: " + r.byMonth.toList().sortedBy { it.first }.joinToString(", ") { it.first + ": " + it.second },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (r.topSenders.isNotEmpty()) Text(
                                    "Top senders: " + r.topSenders.joinToString(", ") { it.first + " (" + it.second + ")" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Monthly pace about KSh " + r.monthlyExpense.toInt() + ", food about KSh " + r.monthlyFor("Food").toInt(), style = MaterialTheme.typography.bodySmall)
                                r.byCategory.toList().sortedByDescending { it.second }.take(3).joinToString(" · ") {
                                    it.first + " " + com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(it.second))
                                }.takeIf { it.isNotBlank() }?.let {
                                    Text("Top: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(scanQueued.toString() + " queued to pending for Home approval. Placeholders only - edit anything.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Fare proposal (commuters only): same ~7–9am amount
                        // across class mornings. One tap fills Transport and
                        // melts a pile of unsure rows into sure ones.
                        fareProposal?.let { fare ->
                            if (!fareDismissed) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Same morning fare? 🚌", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "KSh ${fare.amount.toInt()} around 7–9am (${fare.evidence}). Peak hikes wobble the band — confirm the usual and Transport fills itself.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = {
                                                transportDaily = ((fare.amount * 22) / 30).toInt().toString()
                                                transportFromScan = true
                                                fareDismissed = true
                                            }) { Text("Yes, ~KSh ${(fare.amount * 2).toInt()}/day") }
                                            TextButton(onClick = { fareDismissed = true }) { Text("Not mine") }
                                        }
                                    }
                                }
                            }
                        }
                        // Sender cards: name each sender once with dates attached
                        // so you remember — no 700-row confirming.
                        if (senderCards.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Who are these people? 👥", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Only 16+ texts or KSh 1,500+ moved ask — one-offs file silently. Name each once and every row files itself, past and future.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            senderCards.forEach { card ->
                                SenderCardRow(
                                    card = card,
                                    onSaved = { senderCards = senderCards.filter { it.merchant != card.merchant } }
                                )
                            }
                            // Hands-free mode: remembered people with a usual
                            // category file themselves from now on.
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-confirm familiar faces 🤝", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (autoFacesOb) "ON — texts from remembered people file themselves. Toggle anytime in Settings."
                                        else "Off — even remembered faces wait for your tap.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = autoFacesOb, onCheckedChange = {
                                    autoFacesOb = it
                                    appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("auto_confirm_faces", it).apply()
                                })
                            }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Demo: what you'll see 👀", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New SMS Detected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("KSh 250", fontWeight = FontWeight.Bold)
                            }
                    Text("Merchant: Kibanda", style = MaterialTheme.typography.bodySmall)
                    Text("Suggested: Food · Confirm / Ignore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                5 -> {
                    StepArt(R.drawable.ob_review)
                    Text("Review your setup ✅", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything below becomes your default EVERYWHERE — budgets, net worth, bills, reports, planners. Go Back to edit anything; later, change it in Budgets / Profile. Nothing is locked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Same precedence as the finish block: typed > M-Pesa scan.
                    val scan = scanResult
                    val revAll = monthlyBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: scan?.monthlyExpense?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revFood = foodBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: scan?.monthlyFor("Food")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revRent = rentGuess.toDoubleOrNull()?.takeIf { it > 0 }?.let { it to "you" }
                        ?: scan?.monthlyFor("Rent")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revTransport = transportDaily.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 30 to "you" }
                        ?: scan?.monthlyFor("Transport")?.takeIf { it > 0 }?.let { it to "M-Pesa scan" }
                    val revAirtime = airtimeWeekly.toDoubleOrNull()?.takeIf { it > 0 }?.let { it * 4 to "you" }
                    val revCash = pocket.toDoubleOrNull()?.takeIf { it > 0 }
                    val revMpesa = mpesaNow.toDoubleOrNull()?.takeIf { it > 0 }
                    val revBank = bankNow.toDoubleOrNull()?.takeIf { it > 0 }
                    val revUpkeep = sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 }
                    val revSave = saveTarget.toDoubleOrNull()?.takeIf { it > 0 }
                    val revFees = feesAmount.toDoubleOrNull()?.takeIf { it > 0 }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Defaults from your answers", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            ReviewRow("Monthly budget", revAll?.first?.let { "KSh " + it.toInt() } ?: "—", revAll?.second ?: "skip")
                            ReviewRow("Food budget", revFood?.first?.let { "KSh " + it.toInt() } ?: "—", revFood?.second ?: "skip")
                            ReviewRow("Rent", revRent?.first?.let { "KSh " + it.toInt() } ?: "—", revRent?.second ?: "skip")
                            ReviewRow("Transport", revTransport?.first?.let { "KSh " + it.toInt() } ?: "—", revTransport?.second ?: "skip")
                            ReviewRow("Airtime", revAirtime?.first?.let { "KSh " + it.toInt() } ?: "—", revAirtime?.second ?: "skip")
                            ReviewRow("Cash on hand → ledger", revCash?.let { "KSh " + it.toInt() } ?: "—", if (revCash != null) "you" else "skip")
                            ReviewRow("M-Pesa held → ledger", revMpesa?.let { "KSh " + it.toInt() } ?: "—", if (revMpesa != null) "you" else "skip")
                            ReviewRow("Bank held → ledger", revBank?.let { "KSh " + it.toInt() } ?: "—", if (revBank != null) "you" else "skip")
                            ReviewRow("Expected monthly upkeep", revUpkeep?.let { "KSh " + it.toInt() } ?: "—", if (revUpkeep != null) "expected" else "skip")
                            ReviewRow("Savings goal", revSave?.let { "KSh " + it.toInt() } ?: "—", if (revSave != null) "you" else "skip")
                            ReviewRow("Fees bill", revFees?.let { "KSh " + it.toInt() } ?: "—", if (revFees != null) "you" else "skip")
                            ReviewRow(
                                "Fees due",
                                if (feesDueMillis > 0L) java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(feesDueMillis)) else "—",
                                if (feesDueMillis > 0L) "you entered" else "not set"
                            )
                            val revSources = viewModel.incomeSources.value
                            ReviewRow(
                                "Income sources",
                                if (revSources.isEmpty()) "—" else revSources.size.toString() + " (" + revSources.joinToString(", ") { it.displayKind() } + ")",
                                if (revSources.isEmpty()) "skip" else "KSh " + revSources.sumOf { IncomeSourceStore.budgetedMonthly(it) }.toInt() + " expected"
                            )
                            ReviewRow("Reports", "Night · Sunday · Daily", "auto-armed")
                            val revPersona = com.pesaflow.app.ui.budgets.parsePersona(
                                "home=" + when (homeKind) { "Parents" -> "PARENTS"; "Hostel" -> "HOSTEL"; else -> "RENTAL" } + "|commute=" + commuteForPlanning.uppercase() + "|cooking=" + if (cooksFood == "Yes") "YES" else "NO"
                            )
                            val effPersona = personaOverride.takeIf { it.isNotBlank() }?.let {
                                com.pesaflow.app.ui.budgets.Persona.valueOf(it)
                            } ?: revPersona
                            ReviewRow("Setup", effPersona.label, if (personaOverride.isBlank()) effPersona.blurb + " · auto" else "your pick")
                            val runFirst = firstClassH.toIntOrNull()
                            val runLast = lastClassH.toIntOrNull()
                            ReviewRow(
                                "School run",
                                if (runFirst != null && runLast != null && "classTimes" in visible) "$runFirst:00 in · $runLast:00 out, Mon–Fri" else "—",
                                if (runFirst != null && runLast != null && "classTimes" in visible) "drives commute days" else "skip"
                            )
                            // One-tap correction: six researched setups, detected one
                            // preselected. Tapping the current pick clears back to auto.
                            com.pesaflow.app.ui.budgets.Persona.values().forEach { p ->
                                TextButton(onClick = { personaOverride = if (personaOverride == p.name) "" else p.name }) {
                                    Text(
                                        (if (effPersona == p) "✓ " else "○ ") + p.label + " — " + p.blurb,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (effPersona == p) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // Safe-to-spend preview so day one never feels like guessing.
                    revAll?.first?.let { all ->
                        val calR = java.util.Calendar.getInstance()
                        val dimR = calR.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                        val domR = calR.get(java.util.Calendar.DAY_OF_MONTH)
                        val leftR = (dimR - domR + 1).coerceAtLeast(1)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("How safe-to-spend works 👀", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Day 1: KSh " + (all / leftR).toInt() + "/day safe (" + leftR + " days left of KSh " + all.toInt() + "). " +
                                        "Every expense you log lowers tomorrow's number — watch it move on Home.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (showSmsRationale) {
                AlertDialog(
                    onDismissRequest = { showSmsRationale = false },
                    title = { Text("Why SMS access?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "PesaFlow scans recent inbox messages on this device to find transaction details and stores the parsed records locally. " +
                                "Most detected transactions wait for your review; trusted matches may be filed automatically. " +
                                "SMS access is optional — you can deny it and enter transactions yourself."
                        )
                    },
                    confirmButton = { TextButton(onClick = { showSmsRationale = false; smsPerm.launchPermissionRequest() }) { Text("Allow SMS") } },
                    dismissButton = { TextButton(onClick = { showSmsRationale = false }) { Text("Not now") } }
                )
            }

            // Break confirmation: one tap excludes dead months from every
            // average; Keep leaves them training. Nothing auto-excludes.
            breakPrompt?.let { months ->
                AlertDialog(
                    onDismissRequest = { breakPrompt = null },
                    title = { Text("Break months?", fontWeight = FontWeight.Bold) },
                    text = { Text("Transport nearly vanished in ${months.sorted().joinToString(", ")} — long break, strike, or time away? Exclude them so they never warp your budgets.") },
                    confirmButton = {
                        TextButton(onClick = {
                            appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putStringSet("confirmed_break_months", months).apply()
                            breakPrompt = null
                        }) { Text("Exclude") }
                    },
                    dismissButton = { TextButton(onClick = { breakPrompt = null }) { Text("Keep") } }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (step > 0) step-- }, enabled = step > 0) { Text("Back") }
                Button(
                    onClick = {
                        if (step == 5 && semesterStartMillis > 0L && endMillis > 0L && endMillis <= semesterStartMillis) {
                            android.widget.Toast.makeText(appContext, "Semester end must be after its start.", android.widget.Toast.LENGTH_LONG).show()
                        } else if (step < 5) {
                            step++
                        } else {
                            val now = System.currentTimeMillis()
                            viewModel.setUserName(name)
                            viewModel.setNickname(nickname)
                            viewModel.saveUniversityProfile(
                                UniversityProfile(
                                    universityName = university.trim(),
                                    campus = campus.trim(),
                                    programme = programme.trim(),
                                    yearOfStudy = yearOfStudy.trim(),
                                    currentSemester = semester.toIntOrNull() ?: 1,
                                    academicYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString(),
                                    semesterStartTimestamp = semesterStartMillis,
                                    semesterEndTimestamp = endMillis,
                                    startingFunding = listOf(pocket, mpesaNow, bankNow).sumOf { it.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 },
                                    helbExpected = if (fundSource == "SELF") 0.0 else helbSem.toDoubleOrNull() ?: 0.0,
                                    fundingSource = fundSource,
                                    feesAmount = feesAmount.toDoubleOrNull() ?: 0.0,
                                    feesDueDate = feesDueMillis
                                )
                            )
                            // Smart seeding: HELB tranches + sponsor answers become Income
                            // sources, so More → Income, budgets and Buddy know payday
                            // without re-asking on opening. Never duplicates: kinds
                            // already declared (above or earlier) are left alone.
                            val helbTotal = if (fundSource == "SELF") 0.0
                            else helbSem.toDoubleOrNull() ?: 0.0
                            val existingKinds = viewModel.incomeSources.value.map { it.kind }.toSet()
                            val seededIncome = mutableListOf<IncomeSource>()
                            if (helbTotal > 0 && "HELB_MPESA" !in existingKinds && "HELB_BANK" !in existingKinds) {
                                // Tranches land per semester (~4 months): monthly share keeps budget math honest.
                                seededIncome.add(IncomeSource(kind = "HELB_MPESA", label = "HELB upkeep", expectedAmount = helbTotal / 4, frequency = "MONTHLY", autoTrack = true))
                            }
                            sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 }?.let { sp ->
                                if ("PARENT" !in existingKinds && "GUARDIAN" !in existingKinds) {
                                    seededIncome.add(IncomeSource(kind = "GUARDIAN", label = "Sponsor", expectedAmount = sp, frequency = "MONTHLY"))
                                }
                            }
                            if (seededIncome.isNotEmpty()) {
                                viewModel.setIncomeSources(viewModel.incomeSources.value + seededIncome)
                            }
                            // Declared profile (Phase 7): first-class facts from this
                            // run. Observation may only suggest changes later.
                            viewModel.saveFinancialProfile(
                                com.pesaflow.app.data.models.FinancialProfile(
                                    housing = when (homeKind) {
                                        "Parents" -> "PARENTS"
                                        "Hostel" -> "HOSTEL"
                                        "Shared" -> "SHARED_RENT"
                                        else -> "RENTAL"
                                    },
                                    commute = when {
                                        commuteForPlanning.contains("far", ignoreCase = true) -> "LONG"
                                        commuteForPlanning.contains("walk", ignoreCase = true) -> "WALK"
                                        commuteForPlanning.contains("near", ignoreCase = true) || commuteForPlanning.contains("short", ignoreCase = true) -> "SHORT"
                                        else -> "SHORT"
                                    },
                                    food = when (cooksFood) {
                                        "Yes" -> "COOK"
                                        "No" -> "BUY"
                                        else -> "MIXED"
                                    },
                                    incomeStability = when {
                                        fundSource == "SELF" && sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 } == null -> "NONE"
                                        sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 } != null -> "MIXED"
                                        fundSource == "SELF" -> "VARIABLE"
                                        else -> "MIXED"
                                    },
                                    incomeKindsCsv = buildList {
                                        if (fundSource != "SELF") add("HELB")
                                        if (sponsorMonthly.toDoubleOrNull()?.takeIf { it > 0 } != null) add("GUARDIAN")
                                    }.joinToString(","),
                                    academic = if ((semester.toIntOrNull() ?: 1) <= 1) "FIRST_YEAR" else "RETURNING"
                                )
                            )
                            monthlyBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("ALL", it, BudgetType.MONTHLY)
                            }
                            foodBudget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Food", it, BudgetType.MONTHLY)
                            }
                            rentGuess.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Rent", it, BudgetType.MONTHLY)
                            }
                            transportDaily.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Transport", it * 30, BudgetType.MONTHLY)
                            }
                            airtimeWeekly.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.upsertBudget("Airtime", it * 4, BudgetType.MONTHLY)
                            }
                            saveTarget.toDoubleOrNull()?.takeIf { it > 0 }?.let {
                                viewModel.addSavingsGoal("Monthly savings", it, 30)
                            }
                            // Context facts (Layer C): explicit, provenance-stamped,
                            // user-editable. Inference may read but never overwrite them.
                            viewModel.setContextFact(com.pesaflow.app.data.context.ContextFact(key = "housing.current", value = homeKind, source = com.pesaflow.app.data.context.ContextSources.USER_SELECTED))
                            viewModel.setContextFact(com.pesaflow.app.data.context.ContextFact(key = "transport.primaryMode", value = commuteForPlanning, source = com.pesaflow.app.data.context.ContextSources.USER_SELECTED))
                            if (grocerySpot.isNotBlank()) {
                                viewModel.setContextFact(com.pesaflow.app.data.context.ContextFact(key = "food.grocerySpot", value = grocerySpot.trim(), source = com.pesaflow.app.data.context.ContextSources.USER_ENTERED))
                            }
                            if (stages.isNotBlank()) {
                                viewModel.setContextFact(com.pesaflow.app.data.context.ContextFact(key = "transport.homeToCampus", value = stages.trim(), source = com.pesaflow.app.data.context.ContextSources.USER_ENTERED))
                            }
                            // Session-aware rhythm upgrade: scan-backfilled (or blank)
                            // Transport/Rent are re-derived from fare/anchor rhythms
                            // trained on in-session months only — break months never
                            // train anything. User-typed values are sacred.
                            val semCal = resolveCalendar(
                                semesterStartMillis,
                                endMillis,
                                now,
                                firstYear = (semester.toIntOrNull() ?: 1) <= 1
                            )
                            // User-confirmed dead months (break dialog) leave the
                            // averages even when they fall inside stated spans.
                            val breakOverrides = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                                .getStringSet("confirmed_break_months", emptySet()) ?: emptySet()
                            val firstH = firstClassH.toIntOrNull()
                            val lastH = lastClassH.toIntOrNull()
                            val timetableTimes = if (
                                firstH != null && lastH != null && firstH in 5..23 && lastH in firstH..23 &&
                                "classTimes" in visible
                            ) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri").associateWith { firstH to lastH }
                                    .also { com.pesaflow.app.data.schedule.WeekPlan.saveTimes(appContext, it) }
                            } else {
                                com.pesaflow.app.data.schedule.WeekPlan.loadTimes(appContext)
                            }
                            val timetableDays = timetableTimes.keys
                            val draft = scanResult?.let { scan ->
                                val periodRows = if (semesterStartMillis > 0L && endMillis > semesterStartMillis) {
                                    scan.parsed.filter { semCal.regimeOf(it.dateTimestamp) == Regime.SESSION }
                                } else {
                                    scan.parsed
                                }
                                buildDraft(
                                    periodRows
                                        .filter { monthKey(it.dateTimestamp) !in breakOverrides }
                                        .map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) },
                                    isClassDay = { ts ->
                                        if (timetableDays.isEmpty()) {
                                            java.util.Calendar.getInstance().apply { timeInMillis = ts }
                                                .get(java.util.Calendar.DAY_OF_WEEK) in
                                                java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
                                        } else {
                                            com.pesaflow.app.data.schedule.timetableDay(ts) in timetableDays
                                        }
                                    },
                                    isClassTime = if (timetableTimes.isEmpty()) null else { ts ->
                                        com.pesaflow.app.data.schedule.isWithinClassCommuteWindow(ts, timetableTimes)
                                    }
                                )
                            }
                            if ((transportDaily.toDoubleOrNull() == null || transportFromScan) && draft?.transportMonthly != null && draft.transportMonthly > 0) {
                                transportDaily = (draft.transportMonthly / 30).toInt().toString()
                                viewModel.upsertBudget("Transport", draft.transportMonthly, BudgetType.MONTHLY)
                            }
                            if ((rentGuess.toDoubleOrNull() == null || rentFromScan) && draft?.rentMonthly != null && draft.rentMonthly > 0) {
                                rentGuess = draft.rentMonthly.toInt().toString()
                                viewModel.upsertBudget("Rent", draft.rentMonthly, BudgetType.MONTHLY)
                            }
                            // First-run M-Pesa figures: gentle re-adjustment where the user left blanks.
                            scanResult?.let { scan ->
                                if (monthlyBudget.toDoubleOrNull() == null && scan.monthlyExpense > 0) {
                                    viewModel.upsertBudget("ALL", scan.monthlyExpense, BudgetType.MONTHLY)
                                }
                                if (foodBudget.toDoubleOrNull() == null && scan.monthlyFor("Food") > 0) {
                                    viewModel.upsertBudget("Food", scan.monthlyFor("Food"), BudgetType.MONTHLY)
                                }
                            }
                            // From word go, everything works as a unit: arm the report loop
                            // (night + Sunday + daily digest + monthly) and seed the fees bill
                            // so Semester, Bills, Reports and planners all have true data.
                            val nprefs = appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            nprefs.edit()
                                .putBoolean("night_report", true)
                                .putBoolean("sunday_report", true)
                                .putBoolean("daily_summary", true)
                                .putBoolean("lunch_scan", true)
                                .apply()
                            ReminderScheduler.scheduleNightReport(appContext)
                            ReminderScheduler.scheduleSundayReport(appContext)
                            ReminderScheduler.scheduleDailyDigest(appContext)
                            ReminderScheduler.scheduleMonthlyReport(appContext)
                            ReminderScheduler.scheduleDaily(appContext)
                            ReminderScheduler.scheduleLunchScan(appContext)
                            val feesAmt = feesAmount.toDoubleOrNull()?.takeIf { it > 0 }
                            if (feesAmt != null && feesDueMillis > 0L &&
                                viewModel.bills.value.none { it.name == "Semester fees" && it.status != "PAID" }
                            ) {
                                viewModel.addBill("Semester fees", feesAmt, feesDueMillis, "School", "ONE_TIME")
                            }
                            // School run from step 0: class hours ride Mon–Fri into
                            // the timetable — commute days + peak verdict follow.
                            // Only money held now seeds opening equity. Expected
                            // upkeep remains an income source until it actually lands.
                            viewModel.seedOpeningMoney(
                                pocket.toDoubleOrNull() ?: 0.0,
                                mpesaNow.toDoubleOrNull() ?: 0.0,
                                bankNow.toDoubleOrNull() ?: 0.0
                            )
                            // First-sync: coded + sure scans confirm themselves
                            // now, so Home opens on real data, not an empty
                            // ledger. Codeless/unsure rows stay queued; the
                            // approval carries an undo slot like any bulk tap.
                            viewModel.autoApproveOnboardingSync(
                                appContext.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                            )
                            // Persona setup code: shared rentals read as rentals (they pay
                            // rent), solo places too — only Parents/Hostel differ.
                            val homeCode = when (homeKind) {
                                "Parents" -> "PARENTS"
                                "Hostel" -> "HOSTEL"
                                else -> "RENTAL"
                            }
                            viewModel.saveOnboardingAnswers(
                                // Only keys with live readers survive here. BudgetsScreen
                                // cold-start reads rent/transport/airtime; MealPlanner
                                // reads scan_food; parsePersona reads living/home/
                                // commute/cooking/persona. Everything else proved
                                // write-only and was cut.
                                "rent=$rentGuess|transport=$transportDaily" +
                                    "|airtime=$airtimeWeekly" +
                                    "|living=" + if (commuteForPlanning == "Far" && homeKind != "Parents") "COMMUTER" else "HOSTEL" +
                                    "|home=" + homeCode +
                                    "|commute=" + commuteForPlanning.uppercase() +
                                    "|cooking=" + if (cooksFood == "Yes") "YES" else "NO" +
                                    "|persona=" + (personaOverride.takeIf { it.isNotBlank() } ?: com.pesaflow.app.ui.budgets.parsePersona(
                                        "home=" + homeCode + "|commute=" + commuteForPlanning.uppercase() + "|cooking=" + if (cooksFood == "Yes") "YES" else "NO"
                                    ).name) +
                                    (scanResult?.let { s -> "|scan_food=" + s.monthlyFor("Food").toInt() } ?: "")
                            )
                            celebrate = true
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text(if (step < 5) "Next" else "Start Tracking 💰", fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Setup-complete celebration over everything, then home.
    if (celebrate) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.pesaflow.app.ui.motion.SuccessBurst(onDone = onDone)
                Text("Karibu nyumbani 🎉", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startPickerState.selectedDateMillis?.let { semesterStartMillis = it }
                    showStartPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = startPickerState)
        }
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endPickerState.selectedDateMillis?.let { endMillis = it }
                    showEndPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = endPickerState)
        }
    }
    if (showFeesDuePicker) {
        DatePickerDialog(
            onDismissRequest = { showFeesDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    feesDuePickerState.selectedDateMillis?.let { feesDueMillis = it }
                    showFeesDuePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showFeesDuePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = feesDuePickerState)
        }
    }
}


// Grand opening page: full-bleed backdrop, welcome, what the app does for
// you, what happens next (4 quick steps), and the honest permissions note.
// House voice is Mixed — the user hasn't picked a language yet.
@Composable
private fun GrandOpening(onBegin: () -> Unit, onSkip: () -> Unit) {
    val lang = AppLanguage.MIXED
    fun t(en: String, sw: String, sh: String, mix: String) =
        Copy4(en, sw, sh, mix).pick(lang)
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_opening),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060D1A).copy(alpha = 0.55f),
                        Color(0xFF060D1A).copy(alpha = 0.15f),
                        Color(0xFF060D1A).copy(alpha = 0.88f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(48.dp))
                Box(
                    Modifier.width(56.dp).height(4.dp)
                        .background(Color(0xFFD4AF37), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    t("Karibu PesaPlanner.", "Karibu PesaPlanner.", "Karibu PesaPlanner.", "Karibu PesaPlanner."),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    t(
                        "Your semester money, finally in one place. We read your M-Pesa texts so you never type them.",
                        "Pesa za muhula, sehemu moja. Tunasoma SMS zako za M-Pesa usiandike.",
                        "Mullah ya sem, place moja. Tuna-read texts zako za M-Pesa — hu-type kitu.",
                        "Pesa za sem ziko place moja. Tuna-read M-Pesa texts zako so hu-type."
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                OpeningRow(
                    "📩",
                    t("Auto-tracking", "Kufuatilia", "Auto-track", "Auto-tracking"),
                    t(
                        "M-Pesa texts are matched on your phone. Most wait for your review; trusted matches may be filed automatically.",
                        "SMS za M-Pesa hulinganishwa kwa simu yako. Nyingi unasoma kwanza; zinazotambulika vizuri zinaweza kuhifadhiwa moja kwa moja.",
                        "Texts za M-Pesa zina-matchiwa kwa simu yako. Nyingi unareview kwanza; trusted matches zinaweza kufilewa auto.",
                        "M-Pesa texts zina-matchiwa kwa simu yako. Nyingi zinasubiri review; trusted matches zinaweza kufilewa auto."
                    )
                )
                OpeningRow("🎯", t("Budgets that update", "Bajeti zinazo-update", "Budget live", "Budgets live"), t("What you enter now flows into budgets, net worth and planners.", "Unachoingiza sasa kinaingia bajeti, net worth na planners.", "Kile unaingiza sai kinaingia budget, net worth na planners.", "Kile unaingiza sai kinaingia budgets, net worth, planners."))
                OpeningRow("🗣️", t("Your language", "Lugha yako", "Lugha yako", "Lugha yako"), t("English, Kiswahili, Sheng or Mix — reports included.", "Kingereza, Kiswahili, Sheng ama Mix — ripoti pia.", "English, Swahili, Sheng ama Mix — repoti pia.", "English, Swahili, Sheng ama Mix — reports pia."))
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    t(
                        "Next: 5 quick steps — you + living, auto-tracking, semester money, about you. ~2 minutes. Skip anything.",
                        "Ifuatayo: hatua 5 rahisi — wewe + makazi, auto-tracking, pesa za muhula, kukuhusu. Dakika 2. Ruka chochote.",
                        "Next: steps 5 fasta — wewe + place, auto-track, mullah ya sem, about you. 2 mins. Skip chochote.",
                        "Next: steps 5 quick — wewe + living, auto-tracking, pesa za sem, about you. 2 mins. Skip anything."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    t(
                        "SMS access is optional for auto-tracking. Notification permission is requested only if you enable alerts.",
                        "Ruhusa ya SMS ni ya hiari kwa auto-tracking. Ruhusa ya notifications huombwa ukiwasha alerts.",
                        "SMS access ni optional kwa auto-track. Notifications tutaomba ukiwasha alerts.",
                        "SMS access ni optional kwa auto-tracking. Notification permission huombwa ukiwasha alerts."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD4AF37)
                )
                Text(
                    t(
                        "Built for all six setups — home or hostel, walking or long commutes, cooks and buyers alike.",
                        "Imejengwa kwa wote sita — nyumbani ama hostel, kutembea ama safari ndefu, wapishi na wanunuzi.",
                        "Imetengenezwa kwa hali zote sita — nyumbani au hosteli, kutembea au safari ndefu, wapishi na wanunuzi.",
                        "Built for setups zote six — home or hostel, walking or long commutes, cooks na buyers."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Column {
                Button(
                    onClick = onBegin,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37), contentColor = Color(0xFF0A2342))
                ) { Text(t("Begin — 2 mins", "Anza — dakika 2", "Begin — 2 mins", "Begin — 2 mins"), fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Explore sample data", "Angalia data ya mfano", "Explore sample data", "Explore sample data"), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun DemoDataConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Explore sample data?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This loads clearly labeled example transactions, budgets, and a savings goal. " +
                    "They are not your financial records. You can remove them later in Settings."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes, load demo data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Continue setup") }
        }
    )
}


// Step illustration: full-width photo banner that makes each onboarding
// step feel designed instead of form-like. Light-tinted assets, safe in
// both themes.
@Composable
private fun StepArt(bg: Int) {
    Image(
        painter = painterResource(id = bg),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)),
        contentScale = ContentScale.Crop
    )
    Spacer(modifier = Modifier.height(12.dp))
}


@Composable
private fun OpeningRow(emoji: String, title: String, body: String) {    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}


// Sender card: all of one sender's transactions with their date span. Three
// boxes — who they are, their usual category (empty = ask me each time),
// which direction it applies to — and typing the relation prefills the rest
// (Mother → Upkeep + money-in). Saving teaches the triple memory: future rows
// from them resolve and file themselves. Asked exactly once.
@Composable
private fun SenderCardRow(card: SenderCard, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var label by rememberSaveable(card.merchant) { mutableStateOf("") }
    var cat by rememberSaveable(card.merchant) { mutableStateOf("") }
    var scope by rememberSaveable(card.merchant) { mutableStateOf("BOTH") }
    var matchTerms by rememberSaveable(card.merchant) { mutableStateOf("") }
    var savedTick by remember { mutableStateOf(false) }
    val fmt = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
    val span = remember(card) {
        val a = fmt.format(java.util.Date(card.firstSeen))
        val b = fmt.format(java.util.Date(card.lastSeen))
        if (a == b) a else "$a – $b"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.merchant, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                card.count.toString() + " transactions · " + span,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val money = buildList {
                if (card.expenseTotal > 0) add("out KSh " + card.expenseTotal.toInt())
                if (card.incomeTotal > 0) add("in KSh " + card.incomeTotal.toInt())
            }.joinToString(" · ")
            if (money.isNotEmpty()) Text(money, style = MaterialTheme.typography.bodySmall)
            Text(
                "Looks like: " + card.suggestedCategory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = label,
                onValueChange = { v ->
                    label = v
                    // Typing the relation fills category + scope — only where
                    // you haven't typed or saved anything yourself.
                    val s = suggestMemory(v)
                    if (s.category.isNotBlank() && cat.isBlank()) cat = s.category
                    if (scope == "BOTH" && s.scope != "BOTH") scope = s.scope
                },
                label = { Text("Who is ${card.merchant} to you?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = cat,
                onValueChange = { cat = it },
                label = { Text("Usually which category? (empty = ask me)") },
                placeholder = { Text("e.g. Upkeep, Food, Transport") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = matchTerms,
                onValueChange = { matchTerms = it },
                label = { Text("Also match names (optional)") },
                placeholder = { Text("Nancy, Daniel Mayhvjh") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Applies to:", style = MaterialTheme.typography.bodySmall)
                listOf("OUT" to "Money out", "IN" to "Money in", "BOTH" to "Both").forEach { (s, text) ->
                    FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(text) })
                }
            }
            Button(onClick = {
                val prefs = ctx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                if (saveContactMemory(
                        prefs, card.merchant, label,
                        cat.ifBlank { card.suggestedCategory }, scope, matchTerms
                    )
                ) {
                    // Backfill the legacy maps too so every reader agrees.
                    if (label.isNotBlank()) MerchantMemory.learnAlias(prefs, card.merchant, label)
                    if (cat.isNotBlank()) CategoryMemory.learn(prefs, card.merchant, cat)
                    savedTick = true
                    onSaved()
                }
            }) { Text(if (savedTick) "Saved ✓" else "Save") }
        }
    }
}


@Composable
private fun ReviewRow(label: String, value: String, tag: String) {    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "$value · $tag",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (tag == "skip") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}
