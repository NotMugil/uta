package com.notmugil.uta.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerPlayQueueTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parse server play queue response with multiple entries`() {
        val rawResponse = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "playQueue": {
                        "current": "t2",
                        "currentIndex": 1,
                        "position": 35000,
                        "changed": "2026-09-24T12:00:00Z",
                        "changedBy": "Navidrome Web",
                        "entry": [
                            {"id": "t1", "title": "Song 1"},
                            {"id": "t2", "title": "Song 2"},
                            {"id": "t3", "title": "Song 3"}
                        ]
                    }
                }
            }
        """.trimIndent()

        val root = json.parseToJsonElement(rawResponse).jsonObject["subsonic-response"]!!.jsonObject
        val pq = root["playQueue"]!!.jsonObject
        val current = pq["current"]?.jsonPrimitive?.content
        val currentIndex = pq["currentIndex"]?.jsonPrimitive?.intOrNull
        val position = pq["position"]?.jsonPrimitive?.longOrNull ?: 0L
        val changed = pq["changed"]?.jsonPrimitive?.content
        val changedBy = pq["changedBy"]?.jsonPrimitive?.content
        val entries = pq["entry"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) it else kotlinx.serialization.json.JsonArray(listOf(it))
        } ?: kotlinx.serialization.json.JsonArray(emptyList())

        val trackIds = entries.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }

        val serverPlayQueue = ServerPlayQueue(
            currentTrackId = current,
            currentIndex = currentIndex,
            positionMs = position,
            trackIds = trackIds,
            changed = changed,
            changedBy = changedBy
        )

        assertNotNull(serverPlayQueue)
        assertEquals("t2", serverPlayQueue.currentTrackId)
        assertEquals(1, serverPlayQueue.currentIndex)
        assertEquals(35000L, serverPlayQueue.positionMs)
        assertEquals(3, serverPlayQueue.trackIds.size)
        assertEquals(listOf("t1", "t2", "t3"), serverPlayQueue.trackIds)
        assertEquals("Navidrome Web", serverPlayQueue.changedBy)
    }

    @Test
    fun `parse empty server play queue response`() {
        val rawResponse = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "playQueue": {
                        "position": 0
                    }
                }
            }
        """.trimIndent()

        val root = json.parseToJsonElement(rawResponse).jsonObject["subsonic-response"]!!.jsonObject
        val pq = root["playQueue"]!!.jsonObject
        val current = pq["current"]?.jsonPrimitive?.content
        val currentIndex = pq["currentIndex"]?.jsonPrimitive?.intOrNull
        val position = pq["position"]?.jsonPrimitive?.longOrNull ?: 0L
        val entries = pq["entry"]?.let {
            if (it is kotlinx.serialization.json.JsonArray) it else kotlinx.serialization.json.JsonArray(listOf(it))
        } ?: kotlinx.serialization.json.JsonArray(emptyList())

        val trackIds = entries.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }

        val serverPlayQueue = ServerPlayQueue(
            currentTrackId = current,
            currentIndex = currentIndex,
            positionMs = position,
            trackIds = trackIds
        )

        assertEquals(0L, serverPlayQueue.positionMs)
        assertEquals(emptyList<String>(), serverPlayQueue.trackIds)
    }
}
