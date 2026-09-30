package com.notmugil.uta.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notmugil.uta.domain.model.GenreItem

@Composable
fun GenreCard(
    genre: GenreItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    covers: List<String> = emptyList(),
    isOffline: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = genre.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.68f)
                    .padding(start = 14.dp, top = 12.dp, end = 4.dp)
                    .basicMarquee()
            )

            val countSubtitle = when {
                genre.albumCount > 0 -> if (genre.albumCount == 1) "1 album" else "${genre.albumCount} albums"
                genre.songCount > 0 -> if (genre.songCount == 1) "1 song" else "${genre.songCount} songs"
                else -> ""
            }
            if (countSubtitle.isNotBlank()) {
                Text(
                    text = countSubtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 12.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer {
                        translationX = 16f
                        translationY = 16f
                        rotationZ = 12f
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    !isOffline && covers.size >= 3 -> {
                        CoverArtImage(
                            coverArtId = covers[1],
                            contentDescription = null,
                            size = 56.dp,
                            shape = RoundedCornerShape(6.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(56.dp)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    rotationZ = -14f
                                    translationX = -18f
                                    translationY = 2f
                                    alpha = 0.85f
                                }
                        )

                        CoverArtImage(
                            coverArtId = covers[2],
                            contentDescription = null,
                            size = 56.dp,
                            shape = RoundedCornerShape(6.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(56.dp)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    rotationZ = 14f
                                    translationX = 18f
                                    translationY = 2f
                                    alpha = 0.85f
                                }
                        )

                        CoverArtImage(
                            coverArtId = covers[0],
                            contentDescription = genre.name,
                            size = 62.dp,
                            shape = RoundedCornerShape(8.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(62.dp)
                                .aspectRatio(1f)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                        )
                    }
                    !isOffline && covers.size == 2 -> {
                        CoverArtImage(
                            coverArtId = covers[1],
                            contentDescription = null,
                            size = 56.dp,
                            shape = RoundedCornerShape(6.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(56.dp)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    rotationZ = -12f
                                    translationX = -14f
                                    alpha = 0.85f
                                }
                        )

                        CoverArtImage(
                            coverArtId = covers[0],
                            contentDescription = genre.name,
                            size = 62.dp,
                            shape = RoundedCornerShape(8.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(62.dp)
                                .aspectRatio(1f)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                        )
                    }
                    !isOffline && covers.size == 1 -> {
                        CoverArtImage(
                            coverArtId = covers[0],
                            contentDescription = genre.name,
                            size = 62.dp,
                            shape = RoundedCornerShape(8.dp),
                            fallbackIcon = Tabler.Outline.Shape,
                            modifier = Modifier
                                .size(62.dp)
                                .aspectRatio(1f)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .aspectRatio(1f)
                                .shadow(6.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.Shape,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
