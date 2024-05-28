package de.senckenberg.cwr

import com.google.gson.JsonObject

object Validator {
    private val IDENTIFIER_VALIDATION_REGEX = "^([a-z0-9+.-]+):(?://(?:((?:[a-z0-9-._~!\$&'()*+,;=:]|%[0-9A-F]{2})*)@)?((?:[a-z0-9-._~!\$&'()*+,;=]|%[0-9A-F]{2})*)(?::(\\d*))?(/(?:[a-z0-9-._~!\$&'()*+,;=:@/]|%[0-9A-F]{2})*)?|(/?(?:[a-z0-9-._~!\$&'()*+,;=:@]|%[0-9A-F]{2})+(?:[a-z0-9-._~!\$&'()*+,;=:@/]|%[0-9A-F]{2})*)?)(?:\\?((?:[a-z0-9-._~!\$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?(?:#((?:[a-z0-9-._~!\$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?\$".toRegex()

    /**
     * Validate if the key represents a correctly formatted uri identifier
     * returns true if the identifier is valid or not present.
      */
    fun validateIdentifier(obj: JsonObject, key: String = "identifier"): Boolean {
        if (!obj.has(key)) {
            return true
        }

        val property = obj.get(key).asString
        return isUri(property)
    }

    fun isUri(uri: String) = IDENTIFIER_VALIDATION_REGEX.matches(uri)
}