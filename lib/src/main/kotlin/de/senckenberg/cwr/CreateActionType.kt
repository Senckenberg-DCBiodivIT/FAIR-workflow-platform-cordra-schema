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
            if (!Validator.validateIdentifier(json.get("instrument").asJsonObject)) {
                throw CordraException.fromStatusCode(400, "Instrument identifier is not a valid URI identifier.")
            }
            applyTypeAndContext(json.getAsJsonObject("instrument"), "SoftwareApplication", "https://schema.org")
        }

        return co
    }
}