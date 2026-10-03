package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

private const val RELATED_NOTICE_WINDOW_MS = 10L * 60 * 1000

private fun eventFamily(type: TransactionType, category: String, merchant: String): String? {
    val name = merchant.lowercase()
    return when {
        type == TransactionType.SAVING && category.equals("Savings", ignoreCase = true) &&
            name.contains("ziidi") -> "ZIIDI_IN"
        type == TransactionType.INCOME && name.contains("ziidi") -> "ZIIDI_OUT"
        type == TransactionType.EXPENSE && category.equals("Data", ignoreCase = true) &&
            (name.contains("safaricom") || name.contains("bundle") || name.contains("data")) -> "SAFARICOM_DATA"
        else -> null
    }
}

fun isRelatedSmsNotice(
    amount: Double,
    type: TransactionType,
    category: String,
    merchant: String,
    timestamp: Long,
    otherAmount: Double,
    otherType: TransactionType,
    otherCategory: String,
    otherMerchant: String,
    otherTimestamp: Long
): Boolean {
    if (kotlin.math.abs(timestamp - otherTimestamp) > RELATED_NOTICE_WINDOW_MS) return false
    if (kotlin.math.abs(amount - otherAmount) > 0.01 || type != otherType) return false
    val family = eventFamily(type, category, merchant) ?: return false
    if (family != eventFamily(otherType, otherCategory, otherMerchant)) return false
    // A pair must carry distinct channel labels; otherwise two genuine same-
    // amount purchases close together could be mistaken for mirrored notices.
    return !merchant.equals(otherMerchant, ignoreCase = true)
}

fun PendingTransaction.hasRelatedSmsNotice(other: PendingTransaction): Boolean =
    isRelatedSmsNotice(
        amount, type, category, merchant, dateTimestamp,
        other.amount, other.type, other.category, other.merchant, other.dateTimestamp
    )

fun PendingTransaction.hasRelatedSmsNotice(other: Transaction): Boolean =
    isRelatedSmsNotice(
        amount, type, category, merchant, dateTimestamp,
        other.amount, other.type, other.category, other.merchant, other.dateTimestamp
    )
