package de.senckenberg.cwr

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun testValidateIdentifier() {
        val testObject = JsonObject()
        testObject.addProperty("identifier", "http://example.com")
        assertTrue { Validator.validateIdentifier(testObject) }
    }


    @Test
    fun testIsUri() {
        assertTrue { Validator.isUri("http://example.com") }
        assertFalse { Validator.isUri("hello world") }
    }
}