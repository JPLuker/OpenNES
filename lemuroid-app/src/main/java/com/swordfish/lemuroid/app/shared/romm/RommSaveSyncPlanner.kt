package com.swordfish.lemuroid.app.shared.romm

/**
 * A non-mutating, fail-closed planner for future opt-in RomM save sync.
 *
 * Snapshots must be hashes of immutable, fully downloaded local/remote
 * content, not server filenames, timestamps or optional RomM content_hash
 * values. Plans require a complete previous baseline, explicit direction,
 * and a verified versioned backup before ANY replacement can be attempted.
 *
 * There is intentionally no network, file deletion or upload method here.
 */
internal object RommSaveSyncPlanner {
    data class Snapshot(
        val sha256: String,
        val bytes: Long,
    ) {
        init {
            require(sha256.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 fingerprint required" }
            require(bytes > 0) { "Empty saves cannot be synchronized" }
        }
    }

    enum class Direction {
        NONE,
        UPLOAD,
        DOWNLOAD,
    }

    data class Request(
        val optedIn: Boolean,
        val online: Boolean,
        val romId: Int,
        val confirmedRomId: Int,
        val previousLocal: Snapshot?,
        val previousRemote: Snapshot?,
        val local: Snapshot?,
        val remote: Snapshot?,
        val direction: Direction = Direction.NONE,
        val approvedThisOperation: Boolean = false,
        val versionedBackupVerified: Boolean = false,
    )

    sealed interface Decision {
        data object Disabled : Decision

        data object RevalidateWhenOnline : Decision

        data object UnverifiedIdentity : Decision

        data object InitialPairingRequired : Decision

        data object InSync : Decision

        data object Conflict : Decision

        data object ExplicitApprovalRequired : Decision

        data object VerifiedBackupRequired : Decision

        /** Callers must recheck both expected hashes immediately before any write. */
        data class UploadCopy(
            val expectedLocal: Snapshot,
            val expectedRemote: Snapshot,
        ) : Decision

        /** Download must stage content and create a local versioned backup first. */
        data class DownloadCopy(
            val expectedLocal: Snapshot,
            val expectedRemote: Snapshot,
        ) : Decision
    }

    fun plan(request: Request): Decision {
        if (!request.optedIn) return Decision.Disabled
        if (!request.online) return Decision.RevalidateWhenOnline
        if (request.romId <= 0 || request.confirmedRomId != request.romId) {
            return Decision.UnverifiedIdentity
        }
        val local = request.local ?: return Decision.InitialPairingRequired
        val remote = request.remote ?: return Decision.InitialPairingRequired
        if (local == remote) return Decision.InSync

        // Initial pairing is a separate, explicit choice. Even if one side
        // looks newer, timestamps do not establish a safe sync ancestry.
        val previousLocal = request.previousLocal ?: return Decision.InitialPairingRequired
        val previousRemote = request.previousRemote ?: return Decision.InitialPairingRequired
        // A common ancestor is mandatory before treating either side as
        // changed independently. A divergent "baseline" is already a
        // conflict, even if only one side changed after it was recorded.
        if (previousLocal != previousRemote) return Decision.Conflict
        val localChanged = local != previousLocal
        val remoteChanged = remote != previousRemote
        if (localChanged == remoteChanged) return Decision.Conflict

        val expectedDirection =
            if (localChanged) {
                Direction.UPLOAD
            } else {
                Direction.DOWNLOAD
            }
        if (request.direction != expectedDirection || !request.approvedThisOperation) {
            return Decision.ExplicitApprovalRequired
        }
        if (!request.versionedBackupVerified) return Decision.VerifiedBackupRequired

        return if (localChanged) {
            Decision.UploadCopy(local, remote)
        } else {
            Decision.DownloadCopy(local, remote)
        }
    }
}
