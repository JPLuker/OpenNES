package com.swordfish.lemuroid.app.shared.diagnostics

import com.swordfish.lemuroid.lib.game.GameLoaderError
import java.io.IOException

/**
 * Support-safe error categories. Never copy Throwable.message, stack traces,
 * file paths, server URLs, tokens or arbitrary response bodies into UI.
 */
internal enum class OpenNesFailureCategory {
    ACCESS,
    IO,
    INVALID_INPUT,
    CORE,
    ROM,
    SAVE,
    GRAPHICS,
    UNEXPECTED,
    ;

    companion object {
        /** Only typed failure reasons cross the process boundary; never raw messages or paths. */
        fun fromLoader(error: GameLoaderError): OpenNesFailureCategory =
            when (error) {
                GameLoaderError.LoadCore,
                GameLoaderError.UnsupportedArchitecture,
                -> CORE
                GameLoaderError.LoadGame,
                is GameLoaderError.MissingBiosFiles,
                -> ROM
                GameLoaderError.Saves -> SAVE
                GameLoaderError.GLIncompatible -> GRAPHICS
                GameLoaderError.Generic -> UNEXPECTED
            }

        fun classify(exception: Throwable): OpenNesFailureCategory =
            when (exception) {
                is SecurityException -> ACCESS
                is IOException -> IO
                is IllegalArgumentException -> INVALID_INPUT
                else -> UNEXPECTED
            }
    }
}
