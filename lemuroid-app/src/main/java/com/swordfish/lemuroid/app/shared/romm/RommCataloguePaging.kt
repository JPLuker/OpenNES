package com.swordfish.lemuroid.app.shared.romm

/**
 * A RomM response may contain files OpenNES deliberately excludes (.bin,
 * .txt, multi-file sets, other systems). Only the raw API row count can
 * advance the offset safely. Never use filtered items.size for pagination.
 */
internal object RommCataloguePaging {
    fun nextOffset(
        requestedOffset: Int,
        page: RommApiClient.Page,
        maxRows: Int = 10_000,
    ): Int? {
        require(requestedOffset >= 0 && requestedOffset == page.offset)
        require(page.limit in 1..100)
        require(page.rawCount in 0..page.limit)
        require(page.total == -1 || page.total in 0..maxRows)
        require(requestedOffset <= maxRows)
        val next = requestedOffset + page.rawCount
        require(next <= maxRows) { "RomM catalogue exceeds size limit" }
        if (page.total >= 0) {
            require(next <= page.total) { "RomM returned inconsistent page totals" }
        }
        if (page.rawCount == 0) {
            require(page.total == -1 || requestedOffset >= page.total) {
                "RomM returned an empty page before its reported end"
            }
            return null
        }
        if (page.total >= 0 && next >= page.total) return null
        require(next < maxRows) { "RomM catalogue exceeds size limit" }
        return next
    }
}
