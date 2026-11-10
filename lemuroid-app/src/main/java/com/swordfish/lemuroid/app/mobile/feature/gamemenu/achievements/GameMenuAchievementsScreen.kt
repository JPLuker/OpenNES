package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuStyle
import com.swordfish.lemuroid.app.mobile.feature.home.NesBoxArtwork
import com.swordfish.lemuroid.app.shared.achievements.NesRetroAchievementsHash
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsDisplayState
import com.swordfish.lemuroid.app.shared.achievements.RetroAchievementsNativeGameResolver
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * In-game RetroAchievements destination, deliberately independent of a browser
 * or the main Settings screen. Only actual native rc_client sessions may
 * provide achievement unlocks/points later; this initial implementation never
 * substitutes dummy game achievements or falsely marks the player signed in.
 */
@Composable
fun GameMenuAchievementsScreen(
    game: Game,
    session: RetroAchievementsDisplayState = RetroAchievementsDisplayState.NotConnected,
) {
    var advancedExpanded by remember { mutableStateOf(false) }
    var identification by remember(game.id, game.fileUri) {
        mutableStateOf<LocalIdentification>(LocalIdentification.Checking)
    }
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var recognizedGame by remember(game.id, game.fileUri) {
        mutableStateOf<RetroAchievementsNativeGameResolver.Result?>(null)
    }
    LaunchedEffect(game.id, game.fileUri) {
        identification = LocalIdentification.Checking
        recognizedGame = null
        identification =
            try {
                LocalIdentification.Identified(NesRetroAchievementsHash.forGame(context, game))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Do not log ROM paths or tokens in a user-visible status.
                Timber.w("RetroAchievements ROM identification unavailable: %s", e.javaClass.simpleName)
                LocalIdentification.Unavailable
            }
        recognizedGame =
            when (val local = identification) {
                is LocalIdentification.Identified ->
                    RetroAchievementsNativeGameResolver().resolve(local.identity)
                else -> RetroAchievementsNativeGameResolver.Result.Unavailable
            }
    }

    // Never show data from a different ROM or a merely local (unmatched)
    // identity. Only the native session provider may ever supply Active.
    val activeSession =
        (session as? RetroAchievementsDisplayState.Active)?.takeIf { active ->
            val hash = (identification as? LocalIdentification.Identified)?.identity?.md5
            hash != null && active.romMd5.equals(hash, ignoreCase = true)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NesBoxArtwork(game = game, modifier = Modifier.width(76.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.opennes_ra_game_label),
                    color = GameMenuStyle.red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = game.displayName(),
                    color = GameMenuStyle.foreground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            color = GameMenuStyle.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GameMenuStyle.border),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = GameMenuStyle.red,
                    )
                    val statusTitle =
                        when {
                            activeSession != null -> R.string.opennes_ra_runtime_ready
                            session is RetroAchievementsDisplayState.Connecting ->
                                R.string.opennes_ra_runtime_connecting
                            session is RetroAchievementsDisplayState.NoMatchingGame ->
                                R.string.opennes_ra_runtime_no_match
                            session is RetroAchievementsDisplayState.Active -> R.string.opennes_ra_runtime_hash_mismatch
                            else -> R.string.opennes_ra_runtime_unavailable
                        }
                    Text(
                        text = stringResource(statusTitle),
                        color = GameMenuStyle.foreground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                if (activeSession == null) {
                    Text(
                        text =
                            stringResource(
                                if (session is RetroAchievementsDisplayState.NoMatchingGame) {
                                    R.string.opennes_ra_no_match_explanation
                                } else {
                                    R.string.opennes_ra_native_short
                                },
                            ),
                        color = GameMenuStyle.muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        activeSession?.let { sessionData ->
            VerifiedAchievementList(
                username = sessionData.username,
                romMd5 = sessionData.romMd5,
                achievements = sessionData.achievements,
            )
        }

        Surface(
            color = GameMenuStyle.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GameMenuStyle.border),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.opennes_ra_lookup_heading),
                    color = GameMenuStyle.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                when (val resolved = recognizedGame) {
                    null ->
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            color = GameMenuStyle.red,
                            strokeWidth = 2.dp,
                        )
                    is RetroAchievementsNativeGameResolver.Result.VerifiedGame ->
                        Text(
                            text = stringResource(R.string.opennes_ra_lookup_match, resolved.id),
                            color = GameMenuStyle.foreground,
                        )
                    RetroAchievementsNativeGameResolver.Result.NoMatch ->
                        Text(
                            text = stringResource(R.string.opennes_ra_lookup_not_found),
                            color = GameMenuStyle.muted,
                        )
                    RetroAchievementsNativeGameResolver.Result.Unavailable ->
                        Text(
                            text = stringResource(R.string.opennes_ra_lookup_offline),
                            color = GameMenuStyle.muted,
                        )
                }
                Text(
                    text = stringResource(R.string.opennes_ra_lookup_notice),
                    color = GameMenuStyle.muted,
                    fontSize = 12.sp,
                )
            }
        }

        RetroAchievementsWebSection(
            romMd5 = (identification as? LocalIdentification.Identified)?.identity?.md5,
            console = (identification as? LocalIdentification.Identified)?.identity?.console,
        )
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(
                stringResource(
                    if (advancedExpanded) {
                        R.string.opennes_ra_advanced_hide
                    } else {
                        R.string.opennes_ra_advanced_show
                    },
                ),
            )
        }
        if (advancedExpanded) {
            NativeBridgeDiagnostics()

            Text(
                text = stringResource(R.string.opennes_ra_rom_identification),
                color = GameMenuStyle.foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                color = GameMenuStyle.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GameMenuStyle.border),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = null,
                            tint = GameMenuStyle.muted,
                        )
                        when (val state = identification) {
                            LocalIdentification.Checking -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp),
                                    strokeWidth = 2.dp,
                                    color = GameMenuStyle.red,
                                )
                                Text(
                                    text = stringResource(R.string.opennes_ra_identifying),
                                    color = GameMenuStyle.muted,
                                )
                            }
                            LocalIdentification.Unavailable -> {
                                Text(
                                    text = stringResource(R.string.opennes_ra_identity_unavailable),
                                    color = GameMenuStyle.muted,
                                )
                            }
                            is LocalIdentification.Identified -> {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.opennes_ra_identified,
                                            state.identity.console.name,
                                        ),
                                    color = GameMenuStyle.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    val state = identification
                    if (state is LocalIdentification.Identified) {
                        Text(
                            text = stringResource(R.string.opennes_ra_md5_label),
                            color = GameMenuStyle.muted,
                            fontSize = 12.sp,
                        )
                        SelectionContainer {
                            Text(
                                text = state.identity.md5,
                                color = GameMenuStyle.foreground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.opennes_ra_local_hash_notice),
                        color = GameMenuStyle.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

private sealed interface LocalIdentification {
    data object Checking : LocalIdentification

    data object Unavailable : LocalIdentification

    data class Identified(
        val identity: NesRetroAchievementsHash.Identity,
    ) : LocalIdentification
}
