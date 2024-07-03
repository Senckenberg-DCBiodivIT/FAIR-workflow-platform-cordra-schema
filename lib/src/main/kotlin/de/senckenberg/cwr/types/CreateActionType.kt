package de.senckenberg.cwr.types

import de.senckenberg.cwr.Validator
import de.senckenberg.cwr.applyTypeAndContext
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("CreateAction")
class CreateActionType: JsonLdType("CreateAction") {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        super.beforeSchemaValidation(co, context)
        val json = co.content.asJsonObject
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