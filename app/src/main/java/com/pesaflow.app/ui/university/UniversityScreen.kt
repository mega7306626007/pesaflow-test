package com.pesaflow.app.ui.university

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.UniversityProfile
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.R
import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.finance.calculateSemesterRunway
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.SkinAccentLine
import com.pesaflow.app.ui.theme.SkinCampus
import com.pesaflow.app.ui.theme.SkinCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityScreen(viewModel: FinanceViewModel) {
    val universityProfile by viewModel.universityProfile.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val financialSnapshot by viewModel.financialSnapshot.collectAsState()
    // Explicit save receipts: profile edits used to close silently, so
    // "did it save?" was unanswerable on-device.
    val screenContext = LocalContext.current
    fun savedToast(msg: String) {
        android.widget.Toast.makeText(screenContext, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAllowanceDialog by remember { mutableStateOf(false) }
    var showSavingsDialog by remember { mutableStateOf(false) }


    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = SkinCampus.tint, bgRes = R.drawable.bg_university_campus)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("University Profile", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val up = universityProfile
                val heroDaysLeft = run {
                    val e = up?.semesterEndTimestamp ?: 0L
                    if (e > System.currentTimeMillis()) ((e - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt() else 0
                }
                SkinCard(skin = SkinCampus) {
                    Text("Campus command", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkinAccentLine(SkinCampus.accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        (up?.universityName?.takeIf { it.isNotBlank() } ?: "Your campus") + " - Semester " + ((up?.currentSemester ?: 1).toString()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (heroDaysLeft > 0) heroDaysLeft.toString() + " days left - KSh " + (up?.startingFunding ?: 0.0).toInt() + " starting funds" else "Set semester dates in Edit Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // University Info Header
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "University Finance Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showEditDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Edit Profile")
                    }
                }

                // Profile details (local val enables smart cast)
                val profile = universityProfile
                if (profile != null) {
                    UniversityProfileCard(
                        universityName = profile.universityName,
                        campus = profile.campus,
                        programme = profile.programme,
                        yearOfStudy = profile.yearOfStudy,
                        currentSemester = profile.currentSemester,
                        academicYear = profile.academicYear,
                        startingFunding = profile.startingFunding
                    )
                } else {
                    UniversityProfileCard(
                        universityName = "--",
                        campus = "--",
                        programme = "",
                        yearOfStudy = "",
                        currentSemester = 1,
                        academicYear = "--",
                        startingFunding = 0.0
                    )
                }


                // Semester Financial Planner (real math from live transactions)
                UniversityFinancialPlanner(
                    profile = profile,
                    transactions = transactions,
                    committed = financialSnapshot.committed.toDouble(),
                    viewModel = viewModel
                )


                // Action Buttons
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Button(
                        onClick = { showAllowanceDialog = true },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Set Allowance")
                    }
                    Button(
                        onClick = { showSavingsDialog = true },
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Add Savings Goal")
                    }
                }
            }
    }

    if (showEditDialog) {
        val current = universityProfile
        var name by remember { mutableStateOf(current?.universityName ?: "") }
        var campus by remember { mutableStateOf(current?.campus ?: "") }
        var programme by remember { mutableStateOf(current?.programme ?: "") }
        var yearOfStudy by remember { mutableStateOf(current?.yearOfStudy ?: "") }
        var semester by remember { mutableStateOf((current?.currentSemester ?: 1).toString()) }
        var year by remember { mutableStateOf(current?.academicYear ?: "") }
        var fees by remember { mutableStateOf((current?.feesAmount ?: 0.0).takeIf { it > 0 }?.toInt()?.toString() ?: "") }
        var helb by remember { mutableStateOf((current?.helbExpected ?: 0.0).takeIf { it > 0 }?.toInt()?.toString() ?: "") }
        var fundSource by remember { mutableStateOf(current?.fundingSource ?: "HELB") }
        var semesterStart by remember { mutableStateOf(current?.semesterStartTimestamp ?: 0L) }
        var semesterEnd by remember { mutableStateOf(current?.semesterEndTimestamp ?: 0L) }
        var feesDueDate by remember { mutableStateOf(current?.feesDueDate ?: 0L) }
        var profileError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit University Profile") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("University") })
                    OutlinedTextField(value = campus, onValueChange = { campus = it }, label = { Text("Campus") })
                    OutlinedTextField(value = programme, onValueChange = { programme = it }, label = { Text("Programme or course (optional)") })
                    OutlinedTextField(value = yearOfStudy, onValueChange = { yearOfStudy = it }, label = { Text("Year of study (optional)") })
                    OutlinedTextField(value = semester, onValueChange = { semester = it }, label = { Text("Semester") })
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Academic Year") })
                    OutlinedTextField(value = fees, onValueChange = { fees = it }, label = { Text("Fees owed (KSh, optional)") })
                    OutlinedTextField(value = helb, onValueChange = { helb = it }, label = { Text("HELB expected (KSh, optional)") })
                    TimestampPickerField("Semester start date", semesterStart) { semesterStart = it }
                    TimestampPickerField("Semester end date", semesterEnd) { semesterEnd = it }
                    TimestampPickerField("Fees due date", feesDueDate) { feesDueDate = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HELB", "SELF", "BOTH").forEach { s ->
                        FilterChip(selected = fundSource == s, onClick = { fundSource = s }, label = { Text(s) })
                        }
                    }
                    profileError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sem = semester.toIntOrNull()
                    val feesValue = fees.toDoubleOrNull() ?: 0.0
                    val helbValue = helb.toDoubleOrNull() ?: 0.0
                    when {
                        sem == null || sem < 1 -> profileError = "Enter a valid semester number."
                        feesValue < 0 || helbValue < 0 -> profileError = "Amounts cannot be negative."
                        semesterStart > 0 && semesterEnd > 0 && semesterEnd <= semesterStart ->
                        profileError = "Semester end must be after its start."
                        else -> {
                        viewModel.saveUniversityProfile(
                            (current ?: UniversityProfile()).copy(
                                universityName = name.trim(),
                                campus = campus.trim(),
                                programme = programme.trim(),
                                yearOfStudy = yearOfStudy.trim(),
                                currentSemester = sem,
                                academicYear = year.trim(),
                                feesAmount = feesValue,
                                helbExpected = helbValue,
                                feesDueDate = feesDueDate,
                                fundingSource = fundSource,
                                semesterStartTimestamp = semesterStart,
                                semesterEndTimestamp = semesterEnd
                            )
                        )
                        showEditDialog = false
                        savedToast("Profile saved ✓ — dates and finance figures updated.")
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAllowanceDialog) {
        var allowance by remember { mutableStateOf((universityProfile?.startingFunding ?: 0.0).toString()) }

        AlertDialog(
            onDismissRequest = { showAllowanceDialog = false },
            title = { Text("Semester Starting Funds") },
            text = {
                OutlinedTextField(value = allowance, onValueChange = { allowance = it }, label = { Text("Amount (KSh)") })
            },
            confirmButton = {
                Button(onClick = {
                    val amt = allowance.toDoubleOrNull()
                    if (amt != null && amt >= 0) {
                        viewModel.saveUniversityProfile(
                            (universityProfile ?: UniversityProfile()).copy(startingFunding = amt)
                        )
                        showAllowanceDialog = false
                        savedToast("Starting funds set to KSh ${amt.toInt()} ✓")
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAllowanceDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSavingsDialog) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("90") }

        AlertDialog(
            onDismissRequest = { showSavingsDialog = false },
            title = { Text("New Savings Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal name") })
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target (KSh)") })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Days from now") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetVal = target.toDoubleOrNull()
                    val daysVal = days.toIntOrNull()
                    if (targetVal != null && targetVal > 0 && daysVal != null && title.isNotBlank()) {
                        viewModel.addSavingsGoal(title.trim(), targetVal, daysVal)
                        showSavingsDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSavingsDialog = false }) { Text("Cancel") } }
        )
    }
    }
}


@Composable
fun UniversityProfileCard(
    universityName: String,
    campus: String,
    programme: String,
    yearOfStudy: String,
    currentSemester: Int,
    academicYear: String,
    startingFunding: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("University Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            UniversityInfoRow(label = "University", value = universityName)
            UniversityInfoRow(label = "Campus", value = campus)
            if (programme.isNotBlank()) UniversityInfoRow(label = "Programme", value = programme)
            if (yearOfStudy.isNotBlank()) UniversityInfoRow(label = "Year of Study", value = yearOfStudy)
            UniversityInfoRow(label = "Semester", value = "$currentSemester")
            UniversityInfoRow(label = "Academic Year", value = academicYear)
            UniversityInfoRow(label = "Starting Funding", value = "KSh ${startingFunding.toInt()}")
        }
    }
}


@Composable
fun UniversityInfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(120.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Default.ArrowForward,
            contentDescription = "More info",
            tint = Color.Gray
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimestampPickerField(
    label: String,
    timestamp: Long,
    onSelected: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp.takeIf { it > 0L })
    val display = if (timestamp > 0L) {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    } else {
        "Not set"
    }
    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $display")
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let(onSelected)
                        showPicker = false
                    }
                ) { Text("Set date") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}


@Composable
fun UniversityFinancialPlanner(
    profile: UniversityProfile?,
    transactions: List<Transaction>,
    committed: Double,
    viewModel: FinanceViewModel
) {
    val day = 24L * 60 * 60 * 1000
    val now = System.currentTimeMillis()
    val runway = profile?.let {
        calculateSemesterRunway(
            transactions = transactions,
            startTimestamp = it.semesterStartTimestamp,
            endTimestamp = it.semesterEndTimestamp,
            startingFunding = it.startingFunding,
            committed = committed,
            now = now
        )
    }


    if (runway == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Semester Financial Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Semester dates needed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Set the actual semester start and end dates in your university profile. The forecast will use your ledger and unpaid commitments; it will not guess a 120-day term.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Semester Financial Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    MoneyFormatter.compact(Money.of(runway.availableAfterCommitments)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (runway.availableAfterCommitments < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                "available after KSh ${MoneyFormatter.compact(Money.of(runway.committed))} in bills, fees, debts and goals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            UniversityInfoRow(label = "Opening funds", value = MoneyFormatter.compact(Money.of(runway.openingFunds)))
            UniversityInfoRow(label = "Income since start", value = MoneyFormatter.compact(Money.of(runway.income)), color = MaterialTheme.colorScheme.primary)
            UniversityInfoRow(label = "Outflows", value = MoneyFormatter.compact(Money.of(runway.outflows)), color = MaterialTheme.colorScheme.error)
            UniversityInfoRow(label = "Remaining before commitments", value = MoneyFormatter.compact(Money.of(runway.remainingBeforeCommitments)))
            UniversityInfoRow(label = "Daily outflow pace", value = MoneyFormatter.compact(Money.of(runway.dailyPace)) + "/day")
            UniversityInfoRow(
                label = "Time left",
                value = if (runway.isUpcoming) "Starts in ${runway.daysUntilStart} days"
                    else if (runway.isEnded) "Semester ended"
                    else "${runway.daysRemaining} days"
            )
            UniversityInfoRow(
                label = if (runway.weeklyAllowance < 0) "Weekly shortfall" else "Weekly allowance",
                value = MoneyFormatter.compact(Money.of(kotlin.math.abs(runway.weeklyAllowance))),
                color = if (runway.weeklyAllowance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            UniversityInfoRow(
                label = "Projected after commitments",
                value = MoneyFormatter.compact(Money.of(runway.projectedEndAfterCommitments)),
                color = if (runway.projectedEndAfterCommitments < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            // Persona semester note: the same runway means different things.
            val semPersona = remember { com.pesaflow.app.ui.budgets.parsePersona(viewModel.getOnboardingAnswers()) }
            val semNote = when (semPersona) {
                com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR, com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE ->
                    "Long commute ahead — protect fares before lifestyle."
                com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK ->
                    "Every meal bought — Food decides this semester."
                com.pesaflow.app.ui.budgets.Persona.PARENTS_NEAR ->
                    "Home-fed stretch — small leaks, not rent, end semesters."
                else -> null
            }
            if (semNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(semNote, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            val healthTotal = (runway.openingFunds + runway.income).coerceAtLeast(1.0)
            val healthFrac = (runway.availableAfterCommitments / healthTotal).toFloat().coerceIn(0f, 1f)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Semester health " + (healthFrac * 100).toInt() + "%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { healthFrac },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = when {
                    runway.availableAfterCommitments < 0 || runway.projectedEndAfterCommitments < 2000 -> MaterialTheme.colorScheme.error
                    healthFrac < 0.3f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val feesAmt = profile?.feesAmount ?: 0.0
            val feesDue = profile?.feesDueDate ?: 0L
            if (feesAmt > 0) {
                val feeDays = ((feesDue - now) / day).toInt()
                UniversityInfoRow(
                    label = "Fees owed",
                    value = "KSh " + feesAmt.toInt() + if (feesDue <= 0L) "" else if (feeDays < 0) " - overdue " + (-feeDays) + "d" else " - due in " + feeDays + "d",
                    color = if (feeDays < 0 && feesDue > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                val helbExp = profile?.helbExpected ?: 0.0
                if (helbExp > 0) {
                    val afterFees = helbExp - feesAmt
                    UniversityInfoRow(
                        label = "HELB after fees",
                        value = "KSh " + afterFees.toInt() + if (afterFees >= 0) " for upkeep" else " short — gap plan",
                        color = if (afterFees >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                when {
                    runway.isUpcoming -> "Term not started — this forecast includes the opening funds and recorded commitments."
                    runway.isEnded -> "Semester window ended. Set the dates for your next term to start a new forecast. 🏁"
                    runway.availableAfterCommitments < 0 -> "⚠️ Recorded commitments exceed the semester money available."
                    runway.projectedEndAfterCommitments < 2000 -> "⚠️ At this outflow pace the projected amount after commitments is ${MoneyFormatter.compact(Money.of(runway.projectedEndAfterCommitments))}."
                    else -> "Projection uses your recorded ledger pace; future unconfirmed income is not included."
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (runway.availableAfterCommitments < 0 || runway.projectedEndAfterCommitments < 2000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            if (!runway.isEnded && !runway.isUpcoming && (runway.availableAfterCommitments < 0 || runway.projectedEndAfterCommitments < 2000)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Broke-week essentials 🛟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    "Ugali + sukuma stretches furthest per shilling",
                    "Cook in bulk twice a week, reheat",
                    "Carry water — skip the soda",
                    "Walk trips under 2 km",
                    "Borrow notes, don't buy bundles for PDFs"
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}
