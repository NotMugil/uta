package com.notmugil.uta.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class ToastType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class ToastMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val subtitle: String? = null,
    val coverArtId: String? = null,
    val playlistId: String? = null,
    val type: ToastType = ToastType.INFO,
    val icon: ImageVector? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 3500L
)

object AppToastManager {
    private val scope: CoroutineScope by lazy {
        try {
            CoroutineScope(Dispatchers.Main.immediate + Job())
        } catch (_: Throwable) {
            CoroutineScope(Dispatchers.Default + Job())
        }
    }
    private var dismissJob: Job? = null

    private val _currentToast = MutableStateFlow<ToastMessage?>(null)
    val currentToast: StateFlow<ToastMessage?> = _currentToast.asStateFlow()

    fun show(toast: ToastMessage) {
        dismissJob?.cancel()
        _currentToast.value = toast
        dismissJob = scope.launch {
            delay(toast.durationMs)
            if (_currentToast.value?.id == toast.id) {
                _currentToast.value = null
            }
        }
    }

    fun show(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        type: ToastType = ToastType.INFO,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        show(
            ToastMessage(
                message = message,
                subtitle = subtitle,
                coverArtId = coverArtId,
                playlistId = playlistId,
                type = type,
                icon = icon,
                actionLabel = actionLabel,
                onAction = onAction,
                durationMs = durationMs
            )
        )
    }

    fun showSuccess(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.CircleCheck,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        show(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = ToastType.SUCCESS,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showError(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.AlertTriangle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        show(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = ToastType.ERROR,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showWarning(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.AlertCircle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        show(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = ToastType.WARNING,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showInfo(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.InfoCircle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        show(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = ToastType.INFO,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showFavorite(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        isFavorite: Boolean,
        onUndo: (() -> Unit)? = null
    ) {
        show(
            message = if (isFavorite) "Added to favourites" else "Removed from favourites",
            subtitle = subtitle ?: title,
            coverArtId = coverArtId,
            type = ToastType.SUCCESS,
            icon = if (isFavorite) Tabler.Filled.Heart else Tabler.Outline.HeartOff,
            actionLabel = if (onUndo != null) "Undo" else null,
            onAction = onUndo
        )
    }

    fun showPlaylist(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        isAdded: Boolean = true,
        onUndo: (() -> Unit)? = null
    ) {
        show(
            message = if (isAdded) "Added to playlist" else "Removed from playlist",
            subtitle = subtitle ?: title,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = ToastType.SUCCESS,
            icon = Tabler.Outline.PlaylistAdd,
            actionLabel = if (onUndo != null) "Undo" else null,
            onAction = onUndo
        )
    }

    fun showDownload(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        onView: (() -> Unit)? = null
    ) {
        show(
            message = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            type = ToastType.INFO,
            icon = Tabler.Outline.Download,
            actionLabel = if (onView != null) "View" else null,
            onAction = onView
        )
    }

    fun showQueue(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        show(
            message = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            type = ToastType.INFO,
            icon = Tabler.Outline.ListTree,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    fun dismiss(id: String? = null) {
        if (id == null || _currentToast.value?.id == id) {
            dismissJob?.cancel()
            _currentToast.value = null
        }
    }
}

class ToastHostState {
    val currentToast: StateFlow<ToastMessage?> = AppToastManager.currentToast

    fun showToast(
        message: String,
        type: ToastType = ToastType.INFO,
        icon: ImageVector? = null,
        durationMs: Long = 3500L
    ) {
        AppToastManager.show(
            message = message,
            type = type,
            icon = icon,
            durationMs = durationMs
        )
    }

    fun show(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        type: ToastType = ToastType.INFO,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.show(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            type = type,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showSuccess(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.CircleCheck,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.showSuccess(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showError(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.AlertTriangle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.showError(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showWarning(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.AlertCircle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.showWarning(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showInfo(
        message: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        icon: ImageVector = Tabler.Outline.InfoCircle,
        actionLabel: String? = null,
        durationMs: Long = 3500L,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.showInfo(
            message = message,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            icon = icon,
            actionLabel = actionLabel,
            durationMs = durationMs,
            onAction = onAction
        )
    }

    fun showFavorite(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        isFavorite: Boolean,
        onUndo: (() -> Unit)? = null
    ) {
        AppToastManager.showFavorite(
            title = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            isFavorite = isFavorite,
            onUndo = onUndo
        )
    }

    fun showPlaylist(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        playlistId: String? = null,
        isAdded: Boolean = true,
        onUndo: (() -> Unit)? = null
    ) {
        AppToastManager.showPlaylist(
            title = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            playlistId = playlistId,
            isAdded = isAdded,
            onUndo = onUndo
        )
    }

    fun showDownload(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        onView: (() -> Unit)? = null
    ) {
        AppToastManager.showDownload(
            title = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            onView = onView
        )
    }

    fun showQueue(
        title: String,
        subtitle: String? = null,
        coverArtId: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        AppToastManager.showQueue(
            title = title,
            subtitle = subtitle,
            coverArtId = coverArtId,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }

    fun dismiss(id: String? = null) {
        AppToastManager.dismiss(id)
    }
}

val LocalToastHostState = compositionLocalOf<ToastHostState> {
    ToastHostState()
}

@Composable
fun ToastHost(
    hostState: ToastHostState = LocalToastHostState.current,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopCenter
) {
    val toast by hostState.currentToast.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (alignment == Alignment.TopCenter) Modifier.statusBarsPadding().padding(top = 12.dp)
                else Modifier.navigationBarsPadding().padding(bottom = 80.dp)
            ),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                initialOffsetY = { if (alignment == Alignment.TopCenter) -it else it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { if (alignment == Alignment.TopCenter) -it else it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)
            ) + fadeOut()
        ) {
            val currentToast = toast
            if (currentToast != null) {
                ToastCard(
                    toast = currentToast,
                    onDismiss = { AppToastManager.dismiss(currentToast.id) }
                )
            }
        }
    }
}

@Composable
fun ToastCard(
    toast: ToastMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultIcon = when (toast.type) {
        ToastType.INFO -> Tabler.Outline.InfoCircle
        ToastType.SUCCESS -> Tabler.Outline.CircleCheck
        ToastType.WARNING -> Tabler.Outline.AlertCircle
        ToastType.ERROR -> Tabler.Outline.AlertTriangle
    }
    val icon = toast.icon ?: defaultIcon

    val accentColor = when (toast.type) {
        ToastType.INFO -> MaterialTheme.colorScheme.primary
        ToastType.SUCCESS -> MaterialTheme.colorScheme.primary
        ToastType.WARNING -> Color(0xFFFF9800)
        ToastType.ERROR -> MaterialTheme.colorScheme.error
    }

    val hasImage = !toast.coverArtId.isNullOrBlank() || !toast.playlistId.isNullOrBlank()
    val hasIcon = toast.icon != null || !hasImage

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .widthIn(max = 480.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (hasImage || hasIcon) 10.dp else 14.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                    end = 8.dp
                )
        ) {
            if (hasImage) {
                CoverArtImage(
                    coverArtId = toast.coverArtId,
                    playlistId = toast.playlistId,
                    contentDescription = toast.message,
                    size = 42.dp,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else if (hasIcon) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = toast.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                if (!toast.subtitle.isNullOrBlank()) {
                    Text(
                        text = toast.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            if (!toast.actionLabel.isNullOrBlank() && toast.onAction != null) {
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        toast.onAction.invoke()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = toast.actionLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Tabler.Outline.X,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
