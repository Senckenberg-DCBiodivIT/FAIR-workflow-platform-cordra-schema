package org.example

import com.google.gson.JsonArray
import com.google.gson.JsonObject

fun JsonObject.applyTypeAndContext(type: String, context: String): JsonObject {
    addProperty("@type", type)
    addProperty("@context", context)
    return this
}
