package dev.zephyr.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ surfaces

@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Z.card, RoundedCornerShape(Z.radius))
            .border(1.dp, Z.line, RoundedCornerShape(Z.radius)),
        content = content,
    )
}

@Composable
fun CardHeader(
    title: String,
    count: String? = null,
    description: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 13.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
                if (count != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(count, fontSize = 14.sp, color = Z.muted)
                }
            }
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = Z.muted, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
    HairLine()
}

@Composable
fun HairLine(color: Color = Z.lineIn) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = KickerStyle, color = Z.muted, modifier = modifier)
}

/** `01 / OVERVIEW` with the short blue rule the desktop build puts before it. */
@Composable
fun Kicker(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp).height(2.dp).background(Z.blue, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(9.dp))
        Text(text, style = KickerStyle, color = Z.blue)
    }
}

@Composable
fun PageHeader(
    kicker: String,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Kicker(kicker)
            Spacer(Modifier.height(7.dp))
            Text(
                title,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = Z.ink,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.5.sp, color = Z.muted, lineHeight = 17.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

// ------------------------------------------------------------------ latency

/** The blue-white glass pill the desktop build shows beside a group. */
@Composable
fun DelayPill(ms: Int?, modifier: Modifier = Modifier) {
    val tone = delayTone(ms)
    val (fill, edge, ink) = when (tone) {
        DelayTone.OK -> Triple(Z.bluePale, Z.blueLine, Color(0xFF2148C9))
        DelayTone.MID -> Triple(Color(0xFFFFEFD6), Color(0xFFF0CD8C), Color(0xFFB5701A))
        DelayTone.BAD -> Triple(Color(0xFFFFE4E4), Color(0xFFF0B4B4), Z.red)
        DelayTone.NONE -> Triple(Color(0xFFF1F2F5), Z.lineDark, Z.muted)
    }
    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(999.dp))
            .border(1.dp, edge, RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            delayText(ms),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** Dot plus number, for dense rows where a pill would be too heavy. */
@Composable
fun DelayDot(ms: Int?, modifier: Modifier = Modifier) {
    val tone = delayTone(ms)
    val color = if (tone == DelayTone.NONE) Z.muted else tone.color()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(delayText(ms), fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, tone: Color = Z.muted) {
    Box(
        modifier = modifier
            .background(Z.card, RoundedCornerShape(4.dp))
            .border(1.dp, Z.lineDark, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text.uppercase(),
            style = MonoSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold),
            color = tone,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ controls

/**
 * Visually the desktop switch, 40 by 22; the touch target is the platform's
 * 48 dp minimum, and the semantics say "switch" so TalkBack reads it as one.
 */
@Composable
fun ZSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val knob by animateDpAsState(if (checked) 21.dp else 3.dp, animationSpec = tween(150), label = "knob")
    val track by animateColorAsState(
        when {
            !enabled -> Z.lineDark.copy(alpha = 0.5f)
            checked -> Z.blue
            else -> Color(0xFFDCDDE1)
        },
        label = "track",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(track, RoundedCornerShape(13.dp))
                .border(
                    1.dp,
                    if (checked && enabled) Z.blueDark else Z.lineDark,
                    RoundedCornerShape(13.dp),
                ),
        ) {
            Box(
                Modifier
                    .offset(x = knob, y = 3.dp)
                    .size(20.dp)
                    .background(Color.White.copy(alpha = if (enabled) 1f else 0.7f), CircleShape),
            )
        }
    }
}

@Composable
fun <T> Segmented(
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    equalWidth: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Z.radiusSm))
            .background(Z.card, RoundedCornerShape(Z.radiusSm))
            .border(1.dp, Z.lineDark, RoundedCornerShape(Z.radiusSm)),
    ) {
        options.forEachIndexed { index, (optionValue, label) ->
            val active = optionValue == value
            if (index > 0) Box(Modifier.width(1.dp).height(32.dp).background(Z.lineDark))
            Box(
                modifier = Modifier
                    .then(if (equalWidth) Modifier.weight(1f) else Modifier)
                    .background(if (active) Z.bluePale else Color.Transparent)
                    .selectable(selected = active, enabled = enabled, role = Role.RadioButton) { onChange(optionValue) }
                    .defaultMinSize(minHeight = 32.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Z.blueDark else Z.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The desktop button, sized for a thumb: 32 dp tall in its small form and
 * 40 dp otherwise, where the desktop uses 26 and 30.
 */
@Composable
fun ZButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val background = when {
        !enabled -> Z.hover
        primary -> Z.blue
        else -> Z.card
    }
    val content = when {
        !enabled -> Z.muted
        primary -> Color.White
        danger -> Z.red
        else -> Z.ink
    }
    val edge = when {
        !enabled -> Z.line
        primary -> Z.blueDark
        danger -> Color(0xFFE9C3C6)
        else -> Z.lineDark
    }
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(Z.radiusSm))
            .border(1.dp, edge, RoundedCornerShape(Z.radiusSm))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = if (small) 32.dp else 40.dp)
            .padding(horizontal = if (small) 11.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(if (small) 13.dp else 15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            fontSize = if (small) 12.5.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun ZTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    rounded: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    clearable: Boolean = false,
) {
    val shape = RoundedCornerShape(if (rounded) 999.dp else Z.radiusSm)
    Row(
        modifier = modifier
            .background(Z.card, shape)
            .border(1.dp, Z.lineDark, shape)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = Z.muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 13.sp,
                    color = Z.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                textStyle = TextStyle(fontSize = 13.sp, color = Z.ink),
                cursorBrush = SolidColor(Z.blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (clearable && value.isNotEmpty()) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(ZIcon.Close, "清空", tint = Z.muted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * The traffic multiplier written into a node's name. Anything at 3x or above
 * is red: on a metered plan it quietly spends quota several times as fast.
 */
@Composable
fun MultiplierBadge(multiplier: Double?, modifier: Modifier = Modifier, spelled: Boolean = false) {
    if (multiplier == null) return
    val (fill, ink) = when {
        multiplier >= 3 -> Color(0xFFFFE4E4) to Z.red
        multiplier > 1 -> Color(0xFFFFEFD6) to Color(0xFFB5701A)
        multiplier < 1 -> Color(0xFFE3F4EA) to Z.green
        else -> Color(0xFFF1F2F5) to Z.muted
    }
    Box(
        modifier
            .background(fill, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            formatMultiplier(multiplier) + if (spelled && multiplier >= 3) " 高倍率" else "",
            style = MonoSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = ink,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ states

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(42.dp).background(Z.bluePale, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Z.blue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(11.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
        Spacer(Modifier.height(5.dp))
        Text(
            description,
            fontSize = 13.sp,
            color = Z.muted,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

@Composable
fun CardFoot(text: String, trailing: @Composable (() -> Unit)? = null) {
    HairLine()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, color = Z.muted, modifier = Modifier.weight(1f), lineHeight = 16.sp)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** A row used throughout the settings and detail lists. */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Z.ink)
            if (description != null) {
                Spacer(Modifier.height(1.dp))
                Text(description, fontSize = 12.sp, color = Z.muted, lineHeight = 16.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
