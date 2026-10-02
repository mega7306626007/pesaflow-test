package com.pesaflow.app.data.finance

import com.pesaflow.app.data.context.ContextFact
import com.pesaflow.app.data.context.ContextSources

// Transport journeys live as ContextFacts (no new tables): each journey is
// a fact family journey.{id}.{field}. Fares are user-reported or verified
// — never invented. Pure math + fact helpers, fully unit-tested.
data class Journey(
    val id: String,
    val from: String,
    val to: String,
    val mode: String,
    /** <= 0 means unknown — estimates say so instead of guessing. */
    val fareOneWay: Double,
    val daysPerWeek: Int,
    val verified: Boolean
)

/** A week of return trips; null when the fare is unknown. */
fun journeyWeekly(j: Journey): Double? {
    if (j.fareOneWay <= 0 || j.daysPerWeek <= 0) return null
    return j.fareOneWay * 2 * j.daysPerWeek
}

/** A month of return trips; null when the fare is unknown. */
fun journeyMonthly(j: Journey): Double? {
    val weekly = journeyWeekly(j) ?: return null
    return weekly * 4.33
}

/** Read one journey back from a fact map (missing fields = unknowns). */
fun readJourney(id: String, facts: Map<String, ContextFact>): Journey? {
    val from = facts[key(id, "from")]?.value.orEmpty()
    val to = facts[key(id, "to")]?.value.orEmpty()
    if (from.isBlank() || to.isBlank()) return null
    return Journey(
        id = id,
        from = from,
        to = to,
        mode = facts[key(id, "mode")]?.value ?: "MATATU",
        fareOneWay = facts[key(id, "fare")]?.value?.toDoubleOrNull() ?: 0.0,
        daysPerWeek = facts[key(id, "days")]?.value?.toIntOrNull() ?: 0,
        verified = facts[key(id, "fare")]?.userConfirmed == true
    )
}

/** Facts to store for a journey (caller stamps + persists via repository). */
fun journeyFacts(j: Journey, source: String = ContextSources.USER_ENTERED): List<ContextFact> =
    listOf(
        ContextFact(key(id = j.id, field = "from"), value = j.from, source = source),
        ContextFact(key(id = j.id, field = "to"), value = j.to, source = source),
        ContextFact(key(id = j.id, field = "mode"), value = j.mode, source = source),
        ContextFact(
            key = key(j.id, "fare"), value = j.fareOneWay.toString(), source = source,
            userConfirmed = source == ContextSources.USER_CONFIRMED
        ),
        ContextFact(key(id = j.id, field = "days"), value = j.daysPerWeek.toString(), source = source)
    )

private fun key(id: String, field: String) = "journey.$id.$field"
