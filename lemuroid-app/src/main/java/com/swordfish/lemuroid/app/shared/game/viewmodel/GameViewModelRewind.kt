package com.swordfish.lemuroid.app.shared.game.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.swordfish.lemuroid.app.shared.achievements.HardcoreRuntime
import com.swordfish.lemuroid.app.shared.achievements.HardcoreSessionPolicy
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Bounded Casual-only rewind. Captures only volatile emulator states, never
 * overwrites on-disk saves and drops abandoned future snapshots on restore.
 */
@OptIn(FlowPreview::class)
internal class GameViewModelRewind(
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val history = BoundedRewindHistory()
    private val stateMutex = Mutex()
    private var captureJob: Job? = null

    override fun onResume(owner: LifecycleOwner) {
        captureJob?.cancel()
        captureJob =
            scope.launch {
                val view = retroGameView.retroGameViewFlow()
                view.getGLRetroEvents()
                    .filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>()
                    .sample(1000L)
                    .collect {
                        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.REWIND)) {
                            history.clear()
                            return@collect
                        }
                        try {
                            stateMutex.withLock {
                                // FCEUmm serialization is synchronized by the
                                // native core mutex; don't block a paused GL thread.
                                val state =
                                    withContext(Dispatchers.IO) {
                                        view.serializeState(useEmulationThread = false)
                                    }
                                history.record(state)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            history.clear()
                            Timber.w(e, "Rewind snapshot capture failed")
                        }
                    }
            }
    }

    override fun onPause(owner: LifecycleOwner) {
        captureJob?.cancel()
        captureJob = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        captureJob?.cancel()
        captureJob = null
        history.clear()
    }

    fun canRewind(): Boolean =
        HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.REWIND) &&
            history.canRewind(REWIND_INTERVALS)

    suspend fun rewind(): Boolean {
        if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.REWIND)) return false
        return stateMutex.withLock {
            // Recheck at the native call boundary, not only in the pause UI.
            if (!HardcoreRuntime.allow(HardcoreSessionPolicy.Operation.REWIND)) return@withLock false
            val target = history.candidate(REWIND_INTERVALS) ?: return@withLock false
            val view = retroGameView.retroGameView ?: return@withLock false
            try {
                val restored =
                    withContext(Dispatchers.IO) {
                        view.unserializeState(target.state, useEmulationThread = false)
                    }
                if (restored) history.commit(target) else false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Rewind restore failed")
                false
            }
        }
    }

    fun clear() {
        history.clear()
    }

    companion object {
        // Approximately five seconds of active play at 1 Hz sampling.
        private const val REWIND_INTERVALS = 5
    }
}
