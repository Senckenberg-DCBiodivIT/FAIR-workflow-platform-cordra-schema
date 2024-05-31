package de.senckenberg.cwr

import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("CreateAction")
class CreateActionType: CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val json = co.content.asJsonObject
        applyTypeAndContext(json, "CreateAction", "https://schema.org")
        if (json.has("instrument")) {
            val instrument = json.getAsJsonObject("instrument")
            if (!Validator.validateIdentifier(instrument)) {
                throw CordraException.fromStatusCode(400, "Instrument identifier is not a valid URI identifier.")
            }
            applyTypeAndContext(instrument, "SoftwareApplication", "https://schema.org")
        }

        return co
    }
}