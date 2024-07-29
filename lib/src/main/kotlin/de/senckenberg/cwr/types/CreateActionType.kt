package de.senckenberg.cwr.types

import net.cnri.cordra.CordraType

@CordraType("CreateAction")
class CreateActionType: JsonLdType("CreateAction", coercedTypes = listOf("agent", "result", "object", "instrument"))