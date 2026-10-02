package com.pesaflow.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// PesaPlanner component library — every screen composes from these.
// Four card voices (hero/feature/information/compact), one header, one
// action, one row, kind-aware insights, token progress + buttons.

// ---------------------------------------------------------------- cards ---

enum class PpCardKind { STANDARD, LARGE, COMPACT, HERO, INFO }

@Composable
fun PpCard(
    kind: PpCardKind = PpCardKind.STANDARD,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    goldAccent: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = when (kind) {
        PpCardKind.STANDARD -> ppShapes.card
        PpCardKind.LARGE -> ppShapes.cardLarge
        PpCardKind.COMPACT -> ppShapes.cardCompact
        PpCardKind.HERO -> ppShapes.hero
        PpCardKind.INFO -> ppShapes.card
    }
    val pad: Dp = when (kind) {
        PpCardKind.LARGE -> ppSpacing.xl
        PpCardKind.COMPACT -> ppSpacing.md
        PpCardKind.HERO -> ppSpacing.xxl
        else -> ppSpacing.lg
    }
    val container = when (kind) {
        PpCardKind.HERO -> ppColors.surface
        PpCardKind.INFO -> ppColors.surfaceSoft
        else -> ppColors.surface
    }
    val elevation = when (kind) {
        PpCardKind.HERO -> PesaElevation.hero
        PpCardKind.INFO, PpCardKind.COMPACT -> PesaElevation.flat
        else -> PesaElevation.card
    }
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(1.dp, ppColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column {
            // Restrained gold moment — heroes only, never decoration.
            if (goldAccent && kind == PpCardKind.HERO) {
                Box(Modifier.width(48.dp).height(3.dp).clip(CircleShape).background(ppColors.gold))
                Spacer(Modifier.height(ppSpacing.md))
            }
            Box(Modifier.fillMaxWidth().padding(pad)) { content() }
        }
    }
}

// ------------------------------------------------------- section header ---

@Composable
fun PpSectionHeader(
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

// ------------------------------------------------------- quick action ---

@Composable
fun PpQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(ppShapes.cardCompact)
            .background(if (selected) ppColors.surfaceElevated else ppColors.surface)
            .border(
                1.dp,
                if (selected) ppColors.borderGold else ppColors.border,
                ppShapes.cardCompact
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = ppSpacing.sm, horizontal = ppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)
    ) {
        Box(
            Modifier.size(44.dp).clip(ppShapes.cardCompact).background(ppColors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (selected) ppColors.gold else ppColors.brightBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            label, style = ppTypography.labelMedium, color = ppColors.textSecondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------- insights ---

enum class InsightKind { WARNING, ACHIEVEMENT, OPPORTUNITY, ANOMALY, PREDICTION, INFO }

/** Visual treatment per insight family — warnings warn, wins celebrate. */
fun classifyInsight(text: String): InsightKind {
    val l = text.lowercase()
    return when {
        l.contains("danger") || l.contains("over ") || l.contains("over:") || l.contains("dry") ||
            l.contains("overdue") || l.contains("too fast") || l.contains("finished") ||
            l.contains("hatari") || l.contains("red:") || l.contains("slow down") ||
            l.contains("ease off") || l.contains("freeze") -> InsightKind.WARNING
        l.contains("unusual") || l.contains("weird") || l.contains("double-log") ||
            l.contains("ajabu") -> InsightKind.ANOMALY
        l.contains("saved") || l.contains("save ") || l.contains("fully stacked") ||
            l.contains("inside") || l.contains("all clear") || l.contains("good") ||
            l.contains("poa") || l.contains("great") || l.contains("solid") ||
            l.contains("down ") && l.contains("last month") -> InsightKind.ACHIEVEMENT
        l.contains("pace") || l.contains("project") || l.contains("forecast") ||
            l.contains("may end") || l.contains("heading for") || l.contains("guess") ||
            l.contains("run dry") || l.contains("needs ksh") -> InsightKind.PREDICTION
        l.contains("punguza") || l.contains("try ") || l.contains("consider") ||
            l.contains("watch ") || l.contains("review") || l.contains("uncategorized") ||
            l.contains("add income") || l.contains("waiting on") -> InsightKind.OPPORTUNITY
        else -> InsightKind.INFO
    }
}

private fun insightColors(kind: InsightKind): Triple<Color, Color, String> = when (kind) {
    InsightKind.WARNING -> Triple(ppColors.error, ppColors.errorSoft, "⚠️")
    InsightKind.ACHIEVEMENT -> Triple(ppColors.success, ppColors.successSoft, "✅")
    InsightKind.OPPORTUNITY -> Triple(ppColors.info, ppColors.infoSoft, "💡")
    InsightKind.ANOMALY -> Triple(ppColors.warning, ppColors.warningSoft, "👀")
    InsightKind.PREDICTION -> Triple(ppColors.gold, ppColors.warningSoft, "🔮")
    InsightKind.INFO -> Triple(ppColors.brightBlue, ppColors.infoSoft, "ℹ️")
}

@Composable
fun PpInsightCard(
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val kind = classifyInsight(text)
    val (accent, soft, glyph) = insightColors(kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ppShapes.cardCompact)
            .background(ppColors.surface)
            .border(1.dp, ppColors.border, ppShapes.cardCompact)
            .padding(ppSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(3.dp).height(40.dp).clip(CircleShape).background(accent)
                .align(Alignment.CenterVertically)
        )
        Spacer(Modifier.width(ppSpacing.md))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(soft),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, style = ppTypography.bodyMedium)
        }
        Spacer(Modifier.width(ppSpacing.md))
        Text(
            text, style = ppTypography.bodyMedium, color = ppColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            TextButton(onClick = onDismiss) {
                Text("✕", style = ppTypography.bodySmall, color = ppColors.textTertiary)
            }
        }
    }
}

// --------------------------------------------------------------- progress ---

enum class PpProgressKind { GOLD, SUCCESS, ERROR, INFO }

@Composable
fun PpProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    kind: PpProgressKind = PpProgressKind.GOLD
) {
    // Bars ease to their value — progress that moves feels alive.
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(ppMotion.standard),
        label = "ppProgress"
    )
    val color = when (kind) {
        PpProgressKind.GOLD -> ppColors.gold
        PpProgressKind.SUCCESS -> ppColors.success
        PpProgressKind.ERROR -> ppColors.error
        PpProgressKind.INFO -> ppColors.brightBlue
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(ppShapes.progress)
            .background(ppColors.border)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .clip(ppShapes.progress)
                .background(color)
        )
    }
}

// ---------------------------------------------------------------- buttons ---

@Composable
fun PpPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = ppShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = ppColors.gold,
            contentColor = ppColors.textOnGold,
            disabledContainerColor = ppColors.border,
            disabledContentColor = ppColors.textDisabled
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
    ) {
        Text(text, style = ppTypography.labelLarge)
    }
}

@Composable
fun PpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = ppShapes.buttonSecondary,
        colors = ButtonDefaults.buttonColors(
            containerColor = ppColors.royalBlue,
            contentColor = ppColors.textOnBlue
        )
    ) {
        Text(text, style = ppTypography.labelLarge)
    }
}

@Composable
fun PpOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ppShapes.buttonSecondary,
        border = androidx.compose.foundation.BorderStroke(1.dp, ppColors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ppColors.textPrimary)
    ) {
        Text(text, style = ppTypography.labelLarge)
    }
}

@Composable
fun PpTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 44.dp)) {
        Text(text, style = ppTypography.labelLarge, color = ppColors.gold)
    }
}

// ------------------------------------------------------------ bottom bar ---

data class PpNavItem(val label: String, val icon: ImageVector)

@Composable
fun PpBottomBar(
    items: List<PpNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Edge-to-edge is on (decorFitsSystemWindows=false): lift the whole bar
    // above the gesture pill / 3-button nav instead of drawing under it.
    Column(modifier.navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ppColors.border))
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(ppColors.primaryNavy.copy(alpha = ppOverlays.navAlpha))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item ->
                val selected = i == selectedIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = { onSelect(i) })
                        .semantics { contentDescription = item.label }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        item.icon, contentDescription = null,
                        tint = if (selected) ppColors.gold else ppColors.textTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        item.label,
                        style = ppTypography.labelSmall,
                        color = if (selected) ppColors.gold else ppColors.textTertiary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // Quiet active marker — a gold tick, never a pill.
                    Box(
                        Modifier.width(16.dp).height(3.dp).clip(CircleShape)
                            .background(if (selected) ppColors.gold else Color.Transparent)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------- segmented choice ---

data class SegOption(val value: String, val label: String, val emoji: String = "")

/**
 * Single-select option group: big thumb-friendly cards instead of cramped
 * chips. The picked card gets the gold rail + tint + tick; 5+ options scroll
 * horizontally instead of crushing labels ("Wkday", "≤50" truncations die).
 */
@Composable
fun SegChoice(
    options: List<SegOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.size > 4) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { SegCell(it, it.value == selected, { onSelect(it.value) }, Modifier.width(108.dp)) }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { SegCell(it, it.value == selected, { onSelect(it.value) }, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SegCell(opt: SegOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) ppColors.gold else ppColors.border
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) ppColors.surfaceElevated else ppColors.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (opt.emoji.isNotBlank()) Text(opt.emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (selected) "✓ " else "") + opt.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = ppColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ------------------------------------------------------- feature header ---

@Composable
fun PpFeatureHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = ppTypography.h1, color = ppColors.textPrimary)
        Spacer(Modifier.height(ppSpacing.xs))
        Text(subtitle, style = ppTypography.bodySmall, color = ppColors.textTertiary)
    }
}

// ------------------------------------------------------------ photo scrim ---

/** Token photo treatment: 15 → 35 → 75 navy gradient, photo stays readable. */
@Composable
fun PpPhotoScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    ppOverlays.photoBase.copy(alpha = 0.15f),
                    ppOverlays.photoBase.copy(alpha = 0.35f),
                    ppOverlays.photoBase.copy(alpha = 0.75f)
                )
            )
        )
    )
}
