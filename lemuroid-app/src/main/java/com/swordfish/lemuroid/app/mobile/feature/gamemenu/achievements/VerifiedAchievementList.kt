package com.swordfish.lemuroid.app.mobile.feature.gamemenu.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuStyle
import com.swordfish.lemuroid.app.shared.achievements.VerifiedAchievement

private enum class AchievementFilter {
    ALL,
    UNLOCKED,
    LOCKED,
}

/**
 * Steam-style in-game list that accepts only data from a verified game session.
 * No sample entries are supplied, so there is no fake award or progress state.
 */
@Composable
internal fun VerifiedAchievementList(
    username: String,
    romMd5: String,
    achievements: List<VerifiedAchievement>,
    readOnlyWeb: Boolean = false,
) {
    var filter by remember(romMd5, username) { mutableStateOf(AchievementFilter.ALL) }
    val total = achievements.size
    val unlocked = achievements.count { it.unlocked }
    val unlockedPoints = achievements.filter { it.unlocked }.sumOf { it.points.coerceAtLeast(0) }
    val completion = if (total == 0) 0f else unlocked.toFloat() / total
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text =
                stringResource(
                    if (readOnlyWeb) {
                        R.string.opennes_ra_web_progress_list_title
                    } else {
                        R.string.opennes_ra_verified_list_title
                    },
                ),
            color = GameMenuStyle.foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(
            text =
                if (readOnlyWeb) {
                    stringResource(R.string.opennes_ra_web_progress_account, username)
                } else {
                    stringResource(R.string.opennes_ra_signed_in_as, username)
                },
            color = GameMenuStyle.muted,
            fontSize = 12.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.opennes_ra_completion, unlocked, total),
                color = GameMenuStyle.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.opennes_ra_points, unlockedPoints),
                color = GameMenuStyle.muted,
                fontSize = 12.sp,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GameMenuStyle.border),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(completion.coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(GameMenuStyle.red),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AchievementFilter.ALL to R.string.opennes_ra_filter_all,
                AchievementFilter.UNLOCKED to R.string.opennes_ra_filter_unlocked,
                AchievementFilter.LOCKED to R.string.opennes_ra_filter_locked,
            ).forEach { (option, nameId) ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(stringResource(nameId)) },
                )
            }
        }
        val filtered =
            achievements.filter { achievement ->
                when (filter) {
                    AchievementFilter.ALL -> true
                    AchievementFilter.UNLOCKED -> achievement.unlocked
                    AchievementFilter.LOCKED -> !achievement.unlocked
                }
            }
        if (filtered.isEmpty()) {
            Text(
                text = stringResource(R.string.opennes_ra_no_results),
                color = GameMenuStyle.muted,
                fontSize = 13.sp,
            )
        }
        filtered.forEach { achievement ->
            Surface(
                color = GameMenuStyle.surface,
                border = BorderStroke(1.dp, GameMenuStyle.border),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector =
                            if (achievement.unlocked) {
                                Icons.Outlined.CheckCircleOutline
                            } else {
                                Icons.Outlined.Lock
                            },
                        tint = if (achievement.unlocked) GameMenuStyle.red else GameMenuStyle.muted,
                        contentDescription = null,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = achievement.title,
                            color = GameMenuStyle.foreground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = achievement.description,
                            color = GameMenuStyle.muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = stringResource(R.string.opennes_ra_points, achievement.points.coerceAtLeast(0)),
                            color = GameMenuStyle.muted,
                            fontSize = 12.sp,
                        )
                        if (!achievement.unlocked && achievement.progressPercent != null) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.opennes_ra_partial_progress,
                                        achievement.progressPercent.coerceIn(0, 100),
                                    ),
                                color = GameMenuStyle.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    // Points are shown below the description so long titles can wrap.
                }
            }
        }
    }
}
