package com.pesaflow.app.data.context

import androidx.room.*

// UserContext graph: explicit, editable facts with provenance — never a flat
// questionnaire dump. Each fact carries value + source + confidence +
// confirmation, so inference can NEVER silently become user truth.
// Layer C (context) reads ledger/profile; it never writes financial truth.
@Entity(tableName = "user_context")
data class ContextFact(
    @PrimaryKey val key: String, // e.g. "accommodation.type", "transport.primaryMode"
    val value: String,
    // USER_SELECTED, USER_ENTERED, USER_CONFIRMED, IMPORTED_TRANSACTION,
    // MODEL_INFERRED, PUBLIC_DATASET, CALCULATED
    val source: String = "USER_ENTERED",
    val confidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // 0 = no expiry/review date.
    val expiresAt: Long = 0L,
    val userConfirmed: Boolean = false
)

object ContextSources {
    const val USER_SELECTED = "USER_SELECTED"
    const val USER_ENTERED = "USER_ENTERED"
    const val USER_CONFIRMED = "USER_CONFIRMED"
    const val IMPORTED_TRANSACTION = "IMPORTED_TRANSACTION"
    const val MODEL_INFERRED = "MODEL_INFERRED"
    const val PUBLIC_DATASET = "PUBLIC_DATASET"
    const val CALCULATED = "CALCULATED"

    private val EXPLICIT = setOf(USER_SELECTED, USER_ENTERED, USER_CONFIRMED)

    fun isExplicit(source: String): Boolean = source in EXPLICIT
}

/**
 * Merge an incoming fact against the stored one. Precedence, highest first:
 * 1. newer explicit beats older explicit (user changed their mind),
 * 2. any explicit beats any inference (a fact beats a guess),
 * 3. higher-confidence inference beats lower (tentative → observed),
 * 4. ties break toward newer.
 * Returns null only when both are null (nothing to store).
 * Pure logic — fully unit-tested; stamping happens at the call site.
 */
fun mergeFact(
    existing: ContextFact?,
    incoming: ContextFact?,
    now: Long = System.currentTimeMillis()
): ContextFact? {
    if (incoming == null) return existing
    if (existing == null) return incoming.copy(
        updatedAt = now,
        userConfirmed = incoming.userConfirmed || incoming.source == ContextSources.USER_CONFIRMED
    )
    val existingExplicit = ContextSources.isExplicit(existing.source)
    val incomingExplicit = ContextSources.isExplicit(incoming.source)
    val winner = when {
        incomingExplicit && !existingExplicit -> incoming
        !incomingExplicit && existingExplicit -> existing
        incomingExplicit && existingExplicit ->
            if (incoming.updatedAt >= existing.updatedAt) incoming else existing
        else ->
            if (incoming.confidence > existing.confidence) incoming
            else if (incoming.confidence < existing.confidence) existing
            else if (incoming.updatedAt >= existing.updatedAt) incoming else existing
    }
    if (winner == existing && existingExplicit) return existing
    return winner.copy(
        updatedAt = now,
        userConfirmed = winner.userConfirmed || winner.source == ContextSources.USER_CONFIRMED
    )
}

// Invalidation graph: changing a root fact drops its dependents so stale
// context can never silently drive recommendations. "I moved" kills the
// commute, the fare estimate, and the food-distance read in one pass.
private val DEPENDENTS: Map<String, Set<String>> = mapOf(
    "housing.current" to setOf(
        "commute.distanceKm", "commute.primaryMode", "commute.fare",
        "food.nearestOptions", "transport.homeToCampus"),
    "housing.type" to setOf("housing.current", "housing.rent", "housing.utilities"),
    "education.institution" to setOf(
        "commute.distanceKm", "food.nearestOptions", "transport.homeToCampus"),
    "education.campus" to setOf(
        "commute.distanceKm", "food.nearestOptions", "transport.homeToCampus"),
    "transport.primaryMode" to setOf("commute.fare", "transport.homeToCampus"),
    "income.nextExpected" to setOf("forecast.runwayDays", "forecast.safeToday"),
    "income.monthly" to setOf("forecast.runwayDays", "budget.monthlyPace")
)

/** Keys that must be dropped (or re-derived) after [changedKey] changes. */
fun invalidationKeys(changedKey: String): Set<String> =
    DEPENDENTS[changedKey].orEmpty()

/** True when the fact needs review (expired and never confirmed). */
fun needsReview(fact: ContextFact, now: Long = System.currentTimeMillis()): Boolean =
    fact.expiresAt > 0 && fact.expiresAt <= now && !fact.userConfirmed
