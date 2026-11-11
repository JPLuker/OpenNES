package com.swordfish.lemuroid.app.shared.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class OpenNesFailureCategoryTest {
    @Test
    fun classifiesByTypeWithoutInspectingUntrustedExceptionMessage() {
        assertEquals(
            OpenNesFailureCategory.ACCESS,
            OpenNesFailureCategory.classify(SecurityException("password=PRIVATE")),
        )
        assertEquals(
            OpenNesFailureCategory.IO,
            OpenNesFailureCategory.classify(IOException("https://secret.example.org/token")),
        )
        assertEquals(
            OpenNesFailureCategory.INVALID_INPUT,
            OpenNesFailureCategory.classify(IllegalArgumentException("/private/rom.nes")),
        )
        assertEquals(
            OpenNesFailureCategory.UNEXPECTED,
            OpenNesFailureCategory.classify(IllegalStateException("refresh-token=PRIVATE")),
        )
    }
}
