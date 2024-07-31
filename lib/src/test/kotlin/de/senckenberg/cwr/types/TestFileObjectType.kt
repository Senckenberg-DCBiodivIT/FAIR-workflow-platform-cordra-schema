package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import de.senckenberg.cwr.assertContextExists
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
                "name": "this is a test file",
                "description": "test desc"
            }
        """.trimIndent())
        val cordraObject = CordraObject("FileObject", jsonObject)
        val payload = Payload().apply {
            filename = "test.txt"
            name = "path/to/test/file.txt"
            size = 10
            mediaType = "text/plain"
        }
        cordraObject.content = jsonObject
        cordraObject.payloads = listOf(payload)

        val resultObject = FileObjectType().beforeSchemaValidation(cordraObject, mockk())
        assertEquals(resultObject.content.asJsonObject.get("@type").asString, "MediaObject")
        assertContextExists( resultObject.content.asJsonObject, "https://schema.org/")
        assertEquals(resultObject.content.asJsonObject.get("name").asString, "this is a test file")
        assertEquals(resultObject.content.asJsonObject.get("description").asString, "test desc")
        assertEquals(resultObject.content.asJsonObject.get("contentSize").asInt, 10)
        assertEquals(resultObject.content.asJsonObject.get("encodingFormat").asString, "text/plain")
        assertEquals(resultObject.content.asJsonObject.get("contentUrl").asString, "path/to/test/file.txt")

    }

}