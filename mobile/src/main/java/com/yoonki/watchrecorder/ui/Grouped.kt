package com.yoonki.watchrecorder.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small grey caption above a section, iOS style. */
@Composable
fun SectionHeader(text: String, trailing: String? = null, highlight: Boolean = false) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            text, color = if (highlight) p.accent else p.secondary,
            fontSize = if (highlight) 20.sp else 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, color = if (highlight) p.accent else p.secondary, fontSize = 13.sp)
    }
}

@Composable
fun SectionFooter(text: String) {
    Text(
        text, color = LocalPalette.current.secondary, fontSize = 13.sp,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
    )
}

/** Rounded card that holds rows separated by inset hairlines. */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LocalPalette.current.card)
    ) { content() }
}

@Composable
fun RowDivider(indent: Int = 16) {
    HorizontalDivider(Modifier.padding(start = indent.dp), thickness = 0.5.dp, color = LocalPalette.current.separator)
}

/** Colored rounded-square icon like iOS Settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Int = 29) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.62f).dp)) }
}

@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector? = null,
    iconColor: Color = IosColors.gray,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, color = p.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = p.secondary, fontSize = 17.sp)
        trailing?.invoke()
    }
}

/** iOS segmented control. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(p.fill)
            .padding(2.dp)
    ) {
        options.forEachIndexed { i, label ->
            val bg by animateColorAsState(if (i == selected) p.card else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = p.label, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
