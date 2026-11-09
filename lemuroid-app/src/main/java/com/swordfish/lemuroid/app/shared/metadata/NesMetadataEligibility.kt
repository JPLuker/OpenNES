package com.swordfish.lemuroid.app.shared.metadata

import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * An online result only adds previously missing data. The sole exception is
 * upgrading an old, Wikidata-sourced stub to its verified Wikipedia summary.
 * User-edited descriptions have no Wikidata source and are never replaced.
 */
fun Game.needsOnlineMetadata(candidate: WikidataNesMetadataSource.Result.Match): Boolean {
    if (genre.isNullOrBlank() && !candidate.genre.isNullOrBlank()) return true
    if (releaseYear == null && candidate.releaseYear != null) return true
    if (description.isNullOrBlank() && !candidate.description.isNullOrBlank()) return true

    return wikidataEntityId != null &&
        wikipediaArticleTitle == null &&
        candidate.wikipediaArticleTitle != null
}
