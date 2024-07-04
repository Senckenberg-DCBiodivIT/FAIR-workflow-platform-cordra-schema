package de.senckenberg.cwr

import io.mockk.*
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraObject
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.*

class ROCrateTest {

    @Test
    fun testDeserializeZip() {
        val mockCordra = mockk<CordraClient>()
        every { mockCordra.create(any<CordraObject>()) } answers {
            val cordraObject = firstArg<CordraObject>()
            cordraObject.id = "testprefix/${UUID.randomUUID()}"
            cordraObject
        }

        val deserializer = ROCrate(mockCordra)

        deserializer.deserializeCrate(Path.of("src/test/resources/testcrate"))

        // verify that the number of objects where created
        verifySequence {
            mockCordra.create(withArg { it.type == "Person" })
            mockCordra.create(withArg { it.type == "FileObject" })
            mockCordra.create(withArg { it.type == "Dataset" })
//            mockCordra.create(withArg { it.type == "CreateAction" })
        }
    }
}