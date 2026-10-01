package com.notmugil.uta.ui.screens.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.R

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 14.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItemRow(
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    customLeading: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    chevronIcon: ImageVector = Tabler.Outline.ChevronRight,
    verticalPadding: Dp = 14.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled && onClick != null,
                onClick = {
                    com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.LIGHT)
                    onClick?.invoke()
                }
            )
            .padding(horizontal = 16.dp, vertical = verticalPadding)
    ) {
        if (customLeading != null) {
            customLeading()
            Spacer(modifier = Modifier.width(16.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    modifier = Modifier.fillMaxWidth(0.80f)
                )
            }
        }

        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        } else if (showChevron && onClick != null && enabled) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = chevronIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun UtaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
            checked -> accentColor
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 180),
        label = "uta_switch_track_color"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = 180),
        label = "uta_switch_thumb_offset"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                enabled = enabled && onCheckedChange != null,
                onClick = {
                    com.notmugil.uta.util.HapticFeedbackHelper.perform(context, com.notmugil.uta.util.HapticFeedbackHelper.HapticType.CLICK)
                    onCheckedChange?.invoke(!checked)
                }
            )
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
        )
    }
}

@Composable
fun <T> SettingsDropdownRow(
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    title: String,
    subtitle: String? = null,
    selectedValue: T,
    options: List<T>,
    getDisplayName: (T) -> String,
    onValueChange: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItemRow(
        icon = icon,
        iconTint = iconTint,
        title = title,
        subtitle = subtitle,
        showChevron = false,
        verticalPadding = 14.dp,
        onClick = { expanded = true },
        trailing = {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = getDisplayName(selectedValue),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Tabler.Outline.ChevronDown,
                        contentDescription = stringResource(R.string.view_options_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .widthIn(min = 160.dp, max = 240.dp)
                        .heightIn(max = 280.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                        val isSelected = option == selectedValue
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getDisplayName(option),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Tabler.Outline.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            },
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun StorageLineItem(color: Color, label: String, hint: String? = null, size: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = size,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    subtitle: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    valueLabel: String = "${value.toInt()}%",
    onValueChange: (Float) -> Unit,
    verticalPadding: Dp = 14.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.fillMaxWidth(0.80f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
