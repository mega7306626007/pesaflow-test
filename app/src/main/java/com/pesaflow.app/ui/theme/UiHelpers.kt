package com.pesaflow.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight


/** Consistent Kenyan currency formatting: KSh 500, KSh 1,250, KSh 25,000.50 */
fun Double.toKSh(): String {
    val body = if (this % 1.0 == 0.0) "%,.0f".format(this) else "%,.2f".format(this)
    return "KSh $body"
}


/** Friendly emoji per spending category for ledgers and lists. */
fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "food" -> "🍲"
    "rent" -> "🏠"
    "transport" -> "🚌"
    "airtime" -> "📱"
    "data", "internet" -> "🌐"
    "electricity" -> "💡"
    "water" -> "💧"
    "school", "books", "printing", "stationery" -> "📚"
    "entertainment" -> "🎬"
    "shopping", "clothing" -> "🛍️"
    "health", "personal care" -> "🏥"
    "family" -> "👨‍👩‍👧"
    "emergency" -> "🚨"
    "savings" -> "🐖"
    "investment" -> "📈"
    "salary", "income" -> "💰"
    "debt" -> "🤝"
    else -> "🧾"
}


/**
 * Stable per-category chart color. The old index-based coloring repainted
 * Food blue one week and red the next as categories came and went — same
 * category, same color, every chart, every screen. Big envelopes get fixed
 * hues; everything else hashes deterministically into the palette.
 */
fun categoryChartColor(category: String): Color {
    val key = category.trim().lowercase()
    val fixed = when (key) {
        "food" -> 2 // gold
        "rent" -> 0 // blue
        "transport" -> 3 // info
        "airtime", "data", "internet" -> 4 // warning
        "savings", "investment" -> 1 // income green
        "salary", "income" -> 1
        else -> null
    }
    val idx = fixed ?: ((key.hashCode() and Int.MAX_VALUE) % ChartPalette.size)
    return ChartPalette[idx % ChartPalette.size]
}


/** Consistent section header with optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
