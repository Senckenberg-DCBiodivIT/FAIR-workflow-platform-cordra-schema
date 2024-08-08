package de.senckenberg.cwr.types

import com.google.gson.JsonObject
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject

@CordraType("PropertyValue")
class PropertyValueType: JsonLdType(listOf("PropertyValue"))