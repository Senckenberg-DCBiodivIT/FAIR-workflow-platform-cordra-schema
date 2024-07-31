package de.senckenberg.cwr.types

import com.google.gson.JsonParser
import de.senckenberg.cwr.assertContextExists
import de.senckenberg.cwr.assertTypeCoerctionExists
import io.mockk.mockk
import net.cnri.cordra.api.CordraObject
import net.cnri.cordra.api.Payload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WorkflowTypeTest {

    @Test
    fun testBeforeSchemaValidation() {
        val jsonObject = JsonParser().parse("""
            {
                "name": "this is a test workflow",
                "programmingLanguage": "https://www.commonwl.org/spec/CommonWorkflowLanguage/1.0"
            }
        """.trimIndent())
        val cordraObject = CordraObject("Workflow", jsonObject)
        val payload = Payload().apply {
            filename = "test.txt"
            name = "path/to/test/file.txt"
            size = 10
            mediaType = "text/plain"
        }
        cordraObject.content = jsonObject
        cordraObject.payloads = listOf(payload)

        val resultObject = WorkflowType().beforeSchemaValidation(cordraObject, mockk())
        assertEquals(resultObject.content.asJsonObject.get("@type").asJsonArray.size(), 3)
        assertContextExists(resultObject.content.asJsonObject, "https://schema.org/")
        assertTypeCoerctionExists(resultObject.content.asJsonObject, "partOf")
        assertTrue { resultObject.content.asJsonObject.get("@context").asJsonArray.any { it.isJsonObject && it.asJsonObject.has("ComputationalWorkflow") } }
        assertEquals(resultObject.content.asJsonObject.get("name").asString, "this is a test workflow")
        assertEquals(resultObject.content.asJsonObject.get("contentSize").asInt, 10)
        assertEquals(resultObject.content.asJsonObject.get("encodingFormat").asString, "text/plain")
        assertEquals(resultObject.content.asJsonObject.get("contentUrl").asString, "path/to/test/file.txt")

    }

}