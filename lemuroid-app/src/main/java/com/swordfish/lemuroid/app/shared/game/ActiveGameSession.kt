package com.swordfish.lemuroid.app.shared.game

import android.content.Context
import android.util.AtomicFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.json.JSONObject
import java.io.File

/**
 * Small cross-process session marker. Reads are gated by the emulator-process
 * file lock, so a crashed or killed game can never leave a false Running badge.
 * GameService (the emulator process) writes and clears this marker; the main
 * process only reads it. No ROM paths, saves or credentials are stored.
 */
object ActiveGameSession {
    data class Running(
        val gameId: Int,
        val title: String,
    )

    private fun file(context: Context) = AtomicFile(File(context.filesDir, "active_game_session.json"))

    fun record(
        context: Context,
        game: Game,
    ) {
        if (game.id <= 0) return
        runCatching {
            val json = JSONObject().put("id", game.id).put("title", game.displayName())
            val destination = file(context)
            val stream = destination.startWrite()
            try {
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                destination.finishWrite(stream)
            } catch (e: Exception) {
                destination.failWrite(stream)
            }
        }
        // Session marker failures must never interrupt running a game.

    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun current(context: Context): Running? {
        val active =
            runCatching { GameProcessLock.isHeldByAnotherProcess(context.applicationContext) }
                .getOrDefault(false)
        if (!active) return null
        return runCatching {
            val json = JSONObject(file(context).openRead().bufferedReader().use { it.readText() })
            val id = json.getInt("id")
            val title = json.getString("title").take(120)
            if (id <= 0 || title.isBlank()) null else Running(id, title)
        }.getOrNull()
    }
}
