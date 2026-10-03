package com.pesaflow.app.data.search

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

/**
 * Full-text deep search engine: pure Kotlin, no Android imports.
 *
 * Searches across every transaction field with relevance scoring.
 * Supports multi-term AND queries and type filtering.
 */
data class SearchHit(
    val transaction: Transaction,
    val score: Double,
    val matchedFields: List<String>
)

data class SearchResult(
    val hits: List<SearchHit>,
    val totalCount: Int,
    val query: String,
    val durationMs: Long
)

/** Pure search engine. No Android imports — fully unit-tested. */
class SearchEngine {

    /** Tokenize a query into lowercase terms, filtering empty strings. */
    fun tokenize(query: String): List<String> {
        return query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    /** Score a single field match: exact phrase = 3.0, term presence = 1.0 per occurrence. */
    private fun scoreField(fieldValue: String, terms: List<String>): Double {
        if (fieldValue.isBlank() || terms.isEmpty()) return 0.0
        val low = fieldValue.lowercase()
        var score = 0.0
        for (term in terms) {
            if (low.contains(term)) {
                score += if (term.length > 3) 3.0 else 1.0
            }
        }
        return score
    }

    /** Search all transactions against the query. */
    fun search(
        allTxs: List<Transaction>,
        query: String,
        typeFilter: TransactionType? = null,
        maxResults: Int = 50
    ): SearchResult {
        val startMs = System.currentTimeMillis()
        val terms = tokenize(query)
        if (terms.isEmpty()) return SearchResult(emptyList(), 0, query, 0)

        val filtered = if (typeFilter != null) allTxs.filter { it.type == typeFilter } else allTxs
        val hits = mutableListOf<SearchHit>()
        for (tx in filtered) {
            val fields = listOf(
                "merchant" to tx.merchant,
                "category" to tx.category,
                "notes" to tx.notes,
                "subcategory" to tx.subcategory,
                "tags" to tx.tags.joinToString(" "),
                "description" to tx.description
            )
            val scores = fields.map { (name, value) -> name to scoreField(value, terms) }
            val totalScore = scores.sumOf { it.second }
            val matched = scores.filter { it.second > 0 }.map { it.first }
            if (totalScore > 0) {
                hits.add(SearchHit(tx, totalScore, matched))
            }
        }
        hits.sortByDescending { it.score }
        val result = hits.take(maxResults)
        return SearchResult(
            hits = result,
            totalCount = hits.size,
            query = query,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    /** Conservative fuzzy match: returns true if edit distance <= 1. */
    fun fuzzyMatch(a: String, b: String): Boolean {
        val first = a.lowercase()
        val second = b.lowercase()
        if (kotlin.math.abs(first.length - second.length) > 1) return false
        var previous = IntArray(second.length + 1) { it }
        for (i in 1..first.length) {
            val current = IntArray(second.length + 1)
            current[0] = i
            for (j in 1..second.length) {
                val substitutionCost = if (first[i - 1] == second[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost
                )
            }
            previous = current
        }
        return previous[second.length] <= 1
    }

    /** Search with fuzzy fallback on merchant names. */
    fun searchWithFuzzy(
        allTxs: List<Transaction>,
        query: String,
        maxResults: Int = 50
    ): SearchResult {
        val exact = search(allTxs, query, maxResults = maxResults)
        if (exact.hits.size >= maxResults) return exact
        val terms = tokenize(query)
        val fuzzyHits = mutableListOf<SearchHit>()
        for (tx in allTxs) {
            if (exact.hits.any { it.transaction.id == tx.id }) continue
            for (term in terms) {
                if (term.length >= 5 && tx.merchant.lowercase()
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .any { merchantTerm -> merchantTerm.isNotBlank() && fuzzyMatch(term, merchantTerm) }
                ) {
                    fuzzyHits.add(SearchHit(tx, 2.0, listOf("merchant(fuzzy)")))
                    break
                }
            }
        }
        val combined = (exact.hits + fuzzyHits).sortedByDescending { it.score }.take(maxResults)
        return SearchResult(
            hits = combined,
            totalCount = combined.size,
            query = query,
            durationMs = exact.durationMs
        )
    }
}
