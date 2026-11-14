package com.swordfish.lemuroid.app.shared.romm

import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySyncPlan.ConflictReason
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySyncPlan.SkipReason
import com.swordfish.lemuroid.app.shared.romm.RommTwoWaySyncPlan.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class RommTwoWaySyncPlanTest {
    private val a = "a".repeat(32)
    private val b = "b".repeat(32)
    private val c = "c".repeat(32)

    private fun op(
        action: RommDeviceSyncProtocol.Action,
        saveId: Long? = 9,
        server: String? = null,
    ) = RommDeviceSyncProtocol.Operation(action, 3, saveId, "Zelda.srm", "autosave", server)

    private fun plan(
        action: RommDeviceSyncProtocol.Action,
        local: String?,
        baseline: String?,
        server: String?,
        saveId: Long? = 9,
    ) = RommTwoWaySyncPlan.plan(op(action, saveId, server), local, baseline)

    // Uploads

    @Test
    fun progressMadeOnThePhoneSinceTheLastSyncIsUploaded() {
        assertEquals(Step.Upload(b), plan(RommDeviceSyncProtocol.Action.UPLOAD, local = b, baseline = a, server = a))
    }

    @Test
    fun aGameTheServerHasNeverSeenIsUploaded() {
        assertEquals(
            Step.Upload(a),
            plan(RommDeviceSyncProtocol.Action.UPLOAD, local = a, baseline = null, server = null, saveId = null),
        )
    }

    @Test
    fun aTimestampOnlyUploadOverAnUnpairedServerSaveIsAskedNotDone() {
        // RomM: "Client save is newer (no sync history)". Wrong phone clock
        // would otherwise overwrite the PC's progress on the server.
        assertEquals(
            Step.Conflict(9, b, a, ConflictReason.NO_SHARED_HISTORY),
            plan(RommDeviceSyncProtocol.Action.UPLOAD, local = b, baseline = null, server = a),
        )
    }

    @Test
    fun anUploadOfTheSaveAlreadySyncedDoesNothing() {
        assertEquals(Step.Nothing, plan(RommDeviceSyncProtocol.Action.UPLOAD, local = a, baseline = a, server = b))
    }

    @Test
    fun anUploadWithNoSaveOnThePhoneIsSkipped() {
        assertEquals(
            Step.Skip(SkipReason.NO_LOCAL_SAVE),
            plan(RommDeviceSyncProtocol.Action.UPLOAD, local = null, baseline = a, server = a),
        )
    }

    // Downloads

    @Test
    fun aSaveMadeElsewhereDownloadsWhenThePhoneHasNone() {
        assertEquals(Step.Download(9, a), plan(RommDeviceSyncProtocol.Action.DOWNLOAD, null, null, server = a))
    }

    @Test
    fun aSaveMadeElsewhereDownloadsWhenThePhoneIsUnchangedSinceTheLastSync() {
        assertEquals(Step.Download(9, b), plan(RommDeviceSyncProtocol.Action.DOWNLOAD, a, a, server = b))
    }

    @Test
    fun aDownloadNeverReplacesProgressMadeOnThePhone() {
        assertEquals(
            Step.Conflict(9, c, b, ConflictReason.PHONE_CHANGED_TOO),
            plan(RommDeviceSyncProtocol.Action.DOWNLOAD, local = c, baseline = a, server = b),
        )
    }

    @Test
    fun aTimestampOnlyDownloadOverAnUnpairedPhoneSaveIsAsked() {
        assertEquals(
            Step.Conflict(9, c, b, ConflictReason.NO_SHARED_HISTORY),
            plan(RommDeviceSyncProtocol.Action.DOWNLOAD, local = c, baseline = null, server = b),
        )
    }

    @Test
    fun aDownloadWithoutTheServerHashIsNotTrusted() {
        assertEquals(
            Step.Skip(SkipReason.INCOMPLETE_ANSWER),
            plan(RommDeviceSyncProtocol.Action.DOWNLOAD, null, null, server = null),
        )
        assertEquals(
            Step.Skip(SkipReason.INCOMPLETE_ANSWER),
            plan(RommDeviceSyncProtocol.Action.DOWNLOAD, null, null, server = a, saveId = null),
        )
    }

    // Identical content, conflicts, deletions

    @Test
    fun identicalContentOnBothSidesBecomesTheBaselineWhateverRommSuggested() {
        for (action in RommDeviceSyncProtocol.Action.entries.filter { it != RommDeviceSyncProtocol.Action.DELETE }) {
            assertEquals(action.name, Step.RecordBaseline(a), plan(action, local = a, baseline = null, server = a))
        }
    }

    @Test
    fun rommConflictsAreAskedAndCarryBothHashes() {
        assertEquals(
            Step.Conflict(9, b, c, ConflictReason.BOTH_CHANGED),
            plan(RommDeviceSyncProtocol.Action.CONFLICT, local = b, baseline = a, server = c),
        )
    }

    @Test
    fun serverDeletionsNeverTouchThePhone() {
        assertEquals(
            Step.Skip(SkipReason.SERVER_DELETED),
            plan(RommDeviceSyncProtocol.Action.DELETE, local = a, baseline = a, server = null, saveId = null),
        )
    }

    @Test
    fun malformedHashesAreTreatedAsMissing() {
        // An upper-case or truncated value never matches a real baseline.
        assertEquals(
            Step.Skip(SkipReason.NO_LOCAL_SAVE),
            plan(RommDeviceSyncProtocol.Action.UPLOAD, local = "ABC", baseline = a, server = a),
        )
        assertEquals(
            Step.Download(9, b),
            plan(RommDeviceSyncProtocol.Action.DOWNLOAD, local = null, baseline = "nope", server = b),
        )
    }
}
