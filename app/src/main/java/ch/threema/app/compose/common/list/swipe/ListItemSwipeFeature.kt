package ch.threema.app.compose.common.list.swipe

import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * @param onSwipe Will be called if the distance of the swipe exceeded the [SWIPE_TRIGGER_FROM_PERCENT]
 * @param hapticFeedback Set to `null` to turn off. If a value is set, there is no guarantee for the feedback to happen, since this is
 * depending on the device and its OS version
 * @param contentColor Must have contrast on [containerColor]
 */
@Immutable
sealed interface ListItemSwipeFeature<T> {

    val direction: SwipeDirection
    val onSwipe: (T) -> Unit
    val containerColor: Color
    val contentColor: Color
    val hapticFeedback: HapticFeedbackType?
    val state: ListItemSwipeFeatureState

    @Immutable
    data class StartToEnd<T>(
        override val onSwipe: (T) -> Unit,
        override val hapticFeedback: HapticFeedbackType? = HapticFeedbackType.Confirm,
        override val containerColor: Color,
        override val contentColor: Color,
        override val state: ListItemSwipeFeatureState,
    ) : ListItemSwipeFeature<T> {
        override val direction: SwipeDirection = SwipeDirection.StartToEnd
    }

    @Immutable
    data class EndToStart<T>(
        override val onSwipe: (T) -> Unit,
        override val hapticFeedback: HapticFeedbackType? = HapticFeedbackType.Confirm,
        override val containerColor: Color,
        override val contentColor: Color,
        override val state: ListItemSwipeFeatureState,
    ) : ListItemSwipeFeature<T> {
        override val direction: SwipeDirection = SwipeDirection.EndToStart
    }

    companion object {

        /**
         *  Defines the percentage of horizontal pixels the user has to swipe the list item in order to trigger the action
         */
        @FloatRange(from = 0.0, to = 1.0)
        const val SWIPE_TRIGGER_FROM_PERCENT = 0.25f
    }
}

enum class SwipeDirection {

    /**
     * In a LTR layout this is a swipe starting from the left and ending on the right
     */
    StartToEnd,

    /**
     * In a LTR layout this is a swipe starting from the right and ending on the left
     */
    EndToStart,
}

@Immutable
data class ListItemSwipeFeatureState(
    @DrawableRes val icon: Int,
    val text: String,
)
