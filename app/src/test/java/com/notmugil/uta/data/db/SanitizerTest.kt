package com.notmugil.uta.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizerTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test
    fun `sanitizeSubsonicJson removes empty or incomplete releaseDate objects lacking year`() {
        val rawJson = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "album": {
                        "id": "alb1",
                        "name": "Album Missing Year",
                        "releaseDate": {
                            "month": 5
                        },
                        "originalReleaseDate": {}
                    }
                }
            }
        """.trimIndent()

        val sanitized = sanitizeSubsonicJson(rawJson, json)
        val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]!!.jsonObject
        val album = root["album"]!!.jsonObject

        assertFalse("releaseDate should be removed when year is missing", album.containsKey("releaseDate"))
        assertFalse("originalReleaseDate should be removed when empty", album.containsKey("originalReleaseDate"))
    }

    @Test
    fun `sanitizeSubsonicJson normalizes releaseDate when year is valid`() {
        val rawJson = """
            {
                "subsonic-response": {
                    "status": "ok",
                    "album": {
                        "id": "alb2",
                        "name": "Valid Year Album",
                        "releaseDate": {
                            "year": 2022
                        }
                    }
                }
            }
        """.trimIndent()

        val sanitized = sanitizeSubsonicJson(rawJson, json)
        val root = json.parseToJsonElement(sanitized).jsonObject["subsonic-response"]!!.jsonObject
        val album = root["album"]!!.jsonObject

        assertTrue("releaseDate should be retained", album.containsKey("releaseDate"))
        val dateObj = album["releaseDate"]!!.jsonObject
        assertEquals("2022", dateObj["year"].toString())
        assertEquals("1", dateObj["month"].toString())
        assertEquals("1", dateObj["day"].toString())
    }
}
