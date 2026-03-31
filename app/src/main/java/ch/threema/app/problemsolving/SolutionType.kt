package ch.threema.app.problemsolving

import androidx.annotation.StringRes

sealed class SolutionType {
    /**
     * For problems that require the user to make changes on a separate setting screen.
     */
    data object ToSettings : SolutionType()

    /**
     * For problems that can be instantly resolved by a single button press.
     */
    data class InstantAction(@StringRes val label: Int) : SolutionType()
}
