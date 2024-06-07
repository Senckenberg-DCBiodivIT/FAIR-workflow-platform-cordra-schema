package de.senckenberg.cwr

import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

/**
 * An abstract class that adds jsonld `@type` and `@context` during schema validation
 */
abstract class JsonLdType(private val type: String, private val context: String = "https://schema.org") : CordraTypeInterface {
    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val content = co.content.asJsonObject
        applyTypeAndContext(content, type, this.context )

        return co
    }
}
