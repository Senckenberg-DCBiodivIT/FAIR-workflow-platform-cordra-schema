package de.senckenberg.cwr

import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

@CordraType("Person")
class PersonType: CordraTypeInterface {

    val cordra = CordraHooksSupportProvider.get().cordraClient

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val person = co.content.asJsonObject
        person.applyTypeAndContext("Person", "https://schema.org")

        if (person.has("affiliation")) {
            // create affiliation object
            val affiliation = cordra.create("Organization", person.get("affiliation"))
            person.addProperty("affiliation", affiliation.id)
        }

        return co
    }

}
