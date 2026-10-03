package com.pesaflow.app.data.ledger

import android.content.SharedPreferences
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

data class ContactEntry(
    val name: String,
    val displayName: String,
    val relationship: String = "",
    val category: String = "",
    val scope: String = "BOTH",
    val notes: String = "",
    val transactionCount: Int = 0,
    val lastSeen: Long = 0L,
    val totalIn: Double = 0.0,
    val totalOut: Double = 0.0,
    val matchTerms: String = ""
)

object ContactBook {

    private const val KEY = "contact_book"
    private const val MAX = 300
    private const val MAX_NOTES = 200

    private val RELATIONSHIPS = listOf(
        "Friend", "Brother", "Sister", "Family", "Landlord", "Boss", "Business", "School", "Other"
    )

    fun relationships(): List<String> = RELATIONSHIPS

    private fun keyOf(name: String) =
        name.trim().lowercase().replace("|", "").replace("=", "").replace(";", "")

    fun readAll(prefs: SharedPreferences): List<ContactEntry> {
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0]
            if (name.isBlank()) return@mapNotNull null
            ContactEntry(
                name = name,
                displayName = parts[1],
                relationship = parts.getOrElse(2) { "" },
                category = parts.getOrElse(3) { "" },
                scope = parts.getOrElse(4) { "BOTH" },
                notes = parts.getOrElse(5) { "" },
                transactionCount = parts.getOrElse(6) { "0" }.toIntOrNull() ?: 0,
                lastSeen = parts.getOrElse(7) { "0" }.toLongOrNull() ?: 0L,
                totalIn = parts.getOrElse(8) { "0" }.toDoubleOrNull() ?: 0.0,
                totalOut = parts.getOrElse(9) { "0" }.toDoubleOrNull() ?: 0.0,
                matchTerms = parts.getOrElse(10) { "" }
            )
        }
    }

    fun save(
        prefs: SharedPreferences,
        name: String,
        displayName: String,
        relationship: String,
        category: String,
        scope: String,
        notes: String,
        matchTerms: String = ""
    ): Boolean {
        val key = keyOf(name)
        if (key.isEmpty() || displayName.isBlank()) return false
        val cleanRel = relationship.trim().take(20)
        val cleanCat = category.trim().take(30)
        val cleanScope = scope.trim().uppercase().takeIf { it == "IN" || it == "OUT" } ?: "BOTH"
        val cleanNotes = notes.trim().take(MAX_NOTES)
        val cleanMatches = matchTerms.split(",")
            .map { it.trim().replace("|", "").replace("=", "").replace(";", "").take(60) }
            .filter { it.isNotBlank() }.distinct().take(20).joinToString(",")
        val all = readAll(prefs).toMutableList()
        val idx = all.indexOfFirst { it.name == key }
        if (idx >= 0) {
            val old = all[idx]
            all[idx] = old.copy(
                displayName = displayName.trim().take(40),
                relationship = cleanRel,
                category = cleanCat,
                scope = cleanScope,
                notes = cleanNotes,
                matchTerms = cleanMatches
            )
        } else {
            all.add(
                ContactEntry(
                    name = key,
                    displayName = displayName.trim().take(40),
                    relationship = cleanRel,
                    category = cleanCat,
                    scope = cleanScope,
                    notes = cleanNotes,
                    matchTerms = cleanMatches
                )
            )
        }
        while (all.size > MAX) all.removeAt(0)
        writeAll(prefs, all)
        return true
    }

    fun delete(prefs: SharedPreferences, name: String) {
        val key = keyOf(name)
        if (key.isEmpty()) return
        val all = readAll(prefs).filter { it.name != key }
        writeAll(prefs, all)
    }

    fun lookup(prefs: SharedPreferences, name: String): ContactEntry? {
        val key = keyOf(name)
        if (key.isEmpty()) return null
        return readAll(prefs).firstOrNull { it.name == key }
    }

    fun recordTransaction(prefs: SharedPreferences, name: String, type: TransactionType, amount: Double, ts: Long) {
        val key = keyOf(name)
        if (key.isEmpty() || amount <= 0) return
        val all = readAll(prefs).toMutableList()
        val idx = all.indexOfFirst { it.name == key }
        if (idx >= 0) {
            val old = all[idx]
            all[idx] = old.copy(
                transactionCount = old.transactionCount + 1,
                lastSeen = maxOf(old.lastSeen, ts),
                totalIn = if (type == TransactionType.INCOME) old.totalIn + amount else old.totalIn,
                totalOut = if (type != TransactionType.INCOME) old.totalOut + amount else old.totalOut
            )
        } else {
            all.add(
                ContactEntry(
                    name = key,
                    displayName = name.trim().take(40),
                    transactionCount = 1,
                    lastSeen = ts,
                    totalIn = if (type == TransactionType.INCOME) amount else 0.0,
                    totalOut = if (type != TransactionType.INCOME) amount else 0.0
                )
            )
        }
        while (all.size > MAX) all.removeAt(0)
        writeAll(prefs, all)
    }

    fun knownNames(prefs: SharedPreferences): Set<String> =
        readAll(prefs).map { it.name }.toSet()

    private fun writeAll(prefs: SharedPreferences, entries: List<ContactEntry>) {
        val raw = entries.joinToString("||") { e ->
            listOf(
                e.name, e.displayName, e.relationship, e.category, e.scope,
                e.notes, e.transactionCount.toString(), e.lastSeen.toString(),
                e.totalIn.toString(), e.totalOut.toString(), e.matchTerms
            ).joinToString("|")
        }
        prefs.edit().putString(KEY, raw).apply()
    }
}
