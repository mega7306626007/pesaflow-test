package com.pesaflow.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.placeholder
import com.google.accompanist.placeholder.shimmer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Financial number hierarchy: amount dominates, label secondary, meta tertiary.
// Currency stays KSh via Double.toKSh().
@Composable
fun FinancialAmount(
    amountKSh: String,
    label: String,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    large: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(PesaSpacing.xxs))
        Text(
            text = if (hidden) "KSh ••••" else amountKSh,
            style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PesaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = ppTypography.h3, color = ppColors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(ppSpacing.xs))
                Text(text = subtitle, style = ppTypography.bodySmall, color = ppColors.textTertiary)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = ppTypography.labelMedium, color = ppColors.gold)
            }
        }
    }
}

// One-line concept explainer: label + ⓘ opens plain-language dialog.
// For features no first-timer understands (safe-to-spend, pending, HELB).
@Composable
fun ExplainChip(
    label: String,
    body: String,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }, modifier = modifier) {
        Text("ⓘ $label", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Got it") } }
        )
    }
}

// Deliberate empty state: WHAT + WHY + WHAT NEXT. Never blank "No data".
@Composable
fun PesaEmptyState(
    title: String,
    explanation: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ppShapes.card,
        colors = CardDefaults.cardColors(containerColor = ppColors.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ppColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.flat)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(ppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = ppTypography.h3, color = ppColors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(ppSpacing.sm))
            Text(
                explanation,
                style = ppTypography.bodyMedium,
                color = ppColors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(ppSpacing.lg))
                PpPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun PesaErrorState(
    title: String = "Something went wrong",
    explanation: String = "Please try again.",
    actionLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PesaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(PesaSpacing.xl)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(PesaSpacing.xs))
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(PesaSpacing.md))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PesaLoadingRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(PesaMotion.medium, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
        repeat(3) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(PesaRadius.md)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    .placeholder(
                        visible = true,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        highlight = PlaceholderHighlight.shimmer(highlightColor = MaterialTheme.colorScheme.surface)
                    )
            )
        }
    }
}

// Consistent category treatment: one container style, one size — tinted by
// the category's own chart color, so lists and charts speak one language.
// Emoji content is preserved from existing data (no new asset pipeline, offline-safe).
@Composable
fun CategoryIcon(
    category: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val container = if (accent) MaterialTheme.colorScheme.primaryContainer
    else categoryChartColor(category).copy(alpha = 0.16f)
    val content = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = categoryEmoji(category),
            style = MaterialTheme.typography.titleMedium,
            color = content
        )
    }
}

@Composable
fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.semantics { contentDescription = label }.clickable(onClick = onClick),
        shape = PesaRadius.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.card)
    ) {
        Column(
            Modifier.padding(PesaSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Calm budget progress: token 8dp track, gold while healthy, amber near the
// edge, restrained red past it. Never conveys status by color alone.
@Composable
fun BudgetProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true
) {
    val status = budgetStatusFor(fraction)
    // Gold while healthy or approaching (amber-adjacent, calm); restrained
    // red only once blown. The percent line names the state in words.
    Column(modifier.fillMaxWidth()) {
        PpProgress(
            fraction = fraction,
            kind = if (status == BudgetStatus.EXCEEDED) PpProgressKind.ERROR else PpProgressKind.GOLD
        )
        if (showPercent) {
            Spacer(Modifier.height(ppSpacing.xs))
            val label = when (status) {
                BudgetStatus.HEALTHY -> "${(fraction * 100).toInt()}% used"
                BudgetStatus.APPROACHING -> "${(fraction * 100).toInt()}% used — approaching limit"
                BudgetStatus.EXCEEDED -> "${(fraction * 100).toInt()}% used — over budget"
            }
            Text(
                label, style = ppTypography.labelMedium,
                color = if (status == BudgetStatus.APPROACHING) ppColors.warning else ppColors.textTertiary
            )
        }
    }
}

// Hero financial card: token navy gradient, single gold accent line, calm
// hierarchy. One hero figure dominates (36sp tabular); supports ride below.
@Composable
fun HeroFinanceCard(
    greeting: String,
    dateLine: String,
    availableLabel: String,
    availableValue: String,
    stats: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier,
    onHideToggle: (() -> Unit)? = null,
    hideLabel: String? = null
) {
    val gradient = Brush.linearGradient(listOf(ppColors.surface, ppColors.surfaceElevated))
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ppShapes.hero,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, ppColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = PesaElevation.hero)
    ) {
        Box(Modifier.background(gradient).fillMaxWidth().padding(ppSpacing.xxl)) {
            Column(verticalArrangement = Arrangement.spacedBy(PesaSpacing.md)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = ppTypography.h3, color = ppColors.textPrimary)
                        Text(dateLine, style = ppTypography.bodySmall, color = ppColors.textTertiary)
                    }
                    if (onHideToggle != null && hideLabel != null) {
                        AssistChip(
                            onClick = onHideToggle,
                            label = { Text(hideLabel) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
                // Restrained gold accent line — brand moment, not decoration everywhere.
                Box(Modifier.width(48.dp).height(3.dp).clip(CircleShape).background(ppColors.gold))
                Text(availableLabel, style = ppTypography.labelMedium, color = ppColors.goldLight)
                Text(
                    availableValue,
                    style = ppTypography.financialHero,
                    color = ppColors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(PesaSpacing.lg)) {
                    stats.take(3).forEach { (label, value) -> HeroStat(label, value, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = ppTypography.labelMedium, color = ppColors.textTertiary)
        Text(value, style = ppTypography.bodyLarge, fontWeight = FontWeight.SemiBold, color = ppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Extension: true if current scheme is dark (background luminance heuristic via onBackground).
@Composable
fun androidx.compose.material3.ColorScheme.brightness(): Boolean {
    // PesaFlow dark schemes use near-white onBackground; light uses dark ink.
    return (onBackground.red + onBackground.green + onBackground.blue) / 3f > 0.5f
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier, message: String = "Loading…") {
    Column(modifier.fillMaxWidth().padding(PesaSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(PesaSpacing.sm))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
