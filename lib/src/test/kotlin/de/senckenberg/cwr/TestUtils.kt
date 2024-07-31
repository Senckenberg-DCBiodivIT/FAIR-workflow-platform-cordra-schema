package de.senckenberg.cwr

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertTrue

fun assertContextExists(json: JsonObject, context: String) {
    assertTrue(json.get("@context").asJsonArray.any { it.asString == context })
}

fun assertTypeCoerctionExists(json: JsonObject, type: String) {
    val context = json.get("@context").asJsonArray
    assertTrue(context.any {
        println(it)
        it.isJsonObject && it.asJsonObject.has(type) && it.asJsonObject.get(type).asJsonObject.get("@type").asString == "@id"
    }, "Missing type coerction: $type")
}