package de.senckenberg.cwr

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.gson.JsonParser
import edu.kit.datamanager.ro_crate.RoCrate
import edu.kit.datamanager.ro_crate.entities.AbstractEntity
import edu.kit.datamanager.ro_crate.entities.contextual.ContextualEntity
import edu.kit.datamanager.ro_crate.entities.data.DataEntity
import edu.kit.datamanager.ro_crate.entities.data.RootDataEntity
import edu.kit.datamanager.ro_crate.reader.FolderReader
import edu.kit.datamanager.ro_crate.reader.RoCrateReader
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream

class ROCrate(val cordra: CordraClient) {

    /**
     * Determines the processing order of objects in this RO-Crate based on their dependencies.
     *
     * The method uses a topological sorting algorithm to build a list that has objects placed in such a way,
     * that no object is processed before all it's dependencies are processed first.
     *
     * Uses Kahn's sorting algorithm for directed graphs.
     */
    private fun findProcessingOrder(crate: RoCrate): List<String> {
        // create adjacency map of objects in the crate
        // maps each objects id to a list of object ids it depends on
        val adjList = hashMapOf<String, List<String>>()
        for (entity in crate.allContextualEntities + crate.allDataEntities + listOf(crate.rootDataEntity)) {
            val dependsOnEntities = mutableListOf<String>()
            entity.properties.forEach {
                if (it.isArray) {
                    for (elem in it) {
                        if (elem.isObject && elem.has("@id")) {
                            dependsOnEntities.add(elem.get("@id").asText())
                        }
                    }
                } else if (it.isObject && it.has("@id")) {
                    dependsOnEntities.add(it.get("@id").asText())
                }
            }
            adjList.put(entity.id, dependsOnEntities)
        }

        // find the degree of each object (number of dependencies)
        val inDegree = adjList.keys.map { key ->
            key to adjList[key]!!.size
        }.toMap().toMutableMap()

        // find all nodes with degree 0 and queue them for processing
        val queue = inDegree.filterValues { it == 0 }.keys.toMutableList()

        val sortedOrder = mutableListOf<String>()
        // for each element marked for processing, remove it from the processing list
        // add it to the sorted order, and decrease the degree of dependent objects by one.
        // If an object reaches degree 0, it can also be processed.
        while (!queue.isEmpty()) {
            val id = queue.removeFirst()
            sortedOrder.add(id)
            for (elem in adjList.keys) {
                if (adjList[elem]!!.contains(id)) {
                    inDegree.replace(elem, inDegree[elem]!! - 1)
                    if (inDegree[elem] == 0) {
                        queue.add(elem)
                    }
                }
            }
        }

        return sortedOrder
    }

    private fun createCordraObject(type: String, obj: Any, commitObject: Boolean = true): CordraObject {
        val converted = if (obj is ObjectNode) {
            JsonParser.parseString(obj.toString())
        } else {
            obj
        }
        val cordraObject = CordraObject(type, converted)
        return if (commitObject) {
            cordra.create(cordraObject)
        } else {
            cordraObject
        }
    }

    /**
     * Extension function turns properties into a list of json nodes
     * because it's easier to work with list everywhere instead of checking if something
     * is an array or a value
     */
    fun AbstractEntity.getPropertyList(key: String): List<JsonNode> {
        val property = this.getProperty(key)
        return if (property.isArray) {
            property.elements().asSequence().toList()
        } else {
            listOf(property)
        }
    }


    fun deserializeCrate(folder: Path): CordraObject {

        val reader = RoCrateReader(FolderReader())
        val crate = reader.readCrate(folder.absolutePathString())

        val rootDataEntity = crate.rootDataEntity

        val processingOrder = findProcessingOrder(crate)
        val ingestedObjects = mutableMapOf<String, String>()  // Map crate ids to cordra ids

        var datasetCordraObject: CordraObject? = null

        for (id in processingOrder) {
            logger.info("Processing object ${id}")
            val entity = if (id == rootDataEntity.id) {
                rootDataEntity
            } else {
                crate.getEntityById(id)
                    ?: throw CordraException.fromStatusCode(500, "Object with id ${id} not found in crate.")
            }

            val cordraObject = if (entity is ContextualEntity) {
                val types = entity.getPropertyList("@type").map { it.asText() }
                when {
                    "Person" in types -> ingestPerson(entity, ingestedObjects)
                    "Organization" in types -> ingestOrganization(entity)
                    "CreateAction" in types -> null // TODO ingestAction(entity)
                    else -> null  // ignore everything that has no corresponding cordra schema
                }
            } else {
                if (entity is RootDataEntity) {
                    datasetCordraObject = ingestRootDataEntity(entity, ingestedObjects)
                    datasetCordraObject
                } else {
                    ingestFile(entity as DataEntity)
                }
            }

            if (cordraObject != null) {
                ingestedObjects.put(entity.id, cordraObject.id)
            }
        }

        // TODO add taxon to root entity
//        // resolve taxon/about
//        val aboutEntity = rootDataEntity.getProperty("about")?.let {
//            if (it.isObject) {
//                val aboutId = it.get("@id").asText()
//                val aboutProperties = crate.getContextualEntityById(aboutId).properties.deepCopy()
//                    ?: throw CordraException.fromStatusCode(500, "About object not found in crate.")
//                if (Validator.isUri(aboutId)) {
//                    aboutProperties.put("identifier", aboutId)
//                }
//                datasetProperties.replace("about", aboutProperties)
//            }
//        }

        return datasetCordraObject ?: throw CordraException.fromStatusCode(500, "Dataset entity not found in crate.")
    }

    private fun jsonLdObjToCordraHandleRef(obj: JsonNode, ingestedObjects: Map<String, String>): List<String> {
        fun objToId(obj: JsonNode): String {
            if (obj.has("@id")) {
                val objId = obj.get("@id").asText()
                if (objId in ingestedObjects) {
                    return ingestedObjects[objId]!!
                } else {
                    throw Exception("Object ID not found in ingested objects: ${objId}")
                }
            } else {
                throw Exception("Object $obj is not a valid JsonLD object")
            }
        }

        if (obj.isObject) {
            return listOf(objToId(obj))
        } else if (obj.isArray) {
            return obj.elements().asSequence().map { objToId(it) }.toList()
        } else {
            throw Exception("Object $obj is not a convertible JsonLD Object")
        }
    }

    private fun ingestRootDataEntity(entity: RootDataEntity, ingestedObjects: Map<String, String>): CordraObject {
        val datasetProperties = entity.properties.deepCopy()

        // resolve ids to cordra ids
        for ((key, property) in entity.properties.properties()) {

            // license objects are not separate objects.
            if (key == "license") {
                datasetProperties.put("license", property.get("@id").asText())
                continue
            }

            // only try to resolve objects and arrays of objects
            if (property.isObject || (property.isArray && !property.isEmpty && property.elements().next().isObject)) {
                try {
                    val resolvedCordraReferences = jsonLdObjToCordraHandleRef(property, ingestedObjects)
                    datasetProperties.putArray(key).apply { resolvedCordraReferences.forEach { add(it) } }
                } catch (e: Exception) {
                    logger.warning("Failed to map property under key $key to Cordra objects: ${e.message}")
                    datasetProperties.remove(key)
                }
            }
        }

        // TODO add formatted timestamps
        return createCordraObject("Dataset", datasetProperties)
    }

    private fun ingestFile(dataEntity: DataEntity): CordraObject {
        val properties = dataEntity.properties

        val cordraObject = createCordraObject("FileObject", properties, commitObject = false)
        val fileName = dataEntity.id
        return dataEntity.content.inputStream().use { stream ->
            cordraObject.addPayload(
                fileName,
                fileName,
                dataEntity.getProperty("encodingFormat").asText("application/octet-stream"),
                stream
            )

            // create must be inside the stream block to make sure it is read before being closed
            cordra.create(cordraObject)
        }
    }

    private fun ingestPerson(entity: ContextualEntity, ingestedObjects: Map<String, String>): CordraObject {
        val properties = entity.properties

        // preserve author identifier as additional id
        if (Validator.isUri(entity.id)) {
            properties.put("identifier", entity.id)
        }

        if (properties.has("affiliation")) {
            try {
                val affiliationRef = jsonLdObjToCordraHandleRef(properties.get("affiliation"), ingestedObjects)
                properties.putArray("affiliation").also { arr -> affiliationRef.forEach { arr.add(it) } }
            } catch (e: Exception) {
                logger.warning("Failed to map affiliation to Cordra objects: ${e.message}")
                properties.remove("affiliation")
            }
        }

        return createCordraObject("Person", properties)
    }

    private fun ingestOrganization(entity: ContextualEntity): CordraObject {
        val properties = entity.properties

        // preserve org identifier as additional id
        if (Validator.isUri(entity.id)) {
            properties.put("identifier", entity.id)
        }

        return createCordraObject("Organization", properties)
    }

    private fun ingestAction(entity: ContextualEntity): CordraObject {
        throw NotImplementedError("ingesting actions not yet implemented")
    }

    companion object {
        val logger = Logger.getLogger(this::class.simpleName)
    }

}
