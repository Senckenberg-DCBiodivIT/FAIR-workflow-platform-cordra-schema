package de.senckenberg.cwr

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.api.CordraObject

fun applyTypeAndContext(json: JsonObject, type: String, context: String) {
    json.addProperty("@type", type)
    json.addProperty("@context", context)
}

fun deserializeIntoCordraObject(json: JsonObject, type: String): CordraObject {
    val cordra = CordraHooksSupportProvider.get().cordraClient
    return cordra.create(type, json)
}

fun propertyToReferences(json: JsonObject, property: String, type: String, asArray: Boolean = false) {
    if(!json.has(property)) {
        return
    }

    val propertyValue = json.get(property)
    val cordraObjects = if (propertyValue.isJsonArray) {
        propertyValue.asJsonArray.map { deserializeIntoCordraObject(it.asJsonObject, type) }
    } else {
        listOf(deserializeIntoCordraObject(propertyValue.asJsonObject, type))
    }

    if (asArray) {
        val refsAsJsonArray = cordraObjects.fold(JsonArray()) { array, value ->
            array.add(value.id)
            array
        }
        json.add(property, refsAsJsonArray)
    } else {
        assert(cordraObjects.size == 1) { "Failed to convert $property to $type. Single object expected, but multiple found." }
        json.addProperty(property, cordraObjects.first().id)
    }
}
