package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateActionTypeTest {

    @Test
    fun testBeforeSchemaValidation() {
        val obj = CordraObject()
        obj.content = JsonParser.parseString(
            """
            {
                "@id": "#action1",
                "agent": "cwr/123",
                "instrument": {
                
                },
                "result": ["cwr/456", "cwr/789"]
            }
            """.trimIndent()
        )
        val result = CreateActionType().beforeSchemaValidation(obj, mockk())

        val context = result.content.asJsonObject.get("@context").asJsonObject
        assertEquals("https://schema.org/", context.get("@vocab").asString)
        assertEquals(result.content.asJsonObject.get("@type").asString, "CreateAction")
        for (coercedType in listOf("agent", "result", "object", "instrument")) {
            assertEquals("@id", context.get(coercedType).asJsonObject.get("@type").asString)
        }

        assertTrue { result.content.asJsonObject.get("result").isJsonArray }
        assertTrue { result.content.asJsonObject.get("instrument").isJsonObject }
    }
}