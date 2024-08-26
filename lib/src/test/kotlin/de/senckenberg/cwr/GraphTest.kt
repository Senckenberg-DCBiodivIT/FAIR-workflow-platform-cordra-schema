package de.senckenberg.cwr

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraObject
import net.cnri.cordra.collections.SearchResultsFromIterator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphTest {

    @Test
    fun testResolveEmptyGraph() {
        val cordraObject = CordraObject().apply {
            id = "cwr/dataset1"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
            }
        }
        val cordraClient: CordraClient = mockk()

        val graph = resolveCordraObjectsRecursively(cordraObject, cordraClient)
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertEquals(1, graph.size)
        verify(exactly = 0) {
            cordraClient.get(any<String>())
        }
    }

    @Test
    fun testResolveGraph() {
        val cordraObject = CordraObject().apply {
            id = "cwr/dataset1"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
                add("hasPart", JsonArray().apply {
                    add(JsonPrimitive("cwr/part1"))
                    add(JsonPrimitive("cwr/part2"))
                    add(JsonPrimitive("cwr/part3"))
                })
            }
        }
        val cordraClient: CordraClient = mockk()
        every { cordraClient.get(any<Collection<String>>()) } answers {
            firstArg<Collection<String>>().map {
                CordraObject().apply {
                    id = it
                    content = JsonObject().apply {
                        addProperty("@type", "MediaObject")
                        addProperty("@id", it)
                        addProperty("author", "cwr/author1")
                    }
                }
            }.toList().let { SearchResultsFromIterator(it.size, it.iterator()) }

        }

        val graph = resolveCordraObjectsRecursively(cordraObject, cordraClient)
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertTrue(graph.containsKey("cwr/part1"))
        assertTrue(graph.containsKey("cwr/part2"))
        assertTrue(graph.containsKey("cwr/part3"))
        assertTrue(graph.containsKey("cwr/author1"))
        assertEquals(5, graph.size)
        verifySequence {
            cordraClient.get(listOf("cwr/part1", "cwr/part2", "cwr/part3"))
            cordraClient.get(listOf("cwr/author1"))
        }
    }


    @Test
    fun testResolveNestedDatasetGraph() {
        val cordraObject = CordraObject().apply {
            id = "cwr/dataset1"
            type = "Dataset"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
                addProperty("author", "cwr/author1")
                add("hasPart", JsonArray().apply {
                    add(JsonPrimitive("cwr/dataset2"))
                    add(JsonPrimitive("cwr/part1"))
                })
            }
        }
        val cordraClient: CordraClient = mockk()
        every { cordraClient.get(any<Collection<String>>()) } answers {
            firstArg<Collection<String>>().map {
                CordraObject().apply {
                    id = it
                    type = "Dataset"
                    content = JsonObject().apply {
                        addProperty("@type", "Dataset")
                        addProperty("@id", it)
                        addProperty("author", "cwr/author2")
                        add("hasPart", JsonArray().apply {
                            add(JsonPrimitive("cwr/part2"))
                            add(JsonPrimitive("cwr/part3"))
                        })
                    }
                }
            }.toList().let { SearchResultsFromIterator(it.size, it.iterator()) }

        }

        val graph = resolveCordraObjectsRecursively(cordraObject, cordraClient)
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertTrue(graph.containsKey("cwr/dataset2"))
        assertTrue(graph.containsKey("cwr/author1"))
        assertTrue(graph.containsKey("cwr/author2"))
        assertTrue(graph.containsKey("cwr/part1"))
        assertTrue(graph.containsKey("cwr/part2"))
        assertTrue(graph.containsKey("cwr/part3"))
        assertEquals(7, graph.size)
        verifySequence {
            cordraClient.get(listOf("cwr/author1", "cwr/dataset2", "cwr/part1"))
            cordraClient.get(listOf("cwr/author2", "cwr/part2", "cwr/part3"))
        }
    }


    @Test
    fun testResolveNestedDatasetGraph_SkipNestedParts() {
        val cordraObject = CordraObject().apply {
            id = "cwr/dataset1"
            type = "Dataset"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
                addProperty("author", "cwr/author1")
                add("hasPart", JsonArray().apply {
                    add(JsonPrimitive("cwr/dataset2"))
                    add(JsonPrimitive("cwr/part1"))
                })
            }
        }
        val cordraClient: CordraClient = mockk()
        every { cordraClient.get(any<Collection<String>>()) } answers {
            firstArg<Collection<String>>().map {
                CordraObject().apply {
                    id = it
                    type = "Dataset"
                    content = JsonObject().apply {
                        addProperty("@type", "Dataset")
                        addProperty("@id", it)
                        addProperty("author", "cwr/author2")
                        add("hasPart", JsonArray().apply {
                            add(JsonPrimitive("cwr/part2"))
                            add(JsonPrimitive("cwr/part3"))
                        })
                        add("mainEntity", JsonPrimitive("cwr/workflow"))
                    }
                }
            }.toList().let { SearchResultsFromIterator(it.size, it.iterator()) }

        }

        val graph = resolveCordraObjectsRecursively(cordraObject, cordraClient, nested = false)
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertTrue(graph.containsKey("cwr/dataset2"))
        assertTrue(graph.containsKey("cwr/author1"))
        assertTrue(graph.containsKey("cwr/part1"))
        assertEquals(4, graph.size)
        verifySequence {
            cordraClient.get(listOf("cwr/author1", "cwr/dataset2", "cwr/part1"))
        }
    }
}