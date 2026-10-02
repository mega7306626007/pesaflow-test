package com.pesaflow.app.parsers

import com.pesaflow.app.ui.theme.categoryChartColor
import org.junit.Assert.*
import org.junit.Test


/** Same category, same color — every chart, every screen, every order. */
class ChartColorTest {

    @Test
    fun `stable across calls and casings`() {
        val a = categoryChartColor("Food")
        assertEquals(a, categoryChartColor("Food"))
        assertEquals(a, categoryChartColor("  FOOD "))
    }

    @Test
    fun `big envelopes all distinct`() {
        val colors = listOf("Food", "Rent", "Transport", "Airtime", "Savings")
            .map { categoryChartColor(it) }
        assertEquals(5, colors.toSet().size)
    }

    @Test
    fun `unknown categories deterministic in palette`() {
        assertEquals(categoryChartColor("Paragliding"), categoryChartColor("paragliding"))
    }
}
