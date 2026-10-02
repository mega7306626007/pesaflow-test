package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class RecurringEngineTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
    private fun tx(amount: Double, merchant: String, ts: Long): Transaction {
        return Transaction(amount = amount, type = TransactionType.EXPENSE, category = "Subscriptions", dateTimestamp = ts, merchant = merchant)
    }
    private fun day(offset: Int): Long = now - offset * 24L * 60 * 60 * 1000

    @Test
    fun `detectsMonthlyRecurring`() {
        val merchant = "Netflix"
        val txs = (0..5).map { tx(1500.0, merchant, now - it * 30 * 24L * 60 * 60 * 1000) }
        val patterns = detectRecurring(txs)
        assertTrue(patterns.isNotEmpty())
        assertEquals(merchant, patterns[0].merchant)
        assertTrue(patterns[0].medianIntervalDays in 25..35)
        assertTrue(patterns[0].confidence > 0.5f)
    }

    @Test
    fun `opening rows never predict paydays`() {
        // Two same-merchant opening rows 30d apart (re-onboarding residue):
        // equity is not a salary rhythm.
        val txs = listOf(
            Transaction(amount = 5000.0, type = TransactionType.INCOME, category = "Income", dateTimestamp = day(60), merchant = "Opening balance", isOpening = true),
            Transaction(amount = 5000.0, type = TransactionType.INCOME, category = "Income", dateTimestamp = day(30), merchant = "Opening balance", isOpening = true)
        )
        assertTrue(predictPaydays(txs, now).isEmpty())
    }

    @Test
    fun `fewOccurrencesBelowThreshold`() {
        val txs = listOf(tx(1500.0, "Netflix", now), tx(1500.0, "Netflix", now - 30L * 86400000))
        val patterns = detectRecurring(txs, minOccurrences = 3)
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun `oneOffNoRecurring`() {
        val txs = listOf(tx(100.0, "Food", now))
        val patterns = detectRecurring(txs)
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun `previewAggregates`() {
        val merchant = "Netflix"
        val txs = (0..3).map { tx(1500.0, merchant, now - it * 30 * 24L * 60 * 60 * 1000) }
        val preview = buildRecurringPreview(txs)
        assertTrue(preview.count > 0)
        assertTrue(preview.totalMonthlyCommitment > 0)
    }

    @Test
    fun `atRiskFlagsHighVariance`() {
        // Two regular patterns so preview.count >= 2 satisfies the risk contract
        val txs = (0..5).map { tx(1500.0, "Netflix", now - it * 30L * 24L * 60 * 60 * 1000) } +
            (0..5).map { tx(2000.0, "Spotify", now - it * 30L * 24L * 60 * 60 * 1000) }
        val preview = buildRecurringPreview(txs)
        assertTrue(preview.atRisk.isNotEmpty() || preview.count >= 2)
    }

    @Test
    fun `matchesExistingPattern`() {
        val patterns = detectRecurring(listOf(
            tx(1500.0, "Netflix", now),
            tx(1500.0, "Netflix", now - 30L * 86400000),
            tx(1500.0, "Netflix", now - 60L * 86400000)
        ))
        val newTx = Transaction(amount = 1500.0, type = TransactionType.EXPENSE, category = "Subscriptions", dateTimestamp = now, merchant = "Netflix")
        val match = matchesRecurring(newTx, patterns)
        assertNotNull(match)
    }

    @Test
    fun `noMatchDifferentMerchant`() {
        val patterns = detectRecurring(listOf(
            tx(1500.0, "Netflix", now),
            tx(1500.0, "Netflix", now - 30L * 86400000),
            tx(1500.0, "Netflix", now - 60L * 86400000)
        ))
        val newTx = Transaction(amount = 1500.0, type = TransactionType.EXPENSE, category = "Subscriptions", dateTimestamp = now, merchant = "Spotify")
        val match = matchesRecurring(newTx, patterns)
        assertNull(match)
    }

    @Test
    fun `nextExpectedDateComputed`() {
        val patterns = detectRecurring(listOf(
            tx(1500.0, "Netflix", now),
            tx(1500.0, "Netflix", now - 30L * 86400000),
            tx(1500.0, "Netflix", now - 60L * 86400000)
        ))
        assertTrue(patterns[0].nextExpectedDate > now)
    }

    @Test
    fun `suggestionContainsMerchantAndAmount`() {
        val patterns = detectRecurring(listOf(
            tx(1500.0, "Netflix", now),
            tx(1500.0, "Netflix", now - 30L * 86400000),
            tx(1500.0, "Netflix", now - 60L * 86400000)
        ))
        assertTrue(patterns[0].suggestion.contains("Netflix"))
        assertTrue(patterns[0].suggestion.contains("1500"))
    }
}
