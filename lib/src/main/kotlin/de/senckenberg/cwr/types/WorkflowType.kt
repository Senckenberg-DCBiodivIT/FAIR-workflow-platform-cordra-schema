package de.senckenberg.cwr.types

import net.cnri.cordra.CordraType

@CordraType("Workflow")
class WorkflowType: FileObjectType(additionalTypes = listOf("ComputationalWorkflow", "SoftwareSourceCode"), additionalCoercedTypes = listOf("programmingLanguage"))