package com.swordfish.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.ui.LemuroidControlBackground
import com.swordfish.touchinput.radial.ui.LemuroidCrossForeground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlCross
import gg.padkit.ids.Id

context(PadKitScope)
@Composable
fun LemuroidControlCross(
    modifier: Modifier = Modifier,
    id: Id.DiscreteDirection,
    allowDiagonals: Boolean = true,
    expandedTouchArea: Boolean = false,
    background: @Composable () -> Unit = {
        LemuroidControlBackground()
    },
    foreground: @Composable (State<Offset>) -> Unit = {
        LemuroidCrossForeground(
            allowDiagonals = allowDiagonals,
            directionState = it,
        )
    },
) {
    val theme = LocalLemuroidPadTheme.current
    ControlCross(
        // NES D-pad uses a narrower inset so thumbs can reach the outer
        // directions without reducing the drawn cross itself.
        modifier = modifier.padding(if (expandedTouchArea) 1.dp else theme.padding),
        id = id,
        allowDiagonals = allowDiagonals,
        background = background,
        foreground = foreground,
    )
}
