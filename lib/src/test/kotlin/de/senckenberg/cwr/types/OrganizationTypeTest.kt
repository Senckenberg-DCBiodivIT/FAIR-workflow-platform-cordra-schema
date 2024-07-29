package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OrganizationTypeTest {

    @Test
    fun testBeforeSchemaValidation() {
        val obj = CordraObject()
        obj.content = JsonParser.parseString(
            """
            {
                "@id": "https://ror.org/123",
                "name": "hello world organization"
            }
            """.trimIndent()
        )
        val result = OrganizationType().beforeSchemaValidation(obj, mockk())
        val context = result.content.asJsonObject.get("@context").asJsonObject
        assertEquals("https://schema.org/", context.get("@vocab").asString)
        assertEquals(result.content.asJsonObject.get("@type").asString, "Organization")
        assertEquals(result.content.asJsonObject.get("name").asString, "hello world organization")
    }

}