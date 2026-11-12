package com.swordfish.lemuroid.app.shared.diagnostics

import com.swordfish.lemuroid.lib.game.GameLoaderError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class OpenNesFailureCategoryTest {
    @Test
    fun reportsTypedLoaderFailuresWithoutInspectingErrorMessages() {
        assertEquals(OpenNesFailureCategory.CORE, OpenNesFailureCategory.fromLoader(GameLoaderError.LoadCore))
        assertEquals(OpenNesFailureCategory.ROM, OpenNesFailureCategory.fromLoader(GameLoaderError.LoadGame))
        assertEquals(OpenNesFailureCategory.SAVE, OpenNesFailureCategory.fromLoader(GameLoaderError.Saves))
        assertEquals(OpenNesFailureCategory.GRAPHICS, OpenNesFailureCategory.fromLoader(GameLoaderError.GLIncompatible))
        assertEquals(OpenNesFailureCategory.UNEXPECTED, OpenNesFailureCategory.fromLoader(GameLoaderError.Generic))
    }

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
