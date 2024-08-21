package de.senckenberg.cwr

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.gson.JsonArray
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
import java.time.format.DateTimeFormatter
import java.util.logging.Logger
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream

class ROCrate(val cordra: CordraClient) {

    fun deserializeCrate(folder: Path): CordraObject {

        val reader = RoCrateReader(FolderReader())
        val crate = reader.readCrate(folder.absolutePathString())

        val rootDataEntity = crate.rootDataEntity

        val processingOrder = findProcessingOrder(crate)
        val ingestedObjects = mutableMapOf<String, String>()  // Map crate ids to cordra ids

        var datasetCordraObject: CordraObject? = null
        var createActionObject: CordraObject? = null

        try {
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
                        "CreateAction" in types -> {
                            createActionObject = ingestCreateAction(entity, ingestedObjects)
                            createActionObject
                        }
                        "SoftwareApplication" in types || "SoftwareSourceCode" in types -> ingestSoftwareApplication(entity, ingestedObjects)
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

            // backlink from files to dataset
            for ((id, cordraId) in ingestedObjects.entries) {
                if (crate.getDataEntityById(id) != null) {
                    val cordraObj = cordra.get(cordraId)
                    // backlink to dataset
                    if (cordraObj.content.asJsonObject.has("isPartOf")) {
                        cordraObj.content.asJsonObject.get("isPartOf").asJsonArray.add(datasetCordraObject!!.id)
                    } else {
                        cordraObj.content.asJsonObject.add("isPartOf", JsonArray().apply { add(datasetCordraObject!!.id) })
                    }

                    cordra.update(cordraObj)
                }
            }

            return datasetCordraObject ?: throw CordraException.fromStatusCode(500, "Dataset entity not found in crate.")
        } catch (e: Exception) {
            logger.warning("Failed to process RO-Crate. Deleteing created objects")
            for (cordraId in ingestedObjects.values) {
                logger.info("Delete $cordraId")
                cordra.delete(cordraId)
            }
            throw e
        }
    }


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
        val adjList = (crate.allContextualEntities + crate.allDataEntities + listOf(crate.rootDataEntity)).map {
            it.id to it.linkedTo
        }.toMap()

        // find the degree of each object (number of dependencies)
        val inDegree = adjList.keys.map { key ->
            key to adjList[key]!!.size
        }.toMap().toMutableMap()

        // objects might reference objects defined outside of the crate.
        // These links must be subtracted from the degree, or would otherwise be recognized as cyclic dependencies
        for ((key, values) in adjList.entries) {
            for (value in values) {
                if (crate.getEntityById(value) == null) {
                    inDegree.replace(key, inDegree[key]!!-1)
                }
            }
        }

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

    /**
     * Extension function turns properties into a list of json nodes
     * because it's easier to work with list everywhere instead of checking if something
     * is an array or a value
     */
    private fun AbstractEntity.getPropertyList(key: String): List<JsonNode> {
        val property = this.getProperty(key)
        return if (property.isArray) {
            property.elements().asSequence().toList()
        } else {
            listOf(property)
        }
    }

    private fun ingestRootDataEntity(entity: RootDataEntity, ingestedObjects: Map<String, String>): CordraObject {
        val datasetProperties = entity.properties.deepCopy()

        // resolve ids to cordra ids
        for ((key, property) in entity.properties.properties()) {

            when (key) {
                "license", "about" -> {
                    if (property.isObject) {
                        val id = resolveNestedPropertyId(property as ObjectNode, ingestedObjects)
                        datasetProperties.put(key, id)
                    } else if (property.isTextual && Validator.isUri(property.asText())){
                        // allow textual links for about and license
                        // although this is not actually valid jsonld, it is often used
                        datasetProperties.put(key, property.asText())
                    }
                }
                "dateCreated", "dateModified", "datePublished" -> {
                    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    val parsedDate = try {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").parse(property.asText())
                    } catch (e: Exception) {
                        // try ISO date format if the dateFormatter pattern doesn't work
                        DateTimeFormatter.ISO_DATE_TIME.parse(property.asText())
                    }
                    datasetProperties.put(key, dateFormatter.format(parsedDate))
                }
                else -> replaceNestedPropertiesWithIds(datasetProperties, key, property, ingestedObjects)
            }
        }

        if (entity.properties.has("mainEntity") && entity.properties.get("mainEntity").isObject) {
            val entityId = resolveNestedPropertyId(entity.properties.get("mainEntity") as ObjectNode, ingestedObjects)
            datasetProperties.putArray("mentions").apply { add(entityId) }
            datasetProperties.remove("mainEntity")
        }

        return createCordraObject("Dataset", datasetProperties).also { logger.info("Created dataset object ${it.id}") }
    }

    /**
     * Tries to find the @id of the given property (or array of elements) and replaces the linked object with a list
     * of ids that point either to the corresponding cordra object or is a list of valid uris
     */
    private fun replaceNestedPropertiesWithIds(properties: ObjectNode, propertyKey: String, property: JsonNode, ingestedObjects: Map<String, String>) {
        // only try to resolve objects and arrays of objects
        if (property.isObject) {
            val resolvedId = resolveNestedPropertyId(property as ObjectNode, ingestedObjects)
            if (resolvedId != null) {
                properties.putArray(propertyKey).apply { add(resolvedId) }
            } else {
                logger.warning("Failed to map property under key $propertyKey to Cordra objects.")
                properties.remove(propertyKey)
            }
        } else if (property.isArray && !property.isEmpty && property.elements().next().isObject) {
            val resolvedIds = property.elements().asSequence().mapNotNull {
                resolveNestedPropertyId(it as ObjectNode, ingestedObjects)
            }.toList()
            if (!resolvedIds.isEmpty()) {
                properties.putArray(propertyKey).apply { resolvedIds.forEach { add(it) } }
            } else {
                logger.warning("Failed to map multiple properties under key $propertyKey to Cordra objects.")
                properties.remove(propertyKey)
            }
        }
    }

    /**
     * Tries to resolve a link to a json ld object or nested json ld object to a useable id.
     * If the object was ingested into cordra (is in ingestedObjects), the corresponding id is returned.
     * If no corda id is found and the @id is a URI, the URI is returned
     */
    private fun resolveNestedPropertyId(property: ObjectNode, ingestedObjects: Map<String, String>): String? {
        val id = if (property.has("@id")) {
            property.get("@id").asText()
        } else {
            return null
        }

        // try to resolve cordra id
        if (id in ingestedObjects) {
            return ingestedObjects[id]
        }

        // if it is an object with a valid URI but no cordra object, represent it as URI
        if (Validator.isUri(id)) {
            return id
        }

        return null
    }


    private fun ingestFile(dataEntity: DataEntity): CordraObject {
        val properties = dataEntity.properties

        val cordraObject = createCordraObject("FileObject", properties, commitObject = false)
        val fileName = dataEntity.id
        return dataEntity.content.inputStream().use { stream ->
            cordraObject.addPayload(
                fileName,
                fileName,
                dataEntity.properties.get("encodingFormat")?.asText() ?: "application/octet-stream",
                stream
            )

            // create must be inside the stream block to make sure it is read before being closed
            try {
                cordra.create(cordraObject).also { logger.info("Created file object ${it.id}") }
            } catch (e: CordraException) {
                throw CordraException.fromStatusCode(e.responseCode, "Failed to create FileObject: ${e.message}", e.cause)
            }
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
                replaceNestedPropertiesWithIds(properties, "affiliation", properties.get("affiliation"), ingestedObjects)
            } catch (e: Exception) {
                logger.warning("Failed to map affiliation to Cordra objects: ${e.message}")
                properties.remove("affiliation")
            }
        }

        return createCordraObject("Person", properties).also { logger.info("Created person object ${it.id}") }
    }

    private fun ingestOrganization(entity: ContextualEntity): CordraObject {
        val properties = entity.properties

        // preserve org identifier as additional id
        if (Validator.isUri(entity.id)) {
            properties.put("identifier", entity.id)
        }

        return createCordraObject("Organization", properties).also { logger.info("Created organization object ${it.id}") }
    }

    private fun ingestCreateAction(entity: ContextualEntity, ingestedObjects: Map<String, String>): CordraObject {
        val properties = entity.properties

        for (key in arrayOf("agent", "instrument")) {
            if (properties.has(key) && properties.get(key).isObject) {
                val propertyId = resolveNestedPropertyId(properties.get(key) as ObjectNode, ingestedObjects)
                if (propertyId != null) {
                    properties.put(key, propertyId)
                } else {
                    properties.remove(key)
                }
            }
        }

        if (properties.has("result")) {
            val property = properties.get("result")
            replaceNestedPropertiesWithIds(properties, "result", property, ingestedObjects)
        }

        return createCordraObject("CreateAction", properties).also { logger.info("Created CreateAction object ${it.id}") }
    }

    private fun ingestSoftwareApplication(entity: ContextualEntity, ingestedObjects: Map<String, String>): CordraObject {
        val properties = entity.properties

        if (properties.has("license")) {
            val licenseId = resolveNestedPropertyId(properties.get("license") as ObjectNode, ingestedObjects)
            if (licenseId != null) {
                properties.put("license", licenseId)
            } else {
                properties.remove("license")
            }
        }

        if (Validator.isUri(entity.id)) {
            properties.put("identifier", properties.get("@id").asText())
        }

        return createCordraObject("SoftwareApplication", properties).also { logger.info("Created SoftwareApplication object ${it.id}") }
    }

    /**
     * Create a cordra object from input and optionally commit it to cordra
     * Does the necessary transformation of jackson objects to gson
     */
    private fun createCordraObject(type: String, obj: Any, commitObject: Boolean = true): CordraObject {
        val converted = if (obj is ObjectNode) {
            JsonParser.parseString(obj.toString())
        } else {
            obj
        }
        val cordraObject = CordraObject(type, converted)
        return if (commitObject) {
            try {
                cordra.create(cordraObject)
            } catch (e: CordraException) {
                throw CordraException.fromStatusCode(e.responseCode, "Failed to create $type: ${e.message}", e.cause)
            }
        } else {
            cordraObject
        }
    }

    companion object {
        val logger = Logger.getLogger(this::class.simpleName)
    }

}
