package com.swordfish.lemuroid.app.shared.romm

/**
 * Turns one RomM negotiation operation into what this phone will actually do.
 *
 * RomM decides from timestamps when a device has no sync history, so a wrong
 * clock or file date could quietly replace progress. This plan adds the
 * phone's own record of the last save it synced for each game (the baseline)
 * and only lets a transfer proceed when one side is provably unchanged.
 * Everything else becomes a conflict the player resolves.
 *
 * Pure: no files, no network. Hashes are lower-case MD5, as RomM uses.
 */
internal object RommTwoWaySyncPlan {
    sealed interface Step {
        /** Nothing to transfer and nothing new to remember. */
        data object Nothing : Step

        /** Both sides already hold [md5]; remember it as the baseline. */
        data class RecordBaseline(val md5: String) : Step

        /** Send this phone's save, which must still hash to [md5]. */
        data class Upload(val md5: String) : Step

        /** Replace this phone's save with server save [saveId], which must hash to [serverMd5]. */
        data class Download(val saveId: Long, val serverMd5: String) : Step

        /** Both sides differ and neither is provably unchanged: ask the player. */
        data class Conflict(
            val saveId: Long?,
            val localMd5: String?,
            val serverMd5: String?,
            val reason: ConflictReason,
        ) : Step

        /** The server asked for something this phone never does on its own. */
        data class Skip(val reason: SkipReason) : Step
    }

    enum class ConflictReason {
        /** RomM reported changes on both sides. */
        BOTH_CHANGED,

        /** Both have a different save and this phone has never synced the game. */
        NO_SHARED_HISTORY,

        /** The server has newer progress, but so does this phone. */
        PHONE_CHANGED_TOO,

        /** RomM refused the upload because the server moved on meanwhile. */
        SERVER_CHANGED_DURING_UPLOAD,
    }

    enum class SkipReason {
        /** Server-side deletions never delete a save on the phone. */
        SERVER_DELETED,

        /** The server asked for an upload, but there is no save on the phone. */
        NO_LOCAL_SAVE,

        /** The server's answer lacked the save or hash needed to act safely. */
        INCOMPLETE_ANSWER,
    }

    private val md5Pattern = Regex("[0-9a-f]{32}")

    fun plan(
        operation: RommDeviceSyncProtocol.Operation,
        localMd5: String?,
        baselineMd5: String?,
    ): Step {
        val local = localMd5?.takeIf { it.matches(md5Pattern) }
        val baseline = baselineMd5?.takeIf { it.matches(md5Pattern) }
        val server = operation.serverMd5?.takeIf { it.matches(md5Pattern) }
        return when (operation.action) {
            RommDeviceSyncProtocol.Action.NO_OP ->
                if (local != null && local == server) Step.RecordBaseline(local) else Step.Nothing

            RommDeviceSyncProtocol.Action.UPLOAD ->
                when {
                    local == null -> Step.Skip(SkipReason.NO_LOCAL_SAVE)
                    server != null && local == server -> Step.RecordBaseline(local)
                    // RomM offers an upload over a paired server save by
                    // timestamp alone when there is no history: never trust it.
                    operation.saveId != null && baseline == null ->
                        Step.Conflict(operation.saveId, local, server, ConflictReason.NO_SHARED_HISTORY)
                    // The phone's save is the one already synced: nothing new.
                    baseline != null && local == baseline -> Step.Nothing
                    else -> Step.Upload(local)
                }

            RommDeviceSyncProtocol.Action.DOWNLOAD -> {
                val saveId = operation.saveId
                when {
                    saveId == null || server == null -> Step.Skip(SkipReason.INCOMPLETE_ANSWER)
                    local == null -> Step.Download(saveId, server)
                    local == server -> Step.RecordBaseline(local)
                    baseline != null && local == baseline -> Step.Download(saveId, server)
                    baseline == null ->
                        Step.Conflict(saveId, local, server, ConflictReason.NO_SHARED_HISTORY)
                    else -> Step.Conflict(saveId, local, server, ConflictReason.PHONE_CHANGED_TOO)
                }
            }

            RommDeviceSyncProtocol.Action.CONFLICT ->
                if (local != null && local == server) {
                    Step.RecordBaseline(local)
                } else {
                    Step.Conflict(operation.saveId, local, server, ConflictReason.BOTH_CHANGED)
                }

            RommDeviceSyncProtocol.Action.DELETE -> Step.Skip(SkipReason.SERVER_DELETED)
        }
    }
}
