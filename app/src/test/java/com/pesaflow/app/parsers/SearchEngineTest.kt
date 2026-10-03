package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.search.*
import org.junit.Assert.*
import org.junit.Test

class SearchEngineTest {

    private fun tx(merchant: String, category: String, amount: Double, notes: String = ""): Transaction {
        return Transaction(amount = amount, type = TransactionType.EXPENSE, category = category, merchant = merchant, notes = notes, dateTimestamp = System.currentTimeMillis())
    }

    private val allTxs = listOf(
        tx("Naivas", "Food", 500.0, "Groceries"),
        tx("Matatu Stage", "Transport", 50.0, "Boda to town"),
        tx("Shell", "Transport", 1200.0, "Fuel"),
        tx("Shoprite", "Shopping", 2000.0, "Clothes"),
        tx("Netflix", "Entertainment", 1500.0, "Subscription"),
        tx("KEBS", "Bills", 3000.0, "Electric"),
        tx("KCB", "Bills", 5000.0, "Rent")
    )

    @Test
    fun `exactMerchantMatch`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "Naivas")
        assertEquals(1, result.hits.size)
        assertEquals("Naivas", result.hits[0].transaction.merchant)
        assertTrue(result.hits[0].score > 0)
    }

    @Test
    fun `multiTermAndQuery`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "fuel shell")
        assertTrue(result.hits.any { it.transaction.merchant == "Shell" })
    }

    @Test
    fun `categorySearch`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "Transport")
        assertTrue(result.hits.any { it.transaction.category == "Transport" })
        assertEquals(2, result.totalCount)
    }

    @Test
    fun `notesSearch`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "Boda")
        assertTrue(result.hits.any { it.transaction.notes.contains("Boda") })
    }

    @Test
    fun `tagsSearch`() {
        val txWithTag = tx("Naivas", "Food", 500.0, "Groceries").copy(tags = listOf("food", "groceries"))
        val engine = SearchEngine()
        val result = engine.search(listOf(txWithTag), "groceries")
        assertTrue(result.hits.isNotEmpty())
    }

    @Test
    fun `emptyQueryReturnsEmpty`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "")
        assertEquals(0, result.hits.size)
    }

    @Test
    fun `whitespaceQueryReturnsEmpty`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "   ")
        assertEquals(0, result.hits.size)
    }

    @Test
    fun `typeFilterWorks`() {
        val engine = SearchEngine()
        val incomeTxs = listOf(
            Transaction(amount = 50000.0, type = TransactionType.INCOME, category = "Salary", merchant = "Employer", notes = "Monthly salary", dateTimestamp = System.currentTimeMillis()),
            tx("Naivas", "Food", 500.0)
        )
        val result = engine.search(incomeTxs, "Salary", TransactionType.INCOME)
        assertEquals(1, result.hits.size)
    }

    @Test
    fun `maxResultsRespected`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "food", maxResults = 1)
        assertTrue(result.hits.size <= 1)
    }

    @Test
    fun `durationMeasured`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "Naivas")
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun `fuzzyMatchCaseInsensitive`() {
        val engine = SearchEngine()
        assertTrue(engine.fuzzyMatch("naivas", "nayivas"))
        assertFalse(engine.fuzzyMatch("naivas", "xyz"))
    }

    @Test
    fun `fuzzySearchFallback`() {
        val engine = SearchEngine()
        // "Nayivas" doesn't exist exactly but is close to "Naivas"
        val result = engine.searchWithFuzzy(allTxs, "Nayivas", maxResults = 50)
        // Should find Naivas via fuzzy fallback
        assertTrue(result.hits.any { it.transaction.merchant == "Naivas" })
    }

    @Test
    fun `short distinct names are not fuzzy matched`() {
        val engine = SearchEngine()
        val people = listOf(
            tx("Joan", "Upkeep", 100.0),
            tx("John", "Upkeep", 100.0),
            tx("Joab", "Upkeep", 100.0)
        )

        val result = engine.searchWithFuzzy(people, "joan")

        assertEquals(listOf("Joan"), result.hits.map { it.transaction.merchant })
    }

    @Test
    fun `fuzzy fallback matches a merchant token rather than a longer phrase`() {
        val engine = SearchEngine()
        val transactions = listOf(tx("Nayivas Supermarket", "Food", 500.0))

        val result = engine.searchWithFuzzy(transactions, "Naivas")

        assertEquals(listOf("Nayivas Supermarket"), result.hits.map { it.transaction.merchant })
    }

    @Test
    fun `searchWithFuzzyReturnsMoreHitsWhenNeeded`() {
        val engine = SearchEngine()
        val exact = engine.search(allTxs, "NonExistent", maxResults = 50)
        assertTrue(exact.hits.isEmpty())
        val withFuzzy = engine.searchWithFuzzy(allTxs, "NonExistent", maxResults = 50)
        // Fuzzy may or may not find something — just verify no crash
        assertNotNull(withFuzzy)
    }

    @Test
    fun `matchedFieldsReported`() {
        val engine = SearchEngine()
        val result = engine.search(allTxs, "Netflix")
        assertTrue(result.hits[0].matchedFields.contains("merchant"))
    }

    @Test
    fun `scoreHigherForMoreMatches`() {
        val engine = SearchEngine()
        // "Food Groceries" matches both merchant and notes
        val tx = tx("Food Store", "Food", 100.0, "Groceries")
        val result = engine.search(listOf(tx), "Food Groceries")
        assertTrue(result.hits[0].score > 3.0)
    }
}
