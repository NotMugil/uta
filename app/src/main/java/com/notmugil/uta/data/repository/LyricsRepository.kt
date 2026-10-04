package com.notmugil.uta.data.repository

import android.content.Context
import android.util.LruCache
import com.notmugil.uta.data.SubsonicRepository
import com.notmugil.uta.data.preferences.AppPreferences
import com.notmugil.uta.data.preferences.LyricsProvider
import com.notmugil.uta.data.preferences.LyricsSourceMode
import com.notmugil.uta.domain.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SyncedWord(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String
)

@Serializable
data class SyncedLine(
    val startMs: Long,
    val endMs: Long? = null,
    val text: String,
    val words: List<SyncedWord> = emptyList()
) {
    val isWordSynced: Boolean
        get() = words.isNotEmpty()
}

@Serializable
data class LyricsData(
    val plainLyrics: String? = null,
    val syncedLines: List<SyncedLine> = emptyList(),
    val source: String = "unknown"
) {
    val isWordSynced: Boolean
        get() = syncedLines.any { it.isWordSynced }

    val isSynced: Boolean
        get() = syncedLines.isNotEmpty()

    val isEmpty: Boolean
        get() = syncedLines.isEmpty() && (plainLyrics.isNullOrBlank() || plainLyrics.trim().equals("null", ignoreCase = true))

    val syncTypeTag: String?
        get() = when {
            isWordSynced -> "Word"
            isSynced -> "Line"
            !isEmpty -> "Plain"
            else -> null
        }
}

object LrcParser {
    // Matches line timestamps like [01:23.45], [01:23.456], [01:23], or NetEase [12345,3000]
    private val lineTimestampRegex = Regex("""\[(?:(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?|(\d+),(\d+))\]""")

    // Matches inline word timestamps like <01:23.45>, (01:23.45), or (12345,500)
    private val inlineWordTimestampRegex = Regex("""(?:[<(](\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?[>)]|\((\d+),(\d+)(?:,\d+)?\))""")

    private fun parseTimestamp(minStr: String, secStr: String, fracStr: String): Long {
        val minutes = minStr.toLongOrNull() ?: 0L
        val seconds = secStr.toLongOrNull() ?: 0L
        val millis = when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
        }
        return (minutes * 60 + seconds) * 1000 + millis
    }

    fun parseWords(textWithTags: String, baseLineStartMs: Long): Pair<String, List<SyncedWord>> {
        val matches = inlineWordTimestampRegex.findAll(textWithTags).toList()
        if (matches.isEmpty()) {
            return Pair(textWithTags.trim(), emptyList())
        }

        val words = mutableListOf<SyncedWord>()
        val cleanTextBuilder = StringBuilder()

        for (i in matches.indices) {
            val match = matches[i]
            val wordStartMs = if (match.groupValues[1].isNotEmpty()) {
                parseTimestamp(
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues.getOrNull(3).orEmpty()
                )
            } else if (match.groupValues[4].isNotEmpty()) {
                match.groupValues[4].toLongOrNull() ?: baseLineStartMs
            } else {
                baseLineStartMs
            }

            val durationMs = if (match.groupValues[5].isNotEmpty()) {
                match.groupValues[5].toLongOrNull()
            } else {
                null
            }

            val textStart = match.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else textWithTags.length
            var wordText = if (textStart <= textWithTags.length && textStart <= textEnd) {
                textWithTags.substring(textStart, textEnd)
            } else {
                ""
            }

            if (wordText.isNotEmpty()) {
                if (i + 1 < matches.size && !wordText.endsWith(" ")) {
                    wordText = "$wordText "
                }
                val nextStartMs = if (durationMs != null && durationMs > 0) {
                    wordStartMs + durationMs
                } else if (i + 1 < matches.size) {
                    val nextMatch = matches[i + 1]
                    if (nextMatch.groupValues[1].isNotEmpty()) {
                        parseTimestamp(
                            nextMatch.groupValues[1],
                            nextMatch.groupValues[2],
                            nextMatch.groupValues.getOrNull(3).orEmpty()
                        )
                    } else if (nextMatch.groupValues[4].isNotEmpty()) {
                        nextMatch.groupValues[4].toLongOrNull()
                    } else {
                        null
                    }
                } else {
                    null
                }

                words.add(SyncedWord(startMs = wordStartMs, endMs = nextStartMs, text = wordText))
                cleanTextBuilder.append(wordText)
            }
        }

        val finalCleanText = cleanTextBuilder.toString().trim().ifEmpty {
            textWithTags.replace(inlineWordTimestampRegex, "").trim()
        }
        return Pair(finalCleanText, words)
    }

    fun parse(lrc: String): List<SyncedLine> {
        if (lrc.isBlank()) return emptyList()

        val lines = mutableListOf<SyncedLine>()
        val rawLines = lrc.lines()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            // Skip metadata headers
            if (trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[ti:") ||
                trimmed.startsWith("[al:") ||
                trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:") ||
                trimmed.startsWith("[length:") ||
                trimmed.startsWith("[re:") ||
                trimmed.startsWith("[ve:")
            ) {
                continue
            }

            val lineMatches = lineTimestampRegex.findAll(trimmed).toList()
            if (lineMatches.isEmpty()) continue

            val lastLineMatch = lineMatches.last()
            val textStartIndex = lastLineMatch.range.last + 1
            val rawLyricText = if (textStartIndex <= trimmed.length) {
                trimmed.substring(textStartIndex)
            } else {
                ""
            }

            for (match in lineMatches) {
                val totalMs = if (match.groupValues[1].isNotEmpty()) {
                    parseTimestamp(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues.getOrNull(3).orEmpty()
                    )
                } else if (match.groupValues[4].isNotEmpty()) {
                    match.groupValues[4].toLongOrNull() ?: 0L
                } else {
                    0L
                }

                val (cleanText, words) = parseWords(rawLyricText, totalMs)
                lines.add(SyncedLine(startMs = totalMs, text = cleanText, words = words))
            }
        }

        return lines.sortedBy { it.startMs }
    }
}

object LyricsCache {
    private val memoryCache = LruCache<String, LyricsData>(200)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCacheFile(context: Context, key: String): File {
        val safeName = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(getCacheDir(context), "$safeName.json")
    }

    fun get(context: Context, key: String): LyricsData? {
        memoryCache.get(key)?.let {
            if (!it.isEmpty) return it else memoryCache.remove(key)
        }

        try {
            val file = getCacheFile(context, key)
            if (file.exists() && file.length() > 0) {
                val text = file.readText(Charsets.UTF_8)
                val data = json.decodeFromString<LyricsData>(text)
                if (!data.isEmpty) {
                    memoryCache.put(key, data)
                    return data
                } else {
                    file.delete()
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun put(context: Context, key: String, data: LyricsData) {
        if (data.isEmpty) return
        memoryCache.put(key, data)
        try {
            val file = getCacheFile(context, key)
            file.writeText(json.encodeToString(data), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun clear(context: Context) {
        memoryCache.evictAll()
        try {
            getCacheDir(context).deleteRecursively()
        } catch (_: Exception) {}
    }
}

object GenericLyricsHelper {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseTtmlTimestamp(raw: String): Long {
        val s = raw.trim().removeSuffix("s")
        if (s.contains(":")) {
            val parts = s.split(":")
            return when (parts.size) {
                2 -> {
                    val min = parts[0].toLongOrNull() ?: 0L
                    val secParts = parts[1].split(".", ":")
                    val sec = secParts[0].toLongOrNull() ?: 0L
                    val ms = if (secParts.size > 1) {
                        val frac = secParts[1]
                        when (frac.length) {
                            1 -> (frac.toLongOrNull() ?: 0L) * 100
                            2 -> (frac.toLongOrNull() ?: 0L) * 10
                            3 -> frac.toLongOrNull() ?: 0L
                            else -> frac.take(3).toLongOrNull() ?: 0L
                        }
                    } else {
                        0L
                    }
                    (min * 60 + sec) * 1000 + ms
                }
                3 -> {
                    val hr = parts[0].toLongOrNull() ?: 0L
                    val min = parts[1].toLongOrNull() ?: 0L
                    val secParts = parts[2].split(".", ":")
                    val sec = secParts[0].toLongOrNull() ?: 0L
                    val ms = if (secParts.size > 1) {
                        val frac = secParts[1]
                        when (frac.length) {
                            1 -> (frac.toLongOrNull() ?: 0L) * 100
                            2 -> (frac.toLongOrNull() ?: 0L) * 10
                            3 -> frac.toLongOrNull() ?: 0L
                            else -> frac.take(3).toLongOrNull() ?: 0L
                        }
                    } else {
                        0L
                    }
                    ((hr * 60 + min) * 60 + sec) * 1000 + ms
                }
                else -> 0L
            }
        } else {
            val floatSec = s.toDoubleOrNull() ?: 0.0
            return (floatSec * 1000).toLong()
        }
    }

    fun parseTtml(ttml: String, defaultSource: String): LyricsData? {
        if (!ttml.contains("<p ") && !ttml.contains("<p>") && !ttml.contains("<span ")) return null

        val lines = mutableListOf<SyncedLine>()
        val pRegex = Regex("""<p\b([^>]*)>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val spanRegex = Regex("""<span\b([^>]*)>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
        val beginAttrRegex = Regex("""begin=["']([^"']+)["']""")
        val endAttrRegex = Regex("""end=["']([^"']+)["']""")

        for (pMatch in pRegex.findAll(ttml)) {
            val pAttrs = pMatch.groupValues[1]
            val pBody = pMatch.groupValues[2]

            val lineBegin = beginAttrRegex.find(pAttrs)?.groupValues?.get(1)
            val lineEnd = endAttrRegex.find(pAttrs)?.groupValues?.get(1)
            val lineStartMs = if (lineBegin != null) parseTtmlTimestamp(lineBegin) else 0L
            val lineEndMs = if (lineEnd != null) parseTtmlTimestamp(lineEnd) else null

            val words = mutableListOf<SyncedWord>()
            val spanMatches = spanRegex.findAll(pBody).toList()

            if (spanMatches.isNotEmpty()) {
                for (i in spanMatches.indices) {
                    val spanMatch = spanMatches[i]
                    val spanAttrs = spanMatch.groupValues[1]
                    var spanText = spanMatch.groupValues[2]
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&apos;", "'")
                        .replace("&#39;", "'")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")

                    val spanBegin = beginAttrRegex.find(spanAttrs)?.groupValues?.get(1)
                    val spanEnd = endAttrRegex.find(spanAttrs)?.groupValues?.get(1)

                    val wordStartMs = if (spanBegin != null) parseTtmlTimestamp(spanBegin) else lineStartMs
                    val wordEndMs = if (spanEnd != null) parseTtmlTimestamp(spanEnd) else null

                    val interstitial = if (i + 1 < spanMatches.size) {
                        val interStart = spanMatch.range.last + 1
                        val interEnd = spanMatches[i + 1].range.first
                        if (interStart < interEnd) {
                            pBody.substring(interStart, interEnd).replace(Regex("<[^>]*>"), "")
                        } else {
                            ""
                        }
                    } else {
                        ""
                    }

                    if (interstitial.isNotEmpty()) {
                        spanText += interstitial
                    } else if (i + 1 < spanMatches.size && !spanText.endsWith(" ")) {
                        spanText += " "
                    }

                    if (spanText.isNotEmpty()) {
                        words.add(SyncedWord(startMs = wordStartMs, endMs = wordEndMs, text = spanText))
                    }
                }
            }

            val cleanLineText = if (words.isNotEmpty()) {
                words.joinToString("") { it.text }.trim()
            } else {
                pBody.replace(Regex("<[^>]*>"), "")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
            }

            if (cleanLineText.isNotBlank()) {
                lines.add(SyncedLine(startMs = lineStartMs, endMs = lineEndMs, text = cleanLineText, words = words))
            }
        }

        if (lines.isNotEmpty()) {
            return LyricsData(
                plainLyrics = null,
                syncedLines = lines.sortedBy { it.startMs },
                source = defaultSource
            )
        }
        return null
    }

    fun parseGenericLyrics(body: String, defaultSource: String): LyricsData? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("<") || trimmed.contains("<tt") || trimmed.contains("<p begin=") || trimmed.contains("<span begin=")) {
            parseTtml(trimmed, defaultSource)?.let { return it }
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val element = json.parseToJsonElement(trimmed)
                if (element is kotlinx.serialization.json.JsonObject) {
                    val ttmlContent = element["ttml"]?.jsonPrimitive?.content
                    if (!ttmlContent.isNullOrBlank()) {
                        parseTtml(ttmlContent, defaultSource)?.let { return it }
                    }

                    val syncedText = (element["syncedLyrics"]?.jsonPrimitive?.content
                        ?: element["synced"]?.jsonPrimitive?.content
                        ?: element["lrc"]?.jsonPrimitive?.content
                        ?: element["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
                        ?: element["yrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content
                        ?: element["klyric"]?.jsonObject?.get("lyric")?.jsonPrimitive?.content)
                        ?.takeIf { !it.trim().equals("null", ignoreCase = true) }

                    val plainText = (element["plainLyrics"]?.jsonPrimitive?.content
                        ?: element["lyrics"]?.jsonPrimitive?.content
                        ?: element["text"]?.jsonPrimitive?.content
                        ?: element["plain"]?.jsonPrimitive?.content)
                        ?.takeIf { !it.trim().equals("null", ignoreCase = true) }

                    if (syncedText == null && plainText == null && element.containsKey("data")) {
                        val nested = element["data"]
                        if (nested != null) {
                            val nestedResult = parseGenericLyrics(nested.toString(), defaultSource)
                            if (nestedResult != null && !nestedResult.isEmpty) return nestedResult
                        }
                    }

                    val linesArray = element["lines"]?.jsonArray
                        ?: element["lyrics"]?.jsonArray
                        ?: element["lines"]?.jsonObject?.get("lines")?.jsonArray

                    if (linesArray != null && linesArray.isNotEmpty()) {
                        val lines = mutableListOf<SyncedLine>()
                        for (item in linesArray) {
                            val itemObj = item.jsonObject
                            val timeMs = itemObj["time"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: itemObj["startMs"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: itemObj["startTimeMs"]?.jsonPrimitive?.content?.toLongOrNull()
                                ?: itemObj["time"]?.jsonPrimitive?.intOrNull?.toLong()
                                ?: itemObj["start"]?.jsonPrimitive?.intOrNull?.toLong()
                            val wordsText = itemObj["words"]?.jsonPrimitive?.content
                                ?: itemObj["text"]?.jsonPrimitive?.content
                                ?: itemObj["line"]?.jsonPrimitive?.content

                            val syllablesArray = itemObj["syllables"]?.jsonArray
                                ?: itemObj["words"]?.jsonArray
                                ?: itemObj["lead"]?.jsonObject?.get("syllables")?.jsonArray

                            val parsedWords = mutableListOf<SyncedWord>()
                            if (syllablesArray != null && syllablesArray.isNotEmpty()) {
                                for (sylIndex in syllablesArray.indices) {
                                    val syl = syllablesArray[sylIndex]
                                    if (syl is kotlinx.serialization.json.JsonObject) {
                                        val sylTime = syl["time"]?.jsonPrimitive?.content?.toLongOrNull()
                                            ?: syl["startMs"]?.jsonPrimitive?.content?.toLongOrNull()
                                            ?: syl["startTimeMs"]?.jsonPrimitive?.content?.toLongOrNull()
                                            ?: syl["time"]?.jsonPrimitive?.intOrNull?.toLong()
                                            ?: syl["start"]?.jsonPrimitive?.intOrNull?.toLong()
                                            ?: timeMs
                                        val sylDuration = syl["duration"]?.jsonPrimitive?.intOrNull?.toLong()
                                            ?: syl["durationMs"]?.jsonPrimitive?.intOrNull?.toLong()
                                        var sylText = syl["text"]?.jsonPrimitive?.content
                                            ?: syl["words"]?.jsonPrimitive?.content
                                            ?: syl["word"]?.jsonPrimitive?.content

                                        if (sylTime != null && !sylText.isNullOrEmpty()) {
                                            if (sylIndex + 1 < syllablesArray.size && !sylText.endsWith(" ")) {
                                                sylText = "$sylText "
                                            }
                                            val endMs = if (sylDuration != null && sylDuration > 0) sylTime + sylDuration else null
                                            parsedWords.add(SyncedWord(startMs = sylTime, endMs = endMs, text = sylText))
                                        }
                                    }
                                }
                            }

                            if (timeMs != null) {
                                if (parsedWords.isNotEmpty()) {
                                    val fullText = wordsText ?: parsedWords.joinToString("") { it.text }.trim()
                                    lines.add(SyncedLine(startMs = timeMs, text = fullText, words = parsedWords))
                                } else if (!wordsText.isNullOrBlank()) {
                                    val (cleanText, words) = LrcParser.parseWords(wordsText, timeMs)
                                    lines.add(SyncedLine(startMs = timeMs, text = cleanText, words = words))
                                }
                            }
                        }
                        if (lines.isNotEmpty()) {
                            return LyricsData(
                                plainLyrics = plainText,
                                syncedLines = lines.sortedBy { it.startMs },
                                source = defaultSource
                            )
                        }
                    }

                    if (!syncedText.isNullOrBlank()) {
                        val parsedLines = LrcParser.parse(syncedText)
                        if (parsedLines.isNotEmpty()) {
                            return LyricsData(
                                plainLyrics = plainText ?: syncedText,
                                syncedLines = parsedLines,
                                source = defaultSource
                            )
                        }
                    }

                    if (!plainText.isNullOrBlank()) {
                        val parsedLines = LrcParser.parse(plainText)
                        return LyricsData(
                            plainLyrics = plainText,
                            syncedLines = parsedLines,
                            source = defaultSource
                        )
                    }
                } else if (element is kotlinx.serialization.json.JsonArray) {
                    for (item in element) {
                        val parsed = parseGenericLyrics(item.toString(), defaultSource)
                        if (parsed != null && !parsed.isEmpty) return parsed
                    }
                }
            } catch (_: Exception) {}
        }

        val parsedLines = LrcParser.parse(trimmed)
        if (parsedLines.isNotEmpty()) {
            return LyricsData(
                plainLyrics = trimmed.takeUnless { it.equals("null", ignoreCase = true) },
                syncedLines = parsedLines,
                source = defaultSource
            )
        } else if (trimmed.isNotBlank() && !trimmed.equals("null", ignoreCase = true)) {
            return LyricsData(
                plainLyrics = trimmed,
                syncedLines = emptyList(),
                source = defaultSource
            )
        }

        return null
    }
}

object LrclibService {
    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "Uta-Music-Player/1.0 (https://github.com/notmugil/uta)"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val client = HttpClient(OkHttp)
        try {
            val getResponse = client.get("$BASE_URL/get") {
                header("User-Agent", USER_AGENT)
                parameter("track_name", trackName)
                parameter("artist_name", artistName)
                if (!albumName.isNullOrBlank()) {
                    parameter("album_name", albumName)
                }
                if (durationSeconds != null && durationSeconds > 0) {
                    parameter("duration", durationSeconds)
                }
            }

            if (getResponse.status.isSuccess()) {
                val body = getResponse.bodyAsText()
                val parsed = parseLrclibJson(body)
                if (parsed != null) return@withContext parsed
            }

            val searchResponse = client.get("$BASE_URL/search") {
                header("User-Agent", USER_AGENT)
                parameter("track_name", trackName)
                parameter("artist_name", artistName)
            }

            if (searchResponse.status.isSuccess()) {
                val body = searchResponse.bodyAsText()
                val array = json.parseToJsonElement(body).jsonArray
                for (item in array) {
                    val obj = item.jsonObject
                    val synced = obj["syncedLyrics"]?.jsonPrimitive?.content?.takeIf { !it.trim().equals("null", ignoreCase = true) }
                    val plain = obj["plainLyrics"]?.jsonPrimitive?.content?.takeIf { !it.trim().equals("null", ignoreCase = true) }
                    val isInstrumental = obj["instrumental"]?.jsonPrimitive?.booleanOrNull == true

                    if (!synced.isNullOrBlank()) {
                        val parsedLines = LrcParser.parse(synced)
                        if (parsedLines.isNotEmpty()) {
                            return@withContext LyricsData(
                                plainLyrics = plain,
                                syncedLines = parsedLines,
                                source = "lrclib"
                            )
                        }
                    } else if (!plain.isNullOrBlank()) {
                        return@withContext LyricsData(
                            plainLyrics = plain,
                            syncedLines = emptyList(),
                            source = "lrclib"
                        )
                    } else if (isInstrumental) {
                        return@withContext LyricsData(
                            plainLyrics = "♪ Instrumental ♪",
                            syncedLines = emptyList(),
                            source = "lrclib"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[LrclibService] fetchLyrics failed")
        } finally {
            client.close()
        }

        null
    }

    private fun parseLrclibJson(jsonString: String): LyricsData? = try {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val synced = root["syncedLyrics"]?.jsonPrimitive?.content?.takeIf { !it.trim().equals("null", ignoreCase = true) }
        val plain = root["plainLyrics"]?.jsonPrimitive?.content?.takeIf { !it.trim().equals("null", ignoreCase = true) }
        val isInstrumental = root["instrumental"]?.jsonPrimitive?.booleanOrNull == true

        if (!synced.isNullOrBlank()) {
            val lines = LrcParser.parse(synced)
            LyricsData(
                plainLyrics = plain,
                syncedLines = lines,
                source = "lrclib"
            )
        } else if (!plain.isNullOrBlank()) {
            LyricsData(
                plainLyrics = plain,
                syncedLines = emptyList(),
                source = "lrclib"
            )
        } else if (isInstrumental) {
            LyricsData(
                plainLyrics = "♪ Instrumental ♪",
                syncedLines = emptyList(),
                source = "lrclib"
            )
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

object PaxsenixService {
    private const val PROXY_BASE = "https://lyrics.paxsenix.org"
    private const val APPLE_SEARCH = "https://amp-api.music.apple.com/v1/catalog/us/search"
    private const val DURATION_TOLERANCE_SECONDS = 10
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private val json = Json { ignoreUnknownKeys = true }

    private var cachedToken: String? = null
    private var tokenFetchedAt: Long = 0L

    private fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[\(\[](?:feat|ft|with|prod|explicit|clean|remix|official|video|audio|lyrics|version|deluxe|bonus).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*(?:feat|ft|remix|official|video|version|single|deluxe).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun cleanArtist(artist: String): String = artist
        .replace(Regex("""\s*[\(\[](?:feat|ft|with).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
        .split(",", "&", ";", " feat. ", " ft. ", " with ").firstOrNull()?.trim() ?: artist.trim()

    private suspend fun getToken(client: HttpClient): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && (now - tokenFetchedAt) < 12 * 3600 * 1000L) {
            return cachedToken
        }

        try {
            val homeRes = client.get("https://music.apple.com/us/new") {
                header("User-Agent", USER_AGENT)
            }
            if (homeRes.status.isSuccess()) {
                val body = homeRes.bodyAsText()
                val scriptMatch = Regex("""/assets/index~[^"]+\.js""").find(body)?.value
                if (scriptMatch != null) {
                    val scriptRes = client.get("https://music.apple.com$scriptMatch") {
                        header("User-Agent", USER_AGENT)
                    }
                    if (scriptRes.status.isSuccess()) {
                        val scriptBody = scriptRes.bodyAsText()
                        val tokenMatch = Regex("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""").find(scriptBody)?.value
                        if (tokenMatch != null) {
                            cachedToken = tokenMatch
                            tokenFetchedAt = now
                            return tokenMatch
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[Paxsenix] Apple token scrape error")
        }
        return cachedToken
    }

    private fun score(attrs: kotlinx.serialization.json.JsonObject, title: String, artist: String): Double {
        val name = attrs["name"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: ""
        val targetTitle = title.trim().lowercase()
        val artistName = attrs["artistName"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: ""
        val targetArtist = artist.trim().lowercase()

        var score = 0.0
        if (name == targetTitle) {
            score += 80.0
        } else if (name.contains(targetTitle) || targetTitle.contains(name)) {
            score += 40.0
        }

        if (artistName.contains(targetArtist) || targetArtist.contains(artistName)) {
            score += 40.0
        }

        return score
    }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        durationSeconds: Int? = null
    ): LyricsData? = withContext(Dispatchers.IO) {
        val client = HttpClient(OkHttp)
        try {
            val cleanT = cleanTitle(trackName)
            val cleanA = cleanArtist(artistName)
            val query = "$cleanT $cleanA".trim()
            if (query.isEmpty()) return@withContext null

            val token = getToken(client) ?: return@withContext null
            val searchUri = "$APPLE_SEARCH?term=${URLEncoder.encode(query, "UTF-8")}&types=songs&limit=10&l=en-US"

            val searchRes = client.get(searchUri) {
                header("Authorization", "Bearer $token")
                header("Origin", "https://music.apple.com")
                header("User-Agent", USER_AGENT)
            }

            if (searchRes.status.value == 401 || searchRes.status.value == 403) {
                cachedToken = null
                return@withContext null
            }

            if (!searchRes.status.isSuccess()) return@withContext null

            val searchBody = searchRes.bodyAsText()
            val root = json.parseToJsonElement(searchBody).jsonObject
            val songsArray = root["results"]?.jsonObject?.get("songs")?.jsonObject?.get("data")?.jsonArray
                ?: return@withContext null

            var bestTrack: kotlinx.serialization.json.JsonObject? = null
            var highestScore = -1.0

            for (item in songsArray) {
                val trackObj = item.jsonObject
                val attrs = trackObj["attributes"]?.jsonObject ?: continue

                val durationMs = attrs["durationInMillis"]?.jsonPrimitive?.intOrNull?.toLong()
                    ?: attrs["durationInMillis"]?.jsonPrimitive?.content?.toLongOrNull()
                val trackSec = if (durationMs != null) (durationMs / 1000).toInt() else null

                if (durationSeconds != null && durationSeconds > 0 && trackSec != null) {
                    if (kotlin.math.abs(trackSec - durationSeconds) > DURATION_TOLERANCE_SECONDS) {
                        continue
                    }
                }

                val sc = score(attrs, cleanT, cleanA)
                if (sc > highestScore) {
                    highestScore = sc
                    bestTrack = trackObj
                }
            }

            if (bestTrack == null) return@withContext null
            val appleId = bestTrack["id"]?.jsonPrimitive?.content ?: return@withContext null

            val lyricsRes = client.get("$PROXY_BASE/apple-music/lyrics?id=$appleId") {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
            }

            if (!lyricsRes.status.isSuccess()) return@withContext null

            val lyricsBody = lyricsRes.bodyAsText()
            val lyricsJson = json.parseToJsonElement(lyricsBody).jsonObject

            val ttml = lyricsJson["ttmlContent"]?.jsonPrimitive?.content
            if (!ttml.isNullOrBlank()) {
                val parsed = GenericLyricsHelper.parseTtml(ttml, "Paxsenix")
                if (parsed != null && !parsed.isEmpty) {
                    return@withContext parsed
                }
            }

            val elrc = lyricsJson["elrcMultiPerson"]?.jsonPrimitive?.content
                ?: lyricsJson["elrc"]?.jsonPrimitive?.content
            if (!elrc.isNullOrBlank()) {
                val parsed = GenericLyricsHelper.parseGenericLyrics(elrc, "Paxsenix")
                if (parsed != null && !parsed.isEmpty) {
                    return@withContext parsed
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Timber.w(e, "[PaxsenixService] fetchLyrics failed")
            null
        } finally {
            client.close()
        }
    }
}

@Singleton
class LyricsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val subsonicRepository: SubsonicRepository,
    private val networkMonitor: com.notmugil.uta.data.NetworkMonitor
) {
    private val availabilityCache = LruCache<String, Map<LyricsProvider, String>>(100)

    suspend fun getLyrics(
        track: TrackItem,
        provider: LyricsProvider = LyricsProvider.AUTO,
        forceRefresh: Boolean = false
    ): LyricsData = withContext(Dispatchers.IO) {
        val mode = appPreferences.lyricsSourceMode.value
        if (mode == LyricsSourceMode.DISABLED) {
            return@withContext LyricsData()
        }

        val isManualOffline = appPreferences.isOfflineModeManual.value
        val isOnline = networkMonitor.isOnline.value
        val isTrueOffline = isManualOffline || !isOnline

        val effectiveProvider = if (isTrueOffline && (mode == LyricsSourceMode.SERVER_ONLY || mode == LyricsSourceMode.BOTH)) {
            LyricsProvider.SUBSONIC
        } else {
            provider
        }

        val cacheKey = "${track.id}_${effectiveProvider.name}"

        val cached = LyricsCache.get(context, cacheKey)
        if (cached != null && (!forceRefresh || isTrueOffline)) {
            return@withContext cached
        }

        if (isTrueOffline) {
            return@withContext fetchSubsonicLyrics(track)?.also {
                LyricsCache.put(context, cacheKey, it)
            } ?: LyricsData()
        }

        val result = when (effectiveProvider) {
            LyricsProvider.SUBSONIC -> {
                fetchSubsonicLyrics(track)?.also {
                    LyricsCache.put(context, cacheKey, it)
                }
            }
            LyricsProvider.LRCLIB -> {
                if (mode == LyricsSourceMode.SERVER_ONLY) {
                    null
                } else {
                    LrclibService.fetchLyrics(
                        trackName = track.title,
                        artistName = track.artist,
                        albumName = track.album,
                        durationSeconds = track.durationSeconds.toInt()
                    )?.also {
                        LyricsCache.put(context, cacheKey, it)
                    }
                }
            }
            LyricsProvider.PAXSENIX -> {
                if (mode == LyricsSourceMode.SERVER_ONLY) {
                    null
                } else {
                    PaxsenixService.fetchLyrics(
                        trackName = track.title,
                        artistName = track.artist,
                        durationSeconds = track.durationSeconds.toInt()
                    )?.also {
                        LyricsCache.put(context, cacheKey, it)
                    }
                }
            }
            LyricsProvider.AUTO -> {
                val enabledOnlineProviders = appPreferences.onlineLyricsProviders.value
                    .filter { it.enabled }
                    .map { it.provider }

                val allEnabledProviders = when (mode) {
                    LyricsSourceMode.SERVER_ONLY -> listOf(LyricsProvider.SUBSONIC)
                    LyricsSourceMode.BOTH -> enabledOnlineProviders + listOf(LyricsProvider.SUBSONIC)
                    LyricsSourceMode.DISABLED -> emptyList()
                }

                if (allEnabledProviders.isEmpty()) {
                    null
                } else if (allEnabledProviders.size == 1 && allEnabledProviders.first() == LyricsProvider.SUBSONIC) {
                    fetchSubsonicLyrics(track)?.also {
                        LyricsCache.put(context, cacheKey, it)
                    }
                } else {
                    val deferredResults = allEnabledProviders.map { prov ->
                        async {
                            try {
                                prov to getLyrics(track, provider = prov, forceRefresh = forceRefresh)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }

                    val candidateResults = deferredResults.awaitAll()
                        .filterNotNull()
                        .filter { (_, data) -> !data.isEmpty }
                        .toMap()

                    var chosen: LyricsData? = null

                    for (prov in allEnabledProviders) {
                        val data = candidateResults[prov]
                        if (data != null && data.isWordSynced) {
                            chosen = data
                            break
                        }
                    }

                    if (chosen == null) {
                        for (prov in allEnabledProviders) {
                            val data = candidateResults[prov]
                            if (data != null && data.isSynced) {
                                chosen = data
                                break
                            }
                        }
                    }

                    if (chosen == null) {
                        for (prov in allEnabledProviders) {
                            val data = candidateResults[prov]
                            if (data != null && !data.isEmpty) {
                                chosen = data
                                break
                            }
                        }
                    }

                    if (chosen != null) {
                        LyricsCache.put(context, cacheKey, chosen)
                    }
                    chosen
                }
            }
        }

        result ?: LyricsData()
    }

    private suspend fun fetchSubsonicLyrics(track: TrackItem): LyricsData? {
        try {
            val structured = subsonicRepository.getStructuredLyrics(track.id)
            val firstStructured = structured.firstOrNull()
            if (firstStructured != null && firstStructured.lines.isNotEmpty()) {
                val syncedLines = firstStructured.lines.mapNotNull { line ->
                    val start = line.start?.toLong() ?: return@mapNotNull null
                    SyncedLine(startMs = start, text = line.value)
                }
                if (syncedLines.isNotEmpty()) {
                    return LyricsData(
                        plainLyrics = null,
                        syncedLines = syncedLines,
                        source = "Server (Synced)"
                    )
                }
            }
        } catch (_: Exception) {}

        try {
            val plain = subsonicRepository.getLyrics(track.artist, track.title)
            val rawLyrics = plain?.value
            if (!rawLyrics.isNullOrBlank()) {
                val parsedLines = LrcParser.parse(rawLyrics)
                return if (parsedLines.isNotEmpty()) {
                    LyricsData(
                        plainLyrics = rawLyrics,
                        syncedLines = parsedLines,
                        source = "Server (LRC)"
                    )
                } else {
                    LyricsData(
                        plainLyrics = rawLyrics,
                        syncedLines = emptyList(),
                        source = "Server (Plain)"
                    )
                }
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun checkAvailableProviders(
        track: TrackItem
    ): Map<LyricsProvider, String> = withContext(Dispatchers.IO) {
        val cached = availabilityCache[track.id]
        if (cached != null) {
            return@withContext cached
        }

        val mode = appPreferences.lyricsSourceMode.value
        if (mode == LyricsSourceMode.DISABLED) {
            return@withContext emptyMap()
        }

        val isManualOffline = appPreferences.isOfflineModeManual.value
        val isOnline = networkMonitor.isOnline.value
        val isTrueOffline = isManualOffline || !isOnline

        if (isTrueOffline) {
            val data = getLyrics(track, provider = LyricsProvider.SUBSONIC)
            val tag = data.syncTypeTag
            val map = if (tag != null) mapOf(LyricsProvider.SUBSONIC to tag) else emptyMap()
            availabilityCache.put(track.id, map)
            return@withContext map
        }

        val enabledOnline = appPreferences.onlineLyricsProviders.value
            .filter { it.enabled }
            .map { it.provider }
        val providers = when (mode) {
            LyricsSourceMode.SERVER_ONLY -> listOf(LyricsProvider.SUBSONIC)
            LyricsSourceMode.BOTH -> listOf(LyricsProvider.SUBSONIC) + enabledOnline
            LyricsSourceMode.DISABLED -> emptyList()
        }

        val availableMap = mutableMapOf<LyricsProvider, String>()
        val deferreds = providers.map { provider ->
            async {
                val data = getLyrics(track, provider = provider)
                val tag = data.syncTypeTag
                if (tag != null) {
                    provider to tag
                } else {
                    null
                }
            }
        }

        deferreds.awaitAll().filterNotNull().forEach { (provider, tag) ->
            availableMap[provider] = tag
        }

        if (availableMap.isNotEmpty()) {
            val autoData = getLyrics(track, provider = LyricsProvider.AUTO)
            val autoTag = autoData.syncTypeTag ?: availableMap.values.firstOrNull() ?: "Synced"
            availableMap[LyricsProvider.AUTO] = autoTag
        }

        availabilityCache.put(track.id, availableMap)
        availableMap
    }

    companion object {
        private var instance: LyricsRepository? = null

        fun init(repo: LyricsRepository) {
            instance = repo
        }

        suspend fun getLyrics(
            context: Context,
            track: TrackItem,
            provider: LyricsProvider = LyricsProvider.AUTO,
            forceRefresh: Boolean = false
        ): LyricsData {
            return instance?.getLyrics(track, provider, forceRefresh) ?: LyricsData()
        }

        suspend fun checkAvailableProviders(
            context: Context,
            track: TrackItem
        ): Map<LyricsProvider, String> {
            return instance?.checkAvailableProviders(track) ?: emptyMap()
        }
    }
}
