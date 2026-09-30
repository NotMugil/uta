package com.notmugil.uta.ui.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.domain.model.ArtistItem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    columnCount: Int? = null,
    avatarSize: Dp? = null
) {
    val effectiveCols = columnCount ?: 2
    val textStyle = when {
        columnCount != null && effectiveCols >= 4 -> MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
        columnCount != null && effectiveCols == 3 -> MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 14.sp)
        else -> MaterialTheme.typography.bodySmall
    }
    val effectiveAvatarSize = avatarSize ?: when {
        columnCount != null && effectiveCols >= 4 -> 54.dp
        columnCount != null && effectiveCols == 3 -> 66.dp
        else -> 80.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(if (columnCount != null) Modifier.fillMaxWidth() else Modifier.width(96.dp))
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        CoverArtImage(
            coverArtId = artist.coverArtId,
            contentDescription = artist.name,
            size = effectiveAvatarSize,
            shape = CircleShape,
            fallbackIcon = Tabler.Outline.User,
            modifier = Modifier.size(effectiveAvatarSize)
        )

        Spacer(modifier = Modifier.height(if (columnCount != null && effectiveCols >= 3) 4.dp else 6.dp))

        Text(
            text = artist.name,
            style = textStyle,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
        )
    }
}
