package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SoftwareApplicationTypeTest {

    @Test
    fun testBeforeSchemaValidation() {
        val obj = CordraObject()
        obj.content = JsonParser.parseString(
            """
            {
                "@id": "https://github.com/dnlbauer/somerepository",
                "name": "some repository"
            }
            """.trimIndent()
        )
        val result = SoftwareApplicationType().beforeSchemaValidation(obj, mockk())
        val context = result.content.asJsonObject.get("@context").asJsonObject
        assertEquals("https://schema.org/", context.get("@vocab").asString)
        assertEquals(result.content.asJsonObject.get("@type").asString, "SoftwareApplication")
        assertEquals(result.content.asJsonObject.get("name").asString, "some repository")
    }

}