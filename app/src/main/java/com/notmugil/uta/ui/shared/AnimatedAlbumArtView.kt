package com.notmugil.uta.ui.shared

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.notmugil.uta.data.canvas.CanvasArtwork
import com.notmugil.uta.data.canvas.CanvasCache
import com.notmugil.uta.data.canvas.CanvasRepository
import com.notmugil.uta.data.preferences.LocalAppPreferences
import com.notmugil.uta.domain.model.AlbumItem
import com.notmugil.uta.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun AnimatedAlbumArtView(
    album: AlbumItem,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    val appPreferences = LocalAppPreferences.current
    val isAnimatedPref = appPreferences?.animatedArtworkEnabled?.collectAsStateWithLifecycle()?.value ?: false

    var canvasArtwork by remember(album.id) { mutableStateOf<CanvasArtwork?>(null) }
    var isVideoReady by remember(album.id) { mutableStateOf(false) }

    LaunchedEffect(album.id, isAnimatedPref) {
        isVideoReady = false
        if (isAnimatedPref) {
            canvasArtwork = CanvasRepository.getAlbumCanvas(album, context)
        } else {
            canvasArtwork = null
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady && canvasArtwork != null) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "album_canvas_video_fade"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        CoverArtImage(
            coverArtId = album.coverArtId,
            contentDescription = album.title,
            shape = shape,
            modifier = Modifier.fillMaxSize()
        )

        val activeCanvas = canvasArtwork
        if (isAnimatedPref && activeCanvas != null) {
            val exoPlayer = remember(context) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    try {
                        exoPlayer.stop()
                        exoPlayer.release()
                    } catch (_: Exception) {}
                }
            }

            LaunchedEffect(activeCanvas.url) {
                isVideoReady = false
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isVideoReady = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isVideoReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isVideoReady = false
                        CanvasCache.invalidateVideo(context, activeCanvas.url)
                        val fallback = activeCanvas.fallbackUrl ?: activeCanvas.url
                        try {
                            exoPlayer.setMediaItem(MediaItem.fromUri(fallback))
                            exoPlayer.prepare()
                        } catch (_: Exception) {}
                    }
                }
                exoPlayer.addListener(listener)

                val cachedFile = CanvasCache.getCachedVideoFile(context, activeCanvas.url)
                if (cachedFile != null) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(cachedFile)))
                } else {
                    exoPlayer.setMediaItem(MediaItem.fromUri(activeCanvas.url))
                    launch(Dispatchers.IO) {
                        CanvasCache.downloadAndCacheVideo(context, activeCanvas.url)
                    }
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(videoAlpha)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AnimatedAlbumArtView(
    track: TrackItem?,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    val appPreferences = LocalAppPreferences.current
    val isAnimatedPref = appPreferences?.animatedArtworkEnabled?.collectAsStateWithLifecycle()?.value ?: false

    var canvasArtwork by remember(track?.id) { mutableStateOf<CanvasArtwork?>(null) }
    var isVideoReady by remember(track?.id) { mutableStateOf(false) }

    LaunchedEffect(track?.id, isAnimatedPref) {
        isVideoReady = false
        if (isAnimatedPref && track != null) {
            canvasArtwork = CanvasRepository.getCanvas(track, context)
        } else {
            canvasArtwork = null
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady && canvasArtwork != null) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "canvas_video_fade"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        CoverArtImage(
            coverArtId = track?.coverArtId,
            contentDescription = track?.title,
            shape = shape,
            modifier = Modifier.fillMaxSize()
        )

        val activeCanvas = canvasArtwork
        if (isAnimatedPref && activeCanvas != null) {
            val exoPlayer = remember(context) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                }
            }

            DisposableEffect(exoPlayer) {
                onDispose {
                    try {
                        exoPlayer.stop()
                        exoPlayer.release()
                    } catch (_: Exception) {}
                }
            }

            LaunchedEffect(activeCanvas.url) {
                isVideoReady = false
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isVideoReady = true
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            isVideoReady = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isVideoReady = false
                        CanvasCache.invalidateVideo(context, activeCanvas.url)
                        val fallback = activeCanvas.fallbackUrl ?: activeCanvas.url
                        try {
                            exoPlayer.setMediaItem(MediaItem.fromUri(fallback))
                            exoPlayer.prepare()
                        } catch (_: Exception) {}
                    }
                }
                exoPlayer.addListener(listener)

                val cachedFile = CanvasCache.getCachedVideoFile(context, activeCanvas.url)
                if (cachedFile != null) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(cachedFile)))
                } else {
                    exoPlayer.setMediaItem(MediaItem.fromUri(activeCanvas.url))
                    launch(Dispatchers.IO) {
                        CanvasCache.downloadAndCacheVideo(context, activeCanvas.url)
                    }
                }
                exoPlayer.prepare()
                exoPlayer.playWhenReady = isPlaying
            }

            LaunchedEffect(isPlaying) {
                exoPlayer.playWhenReady = isPlaying
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(videoAlpha)
            )
        }
    }
}
