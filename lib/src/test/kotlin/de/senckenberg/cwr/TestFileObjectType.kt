package de.senckenberg.cwr

import com.google.gson.JsonParser
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import net.cnri.cordra.api.Payload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestFileObjectType {

    @Test
    fun testFileObjectTypeValidation() {
        val jsonObject = JsonParser().parse("""
            {
                "name": "unused.txt"
            }
        """.trimIndent())
        val cordraObject = CordraObject("FileObject", jsonObject)
        val payload = Payload().apply {
            filename = "test.txt"
            size = 10
            mediaType = "text/plain"
        }
        cordraObject.content = jsonObject
        cordraObject.payloads = listOf(payload)

        val resultObject = FileObjectType().beforeSchemaValidation(cordraObject, mockk())
        assertEquals(resultObject.content.asJsonObject.get("@type").asString, "MediaObject")
        assertEquals(resultObject.content.asJsonObject.get("@context").asString, "https://schema.org")
        assertEquals(resultObject.content.asJsonObject.get("name").asString, "test.txt")
        assertEquals(resultObject.content.asJsonObject.get("contentSize").asInt, 10)
        assertEquals(resultObject.content.asJsonObject.get("encodingFormat").asString, "text/plain")

    }

}