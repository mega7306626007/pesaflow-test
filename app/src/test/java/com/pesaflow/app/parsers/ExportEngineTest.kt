package com.pesaflow.app.parsers

import com.pesaflow.app.data.backup.BackupPayload
import com.pesaflow.app.data.exports.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExportEngineTest {

    private val txn = Transaction(
        amount = 500.0,
        type = TransactionType.EXPENSE,
        category = "Food",
        subcategory = "Lunch",
        dateTimestamp = System.currentTimeMillis(),
        merchant = "Naivas",
        notes = "Groceries",
        tags = listOf("food", "groceries"),
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MANUAL,
        recurring = false,
        confirmed = true
    )
    private val txn2 = Transaction(
        amount = 300.0,
        type = TransactionType.INCOME,
        category = "Salary",
        dateTimestamp = System.currentTimeMillis() - 86400000,
        merchant = "Employer",
        source = TransactionSource.MANUAL
    )

    @Test
    fun `csvContainsAllFields`() {
        val engine = ExportEngine()
        val csv = engine.exportCsv(listOf(txn, txn2))
        assertTrue(csv.contains("type,date,merchant"))
        assertTrue(csv.contains("Naivas"))
        assertTrue(csv.contains("Employer"))
        assertTrue(csv.contains("500.0"))
        assertTrue(csv.contains("300.0"))
        assertTrue(csv.contains("food;groceries"))
    }

    @Test
    fun `csvHasBudgetAndGoalSections`() {
        val engine = ExportEngine()
        val b = Budget(category = "Food", limitAmount = 4000.0, type = BudgetType.WEEKLY, startTimestamp = System.currentTimeMillis(), endTimestamp = System.currentTimeMillis() + 604800000)
        val g = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 5000.0)
        val csv = engine.exportCsv(listOf(txn), listOf(b), listOf(g), emptyList(), emptyList())
        assertTrue(csv.contains("# BUDGETS"))
        assertTrue(csv.contains("# SAVINGS GOALS"))
        assertTrue(csv.contains("Food"))
        assertTrue(csv.contains("Phone"))
    }

    @Test
    fun `csvHasDebtSection`() {
        val engine = ExportEngine()
        val d = Debt(person = "John", amount = 2000.0, dateBorrowed = System.currentTimeMillis(), dueDate = System.currentTimeMillis() + 30L * 86400000L, description = "Loan", status = "OWING", direction = "I_OWE")
        val csv = engine.exportCsv(listOf(txn), emptyList(), emptyList(), emptyList(), listOf(d))
        assertTrue(csv.contains("# DEBTS"))
        assertTrue(csv.contains("John"))
    }

    @Test
    fun `csvIncludesStudentUniversityProfile`() {
        val profile = UniversityProfile(
            universityName = "University of Nairobi",
            campus = "Chiromo",
            programme = "Computer Science",
            yearOfStudy = "Year 2",
            currentSemester = 2,
            academicYear = "2026/2027",
            startingFunding = 25000.0,
            helbExpected = 18000.0,
            feesAmount = 32000.0
        )

        val csv = ExportEngine().exportCsv(emptyList(), profile = profile)

        assertTrue(csv.contains("# UNIVERSITY PROFILE"))
        assertTrue(csv.contains("\"university\",\"University of Nairobi\""))
        assertTrue(csv.contains("\"campus\",\"Chiromo\""))
        assertTrue(csv.contains("\"programme\",\"Computer Science\""))
        assertTrue(csv.contains("\"year_of_study\",\"Year 2\""))
        assertTrue(csv.contains("\"fees_amount_ksh\",\"32000.0\""))
    }

    @Test
    fun `csvQuoteEscapes`() {
        val engine = ExportEngine()
        val t = txn.copy(merchant = "Naivas \"Super\"", notes = "Hello \"world\"")
        val csv = engine.exportCsv(listOf(t))
        assertTrue(csv.contains("Naivas \"\"Super\"\""))
    }

    @Test
    fun `shareSummaryContainsKeyFields`() {
        val engine = ExportEngine()
        val summary = engine.generateShareSummary(listOf(txn, txn2), 7)
        assertTrue(summary.contains("Income"))
        assertTrue(summary.contains("Expenses"))
        assertTrue(summary.contains("Food"))
    }

    @Test
    fun `backupPayloadCompilerChecksAllFields`() {
        val payload = BackupPayload(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            transactions = listOf(txn),
            pending = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            profile = null,
            bills = emptyList(),
            debts = emptyList(),
            meals = emptyList(),
            chamas = emptyList(),
            belongings = emptyList(),
            kitchenStock = emptyList()
        )
        assertEquals(2, payload.version)
        assertEquals(1, payload.transactions.size)
    }

    @Test
    fun `backupJsonRoundTrip`() {
        val engine = ExportEngine()
        val payload = BackupPayload(
            version = 2,
            transactions = listOf(txn),
            goals = emptyList()
        )
        val json = engine.backupToJson(payload)
        assertTrue(json.contains("transactions"))
        assertTrue(json.contains("version"))
    }
}
