package com.notmugil.uta.domain.model

data class HistoryItem(
    val id: Long,
    val track: TrackItem,
    val playedAt: Long
)
