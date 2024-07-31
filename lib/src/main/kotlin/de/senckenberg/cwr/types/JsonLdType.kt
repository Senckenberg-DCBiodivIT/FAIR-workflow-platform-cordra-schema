package de.senckenberg.cwr.types

import de.senckenberg.cwr.applyTypeAndContext
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

/**
 * An abstract class that adds jsonld `@type` and `@context` during schema validation
 */
abstract class JsonLdType(private val types: List<String>, private val context: String = "https://schema.org/", private val coercedTypes: List<String> = emptyList()) : CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val content = co.content.asJsonObject
        applyTypeAndContext(content, types, this.context, coercedTypes = coercedTypes)

        return co
    }
}
