package de.senckenberg.cwr.types

import de.senckenberg.cwr.Validator
import de.senckenberg.cwr.applyTypeAndContext
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("Person")
class PersonType: JsonLdType("Person") {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        super.beforeSchemaValidation(co, context)
        val person = co.content.asJsonObject

        if (!Validator.validateIdentifier(person)) {
            print("verified identifier as uri: {${person.get("identifier").asString}}")
            throw CordraException.fromStatusCode(400, "Identifier is not a valid URI identifier.")
        }

        if (person.has("affiliation")) {
            val affiliation = person.getAsJsonObject("affiliation")
            if (affiliation != null) {
                applyTypeAndContext(affiliation, "Organization", "https://schema.org")
                if (affiliation.has("identifier") && !Validator.validateIdentifier(affiliation.asJsonObject)) {
                    throw CordraException.fromStatusCode(400, "Affiliation identifier is not a valid URI identifier.")
                }
            }

        }

        return co
    }

}
