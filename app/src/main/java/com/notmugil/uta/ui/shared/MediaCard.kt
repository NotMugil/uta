package com.notmugil.uta.ui.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    coverArtId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    alpha: Float = 1f,
    columnCount: Int? = null,
    cardWidth: androidx.compose.ui.unit.Dp = 156.dp,
    fallbackIcon: ImageVector = Tabler.Outline.Disc,
    playlistId: String? = null
) {
    val effectiveCols = columnCount ?: 2
    val titleStyle = when {
        columnCount != null && effectiveCols >= 4 -> MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
        columnCount != null && effectiveCols == 3 -> MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
        else -> MaterialTheme.typography.bodyMedium
    }
    val subStyle = when {
        columnCount != null && effectiveCols >= 4 -> MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp)
        columnCount != null && effectiveCols == 3 -> MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp)
        else -> MaterialTheme.typography.bodySmall
    }

    Column(
        modifier = modifier
            .then(if (columnCount != null) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
            .alpha(alpha)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                horizontal = if (columnCount != null && effectiveCols >= 3) 2.dp else 4.dp,
                vertical = if (columnCount != null && effectiveCols >= 3) 2.dp else 4.dp
            )
    ) {
        CoverArtImage(
            coverArtId = coverArtId,
            playlistId = playlistId,
            contentDescription = title,
            fallbackIcon = fallbackIcon,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(if (columnCount != null && effectiveCols >= 3) 4.dp else 6.dp))

        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = subtitle,
            style = subStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
