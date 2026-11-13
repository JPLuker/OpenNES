package com.swordfish.lemuroid.app.shared.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameShortcutIdTest {
    @Test
    fun acceptsOnlyPositiveDecimalIdentifiers() {
        assertEquals(1, GameShortcutId.parse("1"))
        assertEquals(42, GameShortcutId.parse("42"))
        assertEquals(Int.MAX_VALUE, GameShortcutId.parse("2147483647"))
        assertEquals(7, GameShortcutId.parse("0007"))
    }

    @Test
    fun rejectsBrokenOrUntrustedExternalShortcuts() {
        val invalid =
            listOf(
                null,
                "",
                " ",
                "0",
                "-1",
                "+4",
                "2147483648",
                "12345678901",
                "1/2",
                "4?token=secret",
                "0xFF",
                "١٢",
            )
        invalid.forEach { assertNull(GameShortcutId.parse(it)) }
    }
}
