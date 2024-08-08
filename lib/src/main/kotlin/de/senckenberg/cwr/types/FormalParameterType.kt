package de.senckenberg.cwr.types

import com.google.gson.JsonObject
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

@CordraType("FormalParameter")
class FormalParameterType: JsonLdType(listOf("FormalParameter")) {
    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        super.beforeSchemaValidation(co, context)
        val jsonLdContext = co.content.asJsonObject.get("@context").asJsonArray
        jsonLdContext.add(JsonObject().apply { addProperty("FormalParameter", "https://bioschemas.org/FormalParameter") })
        return co
    }
}