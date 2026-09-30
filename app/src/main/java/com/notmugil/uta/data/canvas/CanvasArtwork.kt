package com.notmugil.uta.data.canvas

enum class CanvasSource {
    TIDAL,
    APPLE_MUSIC,
    COMMUNITY
}

data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource
) {
    fun matches(wantTitle: String, wantArtist: String, wantAlbum: String? = null): Boolean {
        val normOurTitle = if (title != null) normalizeForMatch(title) else ""
        val normWantTitle = normalizeForMatch(wantTitle)

        val titleOk = title == null ||
            normWantTitle.isEmpty() ||
            normOurTitle == normWantTitle ||
            normOurTitle.contains(normWantTitle) ||
            normWantTitle.contains(normOurTitle)

        val wantedArtists = splitArtists(wantArtist)
        val ourArtists = splitArtists(artist ?: "")
        val artistOk = artist == null ||
            wantArtist.trim().isEmpty() ||
            wantedArtists.isEmpty() ||
            ourArtists.isEmpty() ||
            wantedArtists.any { want ->
                ourArtists.any { it == want || it.contains(want) || want.contains(it) }
            }

        return titleOk && artistOk
    }

    companion object {
        fun foldDiacritics(text: String): String {
            val withDia = "ÀÁÂÃÄÅàáâãäåÒÓÔÕÖØòóôõöøÈÉÊËèéêëÌÍÎÏìíîïÙÚÛÜùúûüÝýÿÑñÇç"
            val noDia = "AAAAAAaaaaaaOOOOOOooooooEEEEeeeeIIIIiiiiUUUUuuuuYyyNnCc"
            var out = text
            for (i in withDia.indices) {
                out = out.replace(withDia[i], noDia[i])
            }
            return out
        }

        fun normalizeForMatch(raw: String): String = foldDiacritics(raw)
            .lowercase()
            .replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        fun splitArtists(raw: String): List<String> = raw
            .split(
                Regex(
                    """(?:\s*,\s*|\s*&\s*|\s+×\s+|\s+x\s+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)""",
                    RegexOption.IGNORE_CASE
                )
            )
            .map { normalizeForMatch(it) }
            .filter { it.isNotEmpty() }
    }
}
