package de.senckenberg.cwr.types

import de.senckenberg.cwr.Validator
import de.senckenberg.cwr.applyTypeAndContext
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject

@CordraType("CreateAction")
class CreateActionType: JsonLdType("CreateAction")