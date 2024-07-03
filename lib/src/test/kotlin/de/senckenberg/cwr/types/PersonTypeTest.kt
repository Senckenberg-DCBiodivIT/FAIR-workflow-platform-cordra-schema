package de.senckenberg.cwr.types

import com.google.gson.JsonParser
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
                    "affiliation": {
                        "name": "Senckenberg"
                    }
                }
            """.trimIndent()
        )

        val cordraObject = mockk<CordraObject>()
        cordraObject.content = testPersonJson
        val hookContext = mockk<HooksContext>()

        val result = PersonType().beforeSchemaValidation(cordraObject, hookContext)

        val resultPerson = result.content.asJsonObject
        assertEquals(resultPerson.get("@context").asString , "https://schema.org")
        assertEquals(resultPerson.get("@type").asString , "Person")
        assertEquals(resultPerson.get("identifier").asString , "http://orcid.org/0000-0002-1825-0097")
        assertTrue { resultPerson.has("affiliation") }
        val resultAffiliation = resultPerson.get("affiliation").asJsonObject
        assertEquals(resultAffiliation.get("@context").asString , "https://schema.org")
        assertEquals(resultAffiliation.get("@type").asString , "Organization")
        assertEquals(resultAffiliation.get("name").asString , "Senckenberg")
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