package de.senckenberg.cwr

import io.mockk.every
import io.mockk.mockk
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
    }
}