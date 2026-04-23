package ch.threema.app.compose.theme.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import ch.threema.app.R

object CustomColors {

    val listItemHighlightedContainer: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.settings_multipane_selection_bg)

    val availabilityStatusIconNone: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_icon_none)

    val availabilityStatusContainerNone: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_container_none)

    val availabilityStatusIconUnavailable: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_icon_unavailable)

    val availabilityStatusContainerUnavailable: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_container_unavailable)

    val availabilityStatusOnContainerUnavailable: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_on_container_unavailable)

    val availabilityStatusIconBusy: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_icon_busy)

    val availabilityStatusContainerBusy: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_container_busy)

    val availabilityStatusOnContainerBusy: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.availability_status_on_container_busy)
}
