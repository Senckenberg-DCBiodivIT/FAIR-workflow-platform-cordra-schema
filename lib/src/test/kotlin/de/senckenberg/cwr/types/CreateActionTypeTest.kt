package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import de.senckenberg.cwr.assertContextExists
import de.senckenberg.cwr.assertTypeCoerctionExists
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
        val result = CreateActionType().beforeSchemaValidation(obj, mockk()).content.asJsonObject

        assertContextExists(result, "https://schema.org/")
        for (coercedType in listOf("agent", "result", "object", "instrument")) {
            assertTypeCoerctionExists(result, coercedType)
        }
        assertEquals(result.get("@type").asString, "CreateAction")


        assertTrue { result.get("result").isJsonArray }
        assertTrue { result.get("instrument").isJsonObject }
    }
}