package com.pesaflow.app.parsers

import com.pesaflow.app.data.money.DriftZone
import com.pesaflow.app.data.money.evaluateDrift
import com.pesaflow.app.data.money.ledgerBalance
import com.pesaflow.app.data.money.liquidCash
import com.pesaflow.app.data.money.openingBasis
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


/** Opening equity is held cash exactly once — never earnings, never doubled. */
class MoneyMathTest {

    @Test
    fun `opening rows win over profile funding never both`() {
        // Normal onboarding: pocket 5000 seeded as rows AND profile funding.
        assertEquals(8000.0, openingBasis(openingRows = 8000.0, profileFunding = 5000.0), 0.001)
        // Manual path: funding set under University, no seeded rows.
        assertEquals(5000.0, openingBasis(openingRows = 0.0, profileFunding = 5000.0), 0.001)
        // Nothing anywhere.
        assertEquals(0.0, openingBasis(openingRows = 0.0, profileFunding = 0.0), 0.001)
    }

    @Test
    fun `liquid cash is opening plus earnings minus outflows`() {
        assertEquals(
            8000.0 + 20000.0 - 12000.0 - 3000.0 - 0.0,
            liquidCash(8000.0, 20000.0, 12000.0, 3000.0, 0.0),
            0.001
        )
    }

    @Test
    fun `reconcile books the gap in the right direction`() {
        // Ledger higher → spending went unlogged → EXPENSE pulls it down.
        val down = com.pesaflow.app.data.money.reconcileEntry(ledgerMpesa = 600.0, smsBalance = 482.0)!!
        assertEquals(118.0, down.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, down.type)
        assertEquals(com.pesaflow.app.data.models.PaymentMethod.MPESA, down.paymentMethod)
        // SMS higher → income went unlogged → INCOME tops it up.
        val up = com.pesaflow.app.data.money.reconcileEntry(ledgerMpesa = 400.0, smsBalance = 482.0)!!
        assertEquals(82.0, up.amount, 0.001)
        assertEquals(TransactionType.INCOME, up.type)
        // Within a shilling → already reconciled, no row.
        assertNull(com.pesaflow.app.data.money.reconcileEntry(ledgerMpesa = 482.4, smsBalance = 482.0))
    }

    @Test
    fun `spendable folds the confirming queue into the ledger`() {
        // Ledger 5000, one 250 kibanda row awaiting confirm, one 1000 HELB
        // row awaiting confirm: reality is 5750, ledger alone says 5000.
        assertEquals(
            5750.0,
            com.pesaflow.app.data.money.spendableNow(ledgerLiquid = 5000.0, pendingIn = 1000.0, pendingOut = 250.0),
            0.001
        )
        assertEquals(
            5000.0,
            com.pesaflow.app.data.money.spendableNow(ledgerLiquid = 5000.0, pendingIn = 0.0, pendingOut = 0.0),
            0.001
        )
    }

    @Test
    fun `drift zones stay silent small nudge medium and flag large`() {
        // Rounding wobble: silent.
        assertEquals(DriftZone.IN_SYNC, evaluateDrift(482.0, 500.0).first)
        assertEquals(DriftZone.IN_SYNC, evaluateDrift(482.0, 432.0).first)
        // One unlogged row: gentle nudge.
        val (minorZone, minorDrift) = evaluateDrift(482.0, 682.0)
        assertEquals(DriftZone.MINOR, minorZone)
        assertEquals(200.0, minorDrift, 0.001)
        // The recording's -1956: red flag, reconcile path.
        val (majorZone, majorDrift) = evaluateDrift(482.0, -1474.0)
        assertEquals(DriftZone.MAJOR, majorZone)
        assertEquals(-1956.0, majorDrift, 0.001)
    }

    @Test
    fun `paired transfer legs move pockets wealth neutrally`() {
        // M-Pesa → Bank 1000: pockets move, the total does not.
        val group = "g1"
        val txs = listOf(
            Transaction(amount = 1000.0, type = TransactionType.TRANSFER, category = "Transfer", dateTimestamp = 1L, merchant = "Transfer out", description = "", paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA, accountKind = "M_PESA", transferGroupId = group, transferSide = "OUT"),
            Transaction(amount = 1000.0, type = TransactionType.TRANSFER, category = "Transfer", dateTimestamp = 1L, merchant = "Transfer in", description = "", paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA, accountKind = "BANK", transferGroupId = group, transferSide = "IN"),
            Transaction(amount = 5000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = 1L, merchant = "Job", description = "", paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA)
        )
        val mpesa = com.pesaflow.app.data.money.pocketBalance(txs, com.pesaflow.app.data.models.PaymentMethod.MPESA)
        val bank = com.pesaflow.app.data.money.pocketBalance(txs, com.pesaflow.app.data.models.PaymentMethod.BANK_TRANSFER)
        val cash = com.pesaflow.app.data.money.pocketBalance(txs, com.pesaflow.app.data.models.PaymentMethod.CASH)
        assertEquals(5000.0 - 1000.0, mpesa, 0.001)
        assertEquals(1000.0, bank, 0.001)
        assertEquals(0.0, cash, 0.001)
        // Unpaired legacy transfers still read zero everywhere.
        val legacy = listOf(
            Transaction(amount = 700.0, type = TransactionType.TRANSFER, category = "Transfer", dateTimestamp = 1L, merchant = "Move", description = "", paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA)
        )
        assertEquals(0.0, com.pesaflow.app.data.money.pocketBalance(legacy, com.pesaflow.app.data.models.PaymentMethod.MPESA), 0.001)
    }

    @Test
    fun `ledger balance counts opening rows as held cash`() {
        val txs = listOf(
            Transaction(amount = 5000.0, type = TransactionType.INCOME, category = "Income", dateTimestamp = 1L, merchant = "Opening balance", description = "", isOpening = true),
            Transaction(amount = 20000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = 1L, merchant = "Job", description = ""),
            Transaction(amount = 12000.0, type = TransactionType.EXPENSE, category = "Food", dateTimestamp = 1L, merchant = "Kibanda", description = "")
        )
        assertEquals(13000.0, ledgerBalance(txs), 0.001)
    }

    @Test
    fun `month income pace excludes opening equity`() {
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 15)
            set(java.util.Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
        val txs = listOf(
            Transaction(amount = 5000.0, type = TransactionType.INCOME, category = "Income", dateTimestamp = now, merchant = "Opening balance", isOpening = true),
            Transaction(amount = 1200.0, type = TransactionType.INCOME, category = "Income", dateTimestamp = now, merchant = "Sponsor")
        )
        assertEquals(
            1200.0,
            com.pesaflow.app.data.money.monthScopedTotal(txs, TransactionType.INCOME, now),
            0.001
        )
    }
}
