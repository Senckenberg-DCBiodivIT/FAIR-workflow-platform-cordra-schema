package de.senckenberg.cwr

import com.google.gson.JsonObject
import io.mockk.*
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ROCrateTest {

    @Test
    fun testDeserializeZip() {
        val mockCordra = mockk<CordraClient>()
        every { mockCordra.create(any<CordraObject>()) } answers {
            val cordraObject = firstArg<CordraObject>()
            cordraObject.id = "testprefix/${UUID.randomUUID()}"
            cordraObject
        }
        every { mockCordra.get(any<String>()) } returns CordraObject("FileObject", JsonObject())
        every { mockCordra.update(any<CordraObject>()) } answers { firstArg() }

        val deserializer = ROCrate(mockCordra)

        deserializer.deserializeCrate(Path.of("src/test/resources/testcrate"))

        // verify that the number of objects where created
        verifyAll {
            mockCordra.create(withArg {
                assertEquals("Organization", it.type,"Organization")
                assertEquals("University of Oslo", it.content.asJsonObject["name"].asString)
                assertEquals("https://ror.org/01xtthb56", it.content.asJsonObject["identifier"].asString)
            })
            mockCordra.create(withArg {
                assertEquals("FileObject", it.type)
                assertEquals("OUT_Binary.png", it.content.asJsonObject["name"].asString)
                assertTrue { it.payloads.size == 1 }
            })
            mockCordra.create(withArg {
                assertEquals("Person", it.type)
                assertEquals("Erik Kusch", it.content.asJsonObject["name"].asString)
                assertEquals("https://orcid.org/0000-0002-4984-7646", it.content.asJsonObject["identifier"].asString)
                assertTrue { it.content.asJsonObject["affiliation"].asJsonArray.size() == 1 }
            })
            mockCordra.create(withArg {
                assertEquals("CreateAction", it.type)
                assertTrue { it.content.asJsonObject.get("agent")!!.asString.startsWith("testprefix/") }
                assertTrue { it.content.asJsonObject.has("instrument") }
                assertTrue { it.content.asJsonObject["result"].asJsonArray.size() == 1 }
            })
            mockCordra.create(withArg {
                assertEquals("Dataset", it.type)
                assertTrue { it.content.asJsonObject["author"].asJsonArray.size() == 1 }
                assertTrue { it.content.asJsonObject["about"].asJsonArray.first().asString.contains("gbif") }
                assertTrue { it.content.asJsonObject["description"].asString.startsWith("ModGP") }
                assertTrue { it.content.asJsonObject["hasPart"].asJsonArray.size() == 1 }
                assertTrue { it.content.asJsonObject["keywords"].asJsonArray.size() > 1 }
                assertTrue { it.content.asJsonObject["mentions"].asJsonArray.size() == 1 }
                assertTrue { it.content.asJsonObject["license"].asString == "https://creativecommons.org/licenses/by/4.0/" }
            })

            mockCordra.get(any<String>())
            mockCordra.update(withArg {
                assertEquals("FileObject", it.type)
                assertTrue { it.content.asJsonObject.get("partOf").asJsonArray.size() == 1 }
                assertTrue { it.content.asJsonObject.has("resultOf") }
            })
        }
    }
}