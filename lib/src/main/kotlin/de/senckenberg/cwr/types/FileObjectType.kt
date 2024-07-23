package de.senckenberg.cwr.types

import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("FileObject")
class FileObjectType: JsonLdType("MediaObject") {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        super.beforeSchemaValidation(co, context)
        val json = co.content.asJsonObject

        if (co.payloads?.size != 1) {
            throw CordraException.fromStatusCode(400, "FileObject must have exactly one payload but has ${co.payloads?.size ?: "\"null\""}.")
        }

        val payload = co.payloads.first()
        json.addProperty("name", payload.filename)  // overrides given name property
        json.addProperty("contentSize", payload.size)

        if (!json.has("encodingFormat")) {
            json.addProperty("encodingFormat", payload.mediaType)
        }

        return co
    }

}
