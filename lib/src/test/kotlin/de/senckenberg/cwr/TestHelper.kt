package de.senckenberg.cwr

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestHelper {

    @Test
    fun testAddTypeAndContext() {
        val json = JsonObject()
        applyTypeAndContext(json, "Person", "https://schema.org")
        assertEquals(json.get("@type").asString, "Person")
        assertEquals(json.get("@context").asString, "https://schema.org")
    }
}