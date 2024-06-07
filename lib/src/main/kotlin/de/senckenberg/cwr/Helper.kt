package de.senckenberg.cwr

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.api.CordraObject

fun applyTypeAndContext(json: JsonObject, type: String, context: String) {
    json.addProperty("@type", type)
    json.addProperty("@context", context)
}