package com.notmugil.uta.ui.screens.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notmugil.uta.R
import com.notmugil.uta.ui.screens.settings.components.SettingsItemRow

@Composable
fun AboutSubPage() {
    val context = LocalContext.current
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_terms),
            verticalPadding = 16.dp,
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NotMugil/uta/blob/main/docs/TERMS.md"))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_privacy),
            verticalPadding = 16.dp,
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NotMugil/uta/blob/main/docs/PRIVACY.md"))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_source_code),
            verticalPadding = 16.dp,
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NotMugil/uta"))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        )

        SettingsItemRow(
            icon = null,
            title = stringResource(R.string.setting_version),
            value = appVersion,
            showChevron = false,
            verticalPadding = 16.dp
        )
    }
}
