package za.co.galvatech.timemotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.galvatech.timemotion.model.Category
import za.co.galvatech.timemotion.model.PersonResult
import kotlin.math.roundToInt

val Ink = Color(0xFF111827)
val Teal = Color(0xFF0F766E)
val Amber = Color(0xFFE8A317)
val Rust = Color(0xFFC2410C)
val Paper = Color(0xFFF4F5F2)
val Muted = Color(0xFF4B5563)
val LineGrey = Color(0xFFE5E7EB)
val BorderGrey = Color(0xFFD1D5DB)

fun Category.color(): Color = when (this) {
    Category.VA -> Teal
    Category.NNVA -> Amber
    Category.NVA -> Rust
}

fun Category.onColor(): Color = if (this == Category.NNVA) Ink else Color.White

/** Category colour that stays readable as text on white. */
fun Category.textColor(): Color = when (this) {
    Category.VA -> Teal
    Category.NNVA -> Color(0xFF92400E)
    Category.NVA -> Rust
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            secondary = Ink,
            background = Paper,
            surface = Color.White,
            error = Rust
        ),
        content = content
    )
}

@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .statusBarsPadding()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFFD1D5DB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp
    )
}

@Composable
fun CategoryChip(category: Category, text: String = category.label) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(category.color())
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = category.onColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** VA / NNVA / NVA stacked bar with percentages inside. */
@Composable
fun StackedBar(result: PersonResult, height: Dp = 28.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(LineGrey)
    ) {
        Category.entries.forEach { c ->
            val f = result.share(c)
            if (f > 0f) {
                Box(
                    Modifier
                        .weight(f)
                        .fillMaxHeight()
                        .background(c.color())
                        .padding(start = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (f > 0.12f) {
                        Text("${(f * 100).roundToInt()}%", color = c.onColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Category.entries.forEach { c ->
            Box(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.color()).padding(5.dp))
            Text(c.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.padding(end = 12.dp))
        }
    }
}
