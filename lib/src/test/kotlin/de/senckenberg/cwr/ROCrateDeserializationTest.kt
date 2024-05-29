package de.senckenberg.cwr

import com.google.gson.JsonElement
import org.junit.jupiter.api.Test
import io.mockk.mockk
import io.mockk.every
import net.cnri.cordra.CordraHooksSupport
import net.cnri.cordra.CordraHooksSupportProvider
import net.cnri.cordra.api.CordraClient
import net.cnri.cordra.api.CordraObject

class ROCrateDeserializationTest {

    @Test
    fun deserializeROCrate() {
        // mock cordra client that accepts created objects
        val mockCordraClient = mockk<CordraClient>()
        every { mockCordraClient.create(any<String>(), any<JsonElement>()) } answers {
            val mockAnswer = mockk<CordraObject>()
            mockAnswer.content = secondArg()
            mockAnswer.id = "testId"
            mockAnswer
        }
        every { mockCordraClient.create(any<CordraObject>()) } answers {
            val mockAnswer = mockk<CordraObject>()
            mockAnswer.content = firstArg<CordraObject>().content
            mockAnswer.id = "testId"
            mockAnswer
        }

        // prepare hook support for tests
        val hookSupport = mockk<CordraHooksSupport>()
        every { hookSupport.getCordraClient() } returns mockCordraClient
        CordraHooksSupportProvider.set(hookSupport)

        // prepare request
//        val mockHookContext: HooksContext = mockk<HooksContext>()
//        val testFileContent = this.javaClass.getResource("/ro-crate-metadata.json").readText()
//        val json = JsonParser.parseString(testFileContent).asJsonObject
//        every { mockHookContext.params } returns json
//
//        // call function and validate result
//        val result = DatasetType.fromROCrate(mockHookContext)
//        print(result)

    }

}