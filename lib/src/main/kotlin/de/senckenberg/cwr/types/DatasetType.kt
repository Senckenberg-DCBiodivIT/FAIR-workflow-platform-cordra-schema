package de.senckenberg.cwr.types

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.senckenberg.cwr.ROCrate
import de.senckenberg.cwr.resolveCordraObjectsRecursively
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraMethod
import net.cnri.cordra.CordraType
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
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.time.measureTime

@CordraType("Dataset")
class DatasetType : JsonLdType(listOf("Dataset"), coercedTypes = listOf("author", "hasPart", "mentions", "mainEntity", "isPartOf")) {

    override fun beforeSchemaValidation(co: CordraObject, context: HooksContext): CordraObject {
        val json = co.content.asJsonObject

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        val nowStr = dateFormatter.format(LocalDateTime.now())
        for (key in arrayOf("dateCreated", "dateModified", "datePublished")) {
            if (!json.has(key)) {
                json.addProperty(key, nowStr)
            }
        }

        return super.beforeSchemaValidation(co, context)
    }

    override fun afterDelete(co: CordraObject, context: HooksContext) {
        super.afterDelete(co, context)
        val graph = this.resolveNestedGraph(co, context)
        logger.warning("Dataset ${co.id} deleted. This will delete all linked ${graph.asJsonObject.get("@graph").asJsonArray.size()} objects.")

        val cordra = CordraHooksSupportProvider.get().cordraClient
        for (obj in graph.asJsonObject.get("@graph").asJsonArray) {
            val id = obj.asJsonObject.get("@id").asString

            if (id != co.id) {
                cordra.delete(id)
                logger.info("Deleted object ${id}")
            }
        }
    }

    /**
     * Resolves all linked objects of this element and returns them as a JSON-LD Graph object
     */
    @CordraMethod("asGraph", allowGet = true)
    fun resolveGraph(co: CordraObject, ctx: HooksContext): JsonElement {
        val objects = resolveCordraObjectsRecursively(co, CordraHooksSupportProvider.get().cordraClient, nested = false)
        val graph = JsonObject().apply {
            add("@graph", JsonArray().apply {
                objects.forEach { this.add(it.value.content) }
            })
        }
        return graph
    }

    /**
     * Resolves all linked objects of this element and returns them as a JSON-LD Graph object
     */
    @CordraMethod("asNestedGraph", allowGet = true)
    fun resolveNestedGraph(co: CordraObject, ctx: HooksContext): JsonElement {
        val objects = resolveCordraObjectsRecursively(co, CordraHooksSupportProvider.get().cordraClient, nested = true)
        val graph = JsonObject().apply {
            add("@graph", JsonArray().apply {
                objects.forEach { this.add(it.value.content) }
            })
        }
        return graph
    }

    companion object {
        val logger = Logger.getLogger(this::class.simpleName)

        fun JsonArray.findElementWithId(id: String): JsonObject? =
            this.firstOrNull { it.asJsonObject.get("@id").asString == id }?.asJsonObject

        // create a new json array with only elements of the given type
        fun JsonArray.findElementsByType(type: String): List<JsonObject> =
            this.filter { it.asJsonObject.get("@type").asString == type }.map { it.asJsonObject }

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

                logger.info("Processing ROCrate from temp dir ${tempDir.path}")
                val ingested = ROCrate(CordraHooksSupportProvider.get().cordraClient).deserializeCrate(Path(tempDir.path))
                return ingested.content
            } catch (e: Exception) {
                if (e is CordraException) {
                    throw CordraException.fromStatusCode(e.responseCode, "Failed to process ROCrate: ${e.message}", e)
                } else {
                    throw CordraException.fromStatusCode(500, "Failed to process ROCrate: ${e.message}", e)
                }
            } finally {
                // make sure temp dir is deleted
                tempDir.deleteRecursively()
                logger.info("Cleaned temp dir ${tempDir.path}")
            }
        }
    }

}
