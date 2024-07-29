package de.senckenberg.cwr

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Apply @context and @type to a json object.
 * This sets the type as array of all provided types.
 * The context is set as base vocabulary (@vocab).
 * Typed Values are set as {"@type": "@id"} to the context. This allows to simplify their representation in cordra:
 * I.e. by setting {"@context": {"license": {"@type": "@id"}}} in addition to a base @vocab,
 * we can define {"license": "some_id"} instead of {"license": {"@id": "some_id"}}
 */
fun applyTypeAndContext(json: JsonObject, types: List<String>, context: String = "https://schema.org/", coercedTypes: List<String> = emptyList()) {
    // Make sure @context is a json object
    if (!json.has("@context")) {
        json.add("@context", JsonObject())
    } else if (!json.get("@context").isJsonObject) {
        json.add("@context", JsonObject().apply { addProperty("@vocab", json.get("@context").asString) })
    }

    // add vocab
    val jsonContext = json.get("@context").asJsonObject
    jsonContext.addProperty("@vocab", context)

    // add coerced types to context
    for (name in coercedTypes) {
        jsonContext.add(name, JsonObject().apply { addProperty("@type", "@id") })
    }

    // make sure @type is an array
    if (!json.has("@type") || !json.get("@type").isJsonObject) {
        json.add("@type", JsonArray())
    } else if (json.get("@type").isJsonPrimitive) {
        json.add("@type", JsonArray().apply { add(json.get("@type").asString) })
    }

    // add schema type if not present in input
    json.get("@type").asJsonArray.apply {
        types.forEach {
            if (!contains(JsonPrimitive(it))) {
                add(it)
            }
        }
    }
}