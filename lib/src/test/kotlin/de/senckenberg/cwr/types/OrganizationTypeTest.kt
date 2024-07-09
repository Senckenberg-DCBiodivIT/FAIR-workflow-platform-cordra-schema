package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(result.content.asJsonObject.get("@type").asString, "Organization")
        assertEquals(result.content.asJsonObject.get("@context").asString, "https://schema.org")
        assertEquals(result.content.asJsonObject.get("name").asString, "hello world organization")
    }

}