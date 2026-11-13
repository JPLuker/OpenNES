package com.swordfish.lemuroid.app.shared.game

/**
 * Strictly parse external shortcut IDs without trusting arbitrary intent
 * paths. A malformed or stale shortcut must never crash game startup.
 */
internal object GameShortcutId {
    fun parse(lastPathSegment: String?): Int? {
        if (lastPathSegment.isNullOrBlank() ||
            lastPathSegment.length > 10 ||
            !lastPathSegment.all(Char::isDigit)
        ) {
            return null
        }
        return lastPathSegment.toIntOrNull()?.takeIf { it > 0 }
    }
}
