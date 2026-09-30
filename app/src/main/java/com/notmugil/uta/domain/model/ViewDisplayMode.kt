package com.notmugil.uta.domain.model

import androidx.annotation.StringRes
import com.notmugil.uta.R

enum class ViewDisplayMode(@StringRes val displayNameResId: Int) {
    LIST(R.string.view_list),
    TEXT_ONLY(R.string.view_text_only);

    val displayName: String get() = name
}
