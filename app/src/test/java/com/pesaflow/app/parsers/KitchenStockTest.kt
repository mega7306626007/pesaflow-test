package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.KitchenStock
import com.pesaflow.app.data.models.stockAfterTopUp
import com.pesaflow.app.data.models.stockTopUpCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitchenStockTest {
    @Test
    fun `top up prorates pack price and increases shelf quantity`() {
        val item = KitchenStock(
            name = "Unga",
            unit = "kg",
            qtyFull = 2.0,
            qtyLeft = 0.5,
            pricePerPack = 240.0
        )
        assertEquals(180.0, stockTopUpCost(item, 1.5)!!, 0.001)
        val toppedUp = stockAfterTopUp(item, 1.5, now = 100L)!!
        assertEquals(2.0, toppedUp.qtyLeft, 0.001)
        assertEquals(100L, toppedUp.updatedAt)
        assertEquals(0.5, item.qtyLeft, 0.001)
    }

    @Test
    fun `top up cost rejects unknown prices and invalid quantities`() {
        val item = KitchenStock(name = "Rice", qtyFull = 2.0, qtyLeft = 0.5, pricePerPack = 0.0)
        assertNull(stockTopUpCost(item, 1.0))
        assertNull(stockTopUpCost(item.copy(pricePerPack = 300.0), 0.0))
        assertNull(stockTopUpCost(item.copy(pricePerPack = 300.0), Double.NaN))
        assertNull(stockAfterTopUp(item.copy(pricePerPack = 300.0), -1.0))
    }
}
