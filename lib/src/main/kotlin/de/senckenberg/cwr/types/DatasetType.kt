package de.senckenberg.cwr.types

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.senckenberg.cwr.resolveCordraObjectsRecursively
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.CordraMethod
import net.cnri.cordra.CordraType
import net.cnri.cordra.HooksContext
import net.cnri.cordra.api.CordraObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

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


    /**
     * Resolves all linked objects of this element and returns them as a JSON-LD Graph object
     */
    @CordraMethod("asWorkflowGraph", allowGet = true)
    fun resolveWorkflowGraph(co: CordraObject, ctx: HooksContext): JsonElement {
        val objects = resolveCordraObjectsRecursively(co, CordraHooksSupportProvider.get().cordraClient, nested = false, workflowOnly = true)
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
    @CordraMethod("asNestedWorkflowGraph", allowGet = true)
    fun resolveNestedWorkflowGraph(co: CordraObject, ctx: HooksContext): JsonElement {
        val objects = resolveCordraObjectsRecursively(co, CordraHooksSupportProvider.get().cordraClient, nested = true, workflowOnly = true)
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

    }

}
