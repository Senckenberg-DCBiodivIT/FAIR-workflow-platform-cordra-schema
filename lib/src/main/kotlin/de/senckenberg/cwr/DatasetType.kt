/*
 * This source file was generated by the Gradle 'init' task
 */
package de.senckenberg.cwr

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraMethod
import net.cnri.cordra.CordraType
import net.cnri.cordra.CordraTypeInterface
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraException
import net.cnri.cordra.api.CordraObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory

@CordraType("Dataset")
class DatasetType : CordraTypeInterface {

    override fun beforeSchemaValidation(co: CordraObject, ctx: HooksContext): CordraObject {
        val json = co.content.asJsonObject

        if (json.has("about") && json.get("about").isJsonObject) {
            if (!Validator.validateIdentifier(json.get("about").asJsonObject)) {
                throw CordraException.fromStatusCode(400, "Taxon identifier is not a valid URI identifier.")
            }
        }

        return co
    }

    @CordraMethod("toCrate", allowGet = true)
    fun toROCrate(obj: CordraObject, ctx: HooksContext): JsonElement {
        // TODO parse RO Crate
        return Gson().toJsonTree(Unit)
    }

    companion object {
        val logger = Logger.getLogger(this::class.simpleName)

        fun JsonArray.findElementWithId(id: String): JsonObject? =
            this.firstOrNull { it.asJsonObject.get("@id").asString == id }?.asJsonObject

        // create a new json array with only elements of the given type
        fun JsonArray.findElementsByType(type: String): JsonArray =
            JsonArray().apply { this.filter { it.asJsonObject.get("@type").asString == type }.forEach { add(it) } }

        fun JsonObject.getStringProperty(key: String): String? = if (this.has(key)) this.get(key).asString else null

        @Throws(CordraException::class)
        @CordraMethod("fromROCrate")
        @JvmStatic
        fun fromROCrate(ctx: HooksContext): JsonElement {
            // Unpack zip archive into temp dir and process it
            val tempDir = createTempDirectory("crate").toFile()
            logger.info("Unzipping ROCrate into ${tempDir.path}")
            try {
                // map of filenames in the zip archive to temp file paths
                val receivedFiles = mutableMapOf<String, String>()

                // unzip all files to temp dir
                ctx.directIo.inputAsInputStream.use { inStream ->
                    ZipInputStream(inStream).use { zipStream ->
                        var entry: ZipEntry?
                        while (zipStream.nextEntry.also { entry = it } != null) {
                            val newFile = File(tempDir, entry!!.name)
                            receivedFiles[entry!!.name] = newFile.path
                            if (entry!!.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                newFile.outputStream().use { outStream ->
                                    zipStream.copyTo(outStream)
                                }
                            }
                            zipStream.closeEntry()
                        }
                    }
                }
                if (receivedFiles.keys.isEmpty()) {
                    throw CordraException.fromStatusCode(400, "Empty or corrupt archive.")
                }

                // Read RO Crate Metadata to json object
                if ("ro-crate-metadata.json" !in receivedFiles.keys) {
                    throw CordraException.fromStatusCode(400, "Missing ro-crate-metadata.json in archive.")
                }
                val metadata = JsonParser.parseString(
                    File(receivedFiles["ro-crate-metadata.json"]).readText()
                ).asJsonObject
                val digitalObjectJson = ingestROCrate(metadata, receivedFiles)
                return digitalObjectJson
            } catch (e: Exception) {
                if (e is CordraException) {
                    throw e
                } else {
                    throw CordraException.fromStatusCode(400, "Failed to process ROCrate: ${e.message}", e)
                }
            } finally {
                // make sure temp dir is deleted
                tempDir.deleteRecursively()
                logger.info("Cleaned temp dir ${tempDir.path}")
            }
        }

        /**
         * Ingest the RO Crate file into cordra objects.
         *
         * @param metadata metadata json object representing the RO Crate metadata file
         * @param files map of filenames in the RO Crate to file paths of existing files
          */

        fun ingestROCrate(metadata: JsonObject, files: Map<String, String>): JsonObject {
            val cordra = CordraHooksSupportProvider.get().cordraClient

            logger.info { "Processing $metadata." }

            // find dataset element
            val graph = metadata.getAsJsonArray("@graph")
                ?: throw CordraException.fromStatusCode(400, "Missing @graph array element.")
            val metadataEntity = graph.findElementWithId("ro-crate-metadata.json")
                ?: throw CordraException.fromStatusCode(400, "Missing dataset element.")
            val datasetId = metadataEntity.getAsJsonObject("about").get("@id").asString
            val datasetEntity = graph.findElementWithId(datasetId)
                ?: throw CordraException.fromStatusCode(400, "Missing dataset element.")

            // process dataset entity to cordra object
            val dataset = JsonObject()
            applyTypeAndContext(dataset, "Dataset", "https://schema.org")

            for (key in datasetEntity.keySet()) {
                when (key) {
                    // exclude jsonld special attributes
                    "@id", "@type", "@context", "conformsTo" -> continue
                    // resolve file entities to media objects with payload
                    "hasPart" -> {
                        logger.info("Processing hasPart")
                        val ids = if (datasetEntity.get(key).isJsonObject) {
                            listOf(datasetEntity.getAsJsonObject(key).get("@id").asString)
                        } else {
                            datasetEntity.getAsJsonArray(key).map { it.asJsonObject.get("@id").asString }
                        }
                        val partCordraIds = mutableListOf<String>()
                        for (id in ids) {
                            val elem = graph.findElementWithId(id)
                            if (elem != null) {
                                val obj = CordraObject("FileObject", elem)
                                val fileName = elem.getStringProperty("@id")!!
                                files[fileName]?.let { path ->
                                    File(path).inputStream().use {
                                        obj.addPayload(fileName, fileName, "application/octet-stream", File(path).inputStream())
                                    }
                                } ?: throw CordraException.fromStatusCode(400, "File not found in RO Crate: $fileName")
                                val internalId = cordra.create(obj).id
                                partCordraIds.add(internalId)
                            }
                        }
                        dataset.add("hasPart", JsonArray().apply { partCordraIds.forEach { add(it) } })
                    }
                    // resolve about/taxon
                    "about" -> {
                        logger.info("Processing about")
                        val elem = datasetEntity.get("about")
                        if (elem.isJsonPrimitive) {
                            dataset.addProperty("about", elem.asString)
                        } else {
                            val aboutId = elem.asJsonObject.getStringProperty("@id")!!
                            val resolvedAbout = graph.findElementWithId(aboutId)
                            // preserve id as identifier if it's a valid uri  // TODO refactor to helper.
                            resolvedAbout?.apply {
                                if (Validator.isUri(key)) {
                                    addProperty("identifier", key)
                                }
                            }
                            dataset.add("about", resolvedAbout)
                        }
                    }
                    // resolve author entities to Person objects
                    "author" -> {
                        logger.info("Processing author list")
                        val elem = datasetEntity.get(key)
                        val authorIds = if (elem.isJsonObject) {
                            listOf(elem.asJsonObject.get("@id").asString)
                        } else {
                            elem.asJsonArray.map { it.asJsonObject.get("@id").asString }
                        }
                        val authorCordraIds = mutableListOf<String>()
                        for (id in authorIds) {
                            val elem = graph.findElementWithId(id) ?: continue
                            // preserve author id as identifier if it's a valid uri
                            if (Validator.isUri(id)) {
                                elem.addProperty("identifier", id)
                            }
                            val obj = cordra.create("Person", elem)
                            authorCordraIds.add(obj.id)
                        }
                        dataset.add("author", JsonArray().apply { authorCordraIds.forEach { add(it) } })
                    }
                    "mentions" -> continue // TODO add mentions/create actions
                    // try to add everything else as string primitive
                    else -> {
                        val elem = datasetEntity.get(key)
                        if (elem.isJsonPrimitive) {
                            dataset.addProperty(key, elem.asJsonPrimitive.asString)
                        }
                    }
                }
            }

            // add dateCreated, dateModified, datePublished
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
            val nowStr = dateFormatter.format(LocalDateTime.now())
            for (property in arrayOf("dateCreated", "dateModified", "datePublished")) {
                if (!dataset.has(property)) {
                    dataset.addProperty(property, nowStr)
                } else {
                    dataset.addProperty(
                        property,
                        dateFormatter.format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").parse(dataset.getStringProperty(property)))
                    )
                }
            }

            val co = cordra.create("Dataset", dataset)
            return co.content.asJsonObject
        }
    }

}
