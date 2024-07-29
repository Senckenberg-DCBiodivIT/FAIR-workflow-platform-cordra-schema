package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.mockk.mockk
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonTypeTest {

    @Test
    fun testBeforeSchemaValidation() {
        val testPersonJson = JsonParser().parse("""
                {
                    "name": "Daniel Bauer",
                    "identifier": "http://orcid.org/0000-0002-1825-0097",
                    "affiliation": ["cwr/123"]
                }
            """.trimIndent()
        )

        val cordraObject = mockk<CordraObject>()
        cordraObject.content = testPersonJson
        val hookContext = mockk<HooksContext>()

        val result = PersonType().beforeSchemaValidation(cordraObject, hookContext)

        val resultPerson = result.content.asJsonObject

        assertTrue { resultPerson.get("@type").asJsonArray.contains(JsonPrimitive("Person")) }
        val context = resultPerson.get("@context").asJsonObject
        assertEquals("https://schema.org/", context.get("@vocab").asString)
        assertEquals("@id", context.get("affiliation").asJsonObject.get("@type").asString)

        assertEquals(resultPerson.get("identifier").asString , "http://orcid.org/0000-0002-1825-0097")


    }

    @Test
    fun testBeforeSchemaValidationInvalidIdentifier() {
        val testPersonJson = JsonParser().parse(
            """
                {
                    "name": "Daniel Bauer",
                    "identifier": "invalid"
                }
            """.trimIndent()
        )

        val cordraObject = mockk<CordraObject>()
        cordraObject.content = testPersonJson
        val hookContext = mockk<HooksContext>()
        assertThrows<CordraException> {
            PersonType().beforeSchemaValidation(cordraObject, hookContext)
        }
    }
}