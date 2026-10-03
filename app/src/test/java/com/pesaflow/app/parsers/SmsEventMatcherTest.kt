package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.hasRelatedSmsNotice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsEventMatcherTest {
    private fun notice(
        type: TransactionType,
        category: String,
        merchant: String,
        amount: Double = 100.0,
        time: Long = 1_000_000L
    ) = PendingTransaction(
        amount = amount,
        type = type,
        category = category,
        merchant = merchant,
        dateTimestamp = time,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = null,
        rawText = ""
    )

    @Test
    fun `same ziidi or safaricom data event within ten minutes matches`() {
        assertTrue(
            notice(TransactionType.SAVING, "Savings", "Ziidi")
                .hasRelatedSmsNotice(notice(TransactionType.SAVING, "Savings", "Ziidi Wallet", time = 1_500_000L))
        )
        assertTrue(
            notice(TransactionType.EXPENSE, "Data", "Safaricom Data")
                .hasRelatedSmsNotice(notice(TransactionType.EXPENSE, "Data", "Safaricom", time = 1_500_000L))
        )
    }

    @Test
    fun `different events and unrelated purchases are never collapsed`() {
        assertFalse(
            notice(TransactionType.EXPENSE, "Data", "Safaricom Data")
                .hasRelatedSmsNotice(notice(TransactionType.EXPENSE, "Food", "Shop"))
        )
        assertFalse(
            notice(TransactionType.EXPENSE, "Data", "Safaricom Data")
                .hasRelatedSmsNotice(notice(TransactionType.EXPENSE, "Data", "Safaricom Data", amount = 200.0))
        )
        assertFalse(
            notice(TransactionType.EXPENSE, "Data", "Safaricom Data")
                .hasRelatedSmsNotice(notice(TransactionType.EXPENSE, "Data", "Safaricom Data", time = 1_700_001L))
        )
        assertFalse(
            notice(TransactionType.SAVING, "Savings", "Ziidi")
                .hasRelatedSmsNotice(notice(TransactionType.SAVING, "Savings", "Ziidi"))
        )
    }
}
