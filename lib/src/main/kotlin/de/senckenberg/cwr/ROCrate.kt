package de.senckenberg.cwr

import com.fasterxml.jackson.databind.JsonNode
import edu.kit.datamanager.ro_crate.entities.contextual.ContextualEntity
import edu.kit.datamanager.ro_crate.entities.data.DataEntity
import edu.kit.datamanager.ro_crate.reader.FolderReader
import edu.kit.datamanager.ro_crate.reader.RoCrateReader
import edu.kit.datamanager.ro_crate.reader.ZipReader
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import java.io.FileReader
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class ROCrate(val cordra: CordraClient) {

    fun deserializeCrate(folder: Path): CordraObject {

        val reader = RoCrateReader(FolderReader())
        val crate = reader.readCrate(folder.absolutePathString())

        val rootDataEntity = crate.rootDataEntity
        val datasetProperties = rootDataEntity.properties.deepCopy()

        // add file objects
        datasetProperties.putArray("hasPart").let { arr ->
            val dataEntityIds = rootDataEntity.getProperty("hasPart")?.map { it.get("@id").asText() } ?: emptyList()
            for (id in dataEntityIds) {
                val dataEntity = crate.getDataEntityById(id)
                    ?: throw CordraException.fromStatusCode(500, "File not found in crate.")
                val fileObject = ingestFile(dataEntity)
                arr.add(fileObject.id)
            }
        }

        // resolve author(s)
        datasetProperties.putArray("author").let { arr ->
            val authorIds = rootDataEntity.getProperty("author")?.map { it.get("@id").asText() } ?: emptyList()
            for (id in authorIds) {
                val authorEntity = crate.getContextualEntityById(id) ?: continue  // skip authors without entity
                val authorObject = ingestPerson(authorEntity)
                arr.add(authorObject.id)
            }
        }

        // resolve taxon/about
        val aboutEntity = rootDataEntity.getProperty("about")?.let {
            if (it.isObject) {
                val aboutId = it.get("@id").asText()
                val aboutProperties = crate.getContextualEntityById(aboutId).properties.deepCopy()
                    ?: throw CordraException.fromStatusCode(500, "About object not found in crate.")
                if (Validator.isUri(aboutId)) {
                    aboutProperties.put("identifier", aboutId)
                }
                datasetProperties.replace("about", aboutProperties)
            }
        }

        // add license as string if present
        rootDataEntity.getProperty("license")?.let {
            if (!it.isTextual) {
                val licenseId = it.get("@id").asText()
                if (Validator.isUri(licenseId)) {
                    datasetProperties.put("license", licenseId)
                } else {
                    logger.warning("License is not a URI: $licenseId. Skipped")
                }
            }
        }

        // resolve create actions
        datasetProperties.putArray("mentions").let { arr ->
            val actionIds = rootDataEntity.getProperty("mentions")?.map { it.get("@id").asText() } ?: emptyList()
            for (id in actionIds) {
                val actionEntity = crate.getContextualEntityById(id)
                    ?: throw CordraException.fromStatusCode(500, "Action not found in crate.")
                val actionObject = ingestAction(actionEntity)
                arr.add(actionObject.id)
            }
        }

        // TODO add timestamps
        val datasetObject  = CordraObject("Dataset", datasetProperties)
        val co = cordra.create(datasetObject)

        // TODO backlink files to dataset
        return co
    }

    private fun ingestFile(dataEntity: DataEntity): CordraObject {
        val properties = dataEntity.properties

        val cordraObject = CordraObject("FileObject", properties)
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

    private fun ingestPerson(entity: ContextualEntity): CordraObject {
        val properties = entity.properties

        // preserve author identifier as additional id
        if (Validator.isUri(entity.id)) {
            properties.put("identifier", entity.id)
        }

        // TODO: deserialize affiliation

        val cordraObject = CordraObject("Person", properties)
        return cordra.create(cordraObject)
    }

    private fun ingestAction(entity: ContextualEntity): CordraObject {
        throw NotImplementedError("ingesting actions not yet implemented")
    }

    companion object {
        val logger = Logger.getLogger(this::class.simpleName)
    }

}
