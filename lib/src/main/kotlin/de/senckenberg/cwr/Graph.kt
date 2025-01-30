package de.senckenberg.cwr

import com.google.gson.JsonObject
import de.senckenberg.cwr.types.DatasetType.Companion.logger
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import kotlin.time.measureTime

/**
 * Resolves all linked objects of this element and returns a map of their IDs to the object
 *
 */
fun resolveCordraObjectsRecursively(cordraObject: CordraObject, cordraClient: CordraClient, nested: Boolean = true, workflowOnly: Boolean = false): Map<String, CordraObject> {
    var objects: Map<String, CordraObject>
    val time = measureTime {
        objects = resolveObjectIdsRecursively(
            listOf(cordraObject.id),
            mutableMapOf(cordraObject.id to cordraObject),
            cordraClient,
            nested,
            workflowOnly
        )
    }
    logger.info("Resolved object graph ${cordraObject.id}: ${objects.size} objects, ${time.inWholeMilliseconds} ms")

    return objects
}

private fun resolveObjectIdsRecursively(
    idsToResolve: List<String>,
    resolvedObjects: MutableMap<String, CordraObject>,
    cordraClient: CordraClient,
    nested: Boolean,
    workflowOnly: Boolean = false,
    recursionDepth: Int = 0,
    maxRecursion: Int = 5,
): Map<String, CordraObject> {

    // Recursion limit
    if (recursionDepth > maxRecursion) {
        throw CordraException.fromStatusCode(500, "Too many recursive calls to resolve object graph")
    }

    // Retrieve unresolved objects from their ids
    idsToResolve.filterNot { resolvedObjects.containsKey(it) }.let {
        if (it.isNotEmpty()) {
            cordraClient.get(it).forEach { resolvedObjects[it.id] = it }
        }
    }

    // Find IDs of objects linked to newly resolved objects
    val discoveredIds = idsToResolve
        .mapNotNull { resolvedObjects[it] }
        .flatMap{
            val prefix = idsToResolve.first().split("/")[0] + "/"
            // skip discovering ids from datasets if not nested
            if (nested || recursionDepth == 0 || it.type != "Dataset") {
                val skipKeys = if (workflowOnly && it.type == "Dataset") {
                    listOf("mentions", "hasPart")
                } else {
                    emptyList()
                }
                discoverIdsInObject(it.content.asJsonObject, prefix, skipKeys = skipKeys)
            } else {
               emptyList()
            }
        }
        .filterNot { resolvedObjects.containsKey(it) }
        .distinct()

    return if (discoveredIds.isEmpty()) {
        // recursion anchor: all ids have been resolved
        resolvedObjects
    } else {
        resolveObjectIdsRecursively(
            discoveredIds,
            resolvedObjects,
            cordraClient,
            nested,
            workflowOnly,
            recursionDepth + 1,
            maxRecursion,
        )
    }
}

private fun discoverIdsInObject(json: JsonObject, prefix: String, discoveredIds: MutableList<String> = mutableListOf(), skipKeys: List<String> = emptyList()): List<String> {
    for ((key, value) in json.entrySet()) {
        if (key == "@id") continue
        if (key == "isPartOf") continue
        if (key in skipKeys) continue

        if (value.isJsonObject) {
            discoverIdsInObject(value.asJsonObject, prefix, discoveredIds, skipKeys)
        } else if (value.isJsonArray) {
            for (element in value.asJsonArray) {
                if (element.isJsonObject) {
                    discoverIdsInObject(element.asJsonObject, prefix, discoveredIds, skipKeys)
                } else {
                    if (element.asString.startsWith(prefix)) {
                        discoveredIds.add(element.asString)
                    }
                }
            }
        } else if (value.asString.startsWith(prefix)) {
            discoveredIds.add(value.asString)
        }
    }
    return discoveredIds.distinct()
}



