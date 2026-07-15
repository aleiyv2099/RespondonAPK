package com.mickaell.respondon

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekClientTest {

    @Test
    fun parseAnswerExtractsContent() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"  La respuesta es 42.  "}}]}
        """.trimIndent()
        assertEquals("La respuesta es 42.", DeepSeekClient.parseAnswer(json))
    }
}
