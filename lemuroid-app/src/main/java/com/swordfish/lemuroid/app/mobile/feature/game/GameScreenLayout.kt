package com.swordfish.lemuroid.app.mobile.feature.game

import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ChainStyle
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension

object GameScreenLayout {
    const val CONSTRAINTS_BOTTOM_CONTAINER = "bottomContainer"
    const val CONSTRAINTS_LEFT_CONTAINER = "leftContainer"
    const val CONSTRAINTS_RIGHT_CONTAINER = "rightContainer"
    const val CONSTRAINTS_LEFT_PAD = "leftPad"
    const val CONSTRAINTS_RIGHT_PAD = "rightPad"
    const val CONSTRAINTS_GAME_VIEW = "gameView"
    const val CONSTRAINTS_GAME_CONTAINER = "gameContainer"

    fun buildConstraintSet(
        isLandscape: Boolean,
        allowTouchOverlay: Boolean,
        showTouchPads: Boolean = true,
        sidePanelWidthDp: Float? = null,
    ): ConstraintSet {
        return when {
            // With a physical gamepad, no virtual pad exists to constrain the
            // game view. Do not attach its bottom edge to a missing pad.
            !showTouchPads -> buildConstraintSetWithoutTouchPads()
            !isLandscape -> buildConstraintSetPortrait()
            sidePanelWidthDp != null -> buildConstraintSetLandscapeSidePanels(sidePanelWidthDp)
            allowTouchOverlay -> buildConstraintSetLandscape()
            else -> buildConstraintSetLandscapeNoOverlay()
        }
    }

    private fun buildConstraintSetWithoutTouchPads(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    private fun buildConstraintSetPortrait(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)
            val bottomContainer = createRefFor(CONSTRAINTS_BOTTOM_CONTAINER)

            val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)
            // Use whichever cluster rises higher when left/right scaling differs.
            val controlsTop = createTopBarrier(leftPad, rightPad)

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top)
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                bottom.linkTo(controlsTop)
            }

            constrain(bottomContainer) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(controlsTop)
                bottom.linkTo(parent.bottom)
            }

            constrain(gamePadChain) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
            }

            constrain(rightPad) {
                width = Dimension.fillToConstraints
                bottom.linkTo(parent.bottom)
            }

            constrain(leftPad) {
                width = Dimension.fillToConstraints
                bottom.linkTo(parent.bottom)
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    private fun buildConstraintSetLandscape(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)

            val gamePadChain = createHorizontalChain(leftPad, rightPad, chainStyle = ChainStyle.SpreadInside)

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                top.linkTo(parent.top)
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                bottom.linkTo(parent.bottom)
            }

            constrain(gamePadChain) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
            }

            constrain(rightPad) {
                verticalBias = 1.0f
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
            }

            constrain(leftPad) {
                verticalBias = 1.0f
                bottom.linkTo(parent.bottom)
                top.linkTo(parent.top)
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    /**
     * Both framed and handheld modes get equal-width controller zones
     * surrounding a truly centered 4:3 NES viewport. The layout does not
     * depend on saved controller sizes or secondary radial button offsets.
     */
    private fun buildConstraintSetLandscapeSidePanels(sideWidthDp: Float): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)

            // The virtual input surfaces span the full viewport; only the
            // video viewport respects the visual side gutters. Otherwise a
            // moved button is clipped by the original grip's hit-test bounds.
            constrain(leftPad) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
            }
            constrain(rightPad) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
            }
            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(parent.absoluteLeft, margin = sideWidthDp.dp)
                absoluteRight.linkTo(parent.absoluteRight, margin = sideWidthDp.dp)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }
        }
    }

    private fun buildConstraintSetLandscapeNoOverlay(): ConstraintSet {
        return ConstraintSet {
            val gameView = createRefFor(CONSTRAINTS_GAME_VIEW)
            val leftPad = createRefFor(CONSTRAINTS_LEFT_PAD)
            val rightPad = createRefFor(CONSTRAINTS_RIGHT_PAD)
            val gameContainer = createRefFor(CONSTRAINTS_GAME_CONTAINER)
            val leftContainer = createRefFor(CONSTRAINTS_LEFT_CONTAINER)
            val rightContainer = createRefFor(CONSTRAINTS_RIGHT_CONTAINER)

            constrain(leftPad) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.wrapContent // Takes as much space as needed
            }

            constrain(gameView) {
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
                absoluteLeft.linkTo(leftPad.absoluteRight)
                absoluteRight.linkTo(rightPad.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints // Expands between leftPad and rightPad
            }

            constrain(rightPad) {
                absoluteRight.linkTo(parent.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.wrapContent // Takes as much space as needed
            }

            constrain(gameContainer) {
                absoluteLeft.linkTo(gameView.absoluteLeft)
                absoluteRight.linkTo(gameView.absoluteRight)
                top.linkTo(gameView.top)
                bottom.linkTo(gameView.bottom)
            }

            constrain(leftContainer) {
                absoluteLeft.linkTo(parent.absoluteLeft)
                absoluteRight.linkTo(leftPad.absoluteRight)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }

            constrain(rightContainer) {
                absoluteRight.linkTo(parent.absoluteRight)
                absoluteLeft.linkTo(rightPad.absoluteLeft)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }
        }
    }
}
