package de.senckenberg.cwr.types

import com.google.gson.JsonParser
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
        assertEquals(result.content.asJsonObject.get("@type").asString, "CreateAction")
        assertEquals(result.content.asJsonObject.get("@context").asString, "https://schema.org")
        assertTrue { result.content.asJsonObject.get("result").isJsonArray }
        assertTrue { result.content.asJsonObject.get("instrument").isJsonObject }
    }
}