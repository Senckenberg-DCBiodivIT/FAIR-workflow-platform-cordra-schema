package de.senckenberg.cwr.types

import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("FileObject")
open class FileObjectType(additionalTypes: List<String> = emptyList(), additionalCoercedTypes: List<String> = emptyList()):
    // TODO make types an array
    JsonLdType(listOf("MediaObject") + additionalTypes, coercedTypes = listOf("partOf", "resultOf") + additionalCoercedTypes) {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        super.beforeSchemaValidation(co, context)
        val json = co.content.asJsonObject

        if (co.payloads?.size != 1) {
            throw CordraException.fromStatusCode(400, "FileObject must have exactly one payload but has ${co.payloads?.size ?: "\"null\""}.")
        }

        val payload = co.payloads.first()
        json.addProperty("contentSize", payload.size)

        // contentUrl must match payload name. this is used to access the file from the frontend
        if (json.has("contentUrl")) {
            payload.name = json.get("contentUrl").asString
        } else {
            json.addProperty("contentUrl", payload.name)
        }

        if (!json.has("encodingFormat")) {
            json.addProperty("encodingFormat", payload.mediaType)
        }

        return co
    }

}
