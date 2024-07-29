package de.senckenberg.cwr

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TestHelper {

    @Test
    fun testAddTypeAndContext() {
        val json = JsonObject()
        applyTypeAndContext(json, listOf("Person"), "https://schema.org/")
        assertEquals(json.get("@type").asJsonArray.first().asString, "Person")

        val context = json.get("@context").asJsonObject
        assertTrue(context.keySet().size == 1)
        assertEquals("https://schema.org/", context.get("@vocab").asString)
    }

    @Test
    fun testAddContextWithTypeCoercion() {
        val json = JsonObject()
        applyTypeAndContext(json, listOf("Person"), "https://schema.org/", listOf("affiliation"))
        assertEquals(json.get("@type").asJsonArray.first().asString, "Person")

        val context = json.get("@context").asJsonObject
        assertTrue(context.keySet().size == 2)
        assertEquals("https://schema.org/", context.get("@vocab").asString)
        assertTrue { context.get("affiliation").asJsonObject.get("@type").asString.equals("@id") }

    }
}