package de.senckenberg.cwr

import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

@CordraType("Person")
class PersonType: CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val person = co.content.asJsonObject
        applyTypeAndContext(person, "Person", "https://schema.org")
        propertyToReferences(person, "affiliation", "Organization", asArray = false)

        return co
    }

}
