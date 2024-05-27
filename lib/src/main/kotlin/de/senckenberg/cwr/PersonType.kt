package de.senckenberg.cwr

import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("Person")
class PersonType: CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val person = co.content.asJsonObject

        if (!Validator.validateIdentifier(person)) {
            print("verified identifier as uri: {${person.get("identifier").asString}}")
            throw CordraException.fromStatusCode(400, "Identifier is not a valid URI identifier.")
        }

        if (!person.has("affiliation")) {
            val affiliation = person.get("affiliation")
            if (affiliation.isJsonObject && affiliation.asJsonObject.has("identifier") && !Validator.validateIdentifier(affiliation.asJsonObject)) {
                throw CordraException.fromStatusCode(400, "Affiliation identifier is not a valid URI identifier.")
            }
        }

        return co
    }

}
