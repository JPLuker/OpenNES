package com.swordfish.lemuroid.app.shared.romm

import android.content.Context
import android.util.AtomicFile
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Syncs the four save-state slots of each matched game with RomM, as
 * `Game.state1` … `Game.state4` under the `fceumm` emulator (RetroArch's
 * naming, raw core state bytes). Runs as part of save sync, after battery
 * saves, for games that are not running.
 *
 * A state replaced on the phone is first copied to a private folder (one
 * copy per slot), so a wrong overwrite can be undone by hand. Only Casual
 * states are synced; the autosave ("Resume") state stays on the phone.
 */
internal object RommStateSlotSync {
    data class Counts(val uploaded: Int = 0, val downloaded: Int = 0, val failed: Int = 0)

    private const val BASELINE_FILE = "romm-state-slots-v1.json"
    private const val REPLACED_DIR = "romm-replaced-states"
    private const val MAX_ROMS_PER_LIST = 50
    private const val MAX_BASELINES = 4_000

    suspend fun sync(
        context: Context,
        connection: RommConnection,
        owner: String,
        pairs: List<Pair<Game, RommApiClient.Rom>>,
        client: RommApiClient,
    ): Counts {
        if (pairs.isEmpty()) return Counts()
        val app = context.applicationContext
        val states = StatesManager(DirectoriesManager(app))
        val store = AtomicFile(File(app.filesDir, BASELINE_FILE))
        val baselines = withContext(Dispatchers.IO) { readBaselines(store, owner) }
        var uploaded = 0
        var downloaded = 0
        var failed = 0

        for (chunk in pairs.chunked(MAX_ROMS_PER_LIST)) {
            val remote = client.listSlotStates(connection, chunk.map { it.second.id })
            for ((game, rom) in chunk) {
                val info = states.getSavedSlotsInfo(game, CoreID.FCEUMM)
                for (slot in 1..RommStateSlotPlan.SLOTS) {
                    val server = remote[rom.id to slot]
                    val hasLocal = info.getOrNull(slot - 1)?.exists == true
                    if (!hasLocal && server == null) continue
                    val key = "${rom.id}:$slot"
                    try {
                        val local = if (hasLocal) states.getSlotSave(game, CoreID.FCEUMM, slot - 1) else null
                        val localMd5 = local?.let { RommLocalSaveFiles.md5(it.state) }
                        var action = RommStateSlotPlan.plan(localMd5, server?.stamp, baselines[key])
                        var fetched: ByteArray? = null
                        if (action == RommStateSlotPlan.Action.COMPARE && server != null && localMd5 != null) {
                            fetched = client.downloadSlotState(connection, server.id)
                            action =
                                RommStateSlotPlan.resolve(
                                    localMd5,
                                    RommLocalSaveFiles.md5(fetched),
                                    info[slot - 1].date,
                                    server.updatedAtMillis,
                                )
                            if (action == RommStateSlotPlan.Action.NOTHING) {
                                baselines[key] = RommStateSlotPlan.Baseline(localMd5, server.stamp)
                            }
                        }
                        when (action) {
                            RommStateSlotPlan.Action.UPLOAD -> {
                                val stored =
                                    client.uploadSlotState(
                                        connection,
                                        rom.id,
                                        RommStateSlotPlan.remoteFileName(rom.fileName, slot),
                                        local!!.state,
                                    )
                                baselines[key] = RommStateSlotPlan.Baseline(localMd5!!, stored?.stamp)
                                uploaded++
                            }
                            RommStateSlotPlan.Action.DOWNLOAD -> {
                                val bytes = fetched ?: client.downloadSlotState(connection, server!!.id)
                                withContext(Dispatchers.IO) {
                                    local?.let { keepReplaced(app, game, slot, it.state) }
                                }
                                states.setSlotSave(
                                    game,
                                    SaveState(bytes, SaveState.Metadata()),
                                    CoreID.FCEUMM,
                                    slot - 1,
                                )
                                baselines[key] =
                                    RommStateSlotPlan.Baseline(
                                        RommLocalSaveFiles.md5(bytes),
                                        server!!.stamp,
                                    )
                                downloaded++
                            }
                            else -> Unit
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One slot's failure never stops the others.
                        Timber.w("RomM state sync skipped a slot: %s", e.javaClass.simpleName)
                        failed++
                    }
                }
            }
        }
        withContext(Dispatchers.IO) { writeBaselines(store, owner, baselines) }
        return Counts(uploaded, downloaded, failed)
    }

    private fun keepReplaced(
        app: Context,
        game: Game,
        slot: Int,
        bytes: ByteArray,
    ) {
        val dir = File(app.noBackupFilesDir, REPLACED_DIR)
        if (!dir.mkdirs() && !dir.isDirectory) return
        runCatching { File(dir, "game-${game.id}-slot$slot.state").writeBytes(bytes) }
    }

    private fun readBaselines(
        store: AtomicFile,
        owner: String,
    ): MutableMap<String, RommStateSlotPlan.Baseline> {
        val result = LinkedHashMap<String, RommStateSlotPlan.Baseline>()
        runCatching {
            val root = JSONObject(String(store.readFully(), Charsets.UTF_8))
            // Another server or account: its record says nothing about this one.
            if (root.optString("owner") != owner) return result
            val slots = root.optJSONObject("slots") ?: return result
            slots.keys().forEach { key ->
                val row = slots.optJSONObject(key) ?: return@forEach
                val md5 = row.optString("md5").takeIf { it.matches(Regex("[0-9a-f]{32}")) } ?: return@forEach
                val stamp = if (row.isNull("stamp")) null else row.optString("stamp")
                result[key] = RommStateSlotPlan.Baseline(md5, stamp)
            }
        }
        return result
    }

    private fun writeBaselines(
        store: AtomicFile,
        owner: String,
        baselines: Map<String, RommStateSlotPlan.Baseline>,
    ) {
        val slots = JSONObject()
        baselines.entries.toList().takeLast(MAX_BASELINES).forEach { (key, value) ->
            slots.put(key, JSONObject().put("md5", value.md5).put("stamp", value.remoteStamp ?: JSONObject.NULL))
        }
        val text = JSONObject().put("owner", owner).put("slots", slots).toString()
        val stream = store.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            store.finishWrite(stream)
        } catch (e: Exception) {
            store.failWrite(stream)
            throw e
        }
    }
}
