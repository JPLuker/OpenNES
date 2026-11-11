package com.swordfish.lemuroid.app.shared.diagnostics

import java.io.IOException

/**
 * Support-safe error categories. Never copy Throwable.message, stack traces,
 * file paths, server URLs, tokens or arbitrary response bodies into UI.
 */
internal enum class OpenNesFailureCategory {
    ACCESS,
    IO,
    INVALID_INPUT,
    UNEXPECTED,
    ;

    companion object {
        fun classify(exception: Throwable): OpenNesFailureCategory =
            when (exception) {
                is SecurityException -> ACCESS
                is IOException -> IO
                is IllegalArgumentException -> INVALID_INPUT
                else -> UNEXPECTED
            }
    }
}
