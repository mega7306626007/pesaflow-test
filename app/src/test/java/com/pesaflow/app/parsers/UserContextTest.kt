package com.pesaflow.app.parsers

import com.pesaflow.app.data.context.ContextFact
import com.pesaflow.app.data.context.ContextSources
import com.pesaflow.app.data.context.invalidationKeys
import com.pesaflow.app.data.context.mergeFact
import com.pesaflow.app.data.context.needsReview
import org.junit.Assert.*
import org.junit.Test


/** ContextFact provenance: explicit beats inferred, users beat guesses. */
class UserContextTest {

    private fun fact(
        key: String = "transport.primaryMode",
        value: String,
        source: String,
        confidence: Float = 1.0f,
        updatedAt: Long = 1000L,
        expiresAt: Long = 0L,
        userConfirmed: Boolean = false
    ) = ContextFact(
        key = key, value = value, source = source, confidence = confidence,
        createdAt = 500L, updatedAt = updatedAt,
        expiresAt = expiresAt, userConfirmed = userConfirmed
    )

    @Test
    fun `first fact stores with confirmation stamp`() {
        val out = mergeFact(null, fact(value = "WALKING", source = ContextSources.USER_CONFIRMED), now = 2000L)!!
        assertEquals("WALKING", out.value)
        assertTrue(out.userConfirmed)
        assertEquals(2000L, out.updatedAt)
    }

    @Test
    fun `explicit beats inference`() {
        val existing = fact(value = "MATATU", source = ContextSources.MODEL_INFERRED, confidence = 0.9f)
        val out = mergeFact(existing, fact(value = "WALKING", source = ContextSources.USER_ENTERED), now = 2000L)!!
        assertEquals("WALKING", out.value)
    }

    @Test
    fun `inference never overwrites a user fact`() {
        val existing = fact(value = "WALKING", source = ContextSources.USER_CONFIRMED)
        val out = mergeFact(
            existing,
            fact(value = "MATATU", source = ContextSources.MODEL_INFERRED, confidence = 0.99f, updatedAt = 9999L),
            now = 2000L
        )!!
        assertEquals("WALKING", out.value)
        assertSame(existing, out)
    }

    @Test
    fun `newer explicit beats older explicit`() {
        val existing = fact(value = "HOSTEL", source = ContextSources.USER_SELECTED, updatedAt = 1000L)
        val out = mergeFact(
            existing,
            fact(value = "RENTAL", source = ContextSources.USER_SELECTED, updatedAt = 2000L),
            now = 3000L
        )!!
        assertEquals("RENTAL", out.value)
    }

    @Test
    fun `higher confidence inference wins ties break newer`() {
        val existing = fact(value = "A", source = ContextSources.MODEL_INFERRED, confidence = 0.6f, updatedAt = 1000L)
        val weak = fact(value = "B", source = ContextSources.MODEL_INFERRED, confidence = 0.5f, updatedAt = 2000L)
        assertEquals("A", mergeFact(existing, weak, now = 3000L)!!.value)
        val strong = fact(value = "C", source = ContextSources.MODEL_INFERRED, confidence = 0.8f, updatedAt = 500L)
        assertEquals("C", mergeFact(existing, strong, now = 3000L)!!.value)
    }

    @Test
    fun `null incoming keeps existing`() {
        val existing = fact(value = "WALKING", source = ContextSources.USER_ENTERED)
        assertSame(existing, mergeFact(existing, null))
        assertNull(mergeFact(null, null))
    }

    @Test
    fun `moving house invalidates commute and food distance`() {
        val keys = invalidationKeys("housing.current")
        assertTrue(keys.contains("commute.primaryMode"))
        assertTrue(keys.contains("commute.fare"))
        assertTrue(keys.contains("food.nearestOptions"))
        assertTrue(invalidationKeys("unknown.key").isEmpty())
    }

    @Test
    fun `expired unconfirmed facts need review`() {
        assertTrue(needsReview(fact(value = "X", source = ContextSources.PUBLIC_DATASET, expiresAt = 1000L), now = 2000L))
        assertFalse(needsReview(fact(value = "X", source = ContextSources.PUBLIC_DATASET, expiresAt = 1000L, userConfirmed = true), now = 2000L))
        assertFalse(needsReview(fact(value = "X", source = ContextSources.USER_ENTERED), now = 2000L))
    }
}
