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

        if (co.payloads.size != 0) {
            throw CordraException.fromStatusCode(400, "FileObject must not have exactly one payload.")
        }

        val payload = co.payloads.first()
        json.addProperty("name", payload.filename)  // overrides given name property
        json.addProperty("contentSize", payload.size)
        json.addProperty("encodingFormat", payload.mediaType)

        return co
    }

}
