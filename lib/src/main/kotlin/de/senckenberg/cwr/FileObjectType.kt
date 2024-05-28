package de.senckenberg.cwr

import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("FileObject")
class FileObjectType: CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val json = co.content.asJsonObject
        applyTypeAndContext(json, "MediaObject", "https://schema.org")

        if (co.payloads?.size != 1) {
            throw CordraException.fromStatusCode(400, "FileObject must have exactly one payload but has ${co.payloads?.size ?: "\"null\""}.")
        }

        val payload = co.payloads.first()
        json.addProperty("name", payload.filename)  // overrides given name property
        json.addProperty("contentSize", payload.size)
        json.addProperty("encodingFormat", payload.mediaType)

        return co
    }

}
