package com.swordfish.lemuroid.app.shared.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RommCataloguePagingTest {
    private fun page(
        offset: Int,
        raw: Int,
        total: Int,
        visible: Int = raw,
        limit: Int = 100,
    ): RommApiClient.Page =
        RommApiClient.Page(
            items =
                (0 until visible).map {
                    RommApiClient.Rom(it + 1, 1, "Test", "Test.nes", 123L, null)
                },
            total = total,
            offset = offset,
            limit = limit,
            rawCount = raw,
        )

    @Test
    fun filteredPageCannotPrematurelyEndNESCatalogue() {
        // RomM returned 100 rows; OpenNES kept only 3 valid NES files.
        val first = page(offset = 0, raw = 100, total = 230, visible = 3)
        assertEquals(100, RommCataloguePaging.nextOffset(0, first))
        assertEquals(200, RommCataloguePaging.nextOffset(100, page(100, 100, 230, visible = 0)))
        assertNull(RommCataloguePaging.nextOffset(200, page(200, 30, 230, visible = 5)))
    }

    @Test
    fun serverClampedPageSizeUsesActuallyReturnedRowCount() {
        assertEquals(40, RommCataloguePaging.nextOffset(0, page(0, 40, 120)))
        assertEquals(80, RommCataloguePaging.nextOffset(40, page(40, 40, 120)))
    }

    @Test
    fun missingTotalPaginatesUntilARealEmptyPage() {
        assertEquals(25, RommCataloguePaging.nextOffset(0, page(0, 25, -1)))
        assertNull(RommCataloguePaging.nextOffset(25, page(25, 0, -1)))
    }

    @Test
    fun inconsistentPageMetadataIsRejectedInsteadOfDroppingFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            RommCataloguePaging.nextOffset(100, page(0, 80, 400))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommCataloguePaging.nextOffset(0, page(0, 0, 300))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommCataloguePaging.nextOffset(100, page(100, 50, 125))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RommCataloguePaging.nextOffset(0, page(0, 101, 500))
        }
    }
}
