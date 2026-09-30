package com.notmugil.uta.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen loading indicator styled consistently with the Pull-to-Refresh loader.
 * Abstracts the page loading process during page navigation and transitions until visible content is loaded.
 */
@Composable
fun FullScreenLoader(
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    indicatorSize: Dp = 28.dp,
    strokeWidth: Dp = 2.5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    containerColor: Color = MaterialTheme.colorScheme.background,
    onNavigateBack: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
    ) {
        if (title != null || onNavigateBack != null) {
            AppTopBar(
                title = title ?: "",
                containerColor = Color.Transparent,
                onNavigateBack = onNavigateBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(indicatorSize),
                strokeWidth = strokeWidth,
                color = color,
                trackColor = trackColor
            )
            if (!message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
