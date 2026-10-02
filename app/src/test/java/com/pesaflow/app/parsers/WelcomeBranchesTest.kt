package com.pesaflow.app.parsers

import com.pesaflow.app.ui.onboarding.visibleBranches
import org.junit.Assert.*
import org.junit.Test


// Twenty-three branches exist; each case sees only its relevant subset.
class WelcomeBranchesTest {

    private fun answers(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @Test
    fun `parents walker skips rent fare times and helb`() {
        val seen = visibleBranches(
            answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF")
        )
        assertFalse(seen.contains("rent"))
        assertFalse(seen.contains("fare"))
        assertFalse(seen.contains("classTimes"))
        assertFalse(seen.contains("helb"))
        assertTrue(seen.contains("upkeep"))
        assertTrue(seen.contains("walkOk"))
        assertTrue(seen.size < 23)
    }

    @Test
    fun `impractical walk reveals fare and class time questions`() {
        val practical = visibleBranches(
            answers("home" to "Parents", "commute" to "Walk", "walkOk" to "Yes", "fundSource" to "SELF")
        )
        val impractical = visibleBranches(
            answers("home" to "Parents", "commute" to "Walk", "walkOk" to "No", "fundSource" to "SELF")
        )
        assertFalse(practical.contains("fare"))
        assertFalse(practical.contains("classTimes"))
        assertTrue(impractical.contains("fare"))
        assertTrue(impractical.contains("classTimes"))
    }

    @Test
    fun `far renter on helb sees the full run`() {
        val seen = visibleBranches(
            answers("home" to "Alone", "commute" to "Far", "fundSource" to "HELB")
        )
        assertTrue(seen.contains("rent"))
        assertTrue(seen.contains("fare"))
        assertTrue(seen.contains("classTimes"))
        assertTrue(seen.contains("helb"))
        assertTrue(seen.contains("stages"))
        // 20 base + stages (no walkOk for Far, no grocery without cooks key).
        assertEquals(21, seen.size)
    }

    @Test
    fun `cooks see the grocery branch non cooks do not`() {
        val cooks = visibleBranches(
            answers("home" to "Hostel", "commute" to "Near", "fundSource" to "HELB", "cooks" to "Yes")
        )
        val buyers = visibleBranches(
            answers("home" to "Hostel", "commute" to "Near", "fundSource" to "HELB", "cooks" to "No")
        )
        assertTrue(cooks.contains("grocery"))
        assertFalse(buyers.contains("grocery"))
    }

    @Test
    fun `hostel near keeps fare but the count still drops`() {
        val full = visibleBranches(answers("home" to "Alone", "commute" to "Far", "fundSource" to "HELB"))
        val slim = visibleBranches(answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF"))
        assertTrue(slim.size < full.size)
        assertTrue(full.size - slim.size >= 3)
    }

    @Test
    fun `identity branches always ask`() {
        val seen = visibleBranches(answers("home" to "Parents", "commute" to "Walk", "fundSource" to "SELF"))
        listOf("name", "nickname", "university", "semester", "home", "commute", "pocket", "save")
            .forEach { assertTrue("$it should always ask", seen.contains(it)) }
    }
}
