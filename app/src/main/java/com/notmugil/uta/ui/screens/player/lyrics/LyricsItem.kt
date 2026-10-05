package com.notmugil.uta.ui.screens.player.lyrics

import com.notmugil.uta.data.repository.SyncedLine

sealed class LyricsItem {
    abstract val key: String
    abstract val startMs: Long
    abstract val endMs: Long

    data class Lyric(
        val lineIndex: Int,
        val line: SyncedLine,
        override val startMs: Long,
        override val endMs: Long
    ) : LyricsItem() {
        override val key: String get() = "lyric_${lineIndex}_${startMs}"
    }

    data class InstrumentalGap(
        val gapId: String,
        override val startMs: Long,
        override val endMs: Long,
        val isIntro: Boolean = false,
        val isOutro: Boolean = false
    ) : LyricsItem() {
        override val key: String get() = "gap_${gapId}_${startMs}"
    }
}

fun buildLyricsItems(
    lines: List<SyncedLine>,
    totalDurationMs: Long,
    minGapThresholdMs: Long = 2500L
): List<LyricsItem> {
    if (lines.isEmpty()) return emptyList()

    val result = mutableListOf<LyricsItem>()

    val firstStart = lines.first().startMs
    if (firstStart >= minGapThresholdMs) {
        result.add(
            LyricsItem.InstrumentalGap(
                gapId = "intro",
                startMs = 0L,
                endMs = firstStart,
                isIntro = true
            )
        )
    }

    lines.forEachIndexed { index, line ->
        val start = line.startMs
        val nextStart = lines.getOrNull(index + 1)?.startMs
        val lineWordEnd = if (line.words.isNotEmpty()) {
            val lastWord = line.words.last()
            lastWord.endMs ?: (lastWord.startMs + 650L)
        } else null

        val end = when {
            lineWordEnd != null && lineWordEnd > start -> maxOf(lineWordEnd, line.endMs ?: 0L)
            line.endMs != null && line.endMs > start -> line.endMs
            nextStart != null -> nextStart
            else -> start + 3500L
        }

        result.add(
            LyricsItem.Lyric(
                lineIndex = index,
                line = line,
                startMs = start,
                endMs = end
            )
        )

        if (nextStart != null) {
            val gapDuration = nextStart - end
            if (gapDuration >= minGapThresholdMs) {
                result.add(
                    LyricsItem.InstrumentalGap(
                        gapId = "interlude_$index",
                        startMs = end,
                        endMs = nextStart
                    )
                )
            }
        }
    }

    val lastLineEnd = (result.lastOrNull { it is LyricsItem.Lyric } as? LyricsItem.Lyric)?.endMs
        ?: lines.last().startMs + 3500L
    val effectiveDuration = if (totalDurationMs > 0) totalDurationMs else (lastLineEnd + 15000L)
    if (effectiveDuration - lastLineEnd >= minGapThresholdMs) {
        result.add(
            LyricsItem.InstrumentalGap(
                gapId = "outro",
                startMs = lastLineEnd,
                endMs = effectiveDuration,
                isOutro = true
            )
        )
    }

    return result
}
