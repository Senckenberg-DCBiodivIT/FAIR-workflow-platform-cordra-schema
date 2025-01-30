package de.senckenberg.cwr

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.mockk.InternalPlatformDsl.toStr
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

    /**
     * Should resolve the graph, but skip elements that are not part of the workflow of the dataset
     */
    @Test
    fun testResolveWorkflowGraph() {
        val datasetObject = CordraObject().apply {
            id = "cwr/dataset1"
            type = "Dataset"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
                add("hasPart", JsonArray().apply {
                    add(JsonPrimitive("cwr/part1"))
                    add(JsonPrimitive("cwr/workflow"))
                })
                add("mentions", JsonArray().apply {
                    add(JsonPrimitive("cwr/createAction"))
                })
                add("author", JsonArray().apply {
                    add(JsonPrimitive("cwr/author1"))
                })
                addProperty("mainEntity", "cwr/workflow")
            }
        }
        val workflowObject = CordraObject().apply {
            id = "cwr/workflow"
            type = "Workflow"
            content = JsonObject().apply {
                add("@type", JsonArray().apply {
                    add(JsonPrimitive("ComputationalWorkflow"))
                    add(JsonPrimitive("MediaObject"))
                })
                addProperty("@id", "cwr/workflow")
                add("input", JsonArray().apply {
                    add(JsonPrimitive("cwr/arg1"))
                })
                addProperty("programmingLanguage", "cwr/proglang")
            }
        }
        val progLang = CordraObject().apply {
            id = "cwr/proglang"
            type = "ProgrammingLanguage"
            content = JsonObject().apply {
                addProperty("@type", "ComputerLanguage")
                addProperty("@id", "cwr/proglang")
            }
        }
        val authorObject = CordraObject().apply {
            id = "cwr/author1"
            type = "Person"
            content = JsonObject().apply {
                addProperty("@type", "Person")
                addProperty("@id", "cwr/author1")
                addProperty("name", "John Doe")
            }
        }
        val argObject = CordraObject().apply {
            id = "cwr/arg1"
            type = "FormalParameter"
            content = JsonObject().apply {
                addProperty("@type", "FormalParameter")
                addProperty("@id", "cwr/arg1")
            }
        }
        val fileObject = CordraObject().apply {
            id = "cwr/part1"
            type = "MediaObject"
            content = JsonObject().apply {
                addProperty("@type", "MediaObject")
                addProperty("@id", "cwr/part1")
            }
        }
        val createAction = CordraObject().apply {
            id = "cwr/createAction"
            type = "CreateAction"
            content = JsonObject().apply {
                addProperty("@type", "Action")
                addProperty("@id", "cwr/createAction")
            }
        }
        val objectMap = arrayOf(datasetObject, fileObject, workflowObject, progLang, authorObject, argObject, createAction).map { it.id to it }.toMap()

        val cordraClient: CordraClient = mockk()
        every { cordraClient.get(any<Collection<String>>()) } answers {
            firstArg<Collection<String>>()
                .map { objectMap[it]!! }.toList()
                .let { SearchResultsFromIterator(it.size, it.iterator()) }
        }

        val graph = resolveCordraObjectsRecursively(datasetObject, cordraClient, workflowOnly = true)
        // Graph should only contain workflow related objects
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertTrue(graph.containsKey("cwr/workflow"))
        assertTrue(graph.containsKey("cwr/proglang"))
        assertTrue(graph.containsKey("cwr/author1"))
        assertTrue(graph.containsKey("cwr/arg1"))
        assertEquals(5, graph.size)
        verifySequence {
            cordraClient.get(listOf("cwr/author1", "cwr/workflow"))
            cordraClient.get(listOf("cwr/arg1", "cwr/proglang"))
        }
    }

    @Test
    fun testResolveNestedWorkflowGraph() {
        val datasetObject = CordraObject().apply {
            id = "cwr/dataset1"
            type = "Dataset"
            content = JsonObject().apply {
                addProperty("@type", "Dataset")
                addProperty("@id", "cwr/dataset1")
                add("hasPart", JsonArray().apply {
                    add(JsonPrimitive("cwr/part1"))
                    add(JsonPrimitive("cwr/workflow"))
                })
                add("mentions", JsonArray().apply {
                    add(JsonPrimitive("cwr/createAction"))
                })
                add("author", JsonArray().apply {
                    add(JsonPrimitive("cwr/author1"))
                })
                addProperty("mainEntity", "cwr/workflow")
            }
        }
        val workflowObject = CordraObject().apply {
            id = "cwr/workflow"
            type = "Workflow"
            content = JsonObject().apply {
                add("@type", JsonArray().apply {
                    add(JsonPrimitive("ComputationalWorkflow"))
                    add(JsonPrimitive("MediaObject"))
                })
                addProperty("@id", "cwr/workflow")
                add("input", JsonArray().apply {
                    add(JsonPrimitive("cwr/arg1"))
                })
                addProperty("programmingLanguage", "cwr/proglang")
            }
        }
        val progLang = CordraObject().apply {
            id = "cwr/proglang"
            type = "ProgrammingLanguage"
            content = JsonObject().apply {
                addProperty("@type", "ComputerLanguage")
                addProperty("@id", "cwr/proglang")
            }
        }
        val authorObject = CordraObject().apply {
            id = "cwr/author1"
            type = "Person"
            content = JsonObject().apply {
                addProperty("@type", "Person")
                addProperty("@id", "cwr/author1")
                addProperty("name", "John Doe")
            }
        }
        val argObject = CordraObject().apply {
            id = "cwr/arg1"
            type = "FormalParameter"
            content = JsonObject().apply {
                addProperty("@type", "FormalParameter")
                addProperty("@id", "cwr/arg1")
            }
        }
        val fileObject = CordraObject().apply {
            id = "cwr/part1"
            type = "MediaObject"
            content = JsonObject().apply {
                addProperty("@type", "MediaObject")
                addProperty("@id", "cwr/part1")
            }
        }
        val createAction = CordraObject().apply {
            id = "cwr/createAction"
            type = "CreateAction"
            content = JsonObject().apply {
                addProperty("@type", "Action")
                addProperty("@id", "cwr/createAction")
            }
        }
        val objectMap = arrayOf(datasetObject, fileObject, workflowObject, progLang, authorObject, argObject, createAction).map { it.id to it }.toMap()

        val cordraClient: CordraClient = mockk()
        every { cordraClient.get(any<Collection<String>>()) } answers {
            firstArg<Collection<String>>()
                .map { objectMap[it]!! }.toList()
                .let { SearchResultsFromIterator(it.size, it.iterator()) }
        }

        val graph = resolveCordraObjectsRecursively(datasetObject, cordraClient, nested = true, workflowOnly = true)
        // Graph should only contain workflow related objects
        assertTrue(graph.containsKey("cwr/dataset1"))
        assertTrue(graph.containsKey("cwr/workflow"))
        assertTrue(graph.containsKey("cwr/proglang"))
        assertTrue(graph.containsKey("cwr/author1"))
        assertTrue(graph.containsKey("cwr/arg1"))
        assertEquals(5, graph.size)
        verifySequence {
            cordraClient.get(listOf("cwr/author1", "cwr/workflow"))
            cordraClient.get(listOf("cwr/arg1", "cwr/proglang"))
        }
    }
}